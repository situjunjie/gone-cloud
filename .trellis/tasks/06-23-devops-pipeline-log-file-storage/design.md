# Design

## Architecture

Keep the public controller/service contract stable and replace the storage implementation behind `PipelineRunLogLineService`.

Current flow:

```text
Docker/command callback -> PipelineRunLogLineService.appendLine
  -> dev_pipeline_run_log_line row
  -> getLogLines / SSE read rows
```

Target flow:

```text
CommandStepHandler
  -> prepare host log files under mounted run workspace
  -> DockerPipelineCommandExecutor runs script with stdout/stderr redirected by the container
  -> getLogLines / SSE read mounted log files while the container is still running
  -> step completion uploads full log through FileApi and stores PipelineRunLogDO.logFileUrl
  -> fallback to dev_pipeline_run_log_line only when mounted files are absent
```

## File Contract

Use plain text files written by the build container:

- `stdout.log`
- `stderr.log`

The backend prepares the files under the host run workspace and passes container-visible paths into the command executor. The command wrapper redirects stdout and stderr separately. The read service converts file lines to `PipelineRunLogLineRespVO` at query time:

- `id`: derived numeric cursor for API/SSE compatibility.
- `pipelineRunId/runLogId/stageId/jobId/stepId`: from `PipelineRunLogDO`.
- `lineNo`: display line number scoped to one `runLogId`.
- `streamType`: `stdout` or `stderr` based on source file.
- `content`: raw text line read from the file.
- `createTime`: falls back to the run log create time because raw text files do not store per-line timestamps.

The storage component owns path resolution, file preparation, line reading, and full-log assembly. The service owns business validation, API limits, terminal-run checks, DB fallback, and upload orchestration.

## Path Shape

Store files under the already-mounted run workspace so the Docker container can write them directly:

```text
{runWorkspace}/.gone-devops/logs/run-log-{runLogId}/stdout.log
{runWorkspace}/.gone-devops/logs/run-log-{runLogId}/stderr.log
```

Container-visible paths use the existing `/workspace` mount:

```text
/workspace/.gone-devops/logs/run-log-{runLogId}/stdout.log
/workspace/.gone-devops/logs/run-log-{runLogId}/stderr.log
```

This keeps Docker as the writer and the backend as the reader/uploader. It relies on the existing Docker runtime mount contract instead of adding a separate host mount.

## Upload Contract

After a Command step finishes, assemble an ordered full log file from `stdout.log` and `stderr.log` or upload a combined file generated while reading. Use:

```java
fileApi.createFile(bytes, "pipeline-run-{runId}-step-{stepId}.log", "devops/pipeline-run/{runId}", "text/plain")
```

Store the returned URL in `PipelineRunLogDO.logFileUrl` and keep `resultJson.logTruncated` as summary metadata. Upload failure should not hide the command exit code; record the step failure only if the product expects logs to be mandatory. Initial implementation should log the upload error and keep local file-backed query available.

## Compatibility

- API paths, request params, permissions, and response VO remain unchanged.
- `afterId` remains numeric and monotonic. It is no longer promised to be a database row id for new file-backed logs.
- Old DB rows remain readable through fallback when the file does not exist or is empty.
- `dev_pipeline_run_log_line` remains in schema for existing deployments and old data, but new writes stop using it.
- `PipelineRunLogLineDO` and mapper can remain temporarily as compatibility infrastructure; removing them would require a separate migration.

## Truncation

The current 2000-line cap protects database growth but is too small for real build logs. Container-written files should be bounded by command execution and disk retention policy; query responses remain paged by `limit`. If a maximum read/upload size is introduced, set `logTruncated=true` when the backend truncates the assembled view.

## Concurrency

The Docker process is the only writer for a step's stdout/stderr files. Reads are cursor-based scans over stable text files. Given the current paged API, a simple streaming scan is acceptable for this refactor. If log size becomes a bottleneck later, add sidecar index metadata or rolling chunk files.

## Failure Behavior

- Missing run still throws `PIPELINE_RUN_NOT_EXISTS`.
- Blank content still returns `null`.
- File I/O failure should fail the append/query path with a service/runtime exception rather than silently dropping logs.
- SSE should keep existing error completion behavior.

## Spec Drift

`.trellis/spec/backend/devops-pipeline-guidelines.md` currently names `dev_pipeline_run_log_line` as the live output store. Update it to say mounted file storage plus `FileApi` upload is canonical for new runs, DB rows are historical fallback only, and `afterId` is a storage cursor rather than necessarily a row id.
