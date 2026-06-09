# Jenkins Tool Name Dropdowns

## Goal

Avoid free-form typing for Jenkins JDK and Maven tool names in visual pipeline stage configuration by exposing Jenkins configured tools through the DevOps backend and marking the relevant pipeline node schema fields as remotely selectable.

## What I Already Know

* The Maven Jar build node currently exposes `toolJdk` and `toolMaven` as plain string parameters.
* `JenkinsPipelineClientImpl` already centralizes Jenkins base URL, job URL, and Basic Auth handling.
* Jenkins tool names are still used by generated Jenkinsfile `tools { jdk '...' maven '...' }`; this task does not change Jenkinsfile execution semantics.
* This repository does not include the frontend project shown in the screenshot, so the backend will expose the contract the frontend needs.

## Assumptions

* The frontend can render a schema property with a remote options endpoint as a select.
* Jenkins descriptor endpoints are available to the configured Jenkins API user.
* If Jenkins is disabled in `devops.jenkins.enabled`, the tool list endpoint should return an empty list instead of failing.

## Requirements

* Add a backend API to return Jenkins configured JDK and Maven tools.
* Reuse existing Jenkins HTTP client configuration and auth handling.
* Return enough data for select options: tool type, name, and optional home path.
* Keep existing saved pipeline params compatible because `toolJdk` and `toolMaven` remain strings.
* Mark `toolJdk` and `toolMaven` schema metadata with remote option endpoints.
* Cover successful parsing, empty response, and fetch failure in focused tests.

## Acceptance Criteria

* [ ] `GET /devops/pipeline/jenkins-tools` returns Jenkins JDK and Maven tool entries.
* [ ] `GET /devops/pipeline/jenkins-tools?type=JDK` filters to JDK tools.
* [ ] `GET /devops/pipeline/jenkins-tools?type=MAVEN` filters to Maven tools.
* [ ] Maven Jar node schema exposes remote option metadata for `toolJdk` and `toolMaven`.
* [ ] Existing Jenkinsfile generation remains unchanged.
* [ ] Focused DevOps module tests pass.

## Out of Scope

* Creating or updating Jenkins global tool configuration from the platform.
* Frontend implementation, because no frontend source exists in this repository.
* Agent-label, credentials, Maven settings, or NodeJS tool dropdowns.

## Technical Notes

* Relevant files:
  * `yudao-module-devops/yudao-module-devops-server/src/main/java/cn/iocoder/yudao/module/devops/framework/jenkins/JenkinsPipelineClient*.java`
  * `yudao-module-devops/yudao-module-devops-server/src/main/java/cn/iocoder/yudao/module/devops/controller/admin/pipeline/PipelineController.java`
  * `yudao-module-devops/yudao-module-devops-server/src/main/java/cn/iocoder/yudao/module/devops/service/pipeline/PipelineNodeRegistryServiceImpl.java`
  * `yudao-module-devops/yudao-module-devops-api/src/main/java/cn/iocoder/yudao/module/devops/enums/ErrorCodeConstants.java`
* Jenkins descriptor endpoints to use:
  * `/descriptorByName/hudson.model.JDK/api/json`
  * `/descriptorByName/hudson.tasks.Maven/api/json`
