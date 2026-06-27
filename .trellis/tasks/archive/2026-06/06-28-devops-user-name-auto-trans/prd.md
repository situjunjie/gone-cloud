# devops user name auto translation

## Goal

Use @AutoTrans on devops response VOs so frontend receives user display names instead of resolving user ids through /system/user/simple-list.

## Confirmed Facts

- The project uses `org.dromara.core.trans.anno.Trans` with `TransType.AUTO_TRANS` for API-backed translation.
- Admin users are translated through `AdminUserApi.PREFIX`, whose auto-trans namespace exposes the `nickname` field.
- Existing response VO pattern maps a user id field to a sibling name field, for example `ownerUserId` -> `ownerUserName`.
- The affected DevOps endpoints return these VO classes:
  - `/devops/application/page` and `/devops/application/get`: `ApplicationRespVO`
  - `/devops/change/page` and `/devops/change/get`: `ChangeRespVO`
  - release environment detail mounted/unmounted branches: `ApplicationReleaseBranchRespVO`
  - release current run: `ApplicationReleaseCurrentRunRespVO`
  - `/devops/deployment-order/{id}`: `DeploymentOrderRespVO`

## Requirements

- Add backend response fields for user display names so the frontend no longer needs `/system/user/simple-list` for the listed DevOps views.
- Use the existing AutoTrans integration, not manual service joins or ad hoc user lookups.
- Preserve existing id fields for compatibility.
- Translate user names from admin user `nickname`.
- Add the following response fields:
  - `ApplicationRespVO.ownerUserName`, translated from `ownerUserId`
  - `ChangeRespVO.ownerUserName`, translated from `ownerUserId`
  - `ChangeRespVO.testerUserName`, translated from `testerUserId`
  - `ChangeRespVO.codeReviewerUserName`, translated from `codeReviewerUserId`
  - `ApplicationReleaseBranchRespVO.ownerUserName`, translated from `ownerUserId`
  - `ApplicationReleaseBranchRespVO.testerUserName`, translated from `testerUserId`
  - `ApplicationReleaseBranchRespVO.codeReviewerUserName`, translated from `codeReviewerUserId`
  - `ApplicationReleaseCurrentRunRespVO.triggerUserName`, translated from `triggerUserId`
  - `DeploymentOrderRespVO.triggerUserName`, translated from `triggerUserId`
- Do not add unrequested translations such as `mountedByName` unless the scope is explicitly expanded.

## Acceptance Criteria

- [ ] `/devops/application/page` and `/devops/application/get` responses include `ownerUserName` when `ownerUserId` resolves to an admin user.
- [ ] `/devops/change/page` responses include `ownerUserName`; `/devops/change/get` responses include `codeReviewerUserName` and `testerUserName` as applicable.
- [ ] Release environment detail branch items in both `mountedBranches` and `unmountedBranches` include `ownerUserName`, `codeReviewerUserName`, and `testerUserName` as applicable.
- [ ] Release current run response includes `triggerUserName` when `triggerUserId` resolves to an admin user.
- [ ] `/devops/deployment-order/{id}` response includes `triggerUserName` when `triggerUserId` resolves to an admin user.
- [ ] Existing response id fields remain unchanged.
- [ ] The DevOps server module compiles after the VO changes.

## Notes

- This is a lightweight API contract fix; PRD-only planning is sufficient unless implementation reveals service-level behavior outside VO translation.
