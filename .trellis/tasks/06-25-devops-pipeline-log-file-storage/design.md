# DevOps Pipeline Log File Storage Design

## Decision

Use local structured log files as the primary source of truth for pipeline build
output, keep Spring SSE for live viewing, and optionally archive completed step
logs through the existing infra `FileApi`.

Do not add Redis, Kafka, ELK, Loki, or WebSocket in this task.

## Current Problems

Current line logging inserts each output line into `dev_pipeline_run_log_line`.
That has several practical issues:

- Build output is high-volume append-only text, which is a poor primary workload
  for MySQL.
- SSE replay currently polls MySQL every second for new rows.
- Each append selects the max line number for the step and then inserts a row.
- The current caps, 10000 persisted lines and 2000 characters per line, make
  large builds visibly truncated.
- `logMode=CONTAINER` avoids DB writes, but it depends on the runtime container
  still existing and does not satisfy historical log viewing after cleanup.

## Architecture

```text
Step output callback
  -> PipelineRunLogLineService.appendLine(...)
  -> PipelineLogStorageService.append(...)
       -> append structured event to run JSONL file
       -> append rendered text to step raw log file
       -> update in-memory cursor/line counters
       -> publish event to in-process SSE subscribers
  -> handler keeps small resultJson.logLines summary only

Frontend historical query
  -> GET /devops/pipeline-run/{runId}/log-lines
  -> read run JSONL from afterId cursor
  -> filter by optional stepId
  -> return PipelineRunLogLineRespVO list

Frontend live query
  -> GET /devops/pipeline-run/{runId}/log-lines/stream
  -> replay file from afterId cursor
  -> subscribe to in-process broadcaster
  -> complete when run/step reaches terminal state and replay buffer is empty

Step completion
  -> finalize step raw log metadata
  -> optionally upload raw step log to FileApi
  -> update dev_pipeline_run_log.log_file_url and archive metadata
```

## Storage Layout

Add a configurable root:

```properties
yudao.devops.pipeline.logs.root=${java.io.tmpdir}/gone-devops/pipeline-logs
yudao.devops.pipeline.logs.archive-enabled=true
yudao.devops.pipeline.logs.archive-directory=devops/pipeline-logs
yudao.devops.pipeline.logs.archive-max-bytes=52428800
yudao.devops.pipeline.logs.max-run-bytes=209715200
yudao.devops.pipeline.logs.max-line-bytes=16384
```

Local files use only backend-derived names. No user-provided path segment should
enter the file path.

```text
{root}/tenants/tenant-{tenantId}/runs/run-{runId}/
  run.jsonl
  steps/
    run-log-{runLogId}-{safeStepId}.log
```

`run.jsonl` is the canonical structured event file for both run-level and
step-scoped log viewing. A step raw log file is used for full-log download and
object-storage archiving.

## Log Event Format

Each `run.jsonl` line is a compact JSON object:

```json
{
  "id": 12891,
  "pipelineRunId": 800,
  "runLogId": 900,
  "stageId": "build_stage",
  "jobId": "build_job",
  "stepId": "build_step",
  "lineNo": 42,
  "streamType": "stdout",
  "content": "mvn -B clean package",
  "createTime": "2026-06-25T10:12:13"
}
```

`id` is an opaque numeric cursor. In the file-backed implementation it should be
the byte offset immediately after the JSONL record is written. This makes
`afterId` efficient: the reader can seek directly to that offset.

`lineNo` remains display metadata scoped to one `runLogId`.

If a line exceeds `max-line-bytes`, cap the event `content` and append a marker.
The raw step log may still keep the full line unless the run-level byte limit is
already reached.

## API Contracts

Keep existing endpoints:

- `GET /devops/pipeline-run/{runId}/logs`
- `GET /devops/pipeline-run/{runId}/log-lines?stepId=&afterId=&limit=`
- `GET /devops/pipeline-run/{runId}/log-lines/stream?stepId=&afterId=`

Keep `PipelineRunLogLineRespVO` shape.

Change only the cursor semantics:

