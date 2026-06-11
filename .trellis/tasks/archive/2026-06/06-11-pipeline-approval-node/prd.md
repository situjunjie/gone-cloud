# Pipeline Approval Node

## Goal

Implement a platform-side pipeline approval node. When a pipeline run reaches this node, DevOps creates a BPM process instance and pauses the pipeline. Only after the BPM approval is approved should the downstream platform deployment node execute; if the BPM approval is rejected or canceled, the pipeline run should fail or stop without deployment.

## What I Already Know

* The approval node should normally sit after Jenkins build/image stages and before image deployment.
* The approval node must not be inserted into Jenkins stages.
* BPM process creation already exists in `yudao-module-bpm-api` via `BpmProcessInstanceApi#createProcessInstance`.
* BPM process completion already publishes `BpmProcessInstanceStatusEvent` with `processDefinitionKey`, `status`, and `businessKey`.
* DevOps pipeline DSL already has `PipelineNodeRegistryServiceImpl.TYPE_APPROVAL`, but the node is disabled.
* DevOps currently has platform deployment as `CONTAINER_DEPLOY`; Jenkinsfile generation excludes only `CONTAINER_DEPLOY`, so enabling `APPROVAL` without changing the generator would incorrectly emit it into Jenkins.
* Pipeline run execution currently advances directly from all Jenkins nodes completed to `deploymentOrderService.startContainerDeploy(...)`.
* Existing node logs support `WAITING_INPUT`, which matches approval waiting semantics.

## Requirements

* Enable a platform `APPROVAL` pipeline node in the node registry.
* Approval node parameters should include a BPM process definition key.
* Each approval node stores its own `processDefinitionKey` in node params so different applications/environments can use different BPM approval flows.
* Jenkinsfile generation must exclude platform approval nodes.
* After Jenkins executable nodes complete, platform execution should find the next platform node in topological order.
* When the next node is `APPROVAL`, create a BPM process instance and mark the node log as `WAITING_INPUT`.
* Approval creation must be idempotent for duplicate Jenkins callbacks or retry paths.
* Store approval runtime context in `dev_pipeline_run_log.context_json`, including process instance id, process definition key, business key, start user, and timestamps.
* When BPM returns approved status, mark approval node success and continue to the downstream node, usually `CONTAINER_DEPLOY`.
* When BPM returns reject or cancel status, mark approval node failed/canceled and mark the run failed/canceled without starting deployment.
* In the default aggregate `yudao-server` deployment, DevOps listens to `BpmProcessInstanceStatusEvent` directly and filters approval callbacks by deterministic `businessKey` prefix instead of static process definition key.
* Also provide a DevOps internal callback endpoint that accepts `BpmProcessInstanceStatusEvent`, so BPM model HTTP callbacks or future split-service deployments can call the same handling path.

## Acceptance Criteria

* [ ] `APPROVAL` appears in configurable pipeline node types and is categorized as `PLATFORM`.
* [ ] Generated Jenkinsfile contains Jenkins stages only and never contains `APPROVAL` or `CONTAINER_DEPLOY`.
* [ ] A pipeline with Jenkins build -> approval -> container deploy starts approval after Jenkins callbacks complete.
* [ ] While approval is pending, the pipeline run remains `RUNNING` and approval log is `WAITING_INPUT`.
* [ ] BPM approved status marks approval success and starts container deployment exactly once.
* [ ] BPM rejected status marks approval failed and does not start container deployment.
* [ ] Duplicate BPM callbacks do not create duplicate deployment orders.
* [ ] Focused unit tests cover node registry, Jenkinsfile generation, callback advancement, approval creation, and BPM status handling.

## Technical Approach

Add a DevOps platform-node orchestration layer between Jenkins completion and deployment:

1. Keep Jenkins responsible only for Jenkins node types.
2. Introduce an approval service/handler that creates the BPM process and writes a `WAITING_INPUT` pipeline log.
3. Replace direct `advanceRunIfAllJenkinsNodesCompleted -> startContainerDeploy` with `advancePlatformNodes(...)`.
4. `advancePlatformNodes(...)` walks platform nodes after Jenkins completion. It stops at `APPROVAL` until BPM completes; it starts `CONTAINER_DEPLOY` when approval has succeeded or no approval exists.
5. Add a DevOps BPM status event listener and internal callback endpoint. Both delegate to the same approval status handler and identify approval instances by `businessKey`.

## Decision (ADR-lite)

**Context**: Approval is a platform-side wait point and must not live in Jenkins. Existing pipeline code currently treats only `CONTAINER_DEPLOY` as platform-side and advances to it directly after Jenkins completion.

**Decision**: Implement approval as a first-class platform node using BPM for approval lifecycle, `dev_pipeline_run_log` for runtime state, node-level `processDefinitionKey` params, deterministic `businessKey` binding, BPM status event/callback handling, and a shared platform-node advancement service for `APPROVAL -> CONTAINER_DEPLOY`.

**Consequences**: This keeps Jenkinsfile clean and reuses BPM approvals. It requires a small orchestration refactor so future platform nodes can be chained without special-casing every node in Jenkins callback code.

## Out of Scope

* Putting approval inside Jenkinsfile or Jenkins pipeline input steps.
* Building a new approval UI outside BPM.
* Supporting arbitrary branching/parallel platform-node execution in this MVP.
* Creating a separate approval persistence table unless runtime log JSON proves insufficient.

## Technical Notes

* Relevant DevOps files:
  * `PipelineNodeRegistryServiceImpl` has disabled `TYPE_APPROVAL`.
  * `JenkinsfileGeneratorServiceImpl#isJenkinsNode` currently excludes only `CONTAINER_DEPLOY`.
  * `PipelineJenkinsCallbackServiceImpl#advanceRunIfAllJenkinsNodesCompleted` directly starts container deploy.
  * `DeploymentOrderServiceImpl#startContainerDeploy` is already idempotent for successful/running deployment orders.
  * `PipelineRunLogStatusEnum.WAITING_INPUT` already exists.
* Relevant BPM files:
  * `BpmProcessInstanceApi#createProcessInstance`
  * `BpmProcessInstanceCreateReqDTO`
  * `BpmProcessInstanceStatusEvent`
  * `BpmProcessInstanceStatusEventListener`
  * CRM uses a REST/RPC listener pattern for BPM result callbacks.

## Open Questions

* None for MVP.
