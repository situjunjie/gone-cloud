# Implementation Plan

## Checklist

- [x] Define the SSE completion payload DTO for pipeline log streams.
- [x] Refactor the log storage service into workspace-relative step log operations.
- [x] Add workspace path resolution helpers so log reads/uploads never escape the run workspace.
- [x] Update file-backed `readLines` to require `stepId`, read that step's workspace log file, and preserve opaque `afterId` semantics.
- [x] Update SSE streaming to require `stepId`, poll/read that step's file-backed lines, and emit structured `complete` data.
- [x] Change step completion to finalize local metadata only.
- [x] Add run-terminal archive flow in `PipelineExecutionEngine` before any workspace cleanup policy.
- [x] Upload completed log files through `FileApi` and persist URL/archive metadata.
- [x] Remove `CommandStepHandler` `logMode` branching and container log-file plumbing.
- [x] Remove `PipelineCommandContext.hostLogFile` and `containerLogFile`.
- [x] Simplify `DockerPipelineCommandExecutor` so stdout/stderr frames only go through `LogSink`.
- [x] Move `PrivateRegistryDockerBuildStepHandler` and containerized image import/export handlers onto the shared step log writer.
- [x] Preserve legacy DB fallback for old runs without workspace file logs.
- [x] Update controller/service validation so omitting `stepId` returns a clear validation/business error instead of aggregating run-level logs.
- [x] Update `PIPELINE_YAML_SPEC.md`.
- [x] Update focused tests.

## Validation Commands

```bash
mvn -pl yudao-module-devops/yudao-module-devops-server -am -Dtest='PipelineLogStorageServiceImplTest,PipelineRunLogLineServiceImplTest,CommandStepHandlerTest,PrivateRegistryDockerBuildStepHandlerTest,DockerImageExportObjectStorageStepHandlerTest,DockerImageArchiveImportStepHandlerTest,PipelineExecutionEngineTest' -Dsurefire.failIfNoSpecifiedTests=false test
```

If the refactor renames `PipelineLogStorageServiceImpl`, update the test class list accordingly.

## Risky Files

- `PipelineLogStorageService.java`
- `PipelineLogStorageServiceImpl.java`
- `PipelineRunLogLineServiceImpl.java`
- `PipelineLogBroadcaster.java`
- `PipelineExecutionEngine.java`
- `PipelineStepLogHelper.java`
- `CommandStepHandler.java`
- `DockerPipelineCommandExecutor.java`
- `PipelineCommandContext.java`
- `PrivateRegistryDockerBuildStepHandler.java`
- `DockerImageExportObjectStorageStepHandler.java`
- `DockerImageArchiveImportStepHandler.java`
- `PIPELINE_YAML_SPEC.md`

## Rollback Points

- Keep endpoint paths unchanged while refactoring internals.
- Keep `dev_pipeline_run_log_line` fallback untouched until file-backed reads are verified.
- Prefer introducing the workspace-backed service behind the existing interface first, then remove obsolete command log-mode fields in a second pass.
- If archive timing causes instability, ship local workspace file streaming first and keep cleanup disabled until archive success/failure policy is proven.

## Review Gates

- Verify every new filesystem path is workspace-relative or validated with `startsWith(workspaceRoot)`.
- Verify secrets are not written to logs by new shared writer.
- Verify `FileApi` failures are recorded but do not flip successful pipeline execution to failed.
- Verify SSE `complete` event is emitted only after final archive metadata is available for terminal step-scoped streams.
- Verify `/log-lines` and `/log-lines/stream` require `stepId`.
- Verify frontend can still reconnect with `afterId` and no code relies on `lineNo` as a cursor.
