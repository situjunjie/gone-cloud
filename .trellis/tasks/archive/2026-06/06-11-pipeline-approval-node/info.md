# Technical Design: Pipeline Approval Node

## Summary

Implement `APPROVAL` as a platform-side pipeline node. Jenkins remains responsible only for Jenkins stages. After all Jenkins nodes complete, DevOps advances platform nodes in DSL order. An approval node creates a BPM process instance, records `WAITING_INPUT`, and pauses until BPM sends a terminal status callback.

## Node Parameters

Recommended MVP params:

* `processDefinitionKey`: BPM process definition key to start. This is configured per approval node.

Optional later params:

* `approvalTitle` or `summaryTemplate`
* timeout policy
* reject handling policy

## Runtime Context

Store the approval runtime context in `dev_pipeline_run_log.context_json`:

```json
{
  "processDefinitionKey": "devops_deploy_approval",
  "processInstanceId": "xxx",
  "businessKey": "devops:pipeline-approval:1001:approval",
  "startedBy": 7,
  "startedAt": "2026-06-11T10:00:00",
  "status": "WAITING"
}
```

The deterministic `businessKey` is the idempotency key.

## Service Changes

1. Add `yudao-module-bpm-api` dependency to `yudao-module-devops-server`.
2. Enable `TYPE_APPROVAL` in `PipelineNodeRegistryServiceImpl` with platform params.
3. Update `JenkinsfileGeneratorServiceImpl` to exclude all platform nodes, at least `APPROVAL` and `CONTAINER_DEPLOY`.
4. Add `APPROVAL` to `PipelineNodeTypeEnum`.
5. Add `PipelineApprovalService`:
   * creates/reuses the approval log
   * calls `BpmProcessInstanceApi#createProcessInstance`
   * marks the log `WAITING_INPUT`
   * handles BPM approve/reject/cancel status
6. Add a platform advancement service or helper:
   * after Jenkins completion, start next platform node
   * `APPROVAL`: stop and wait
   * approved `APPROVAL`: continue
   * `CONTAINER_DEPLOY`: call `DeploymentOrderService#startContainerDeploy`
7. Add DevOps BPM status integration:
   * an `ApplicationListener<BpmProcessInstanceStatusEvent>` for aggregate `yudao-server`
   * an internal REST endpoint accepting `BpmProcessInstanceStatusEvent` for BPM HTTP callbacks / split-service deployments
   * both filter by `businessKey` prefix and forward to approval service

## Flow

1. User submits release.
2. Code merge succeeds.
3. Jenkins runs Jenkins stages.
4. Jenkins final successful callback triggers platform advancement.
5. `APPROVAL` node starts BPM process and writes `WAITING_INPUT`.
6. BPM terminal status event/callback reaches DevOps.
7. Approve marks approval log success and starts `CONTAINER_DEPLOY`.
8. Reject/cancel marks approval failed/canceled and stops run.

## Tests

* Node registry exposes enabled `APPROVAL` with required params.
* Jenkinsfile generator excludes `APPROVAL`.
* Jenkins callback starts approval instead of deploy when approval is before deploy.
* Duplicate Jenkins callback does not create a second BPM process.
* BPM approve starts deployment once.
* BPM reject/cancel does not start deployment and updates run/log state.
