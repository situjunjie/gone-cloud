# Implementation Plan

1. Add repository provider API method:
   - `deleteRepositoryBranch(providerId, repoIdentifier, branchName)`
   - Add focused tests in `RepositoryProviderServiceImplTest`.
2. Add change finalization API:
   - `ChangeService.finalizePublishedChange(changeId)`
   - Validate active change, app, baseline branch, change branch.
   - Reuse `GitWorkspaceService` to merge branch into baseline and push, then update `status`, `releasedAt`, `mergedToMasterAt`, evict cache, and delete branch.
   - Add focused tests in `ChangeServiceImplTest`.
3. Add pipeline step:
   - Register `TYPE_CHANGE_PUBLISH_FINALIZE = "ChangePublishFinalize"`.
   - Mark it as a platform node and supported validation step.
   - Implement `ChangePublishFinalizeStepHandler`.
   - Add handler/validation tests.
4. Update comments/docs that would mislead future maintainers, especially old `PipelineNodeHandler` wording if it implies new work should be added there.
5. Run focused Maven tests:
   - `mvn -pl yudao-module-devops/yudao-module-devops-server -am -Dtest='RepositoryProviderServiceImplTest,ChangeServiceImplTest,ChangePublishFinalizeStepHandlerTest,PipelineSpecValidationServiceImplTest' -Dsurefire.failIfNoSpecifiedTests=false test`

## Risk Points

- Finalizer retry behavior after a partial branch-delete failure must be explicit in tests.
- Finalizer now follows the user's clarified requirement: merge the published change branch into the baseline branch.
