# Local Code Analysis: Pipeline Approval Node

## Existing Pipeline Execution

The DevOps pipeline has a visual DSL represented by `PipelineSpec`, with nodes, edges, and node params. Node types are registered in `PipelineNodeRegistryServiceImpl`.

`TYPE_APPROVAL` already exists, but it is disabled and has no params. `TYPE_CONTAINER_DEPLOY` is enabled and implemented as a platform node.

Jenkinsfile generation sorts nodes and emits every node except `CONTAINER_DEPLOY`. This means enabling `APPROVAL` as-is would incorrectly generate a Jenkins stage for it.

Jenkins callbacks flow through `PipelineJenkinsCallbackServiceImpl`. When a Jenkins node completes, the service checks whether all non-container-deploy nodes are successful. If they are, it immediately starts `CONTAINER_DEPLOY` if present, otherwise marks the run successful.

## Existing Platform Deployment

`DeploymentOrderServiceImpl#startContainerDeploy` creates or reuses a deployment order and updates the pipeline run/log. It is already idempotent for existing successful or running deployment orders.

Deployment logs are stored in `dev_pipeline_run_log` with node status and JSON context. This is a good fit for approval runtime state too.

## Existing BPM Integration

`BpmProcessInstanceApi#createProcessInstance` creates a BPM process from a `processDefinitionKey`, variables, business key, and optional selected assignees.

BPM publishes `BpmProcessInstanceStatusEvent` when the process ends. The event includes process instance id, process definition key, status, reason, and business key.

`BpmProcessInstanceStatusEnum` statuses:

* `APPROVE = 2`
* `REJECT = 3`
* `CANCEL = 4`

CRM demonstrates the cross-service callback pattern:

* CRM server depends on `yudao-module-bpm-api`.
* CRM implements `BpmProcessInstanceStatusEventListener` as a `@RestController`.
* BPM server has listener bridge classes that call service endpoints through `BpmHttpRequestUtils`.

DevOps server currently does not depend on `yudao-module-bpm-api`, so approval implementation needs that dependency.

One design constraint: `BpmProcessInstanceStatusEventListener` filters by one fixed `processDefinitionKey`. A per-node dynamic `processDefinitionKey` means DevOps should not extend that abstract listener for approval callbacks. A direct `ApplicationListener<BpmProcessInstanceStatusEvent>` can filter by deterministic `businessKey` in aggregate `yudao-server`, and an internal REST endpoint can support BPM model HTTP callbacks or split-service deployments.

## Recommended Shape

Create a DevOps approval service and BPM status callback endpoint:

* `PipelineApprovalService.startApproval(run, node, userId)`
* `PipelineApprovalService.handleBpmStatus(event)`
* `PipelinePlatformNodeAdvanceService.advanceAfterJenkins(run, version)` or equivalent

The approval business key should be deterministic, for example:

`devops:pipeline-approval:<pipelineRunId>:<nodeId>`

This lets duplicate callbacks find the existing log instead of creating another BPM instance.

## Key Constraints

* Do not place approval in Jenkinsfile.
* Do not start container deploy until approval succeeds.
* Reuse `WAITING_INPUT` for pending approval.
* Keep runtime state in `dev_pipeline_run_log.context_json` for MVP.
* For independent microservice deployment, rely on the BPM HTTP/RPC listener pattern rather than only local Spring events.
