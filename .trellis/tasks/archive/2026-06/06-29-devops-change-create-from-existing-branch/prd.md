# DevOps create change from existing branch

## Goal

Allow `POST /devops/change/create-from-application` to create a DevOps change from an already-existing repository branch name, while keeping the current generated-branch creation flow available.

The user value is that teams that already created a Git branch outside the DevOps UI can register that branch as an application change without forcing the platform to create a new `feat/{branchSlug}-{openTimestamp}` branch.

## Confirmed Facts

- The task targets the admin DevOps backend endpoint `POST /devops/change/create-from-application` in `ChangeController`.
- The endpoint currently uses `ChangeCreateFromApplicationReqVO` with required fields `appId`, `title`, `branchSlug`, and `openTimestamp`.
- `ChangeServiceImpl#createChangeFromApplication` currently always builds a branch name as `feat/{branchSlug}-{openTimestamp}`, validates it, generates `changeKey` from `{appKey}-{openTimestamp}`, checks duplicate change key and branch name for the app, then calls `repositoryProviderService.createRepositoryBranch(...)` before inserting `ChangeDO`.
- Current branch validation requires the branch name to start with `feat/`. This fits generated branches but may reject valid existing Git branch names such as `feature/foo`, `bugfix/foo`, or a manually created `hotfix/foo`.
- `ChangeDO.branchName` is the application-level change branch name used later by release, code review, pipeline merge, and build flows.
- Duplicate prevention already exists for `(appId, changeKey)` and `(appId, branchName)`.
- `RepositoryProviderService` currently supports creating and deleting repository branches, but does not expose a branch existence lookup method.
- Existing tests cover generated branch success, invalid generated branch name, duplicate branch name, and GitLab create-branch failure in `ChangeServiceImplTest`.
- Historical task context for this endpoint says the frontend request body currently uses `appId`, `title`, `branchSlug`, and `openTimestamp`, and the endpoint reuses permission `devops:change:create`.

## Requirements

- R1: The endpoint must support a new explicit existing-branch creation mode where the caller supplies a complete branch name instead of a slug that the backend expands.
- R2: Existing generated-branch mode must remain backward compatible for callers that still send `branchSlug` and `openTimestamp`.
- R3: In existing-branch mode, the service must not call `createRepositoryBranch`; it should register the supplied branch as `ChangeDO.branchName`.
- R4: In both modes, the service must still create an active `ChangeDO`, set `appId`, `changeKey`, `title`, `branchName`, `sourceBaseBranchName`, `ownerUserId`, default review/test fields, and insert it through the existing mapper flow.
- R5: In both modes, duplicate `changeKey` and duplicate `branchName` under the same app must continue to fail before insert.
- R6: Existing-branch mode must validate the supplied branch name using Git ref safety rules before insert.
- R7: Existing-branch mode should produce a stable `changeKey` without requiring the user to manually type a change key.
- R8: Tests must cover generated-branch backward compatibility and existing-branch behavior, including duplicate branch protection and avoiding repository branch creation.
- R9: Existing-branch mode must not verify that the supplied branch exists in the remote repository during change creation; later code review or pipeline operations may surface missing-branch failures.

## Technical Notes

- Likely implementation shape: add an optional field to `ChangeCreateFromApplicationReqVO` for the full branch name, and branch service logic based on whether it is present.
- The current `validateGitBranchName` method mixes generic Git ref validation with a generated-branch prefix rule. Existing-branch support likely needs to split these concerns.
- Existing-branch mode intentionally avoids adding a `RepositoryProviderService` remote branch lookup method. This keeps the change stable and local to request validation plus `ChangeDO` creation.
- If a syntactically valid but missing branch is submitted, the change can be created, and later code review / pipeline actions may fail against the Git provider.

## Out of Scope

- Changing release, code review, pipeline merge, or build semantics beyond using the stored `ChangeDO.branchName`.
- Changing existing permission points; this endpoint should continue to use `devops:change:create`.
- Adding frontend implementation unless this task is explicitly expanded beyond backend API planning/implementation.
- Verifying remote Git branch existence during create.

## Acceptance Criteria

- [x] A request using the existing generated fields still creates and persists a branch named `feat/{branchSlug}-{openTimestamp}` and still calls GitLab branch creation from the application default branch.
- [x] A request using the new existing-branch field persists exactly that branch name and does not call GitLab branch creation.
- [x] Existing-branch mode rejects invalid Git branch names with the existing invalid-branch behavior or an equivalent clear service error.
- [x] Existing-branch mode rejects duplicate branch names for the same app before insert.
- [x] Existing-branch mode creates an active change owned by the login user and keeps `sourceBaseBranchName` as the application's default branch.
- [x] Existing-branch mode does not call any GitLab branch create or lookup operation during change creation.
- [x] Unit tests cover the new mode and continue covering current mode.