- `id` is an opaque backend cursor.
- `afterId` means "resume after this cursor".
- File-backed mode uses byte offsets.
- Legacy DB-backed mode can still use row ids.

Add a raw log read endpoint:

- `GET /devops/pipeline-run/{runId}/logs/{runLogId}/raw`
- Requires `devops:pipeline:query`.
- Streams the local step raw log if present.
- If only `log_file_url` is available, return a presigned/readable URL when the
  infra file service supports it, or proxy the file when practical.

The structured `/logs` endpoint remains the source for status, stage/job/step
metadata, runtime metadata, `logFileUrl`, `logTruncated`, and error messages.

## Database Changes

Keep `dev_pipeline_run_log_line` for old data and fallback only. New file-backed
append paths should not insert into it.

Extend `dev_pipeline_run_log`:

```sql
ALTER TABLE dev_pipeline_run_log
  ADD COLUMN log_file_path varchar(500) DEFAULT NULL COMMENT '本地日志文件相对路径',
  ADD COLUMN log_size_bytes bigint DEFAULT NULL COMMENT '日志文件大小，字节',
  ADD COLUMN log_line_count bigint DEFAULT NULL COMMENT '日志行数',
  ADD COLUMN log_archive_status varchar(32) DEFAULT NULL COMMENT '日志归档状态',
  ADD COLUMN log_archived_at datetime DEFAULT NULL COMMENT '日志归档时间';
```

Archive status values:

- `LOCAL_ONLY`: local log exists and archive is disabled or not attempted.
- `PENDING`: archive is planned but not finished.
- `UPLOADED`: `log_file_url` points to the archived file.
- `FAILED`: archive failed, local file remains authoritative.
- `SKIPPED_TOO_LARGE`: file exceeded `archive-max-bytes`; local file remains
  authoritative.

`log_file_path` must be relative to the configured log root. Never expose or
persist absolute filesystem paths in API responses.

## Services

### PipelineLogStorageService

New service responsible for append, replay, and finalize operations.

Suggested methods:

```java
PipelineRunLogLineRespVO appendLine(PipelineRunLogDO runLog, String streamType, String content);

List<PipelineRunLogLineRespVO> readLines(Long pipelineRunId, String stepId, Long afterId, Integer limit);

void completeStepLog(PipelineRunLogDO runLog);

Path resolveRawLogPath(PipelineRunLogDO runLog);
```

Responsibilities:

- Validate/sanitize stream type.
- Allocate per-step `lineNo`.
- Write JSONL event under a per-run lock.
- Write raw step text under a per-step lock.
- Maintain file cursor semantics.
- Enforce `max-run-bytes`.
- Mark `logTruncated=true` when the run-level cap is hit.
- Return DTOs without exposing local absolute paths.

### PipelineLogBroadcaster

In-process SSE fanout:

- Subscribers are keyed by `pipelineRunId`.
- Optional `stepId` filter is applied per subscriber.
- On append, broadcast the `PipelineRunLogLineRespVO`.
- On emitter completion/error/timeout, unregister subscriber.
- No cross-process delivery is attempted in this task.

### PipelineLogArchiveService

Finalizes step raw logs:

- Read raw step file only after step completion.
- If archive disabled, set `LOCAL_ONLY`.
- If file size exceeds `archive-max-bytes`, set `SKIPPED_TOO_LARGE`.
- Otherwise call `FileApi.createFile(bytes, name, directory, "text/plain")`.
- On success, update `log_file_url`, `log_archive_status=UPLOADED`, and
  `log_archived_at`.
- On failure, set `FAILED` but do not fail the pipeline step.

Because `FileApi.createFile` takes `byte[]`, archive size must be bounded in
this MVP. Streaming object upload can be a later infra enhancement.

## Integration Points

### PipelineRunLogLineServiceImpl

Refactor it into an API-facing service:

- `appendLine` delegates to `PipelineLogStorageService.appendLine`.
- `getLogLines` first validates the run, then:
  - if a file-backed log exists, read from file;
  - otherwise fallback to `PipelineRunLogLineMapper.selectListByCursor`.
