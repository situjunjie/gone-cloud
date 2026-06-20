# Pipeline Docker Real-Time Log Streaming Research

## Context

The current Docker command path already receives stdout/stderr incrementally in
`DockerPipelineCommandExecutor.exec(...)` through Docker Java's `execStartCmd`
callback. `CommandStepHandler` currently buffers at most 500 nonblank lines in
memory and writes them to `PipelineRunLogDO.resultJson` only after the command
finishes.

Existing API:

- `GET /devops/pipeline-run/{runId}/logs` returns structured run/step log
  records from `dev_pipeline_run_log`.

Existing real-time transport:

- Kubernetes terminal uses WebSocket at `/devops/kubernetes/pods/terminal` for
  bidirectional interactive IO.

Existing persistence:

- `dev_pipeline_run_log` stores step status, runtime metadata, `result_json`,
  `log_file_url`, and `log_truncated`.
- There is no line-level table or streaming cursor today.

## Comparable Approaches

### Approach A: SSE stream + durable line table

How it works:

- Add a line-level persistence model, for example `dev_pipeline_run_log_line`,
  keyed by `pipeline_run_id`, `run_log_id`, and monotonic `line_no`.
- `CommandStepHandler` writes each command output line through a new append
  service. The service batches or inserts line records and publishes the same
  line to active SSE subscribers.
- Add `GET /devops/pipeline-run/{runId}/logs/stream?stepId=&afterLineNo=`.
  Frontend connects with `EventSource`, receives line events, and reconnects
  with the last seen cursor.
- Keep `GET /devops/pipeline-run/{runId}/logs` for the structured step tree.

Pros:

- One-way output matches SSE well.
- Uses normal HTTP request authorization patterns.
- Cursor-based reconnect is straightforward.
- Finished run logs remain readable without relying on process memory.

Cons:

- Adds a database table and write path.
- High-volume logs need truncation/batching limits.

### Approach B: WebSocket broadcast + in-memory buffer

How it works:

- Add a WebSocket endpoint for pipeline logs.
- Output callback broadcasts lines to sessions subscribed to a run/step.
- Maintain a short in-memory ring buffer for late subscribers.

Pros:

- Reuses the existing WebSocket dependency and some terminal patterns.
- Lower database write volume.

Cons:

- Logs disappear on backend restart unless also persisted elsewhere.
- Reconnect replay is weaker.
- WebSocket is bidirectional overhead for a one-way log stream.

### Approach C: Log file tail + HTTP/SSE

How it works:

- Append command output to a workspace or configured log file.
- Store `log_file_url` or an internal path on the step log.
- Stream by tailing the file from a byte offset.

Pros:

- Good fit for very large logs.
- Avoids writing every log line into the database.

Cons:

- Requires file lifecycle, cleanup, path safety, and possibly object storage.
- More moving parts for the first API version.

## Recommendation

Use Approach A for the MVP: SSE plus durable line storage. It gives the frontend
a simple real-time API and preserves refresh/reconnect behavior, which is part
of the user's stated goal. Add conservative truncation limits so unbounded build
output cannot grow the database indefinitely.

Keep file/object storage as a later optimization for very large full logs. Do
not implement interactive input; pipeline command logs are read-only.
