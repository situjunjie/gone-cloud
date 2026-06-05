# DevOps Change Entity Draft

## Scope

This document refines the `Change` entity only.

Goal:

- freeze the `Change` aggregate before expanding into environment relation objects
- make the model concrete enough for direct backend table and DO generation

## 1. Business Meaning

`Change` is the primary DevOps business object under an application.

It represents one independently managed delivery stream:

- belongs to exactly one application
- binds exactly one code branch
- can be mounted into multiple environments
- can eventually be released to production

In product language, a change is not a single deployment record.

It is the long-lived business carrier for one code modification line.

## 2. Boundary

### Change includes

- application ownership
- branch identity
- title and description
- owner / responsible person
- lightweight lifecycle status
- latest commit summary
- release / discard timestamps

### Change does not include directly

- environment-specific deployment state
- environment approval state
- pipeline run history
- snapshot / artifact ownership

Those belong to relation or runtime entities such as `ChangeEnvironmentRecord`, `PipelineRun`, and `EnvironmentSnapshot`.

## 3. Core Business Rules

1. One change belongs to one tenant.
2. One change belongs to exactly one application.
3. One change binds exactly one branch.
4. One application can own many changes.
5. `changeKey` must be unique within one application.
6. `branchName` must be unique within one application.
7. New change branches are created from the application's `defaultBranchName`.
8. Disabled applications should not allow creating new changes.
9. A discarded change should not accept new deployment submissions.
10. A released change means the change has entered production successfully.

## 4. Status Model

Recommended status values:

- `0` - `VALID` / `有效`
- `1` - `RELEASED` / `已发布`
- `2` - `DISCARDED` / `废弃`

### Recommendation

Do not reuse `CommonStatusEnum` here.

Reason:

- `Change` is not a simple enable/disable object
- its lifecycle is business-specific
- a dedicated `ChangeStatusEnum` is clearer

## 5. Field Design

## 5.1 Required Fields

- `id`
- `tenantId` (inherited from `TenantBaseDO`)
- `appId`
- `changeKey`
- `title`
- `branchName`
- `sourceBaseBranchName`
- `ownerUserId`
- `status`

## 5.2 Optional Fields

- `description`
- `latestCommitSha`
- `latestCommitMessage`
- `latestCommitAt`
- `releasedAt`
- `mergedToMasterAt`
- `discardedAt`
- `discardReason`
- `remark`

## 5.3 Suggested Field Definitions

### `id`

- type: `Long`
- meaning: change primary key

### `appId`

- type: `Long`
- meaning: owning application id
- relation:
  - `ApplicationDO.id`

### `changeKey`

- type: `String`
- meaning: human-readable business identifier under an application
- recommendation:
  - stable after creation
  - tenant-readable and UI-friendly
- examples:
  - `CHG-0001`
  - `DEV-20260604-001`

### `title`

- type: `String`
- meaning: short change title

### `description`

- type: `String`
- meaning: detailed change description

### `branchName`

- type: `String`
- meaning: bound branch name for this change
- examples:
  - `feat/DEV-20260604-001`
  - `feature/order-approval`

### `sourceBaseBranchName`

- type: `String`
- meaning: source trunk branch used when the change branch was created
- MVP expected default:
  - `master`

### `ownerUserId`

- type: `Long`
- meaning: responsible developer or administrator
- relation:
  - `AdminUserDO.id`

### `status`

- type: `Integer`
- meaning: change lifecycle status
- recommendation:
  - use dedicated `ChangeStatusEnum`

### `latestCommitSha`

- type: `String`
- meaning: latest known commit sha on the bound branch

### `latestCommitMessage`

- type: `String`
- meaning: latest known commit message summary

### `latestCommitAt`

- type: `LocalDateTime`
- meaning: latest known commit time

### `releasedAt`

- type: `LocalDateTime`
- meaning: production release success time

### `mergedToMasterAt`

- type: `LocalDateTime`
- meaning: merge-back to trunk success time

### `discardedAt`

- type: `LocalDateTime`
- meaning: discard operation time

### `discardReason`

- type: `String`
- meaning: reason for discard

### `remark`

- type: `String`
- meaning: internal management note

