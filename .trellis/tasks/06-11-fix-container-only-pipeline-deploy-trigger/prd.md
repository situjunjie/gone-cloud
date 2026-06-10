# fix container-only pipeline deploy trigger

## Goal

Fix release pipeline runs whose published visual pipeline contains only platform-side container deploy nodes. These runs should start the platform deployment order directly after code merge succeeds instead of triggering Jenkins with an empty Jenkinsfile.

## What I already know

* Run 18 completed code merge successfully and has no `dev_deployment_order` row.
* Run 18 has a Jenkins queue id but no Jenkins build number, and no Jenkins node logs beyond code merge.
* Jenkins printed a generated Jenkinsfile with an empty `stages {}` block and failed compilation with `No stages specified`.
* `JenkinsfileGeneratorServiceImpl` intentionally filters out `CONTAINER_DEPLOY` because platform deploy runs in the backend, not Jenkins.
* `PipelineExecutionServiceImpl.triggerJenkinsPipeline` only starts container deploy when Jenkins is disabled/skipped, otherwise it waits for Jenkins callbacks.

## Requirements

* If a published pipeline version has a container deploy node but no Jenkins-executable nodes, code merge success must directly call `DeploymentOrderService.startContainerDeploy`.
* The backend must not trigger Jenkins for this container-only pipeline shape.
* Existing mixed pipelines must keep the current behavior: Jenkins runs all Jenkins nodes, then Jenkins callback starts container deploy after those nodes succeed.
* Existing Jenkins-disabled behavior must continue to start container deploy when present.

## Acceptance Criteria

* [ ] Container-only published pipeline creates a deployment order after code merge success without calling `JenkinsPipelineClient.startPipeline`.
* [ ] Mixed Jenkins + container deploy pipeline still triggers Jenkins first.
* [ ] Pipelines with only Jenkins nodes still trigger Jenkins.
* [ ] Focused unit tests cover the container-only branch.

## Definition of Done

* Focused tests updated.
* DevOps server focused test command passes or any environmental blocker is documented.
* No unrelated worktree changes are reverted.

## Out of Scope

* Jenkins runner pipeline-level failure callback for Jenkinsfile compile errors.
* Database repair for already-stuck run 18.
* Frontend status rendering changes.

## Technical Notes

* Main service: `PipelineExecutionServiceImpl`
* Existing callback advancement: `PipelineJenkinsCallbackServiceImpl.advanceRunIfAllJenkinsNodesCompleted`
* Existing generation behavior: `JenkinsfileGeneratorServiceImpl.isJenkinsNode`
* Relevant specs: `.trellis/spec/backend/devops-pipeline-guidelines.md`, `.trellis/spec/backend/quality-guidelines.md`
