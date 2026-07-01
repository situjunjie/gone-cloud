# Implementation Plan

## Checklist

1. Add database migration for `dev_application` and `dev_pipeline_run` baseline fields.
2. Add fields to `ApplicationDO` and `PipelineRunDO`.
3. Extend `ApplicationReleaseCurrentRunRespVO` with baseline status fields.
4. Update current-run response building to fill baseline fields and compute `baseBranchOutdated`.
5. Update GitLab push hook handling for application default branch pushes and evict release current-run caches for all application environments.
6. Update deploy branch resolution to rebuild on base-branch changes or unmounted changes and reuse only when safe.
7. Update `CodeMergeService` to persist `baseBranchName` and `baseCommitSha` from `GitWorkspacePrepareResult`.
8. Add or update focused tests for branch resolution, hook update, current-run response, and code merge snapshot where existing tests support it.
9. Run targeted Maven tests for devops module.

## Validation

- `mvn -pl yudao-module-devops/yudao-module-devops-server -DskipITs test`

If the full module test set is too slow or blocked by unrelated fixtures, run targeted test classes around `ApplicationServiceImpl`, `ChangeServiceImpl`, and `CodeMergeService`.

## Risk Areas

- Cache eviction must cover all environments under the application, not just mounted changes.
- `baseBranchOutdated` must not be true for old runs with null `baseCommitSha`.
- Deploy branch rebuild must happen before `PipelineRunDO` is inserted because `branchName` is captured on the run.
