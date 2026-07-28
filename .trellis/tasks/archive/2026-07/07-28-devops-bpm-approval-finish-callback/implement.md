# Implementation Plan

1. Read the BPM and backend-layer specifications before modifying code.
2. Add a compact BPM API status DTO and a tenant-scoped `BpmProcessInstanceApi` query method; defer the existing BPM post-notification dispatch until transaction commit without changing its request shape or approval-only trigger.
3. Change the DevOps callback controller to bind `processInstanceId` from the BPM form request, query BPM, and delegate through the existing approval service/engine path.
4. Add or update BPM API and DevOps approval tests for approved callback handling, non-approved result no-op behavior, and ignored non-DevOps business keys.
5. Run focused module tests, then the applicable Trellis quality check. Review the diff for API compatibility and tenant propagation.

## Validation

- `mvn -pl yudao-module-bpm/yudao-module-bpm-server -DskipTests compile`
- `mvn -pl yudao-module-devops/yudao-module-devops-server -Dtest=PipelineApprovalServiceImplTest test`
- Run any controller/API tests added for the new callback contract.

## Rollback Point

The contract pivot is isolated to the BPM internal API and `PipelineRunController`; reverting those files restores the former event-body callback behavior.
