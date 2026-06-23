# Implementation Plan

## Checklist

- [x] Extend `GitWorkspacePrepareResult` with preparation metadata while preserving `baseCommitSha`.
- [x] Update `GitWorkspaceServiceImpl.prepareWorkspace()` to fetch baseline first, checkout target, and merge latest baseline when target exists and differs from baseline.
- [x] Update `GitWorkspaceServiceImplTest` expectations for existing deploy branch refresh, missing deploy branch, and same base/target branch.
- [x] Add/update CodeMerge test for empty-change run using refreshed prepared commit.
- [x] Run targeted DevOps tests:
  - `mvn -pl yudao-module-devops/yudao-module-devops-server -am -Dtest='GitWorkspaceServiceImplTest,PipelineExecutionServiceImplTest' -Dsurefire.failIfNoSpecifiedTests=false test`

## Risk Points

- Existing Mockito tests assert exact Git command sequences; update them carefully around fetch/checkout/merge behavior.
- `prepareWorkspace(... baseline, baseline)` is used by publish finalization and must not perform `git merge` into itself.
- `GitWorkspaceServiceImpl.merge()` returns success without a new commit when already up to date; reuse it for baseline refresh rather than duplicating merge behavior.

## Rollback

- Revert changes in `GitWorkspacePrepareResult`, `GitWorkspaceServiceImpl`, and the focused tests.
- No database or API migration is involved.
