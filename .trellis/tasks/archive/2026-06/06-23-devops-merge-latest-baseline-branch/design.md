# Design

## Scope

Implement this inside the DevOps Git workspace preparation path used by CodeMerge. Do not add a separate pipeline node or user-facing YAML option.

## Current Data Flow

`CodeMergeStepHandler` resolves `baseBranch` and `targetBranch`, then calls `CodeMergeService.executeCodeMergeStep`.

`CodeMergeService` validates the application/code source and calls:

```java
gitWorkspaceService.prepareWorkspace(runId, repoUrl, accessToken, resolvedBaseBranch, resolvedTargetBranch)
```

The returned `workspaceKey` is used for each change branch merge, then `finishCodeMerge()` pushes `context.deployBranch` and writes `run.branchName` / `run.commitSha`.

## Change

Extend `GitWorkspaceServiceImpl.prepareWorkspace()`:

1. Clone repository and configure Git user as today.
2. Fetch `baseBranch` and resolve `origin/<baseBranch>` to `baseCommitSha`.
3. Try fetching `deployBranch`.
4. If `deployBranch` exists and differs from `baseBranch`:
   - checkout local `deployBranch` from `origin/<deployBranch>`;
   - merge `baseCommitSha` into the local deploy branch with a platform message;
   - if merge has conflicts or fails, propagate a `GitCommandException`.
5. If `deployBranch` is missing:
   - checkout local `deployBranch` from `origin/<baseBranch>`.
6. If `deployBranch == baseBranch`:
   - checkout local branch from `origin/<baseBranch>` and skip the self-merge.
7. Return prepared HEAD as `baseCommitSha` for compatibility with existing CodeMerge empty-change behavior.

Add metadata fields to `GitWorkspacePrepareResult` so tests and later diagnostics can distinguish:

- `baseBranch`
- `deployBranch`
- `baseRemoteCommitSha`
- `preparedCommitSha`
- `deployBranchExisted`

Keep the existing `baseCommitSha` field as prepared HEAD to avoid changing existing CodeMerge result semantics.

## Compatibility

- Existing callers that only read `workspaceKey` and `baseCommitSha` continue to compile.
- `ChangePublishFinalize` calls `prepareWorkspace(changeId, repoUrl, token, baseline, baseline)` and therefore checks out the latest baseline without trying to merge the baseline into itself.
- CodeMerge conflict APIs remain unchanged because this preparation merge happens before item-level context is created; a baseline-refresh conflict becomes the existing CodeMerge failure path.

## Trade-Offs

- Baseline-refresh conflicts fail the CodeMerge step instead of using the item-level online conflict UI. This avoids inventing a second conflict state for a preparation merge and matches the current requirement to pull the latest deploy branch before the code merge node continues.
- The refresh uses Git merge rather than resetting deploy branch to baseline. This preserves existing deploy-branch commits and lets Git detect real divergence.
