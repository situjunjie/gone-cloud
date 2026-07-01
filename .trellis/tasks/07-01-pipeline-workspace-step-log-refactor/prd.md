# Pipeline Workspace Step Log Refactor

## Goal

Rework DevOps pipeline runtime logging so each executable step writes its full log to a dedicated file inside the current build workspace, streams live output to the frontend from that file, and persists the finished log through `FileApi` before the workspace is cleaned.

## Background

- The current implementation writes step logs under a separate pipeline log root in `PipelineLogStorageServiceImpl`, exposes `/log-lines` and `/log-lines/stream`, and keeps `dev_pipeline_run_log_line` as a legacy fallback.
- `CommandStepHandler` still has `logMode` branching and Docker log-file plumbing through `PipelineCommandContext.hostLogFile` / `containerLogFile`.
- `dev_pipeline_run_log` already has the metadata fields needed for this refactor: `logFilePath`, `logFileUrl`, `logSizeBytes`, `logLineCount`, `logArchiveStatus`, `logArchivedAt`, and `logTruncated`.
- The frontend should receive live log lines from file-backed streaming while the selected step is active. When the step is complete, the SSE completion event should return the persisted log file URL so the frontend can switch to the archived file as the durable full-history source.
- Frontend log query and SSE APIs must require `stepId`. New file-backed log viewing is step-scoped only; run-level log aggregation is intentionally out of scope for this refactor.

## Requirements

- R1: Each step that emits runtime output must write to one step-scoped log file under the current run workspace, not under an independent pipeline log root.
- R2: The workspace log path must be deterministic, workspace-relative, and safe for arbitrary stage/job/step ids.
- R3: `dev_pipeline_run_log.log_file_path` must store the workspace-relative step log path; `log_file_url` must remain empty until `FileApi` upload succeeds.
- R4: High-volume runtime output must not be written to `dev_pipeline_run_log_line` for new runs. That table remains only as a legacy fallback for older runs without workspace file logs.
- R5: `/devops/pipeline-run/{runId}/log-lines` must require `stepId` and read from that step's log file using an opaque cursor.
- R6: `/devops/pipeline-run/{runId}/log-lines/stream` must require `stepId` and stream from that step's file-backed source while the step is active.
- R7: SSE line events must keep the existing `log-line` event shape as much as possible so frontend incremental rendering remains stable.
- R8: When the stream reaches a terminal step state, the SSE `complete` event must return structured completion data including that step's `logFileUrl` when available.
- R8.1: Run-scoped log query and SSE streaming are not part of the new frontend contract. The frontend must select a concrete step before requesting live or historical log lines.
- R9: Before workspace cleanup, the backend must upload completed step log files through `FileApi` and update `dev_pipeline_run_log` archive metadata.
- R10: Archive upload failure must not change the pipeline step/run execution result, but it must be visible through `logArchiveStatus=FAILED` and must not silently delete the only local copy before it can be retried or inspected.
- R11: The old `Command` `logMode` behavior should be removed from new execution logic. Docker commands emit stdout/stderr to the backend `LogSink`; the backend writes the workspace log file.
- R12: Platform steps that emit logs, including Docker image build/import/export handlers, must use the same step log writer abstraction as `Command`.
- R13: `PipelineRunLogRespVO` must continue exposing `logFileUrl`, `logFilePath`, `logTruncated`, `logSizeBytes`, `logLineCount`, `logArchiveStatus`, and `logArchivedAt`.
- R14: `PIPELINE_YAML_SPEC.md` must be updated to remove `steps.<step_id>.with.logMode` as a supported new behavior and document the file-backed SSE completion contract.

## Acceptance Criteria

- [ ] A new pipeline run writes each step's raw log under that run's workspace and records a workspace-relative `logFilePath` on the corresponding `dev_pipeline_run_log` row.
- [ ] New command output is streamed to the frontend from the selected step's workspace log file; new runs do not append high-volume lines to `dev_pipeline_run_log_line`.
- [ ] The log-lines query and SSE endpoints reject or clearly fail frontend requests that omit `stepId`.
- [ ] The SSE endpoint sends `log-line` events during execution and a terminal `complete` event with the selected step's `logFileUrl`.
- [ ] A completed step log is uploaded through `FileApi` before workspace cleanup, and the resulting URL is persisted in `dev_pipeline_run_log.log_file_url`.
- [ ] If upload fails, `logArchiveStatus` is `FAILED`, the step/run result is unchanged, and cleanup behavior preserves enough local information for diagnosis or retry according to the final implementation design.
- [ ] Existing legacy runs without workspace file logs can still be queried through the DB fallback path.
- [ ] Focused tests cover workspace log path creation, file-backed line reads, SSE completion payload, archive success/failure, command handler integration, and at least one platform step handler integration.
