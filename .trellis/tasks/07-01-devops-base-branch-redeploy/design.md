# Technical Design

## Data Model

Add simple baseline tracking to `dev_application`:

- `base_branch_latest_commit_sha`: latest commit SHA observed on `ApplicationDO.defaultBranchName`.
- `base_branch_latest_commit_message`: truncated latest commit message.
- `base_branch_latest_commit_at`: latest commit timestamp from GitLab payload.

Add baseline snapshot fields to `dev_pipeline_run`:

- `base_branch_name`: baseline branch used by this run.
- `base_commit_sha`: remote baseline commit used by this run.

The simple model intentionally treats the application default branch as the only tracked base branch. If future requirements allow arbitrary base branches, these application-level fields can be migrated into a branch-state table.

## GitLab Push Flow

`ChangeServiceImpl.syncLatestCommitFromGitLabPushHook` continues to update active change branches as it does now. Before returning false for a branch that is not an active change, it must also check whether the pushed branch equals `application.defaultBranchName`.

When the pushed branch is the default branch:

1. Parse commit SHA, message, and timestamp from the hook payload.
2. Update `ApplicationDO.baseBranchLatestCommitSha`, `baseBranchLatestCommitMessage`, and `baseBranchLatestCommitAt`.
3. Evict current-run cache entries for all application environments under the application.
4. Return `true`.

## Current Run Contract

`ApplicationReleaseCurrentRunRespVO` returns:

- `baseBranchName`
- `baseCommitSha`
- `baseBranchLatestCommitSha`
- `baseBranchLatestCommitMessage`
- `baseBranchLatestCommitAt`
- `baseBranchOutdated`

`baseBranchOutdated` is true only when all are true:

- There is a current run.
- `run.baseBranchName` equals `application.defaultBranchName`.
- `run.baseCommitSha` is not blank.
- `application.baseBranchLatestCommitSha` is not blank.
- The two commit SHAs differ.

For old runs without `baseCommitSha`, return false.

## Deploy Branch Decision

Before creating `PipelineRunDO`, `ApplicationServiceImpl.submitApplicationReleaseBranch` decides whether to reuse or rebuild the deploy branch.

Rebuild a new deploy branch when:

- The latest release run is missing.
- The latest release run has no deploy branch.
- The latest release run's `baseCommitSha` differs from `application.baseBranchLatestCommitSha`.
- At least one previously mounted change is being unmounted.

Otherwise reuse the latest deploy branch.

This makes a deploy branch a materialized result of current baseline plus current target changes. Rebuilding on base changes avoids carrying stale merge history from old deploy branches.

## Code Merge Snapshot

`CodeMergeService` records the baseline snapshot after preparing the Git workspace:

- `run.baseBranchName = resolvedBaseBranch`
- `run.baseCommitSha = workspace.baseRemoteCommitSha`

The final deploy commit continues to be written to `run.commitSha` after merge success.

For a newly generated deploy branch, `GitWorkspaceService.prepareWorkspace` already checks out from `origin/baseBranch` because the deploy branch does not exist remotely. The subsequent merge loop merges all target changes.

## Compatibility

Old rows have null baseline fields. They must not produce outdated prompts until a new run captures a baseline snapshot.

Database migration must be additive and nullable.
