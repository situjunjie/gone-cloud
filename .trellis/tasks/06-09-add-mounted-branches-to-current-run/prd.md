# Add mounted branches to current-run

## Goal

`GET /devops/application/release/current-run` should return the current environment's already mounted changes so the release page can render the selected branch set from the polling model without calling `env-detail`.

## Requirements

- Add `mountedBranches` to `ApplicationReleaseCurrentRunRespVO`.
- The field must use the same item shape, filtering, and ordering as `ApplicationReleaseEnvDetailRespVO.mountedBranches`.
- Keep the existing short TTL declarative cache on `current-run`.
- Invalidate the current-run cache when mounted branch data changes:
  - release submit target set sync;
  - explicit mount / unmount APIs;
  - pipeline publication and run mutation callbacks already covered;
  - change fields included in `ApplicationReleaseBranchRespVO`, including latest commit, tester, code reviewer, review/test state, release/discard/delete.
- Prefer declarative cache eviction when the `applicationEnvId` is directly available. Use programmatic eviction when a change can affect multiple application environments.

## Acceptance Criteria

- `current-run` returns `mountedBranches` with the same data as `env-detail.mountedBranches`.
- Existing `env-detail` behavior remains unchanged.
- Tests cover the new response field and relevant cache eviction annotations/programmatic eviction.
- Focused DevOps tests pass.

## Out Of Scope

- No frontend changes in this task.
- No new database columns or cache key names.
