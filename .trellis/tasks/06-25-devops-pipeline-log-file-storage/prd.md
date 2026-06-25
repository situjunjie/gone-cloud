# DevOps Pipeline Log File Storage

## Goal

Move DevOps pipeline build output away from MySQL row-level storage as the
primary log store. The frontend must still be able to read historical build
logs and watch running build logs roll in through SSE, without introducing a
large external logging stack such as ELK, Loki, or Redis.

## User Value

- Build-heavy pipeline steps can emit large amounts of output without turning
  MySQL into the main text-log storage engine.
- Operators can open an old pipeline run and inspect its build output after the
  run has finished.
- Operators can open a running pipeline run and see stdout/stderr lines arrive
  continuously.
- The first implementation stays small: local log files are the source of truth
  while optional object-storage archiving uses the existing infra file service.

## Confirmed Facts

- Current backend API already exposes structured run logs:
  `GET /devops/pipeline-run/{runId}/logs`.
- Current backend API already exposes line logs:
  `GET /devops/pipeline-run/{runId}/log-lines?stepId=&afterId=&limit=`.
- Current backend API already exposes live line logs through SSE:
  `GET /devops/pipeline-run/{runId}/log-lines/stream?stepId=&afterId=`.
- `dev_pipeline_run_log` already has `log_file_url` and `log_truncated`.
- `dev_pipeline_run_log_line` currently stores every line in MySQL with a
  10000-line cap and 2000-character content cap.
- `PipelineRunLogLineServiceImpl` currently polls MySQL for SSE replay and live
  updates.
- `CommandStepHandler`, `PrivateRegistryDockerBuildStepHandler`,
  `DockerImageExportObjectStorageStepHandler`, and
  `DockerImageArchiveImportStepHandler` already append build output through
  `PipelineRunLogLineService`.
- `FileApi` can create files and return a URL, but its current create API takes
  bytes, so archive upload should happen after the local log file is closed and
  should be bounded by a configured max archive size.
- The frontend source is not present in this repository, so this task defines
  backend contracts and compatibility behavior for frontend integration.

## Requirements

- Keep local log files as the primary store for pipeline output.
- Optionally archive completed step log files through the existing infra
  `FileApi` and write the returned URL to `dev_pipeline_run_log.log_file_url`.
- Keep the existing line-log query and SSE endpoint paths so frontend migration
  is small.
- Preserve the existing `PipelineRunLogLineRespVO` response shape for line
  events.
- Treat the line response `id` and request `afterId` as an opaque numeric cursor
  owned by the backend. In file-backed mode, it may be a byte offset instead of
  a database row id.
- Support both run-level log viewing and step-scoped log viewing.
- Persist enough metadata on `dev_pipeline_run_log` to display log availability,
  size, line count, truncation, local relative path, and archive status.
- Keep `dev_pipeline_run_log` as the source of status, timing, runtime metadata,
  summary, result, error, and log archive URL.
- Stop writing new build output to `dev_pipeline_run_log_line` in the new file
  storage path.
- Keep a compatibility fallback that can still read older rows from
  `dev_pipeline_run_log_line` when a run has no file-backed log.
- Bound disk usage with configurable max bytes per log and retention/archive
  policy.
- Keep credential and secret handling at least as strict as current pipeline
  handlers: no credential values in logs, `contextJson`, `resultJson`, or
  outputs.
- Keep the transport simple: Spring SSE plus in-process subscribers. Do not add
  Redis, Kafka, ELK, Loki, or WebSocket for this task.

## Acceptance Criteria

- [ ] Running pipeline output is appended to local log files instead of inserted
      into `dev_pipeline_run_log_line`.
- [ ] Running pipeline output is pushed to connected SSE clients without polling
      MySQL for new log rows.
- [ ] A frontend client can reconnect with the last seen `id` as `afterId` and
      replay missed lines from the local log file.
- [ ] Completed runs can return historical log lines through the existing
      `/log-lines` API path.
- [ ] Completed step logs expose a full-log download/read path through
      `logFileUrl` or a backend raw-log endpoint.
- [ ] Existing structured run-log API remains unchanged for status/tree data.
- [ ] Older DB-backed log rows remain readable when no file-backed log exists.
- [ ] Oversized logs are bounded and visibly marked with `logTruncated=true`.
- [ ] Object-storage archive failure does not fail an otherwise successful
      pipeline step; it records archive failure metadata and leaves the local
      file readable.
- [ ] Focused tests cover append, file replay, SSE replay/live push, archive
      success/failure, truncation, and legacy DB fallback.

## Out of Scope

- ELK, Loki, Redis, Kafka, or distributed log aggregation.
- Multi-backend-instance live log fanout.
- Full frontend implementation, because the frontend project is not present in
  this repository.
- Full migration/backfill of historical `dev_pipeline_run_log_line` rows into
  files.
- Removing `dev_pipeline_run_log_line`; it remains for compatibility and can be
  cleaned up in a later migration.
- Streaming upload to object storage while the build is still running.

## Decisions

- `afterId` can become an opaque numeric cursor rather than a database row id.
  Clients should only store the last returned `id` and pass it back for replay;
  this keeps the API path stable while allowing file-backed storage.

## Notes

- Related historical task:
  `.trellis/tasks/archive/2026-06/06-16-pipeline-docker-realtime-logs`.
- The historical MVP intentionally chose DB row storage for speed. This task is
  the planned optimization path from that research.
