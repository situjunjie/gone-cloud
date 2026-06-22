# DevOps pipeline publish finalizer node

## Goal

Add a DevOps pipeline platform step that runs after online deployment and finalizes published changes by merging each published change branch into the application's baseline branch, marking the change as released, and deleting the remote change branch.

## Requirements

- Add a new pipeline step type for the release-finalization action. It must be available through the pipeline node registry and pass pipeline YAML validation.
- The step must use the current `PipelineStepHandler` execution extension point, because the active execution engine resolves `PipelineStepHandler` rather than old graph `PipelineNodeHandler` implementations.
- The step must derive target changes from the pipeline run's submit-time snapshot (`PipelineRunDO.changeSnapshotJson`) and fallback to `PipelineRunDO.changeId` only when no snapshot exists.
- For every change being finalized:
  - Validate the change still exists and is active before mutating it.
  - Merge the change branch into the application baseline branch. The baseline branch is `ChangeDO.sourceBaseBranchName` when present, otherwise `ApplicationDO.defaultBranchName`.
  - Mark the change as released after the merge succeeds.
  - Record `releasedAt` and `mergedToMasterAt`.
  - Delete the remote change branch after the release-state update succeeds.
- The step must fail clearly when application repository linkage, code source credentials, baseline branch, or change branch is missing.
- The remote branch deletion should be idempotent for already-deleted branches, so retrying the finalizer does not fail solely because cleanup already happened.
- Do not log access tokens or full raw GitLab payloads.

## Acceptance Criteria

- [ ] `ChangePublishFinalize` (or equivalent named step type) is registered as an enabled PLATFORM node with default parameters and schema.
- [ ] Pipeline YAML validation accepts the new step type and rejects invalid parameter types if any are introduced.
- [ ] A new `PipelineStepHandler` finalizes all submit snapshot changes by merging their branches into the baseline branch.
- [ ] Git-based finalization logic can merge a change branch into the application baseline branch and delete the remote change branch.
- [ ] Change service exposes a transactional finalization operation that marks a change released and sets `mergedToMasterAt`.
- [ ] Focused unit tests cover successful finalization, missing snapshot commit, repository merge/delete calls, idempotent delete handling, and handler success/failure behavior.
- [ ] DevOps server focused Maven tests pass or any remaining failure is documented with concrete cause.

## Notes

- User originally referenced `PipelineNodeHandler.java`; repository guidelines state new pipeline execution extensions should use `PipelineStepHandler`. The old node interface can have its comment updated if needed, but the new executable behavior belongs in the step handler path.
