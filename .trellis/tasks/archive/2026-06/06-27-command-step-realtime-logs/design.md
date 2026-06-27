# Design

## Goal

Make execution-capable pipeline step handlers emit realtime logs through the same file-log path, while still archiving completed logs.

## Scope

In scope:
- `CommandStepHandler`
- `DockerImageArchiveImportStepHandler`
- `DockerImageExportObjectStorageStepHandler`
- `PrivateRegistryDockerBuildStepHandler`

Out of scope:
- approval / wait / status-only handlers
- node handlers outside pipeline step execution

## Current State

- `PrivateRegistryDockerBuildStepHandler` already appends output to the step log file as output arrives.
- The command and image import/export handlers currently collect lines into `resultJson.logLines` only.
- Realtime streaming is backed by `PipelineLogStorageService.readLines()` and `/log-lines/stream`, not by each handler pushing SSE directly.

## Target Flow

1. Step handler creates or resolves `PipelineRunLogDO`.
2. Handler prepares the step log file before execution.
3. Executor emits stdout/stderr line callbacks.
4. Handler appends each nonblank line to the step log file immediately.
5. Handler also keeps a bounded in-memory list for `resultJson`.
6. `PipelineStepLogHelper.markSuccess()` / `markFailed()` finalizes the step and calls `completeStepLog()` to archive the file.

## Contracts

- File log is the source of truth for realtime UI and historical readback.
- `resultJson.logLines` remains a bounded summary only.
- `logTruncated` should continue reflecting the step log file state.
- Log emission must not include sensitive env values.

## Implementation Shape

- Add a shared utility/helper for execution-step log writes.
- Update each execution-capable handler to call the same append method inside the executor callback.
- Keep `prepareStepLog(runLog)` before execution in every in-scope handler.
- Leave archive behavior unchanged; finalization still happens in `PipelineStepLogHelper`.

## Risks

- Different handlers may have slightly different runId/runLogId lifecycle needs.
- Writing every line to disk increases I/O, but matches the existing build step behavior.
- Need to keep `MAX_LOG_LINES` as a bounded summary to avoid large `resultJson`.
