# Technical Design

## Contract

The DevOps endpoint remains `POST /devops/pipeline-run/bpm/approval-status`, but changes its input from JSON `BpmProcessInstanceStatusEvent` to the BPM post-notification form parameter:

```
processInstanceId=<Flowable process instance id>
```

The BPM notification utility sends the tenant header. The controller must not ignore that tenant context because its BPM RPC query is tenant-scoped.
When no response-variable mapping is configured, the BPM notification is deferred until the enclosing BPM transaction commits, so the cross-service status query cannot observe an uncommitted historic-process record or status variable. Existing notifications with response mappings retain synchronous dispatch because they must write mapped values before the process ends.

## Data Flow

1. A BPM process reaches the approved end state and its configured process post-notification posts `processInstanceId` to DevOps.
2. The controller delegates the ID to `PipelineApprovalService`, which calls a new `BpmProcessInstanceApi` query operation.
3. BPM loads the historic Flowable process instance and returns a compact DTO containing `id`, `processDefinitionKey`, `businessKey`, and `status`.
4. `PipelineApprovalService` ignores query results other than `APPROVE`. For `APPROVE`, it constructs the existing `BpmProcessInstanceStatusEvent` from trusted BPM data and invokes its existing status-transition path.
5. Existing idempotent success transition updates the node/run and re-enters the pipeline engine.

## Boundaries

- BPM owns retrieval and interpretation of the Flowable historic process instance, including filtering the lookup by the current Flowable tenant.
- DevOps owns filtering by its `devops:pipeline-approval:` business-key prefix and updating pipeline state.
- The callback payload is only an instance identifier. Status and business key are never accepted from the external HTTP payload.
- BPM's current post-notification is sent only on approval. DevOps deliberately does not add reject/cancel processing through this HTTP path in this task.

## Compatibility

- `PipelineApprovalStatusEventListener` continues serving monolithic deployments through in-process events.
- This endpoint intentionally changes its request representation to match BPM's uniform post-notification contract. The BPM model must configure `processAfterTriggerSetting.url` to this endpoint.
- A missing process instance, a non-terminal status, or a non-DevOps business key is a no-op callback: return `success(false)`, do not resume the engine, and do not mutate a pipeline run.

## Risks And Rollback

- Removing `@TenantIgnore` is necessary for tenant-aware BPM RPC lookup. The existing service still resolves the pipeline run ignoring tenant and executes updates in the run's tenant context.
- Rollback restores the former JSON-event controller method and removes the new BPM status RPC operation; existing local event processing is unaffected.