- `streamLogLines`:
  - validates the run;
  - creates emitter;
  - replays from file/legacy DB after `afterId`;
  - subscribes to `PipelineLogBroadcaster`;
  - closes on terminal run/step after no pending replay remains.

### Step Handlers

Handlers should keep calling `PipelineRunLogLineService.appendLine`.

Affected handlers include:

- `CommandStepHandler`
- `PrivateRegistryDockerBuildStepHandler`
- `DockerImageExportObjectStorageStepHandler`
- `DockerImageArchiveImportStepHandler`

No handler should write full output to `resultJson`. Keep only a small summary,
for example the existing first 500 lines, for compatibility.

### Step Completion

Call `completeStepLog(runLog)` when a step reaches terminal status.

Preferred integration is in `PipelineStepLogHelper` terminal methods such as
mark success, failed, and canceled, because most handlers already use it. For
handlers that update `PipelineRunLogDO` directly, add explicit completion calls
or migrate them through the helper when low risk.

## Compatibility

### Existing Frontend

Existing frontend calls can keep using:

- `id` from the last event
- `afterId` on reconnect
- `log-line` SSE events

Frontend code should treat `id` as opaque. It should not assume the value is a
database row id or that it increments by 1.

### Historical DB Rows

No backfill is required for old runs.

If a run has no file-backed log path and has rows in `dev_pipeline_run_log_line`,
the service keeps the current DB read behavior.

### `logMode=CONTAINER`

`CONTAINER` mode can remain for direct Docker log debugging, but it should not
be the recommended way to satisfy frontend historical logs. Pipeline log viewing
should use the file-backed `PipelineRunLogLineService` path.

## Error Handling

- Missing run: keep `PIPELINE_RUN_NOT_EXISTS`.
- Missing run log for raw endpoint: add or reuse a pipeline run log not found
  error constant.
- Missing local file but uploaded archive exists: return the archived URL or a
  presigned URL instead of throwing when possible.
- Missing local file and no archive: return a clear business error such as
  pipeline log file not found.
- Archive failure: log a warning, update archive status, do not fail the step.

## Security

- Never expose `log_file_path` as an absolute filesystem path.
- Normalize and validate all derived paths stay under configured log root.
- Do not use user-controlled values as path segments except after strict
  sanitization; prefer ids.
- Keep `@PreAuthorize("@ss.hasPermission('devops:pipeline:query')")` on all log
  read endpoints.
- Continue masking credentials before they reach log append calls. This task
  must not weaken existing credential handling.

## Retention

MVP retention can be conservative:

- Keep local files for a configured retention period.
- Do not delete local files immediately after object archive, because the
  backend needs local JSONL for efficient historical in-page viewing.
- Add a scheduled cleanup only if the project already has an obvious scheduler
  pattern; otherwise leave retention cleanup as a follow-up with config and
  manual operator cleanup documented.

## Rollout Plan

1. Add file-backed storage and use it for new log writes.
2. Keep legacy DB read fallback.
3. Keep `dev_pipeline_run_log_line` table unchanged.
4. Update docs/spec to say full frontend log viewing uses file-backed storage.
5. After production confidence, create a separate cleanup task to stop relying
   on legacy DB rows and optionally archive/delete old rows.

## Trade-offs

- Local files are simpler and faster than DB rows, but live fanout is only
  single-backend-instance in this task.
- Byte-offset cursor is efficient for file replay, but it makes `id` opaque
  instead of human-friendly.
- Object archive through `FileApi` is easy to integrate but not streaming; large
  archive upload must be bounded.
- Keeping legacy DB fallback adds code, but avoids breaking old pipeline runs.

## Files Likely To Change

- `PipelineRunController`
- `PipelineRunLogLineService` / `PipelineRunLogLineServiceImpl`
- new log storage/broadcast/archive services under
  `service/pipeline/execution`
- `PipelineRunLogDO`
- `PipelineRunLogRespVO`
- `sql/mysql/devops.sql`
- `PipelineStepLogHelper`
- log-producing step handler tests
- `PipelineRunLogLineServiceImplTest`
- `PIPELINE_YAML_SPEC.md`
- `.trellis/spec/backend/devops-pipeline-guidelines.md`