## 6. Recommended DO Draft

Suggested Java class name:

- `ChangeDO`

Suggested inheritance:

- extends `TenantBaseDO`

Suggested package:

- `cn.iocoder.yudao.module.devops.dal.dataobject.change`

Suggested annotations:

- `@TableName("dev_change")`
- `@KeySequence("dev_change_seq")`
- `@TableId`

## 7. Recommended Table Draft

Table:

- `dev_change`

### Columns

- `id BIGINT`
- `tenant_id BIGINT`
- `app_id BIGINT`
- `change_key VARCHAR(64)`
- `title VARCHAR(200)`
- `description VARCHAR(1000)`
- `branch_name VARCHAR(128)`
- `source_base_branch_name VARCHAR(64)`
- `owner_user_id BIGINT`
- `status TINYINT`
- `latest_commit_sha VARCHAR(64)`
- `latest_commit_message VARCHAR(512)`
- `latest_commit_at DATETIME`
- `released_at DATETIME`
- `merged_to_master_at DATETIME`
- `discarded_at DATETIME`
- `discard_reason VARCHAR(512)`
- `remark VARCHAR(512)`
- `creator VARCHAR(64)`
- `create_time DATETIME`
- `updater VARCHAR(64)`
- `update_time DATETIME`
- `deleted BIT`

### Recommended indexes

- `PRIMARY KEY (id)`
- `UNIQUE KEY uk_app_change_key (tenant_id, app_id, change_key)`
- `UNIQUE KEY uk_app_branch_name (tenant_id, app_id, branch_name)`
- `KEY idx_app_status (tenant_id, app_id, status)`
- `KEY idx_app_owner_user_id (tenant_id, app_id, owner_user_id)`

## 8. Validation Rules

### Create validation

- `appId` required
- `title` required
- `changeKey` required if generated outside backend, otherwise backend generates it
- `branchName` required if generated outside backend, otherwise backend generates it
- `changeKey` unique under the application
- `branchName` unique under the application
- `sourceBaseBranchName` required
- `ownerUserId` required
- application must be enabled

### Update validation

- `id` required
- cannot update to duplicate `changeKey`
- cannot update to duplicate `branchName`
- released changes should not allow branch identity edits
- discarded changes should not allow lifecycle recovery in MVP unless explicitly designed later

### Discard validation

Recommended behavior:

- allow discard only when status is `VALID`
- record `discardedAt` and `discardReason`
- discarded change cannot be submitted to any environment

### Release validation

Recommended behavior:

- do not change status to `RELEASED` manually
- only mark released after production deployment success
- production release success should also fill `releasedAt`

## 9. Suggested API Set

### Commands

- create change
- update change
- discard change

### Queries

- get change
- page changes by application
- list valid changes by application

## 10. Suggested Response Shape

### `DevChangeRespVO`

Suggested fields:

- `id`
- `appId`
- `changeKey`
- `title`
- `description`
- `branchName`
- `sourceBaseBranchName`
- `ownerUserId`
- `ownerUserNickname`
- `status`
- `latestCommitSha`
- `latestCommitMessage`
- `latestCommitAt`
- `releasedAt`
- `mergedToMasterAt`
- `discardedAt`
- `discardReason`
- `remark`
- `createTime`

## 11. Cross-Entity Relations

### One-to-many

- `Application -> Change`
- `Change -> ChangeEnvironmentRecord`

### Reference-only

- `Change.appId -> ApplicationDO.id`
- `Change.ownerUserId -> AdminUserDO.id`

## 12. Open Follow-ups For Change

These are not blockers for the entity itself, but should be decided later:

1. whether `changeKey` is global sequence per app or date-based code
2. whether branch creation is synchronous or delegated to async integration job
3. whether one change can later support branch rename in non-MVP versions
4. whether discarded changes can be reopened
5. whether change should directly bind requirement / task / defect ids in MVP

## 13. MVP Recommendation

For MVP, keep `Change` deliberately narrow:

- identity
- branch binding
- owner
- lightweight lifecycle
- latest commit summary

Do not overload it with:

- environment deployment status
- approval flow state
- artifact ownership
- rollback information

Those should remain in dedicated downstream entities.
