# DevOps merge latest baseline branch

## Goal

When a DevOps CodeMerge step runs against an existing deploy branch, the deploy branch must first absorb the latest application baseline branch if the baseline branch has advanced. Then the normal change-branch merge continues from that refreshed deploy branch.

## Confirmed Facts

- CodeMerge currently calls `GitWorkspaceService.prepareWorkspace(runId, repoUrl, token, baseBranch, targetBranch)` before merging change branches.
- `prepareWorkspace()` checks out `origin/<deployBranch>` when the deploy branch exists, and only falls back to `origin/<baseBranch>` when the deploy branch is missing.
- This means an existing deploy branch can miss newer commits from the application baseline branch.
- `ChangePublishFinalize` also uses `prepareWorkspace(... baselineBranch, baselineBranch)` to merge a deploy branch back into baseline; that same-baseline call must keep behaving as “checkout latest baseline”.

## Requirements

- If the target deploy branch already exists and the baseline branch has newer commits, CodeMerge must merge the latest remote baseline branch into the local deploy branch workspace before merging submitted change branches.
- If the target deploy branch does not exist, CodeMerge must continue creating it from the latest remote baseline branch.
- If the target branch is the same as the base branch, workspace preparation must not perform a redundant self-merge and must continue checking out the latest baseline branch.
- If merging latest baseline into the deploy branch conflicts or fails, CodeMerge should fail through the existing Git failure path and must not push a partially prepared deploy branch.
- Empty-change CodeMerge runs must push the refreshed deploy branch commit so downstream build/deploy uses the latest baseline content.
- Existing conflict handling for change branches, retry, continue, and publish finalization behavior must remain compatible.

## Acceptance Criteria

- [x] Existing deploy branch preparation fetches both deploy branch and baseline branch, checks out deploy branch, then merges latest baseline commit before change-branch merges.
- [x] Missing deploy branch preparation still checks out from latest baseline without trying to merge baseline again.
- [x] `baseCommitSha`/run commit for empty-change merges reflects the prepared HEAD after baseline refresh.
- [x] Unit tests cover existing deploy branch refresh, missing deploy branch, same base/target branch, and CodeMerge empty-change behavior.
- [x] Targeted DevOps tests pass or any environment limitation is documented.

## Out Of Scope

- No new YAML field or frontend API is required.
- No change to branch naming, conflict-resolution APIs, or publish-finalize cleanup.
