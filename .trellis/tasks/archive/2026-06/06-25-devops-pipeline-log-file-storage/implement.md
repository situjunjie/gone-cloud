# DevOps Pipeline Log File Storage Implementation Plan

## Preconditions

- Do not start implementation until the user reviews this PRD/design/plan or
  explicitly asks to proceed.
- Inline Codex mode: implementation will happen in the main session after
  loading `trellis-before-dev`.
- No sub-agent context curation is required for this task.

## Implementation Checklist

### 1. Configuration and Model

- Add pipeline log properties for root path, archive enablement, archive
  directory, archive max bytes, max run bytes, and max line bytes.
- Extend `PipelineRunLogDO` with:
  - `logFilePath`
  - `logSizeBytes`
  - `logLineCount`
  - `logArchiveStatus`
  - `logArchivedAt`
- Extend `PipelineRunLogRespVO` with the same non-sensitive metadata where useful
  to the frontend.
- Update `sql/mysql/devops.sql` for the new columns.
- Search for `log_file_url`, `logTruncated`, `dev_pipeline_run_log`, and
  `PipelineRunLogRespVO` to update all mappings consistently.

### 2. File Log Storage

- Add a small value/model class for file-backed log events if useful.
- Add `PipelineLogStorageService` and implementation.
- Implement path resolution under the configured root:
  - run JSONL path
  - step raw log path
  - relative path derivation for DB storage
- Implement append:
  - validate runLog and content
  - normalize stream type
  - allocate step line number
  - write raw step text
  - write structured JSONL event
  - set response `id` to the opaque cursor
  - enforce max bytes and mark `logTruncated`
- Implement replay:
  - seek to `afterId` when file-backed
  - parse JSONL line by line
  - filter optional `stepId`
  - cap by request limit
  - return `PipelineRunLogLineRespVO`
- Keep all local path access normalized under root.

### 3. SSE Broadcaster

- Add `PipelineLogBroadcaster`.
- Register/unregister emitters by run id and optional step id.
- Broadcast appended events to matching emitters.
- Close and unregister emitters on completion, timeout, and errors.
- Keep the existing `pipelineLogStreamExecutor`.

### 4. Service Integration

- Refactor `PipelineRunLogLineServiceImpl`:
  - `appendLine` delegates to file storage and broadcaster.
  - `getLogLines` uses file storage when available, otherwise DB fallback.
  - `streamLogLines` replays first, then subscribes to broadcaster.
  - terminal-state checks remain based on `PipelineRunDO` and `PipelineRunLogDO`.
- Keep `PipelineRunLogLineMapper` for legacy fallback.
- Keep old DB line table untouched.

### 5. Archive Integration

- Add `PipelineLogArchiveService`.
- Inject `FileApi`.
- On step terminal:
  - calculate file size and line count
  - update local metadata
  - if enabled and under max archive bytes, upload raw step log through `FileApi`
  - update `logFileUrl`, archive status, and archive time
  - if upload fails, record `FAILED` and log a warning without failing the step
- Integrate completion through `PipelineStepLogHelper` terminal methods first.
- Add explicit completion calls only for handlers that bypass the helper.

### 6. Raw Log Endpoint

- Add `GET /devops/pipeline-run/{runId}/logs/{runLogId}/raw`.
- Require `devops:pipeline:query`.
- Validate run and run log ownership.
- Stream the local raw file when present.
- If only `logFileUrl` exists, return or proxy the archived URL according to the
  simplest existing project pattern.

### 7. Handler Compatibility

- Keep handler append calls through `PipelineRunLogLineService`.
- Keep small `resultJson.logLines` summaries only.
- Ensure `CommandStepHandler` `logMode=PIPELINE` uses file storage.
- Do not rely on `logMode=CONTAINER` for historical pipeline logs.
- Ensure `PrivateRegistryDockerBuildStepHandler` docker-java build/push output
  goes through the same file-backed append path.

### 8. Documentation and Spec Updates

- Update `yudao-module-devops/PIPELINE_YAML_SPEC.md` log behavior notes.
- Update `.trellis/spec/backend/devops-pipeline-guidelines.md`:
  - file-backed log storage is primary
  - `id/afterId` is an opaque cursor
  - `dev_pipeline_run_log_line` is legacy fallback, not new primary storage
- Add any migration notes needed for `log_file_path` and archive status.

## Tests

### Unit Tests

- `PipelineRunLogLineServiceImplTest`
  - append writes file-backed event
  - get lines replays from file cursor
  - stream replays existing file lines and registers for live lines
  - terminal step/run completes stream
  - missing run throws existing error
  - legacy DB fallback still works when no file exists

- New storage service test
  - path stays under root
  - cursor resumes without duplicate lines
  - step filter works
  - max run bytes marks truncation
  - max line bytes caps event content

- New archive service test
  - archive disabled -> `LOCAL_ONLY`
  - upload success -> `UPLOADED` and `logFileUrl`
  - upload failure -> `FAILED` and step still succeeds
  - too large -> `SKIPPED_TOO_LARGE`

- Handler tests
  - `CommandStepHandlerTest`
  - `PrivateRegistryDockerBuildStepHandlerTest`
  - `DockerImageExportObjectStorageStepHandlerTest`
  - `DockerImageArchiveImportStepHandlerTest`

### Verification Commands

Use focused commands first:

```bash
mvn -pl yudao-module-devops/yudao-module-devops-server -am \
  -Dtest='PipelineRunLogLineServiceImplTest,CommandStepHandlerTest,PrivateRegistryDockerBuildStepHandlerTest,DockerImageExportObjectStorageStepHandlerTest,DockerImageArchiveImportStepHandlerTest' \
  -Dsurefire.failIfNoSpecifiedTests=false test
```

Then compile the module:

```bash
mvn -pl yudao-module-devops/yudao-module-devops-server -am -DskipTests compile
```

Run `git diff --check` before reporting implementation complete.

## Risk Points

- Cursor compatibility: frontend must treat `id` as opaque. If it assumes a DB
  row id, the frontend needs a small adaptation.
- File upload memory: existing `FileApi` takes `byte[]`, so archive upload must
  be max-size bounded.
- Multi-backend deployment: in-process SSE fanout only supports live delivery
  from the backend instance executing the build. Reconnect replay still works
  from local files on the same host.
- Local retention: if operators delete local files before object archive is
  available, in-page history may degrade to raw download only.
- Concurrent jobs: run-level JSONL appends must be serialized per run so cursor
  offsets stay valid.

## Rollback Plan

- Keep `dev_pipeline_run_log_line` and legacy read fallback.
- If file-backed append causes issues, temporarily switch
  `PipelineRunLogLineServiceImpl.appendLine` back to DB insertion while leaving
  new columns unused.
- New DB columns are additive and can remain harmless during rollback.

## Review Gate

Before `task.py start`, confirm:

- `afterId` may be treated as an opaque cursor rather than a DB row id.
- Local files are acceptable as the primary historical in-page log source for
  this MVP, with object storage as a bounded archive/download path.
