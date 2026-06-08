# Jenkins console realtime stream API

## Goal

Provide a backend API for the admin frontend to display the full Jenkins build console output in near real time for a DevOps pipeline run. The first version only streams the whole build console; Jenkins pipeline stage/node-specific logs are explicitly out of scope.

## What I already know

* The user wants the backend interface first, then a detailed frontend prompt for a separate frontend integration.
* Current backend Jenkins integration is a custom `RestTemplate` client under `yudao-module-devops/.../framework/jenkins`.
* `JenkinsPipelineClient` currently supports enabling checks, starting a pipeline, and stopping a pipeline.
* `PipelineRunController` already exposes platform-side run logs at `GET /devops/pipeline-run/{runId}/logs`, but those are lifecycle records in `dev_pipeline_run_log`, not raw Jenkins console output.
* `PipelineRunDO` stores `jenkinsQueueId` and `jenkinsBuildNumber`; the initial implementation can stream only once `jenkinsBuildNumber` is available.
* Generated Jenkinsfile callbacks populate `jenkinsBuildNumber`, so a run opened before the first callback may need the frontend to retry or the backend to return a business error.

## Assumptions

* Use existing Jenkins job configuration from `devops.jenkins.base-url`, `job-name`, `username`, and `api-token`.
* Do not introduce a third-party Jenkins SDK for this task.
* Use Spring MVC `SseEmitter` for the server-to-browser stream.
* Use existing `devops:pipeline:query` permission.

## Requirements

* Add an admin API under `/devops/pipeline-run/{runId}/jenkins/console/stream`.
* The API streams raw Jenkins console chunks for the entire Jenkins build.
* The stream starts from offset `0` by default and accepts an optional `start` query parameter for reconnect/resume.
* The backend must not expose Jenkins credentials to the frontend.
* The backend must validate that the pipeline run exists and has a Jenkins build number before connecting to Jenkins.
* The backend should emit structured SSE events that include text, next offset, and whether Jenkins reports more data.
* The stream should complete when Jenkins has no more data.
* Business failures should use module error-code constants and standard project exception handling.

## Acceptance Criteria

* [ ] Calling the new stream API for a run with `jenkinsBuildNumber` emits Jenkins console text chunks.
* [ ] Calling the API for a missing run throws `PIPELINE_RUN_NOT_EXISTS`.
* [ ] Calling the API for a run without `jenkinsBuildNumber` returns a clear DevOps Jenkins business error.
* [ ] Jenkins upstream failures are wrapped in existing Jenkins trigger/config style business errors.
* [ ] Existing pipeline run APIs continue to compile.
* [ ] Focused unit tests cover client URL/header behavior and service validation behavior where practical.
* [ ] Final response includes a detailed frontend implementation prompt.

## Out of Scope

* Jenkins stage/node-specific console logs.
* Jenkins queue-to-build-number resolution.
* Persisting console logs in the database.
* WebSocket bidirectional console.
* Frontend implementation in this repository.

## Technical Notes

* Whole console can be pulled from Jenkins progressive text endpoint:
  `{baseUrl}/job/{jobName}/{buildNumber}/logText/progressiveText?start={offset}`.
* Jenkins response headers `X-Text-Size` and `X-More-Data` are the natural offset and completion contract.
* Current code should reuse `JenkinsPipelineClientImpl.buildHeaders()` authentication pattern and `buildJobUrl(...)` job path handling.
* Native browser `EventSource` cannot set custom authorization headers, so the frontend prompt should recommend `fetch` streaming or `@microsoft/fetch-event-source` if the admin auth token is header-based.
