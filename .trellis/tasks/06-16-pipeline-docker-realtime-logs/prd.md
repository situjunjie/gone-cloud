# Pipeline Docker RunOn Real-Time Logs

## Goal

When a pipeline job runs inside a Docker container via `runsOn`, command output
should be visible to the frontend in near real time. The frontend needs an API
that can continuously receive refreshed output while the step is running and can
recover previous output after page refresh or reconnect.

## What I Already Know

- The user wants logs for Docker-container pipeline tasks to refresh in real
  time and be available through an interface for the frontend.
- Current YAML supports Docker-backed `runsOn.group=local-docker/default` and
  `runsOn.container=<image>`.
- `DockerPipelineCommandExecutor` already receives Docker stdout/stderr frames
  incrementally from `execStartCmd`.
- `CommandStepHandler` currently stores at most 500 lines in memory and writes
  them to `resultJson.logLines` only after command completion.
- Existing structured log API is `GET /devops/pipeline-run/{runId}/logs`.
- Existing table `dev_pipeline_run_log` stores run/step metadata, runtime fields,
  summary/result JSON, `log_file_url`, and `log_truncated`, but no line-level
  streaming cursor.
- Existing WebSocket infrastructure is used for Kubernetes terminal, which is
  bidirectional interactive IO.

## Assumptions

- The first version targets `Command` steps running inside Docker job runtimes.
- Frontend only needs read-only log viewing, not interactive terminal input.
- Real-time means line/event updates should be pushed while the command is
  running; exact sub-second latency is not required.
- Logs must be sanitized consistently with existing pipeline guidelines and must
  not expose credentials.

## Requirements

- Capture stdout/stderr from Docker container command execution as the command
  runs.
- Persist command output in a replayable form so refresh/reconnect can show
  already-emitted lines.
- Provide a frontend-facing SSE real-time log interface for a pipeline run and
  optionally a specific step.
- Preserve the existing structured run log API for status/tree data.
- Keep step status updates owned by existing pipeline execution/log helpers.
- Enforce existing `devops:pipeline:query` permission on log read APIs.
- Apply bounded retention/truncation for overly large command output.

## Acceptance Criteria

- [ ] During a running Docker `Command` step, the frontend can receive new output
      lines without waiting for the command to finish.
- [ ] Refreshing or reconnecting can resume from a cursor and replay missed
      lines for the same run/step.
- [ ] Completed runs expose their command output through the same read path.
- [ ] Existing `GET /devops/pipeline-run/{runId}/logs` still returns structured
      log records.
- [ ] Tests cover append, cursor query, stream publishing, and Docker command
      handler integration at the service level.
- [ ] Large output is truncated or capped in a predictable way and sets a visible
      truncation signal.

## Definition of Done

- Tests added or updated for focused backend behavior.
- Relevant Maven test command passes for the DevOps module.
- API and persistence changes follow backend and DevOps pipeline specs.
- Docs/notes updated if API behavior changes.
- Rollback risk is considered for database changes and runtime output volume.

## Research References

- [`research/log-streaming-approach.md`](research/log-streaming-approach.md) -
  Compares SSE, WebSocket, and log-file approaches for this repo.

## Proposed Technical Approach

Confirmed MVP:

- Add durable line-level storage for command output with a monotonic cursor.
- Add a small pipeline log append/publish service. `CommandStepHandler` sends
  Docker output lines to this service instead of only buffering local
  `resultJson.logLines`.
- Add a read endpoint for historical lines and an SSE stream endpoint for live
  output. Suggested shape:
  - `GET /devops/pipeline-run/{runId}/log-lines?stepId=&afterId=&limit=`
  - `GET /devops/pipeline-run/{runId}/log-lines/stream?stepId=&afterId=`
- SSE events include `runId`, `runLogId`, `stepId`, `lineNo`, `stream`, `content`,
  timestamp, and use the row id as the reconnect cursor.
- Keep the existing `resultJson.logLines` summary for compatibility, but treat
  the new line API as the full frontend log viewer source.

## Decision (ADR-lite)

**Context**: Docker command output is already available incrementally, but the
current implementation only stores a small in-memory summary after the command
finishes. Frontend needs read-only real-time viewing plus refresh/reconnect
replay.

**Decision**: Use SSE plus durable line-level cursor storage for the MVP.

**Consequences**: The implementation adds a new persistence path and must bound
log volume, but it keeps frontend consumption simple, uses standard HTTP
authorization, and supports reliable replay after refresh or reconnect.

## Open Questions

- None for MVP.

## Out of Scope

- Interactive terminal input for pipeline tasks.
- Remote Docker executor groups beyond current `local-docker/default`.
- Object storage or downloadable full log artifacts unless needed for truncation.
- Frontend UI implementation unless explicitly requested in this task.

## Technical Notes

- Relevant files inspected:
  - `DockerPipelineCommandExecutor`
  - `CommandStepHandler`
  - `PipelineRunController`
  - `PipelineExecutionServiceImpl`
  - `PipelineRunLogDO`
  - `PipelineRunLogMapper`
  - `KubernetesTerminalWebSocketHandler`
  - `KubernetesTerminalWebSocketConfiguration`
  - `sql/mysql/devops.sql`
- Relevant specs:
  - `.trellis/spec/backend/index.md`
  - `.trellis/spec/backend/devops-pipeline-guidelines.md`
