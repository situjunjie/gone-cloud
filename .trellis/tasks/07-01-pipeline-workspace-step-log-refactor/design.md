# Design: Workspace-Backed Step Logs

## Current State

- `PipelineLogStorageServiceImpl` owns a separate `yudao.devops.pipeline.logs.root` and computes files outside the pipeline workspace.
- `CommandStepHandler` chooses between `PIPELINE` and `CONTAINER` log modes and passes `hostLogFile` / `containerLogFile` to `DockerPipelineCommandExecutor`.
- `PipelineRunLogLineServiceImpl` currently mixes transient broadcast, file reads, optional run-level reads, and DB fallback.
- `PipelineStepLogHelper` calls `completeStepLog` as soon as a step reaches success or failure.
- `PipelineExecutionEngine` currently resets the stable run workspace at the start of execution; there is no explicit end-of-run workspace cleanup path in the engine.

## Target Architecture

Introduce a workspace-backed step log boundary:

```text
Pipeline step handler
  -> PipelineStepLogService.open(ctx, runLog)
       -> WorkspacePipelineStepLogHandle.accept(stream, line)
            -> append line to workspace file
            -> update in-memory/file cursor metadata
            -> publish SSE line event
       -> close/finalize metadata
PipelineExecutionEngine terminal path
  -> PipelineStepLogService.archiveRunLogs(run)
       -> FileApi.createFile(...)
       -> update dev_pipeline_run_log.log_file_url/archive metadata
  -> workspace cleanup/reset policy
```

## File Layout

Workspace-relative path:

```text
.devops/pipeline-runs/run-{runId}/logs/{safeStageId}/{safeJobId}/{safeStepId}.log
```

Rules:

- Store only the relative path in `PipelineRunLogDO.logFilePath`.
- Resolve relative paths against the run workspace when reading, streaming, uploading, or raw downloading.
- Sanitize stage/job/step ids with the same conservative character set used by current log filenames: `A-Za-z0-9_.-`; replace all other characters with `_`.
- Keep all platform-managed logs under `.devops/` so source preparation and artifact conventions remain easy to reason about.

## Service Boundary

Replace the current broad `PipelineLogStorageService` role with narrower responsibilities:

- `PipelineStepLogService`
  - `open(PipelineStepContext ctx, PipelineRunLogDO runLog)`
  - `readLines(Long runId, String stepId, Long afterId, Integer limit)` where `stepId` is required
  - `streamLogLines(Long runId, String stepId, Long afterId)` where `stepId` is required
  - `getRawLogResource(Long runId, Long runLogId)`
  - `finalizeStepLog(PipelineRunLogDO runLog)`
  - `archiveRunLogs(PipelineRunDO run)`
- `PipelineStepLogHandle`
  - `accept(String streamType, String line)`
  - `close()`

Implementation may keep the existing class names during migration if that reduces churn, but the internal responsibilities should follow this split.

## Cursor Model

Use an opaque file cursor derived from the step log identity and file byte offset, similar to the current `runLogId * factor + offset` encoding.

- `afterId` remains the only reconnect cursor.
- `lineNo` remains display metadata scoped to a run log.
- New frontend reads are always step-scoped. Do not implement run-scoped aggregation over multiple step files for this refactor.
- For legacy runs without workspace files, fall back to `dev_pipeline_run_log_line`.

## SSE Contract

Line event:

```text
event: log-line
id: {opaqueCursor}
data: PipelineRunLogLineRespVO
```

Completion event:

```json
{
  "pipelineRunId": 1024,
  "stepId": "npm-build",
  "runLogId": 2048,
  "status": "SUCCESS",
  "terminal": true,
  "logFileUrl": "https://...",
  "logArchiveStatus": "UPLOADED"
}
```

For step-scoped SSE, return the terminal step log metadata.

Requests without `stepId` should fail clearly, preferably with a business validation error. The frontend must select one concrete step before calling the query or SSE log endpoints.

## Archive Timing

Archive should run after all jobs are terminal and before workspace cleanup.

Flow:

1. Step reaches success/failure/cancel: finalize local file metadata only.
2. Engine aggregates terminal run status.
3. Engine calls `archiveRunLogs(run)` for all logs with local files and missing uploaded URLs.
4. Each eligible file is uploaded through `FileApi.createFile(bytes, fileName, directory, "text/plain")`.
5. Update `logFileUrl`, `logArchiveStatus`, `logArchivedAt`, `logSizeBytes`, and `logLineCount`.
6. Cleanup workspace only after archive attempt policy is satisfied.

Archive failures:

- Mark the affected log `FAILED`.
- Do not fail an otherwise successful pipeline run.
- Do not delete the only local log copy in the same path unless a retry/recovery mechanism is available.

## Handler Changes

- `CommandStepHandler`
  - Remove `logMode`, `LOG_MODE_PIPELINE`, `LOG_MODE_CONTAINER`, and `wrapScriptForLogMode`.
  - Open a `PipelineStepLogHandle` before command execution.
  - Pass only the `LogSink` to `PipelineCommandExecutor`; each sink callback writes to the handle and small `resultJson.logLines` summary.
- `DockerPipelineCommandExecutor`
  - Remove `wrapScriptWithLogFile` and `containerLogFile` behavior.
  - Continue splitting Docker stdout/stderr frames and invoking `LogSink.accept(stream, line)`.
- Platform step handlers
  - `PrivateRegistryDockerBuildStepHandler`, `DockerImageExportObjectStorageStepHandler`, and `DockerImageArchiveImportStepHandler` should open/write through the shared log handle.
- `PipelineStepLogHelper`
  - `markSuccess` / `markFailed` should finalize local log metadata, not upload to object storage immediately.

## API Compatibility

- Keep endpoint paths unchanged.
- Make `stepId` mandatory in the log-lines query and SSE contracts even if the controller parameter remains technically optional during migration.
- Keep `PipelineRunLogLineRespVO` for line events.
- Add a small completion DTO for SSE `complete` event data.
- Keep raw local log API for active runs and uploaded-but-still-local runs; after cleanup, raw local API may fail unless backed by the uploaded URL. The frontend should prefer `logFileUrl` for completed logs.

## Documentation Updates

Update `PIPELINE_YAML_SPEC.md`:

- Remove `with.logMode` as a supported behavior for new command steps.
- Document workspace-backed step logs.
- Document SSE `complete` event payload and frontend behavior:
  - active stream: consume `log-line`
  - terminal stream: consume `complete.logFileUrl`
  - `stepId` is required for log query and stream APIs

## Risks

- Stable application-environment workspaces are reset at the next run. If archive fails and cleanup/reset proceeds, logs may be lost.
- Removing run-scoped streaming means callers that want an all-run view must compose it explicitly from `/logs` plus individual step log streams or archived URLs.
- Reading whole files for `FileApi.createFile(byte[])` is simple but memory-heavy for large logs. Respect archive max bytes and skip/upload only bounded files.
