# Design

## Boundaries

- Add one pipeline platform step type, `ChangePublishFinalize`, in `PipelineNodeRegistryServiceImpl`.
- Add one `PipelineStepHandler` implementation under `service/pipeline/execution/handler`.
- Extend `ChangeService` with a finalization method that owns change status mutation and cache eviction.
- Reuse `GitWorkspaceService` for local Git merge/push behavior and extend `RepositoryProviderService` only for remote branch deletion.

## Data Flow

1. The finalizer handler reads `PipelineRunDO.changeSnapshotJson` as `List<PipelineRunChangeSnapshotContext>`.
2. If the snapshot is empty, it falls back to `PipelineRunDO.changeId` for older single-change runs.
3. For each snapshot item, the handler calls `ChangeService.finalizePublishedChange(changeId)`.
4. `ChangeServiceImpl` validates the active change and linked application, resolves the baseline branch, uses `GitWorkspaceService` to merge the change branch into the baseline branch and push it, marks the change as released/merged, evicts current-run caches, then requests branch deletion.
5. The handler returns `StepResult.CONTINUE` with outputs containing finalized change count and ids. Any business or GitLab failure returns `StepResult.FAIL`.

## Repository Contract

- Branch merge uses the change branch as source and the application baseline branch as target.
- GitLab branch cleanup deletes `ChangeDO.branchName`.
- Branch deletion treats GitLab 404 as success to support retries after partial success.
- Merge failure surfaces as a business error. Conflict handling is intentionally not interactive in this finalizer; a conflict after deployment should fail the finalizer step for operator intervention.

## Compatibility

- Existing `releaseChange(id)` remains for manual/API release state updates.
- The new finalizer uses `sourceBaseBranchName` before `ApplicationDO.defaultBranchName`; this matches the change creation contract while keeping existing full-create changes compatible.
- No database migration is required because `dev_change` already contains `releasedAt` and `mergedToMasterAt`.

## Rollback

- If merge fails, no change status mutation or branch deletion should occur for that change.
- If status update succeeds and branch deletion fails for a non-404 error, the step fails; rerun should be able to skip already released changes only if the implementation explicitly supports idempotency, otherwise it will surface `CHANGE_STATUS_NOT_ACTIVE`.
- Code rollback is standard Git revert of the implementation changes.
