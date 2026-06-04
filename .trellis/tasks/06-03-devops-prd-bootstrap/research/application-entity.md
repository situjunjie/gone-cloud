# DevOps Application Entity Draft

## Scope

This document refines the `Application` entity only.

Goal:

- make the application aggregate concrete enough for direct backend implementation
- reduce context switching by freezing one domain object before moving to the next

## 1. Business Meaning

`Application` is the top-level DevOps business container under a tenant.

It is not just a code repository record.

It is the object that owns:

- one unique source code repository
- one default trunk branch
- multiple long-lived environments
- the future deployment pipeline definitions of those environments
- the future change streams created under that app

In product language, the application is the DevOps management entry for a deliverable system/service.

## 2. Boundary

### Application includes

- repository binding
- default trunk branch
- display metadata
- owner / responsible person
- activation state
- environment collection

### Application does not include directly

- change lifecycle details
- pipeline runtime history
- environment mounted change lists
- deployment snapshots

Those belong to separate entities.

## 3. Core Business Rules

1. One application belongs to one tenant.
2. One application binds exactly one code repository.
3. One application has exactly one default trunk branch.
4. One application can bind many environments through `ApplicationEnv`.
5. One application can own many changes.
6. `appKey` must be unique within a tenant.
7. A repository binding should also be unique within a tenant, to avoid duplicate DevOps ownership of the same repo.
8. Disabled applications should not accept new changes or new environment deployments.

## 4. Status Model

Recommended status values:

- `ENABLE` / `1`
- `DISABLE` / `0`

Optional future extension:

- `ARCHIVED`

### Recommendation

MVP should use the existing project convention first:

- reuse `CommonStatusEnum` style where possible
- keep status simple

If archival semantics become necessary later, add a separate archive flag or extend the enum in a controlled way.

## 5. Field Design

## 5.1 Required Fields

- `id`
- `tenantId` (inherited from `TenantBaseDO`)
- `appKey`
- `name`
- `repoProviderType`
- `repoIdentifier`
- `repoUrl`
- `defaultBranchName`
- `ownerUserId`
- `status`

## 5.2 Optional Fields

- `description`
- `icon`
- `remark`

## 5.3 Suggested Field Definitions

### `id`

- type: `Long`
- meaning: application primary key

### `appKey`

- type: `String`
- meaning: stable business identifier used in UI and API
- recommendation:
  - lowercase letters, digits, hyphen
  - tenant-unique
- example:
  - `erp-core`
  - `mall-admin`

### `name`

- type: `String`
- meaning: human-readable application name

### `description`

- type: `String`
- meaning: brief business description

### `icon`

- type: `String`
- meaning: optional icon or logo url

### `repoProviderType`

- type: `String`
- meaning: repository provider classification
- candidate values:
  - `GITLAB`
  - `GITHUB`
  - `GITEE`
  - `GENERIC_GIT`

### `repoIdentifier`

- type: `String`
- meaning: provider-side repo unique identifier
- examples:
  - `group/erp-core`
  - numeric repo id if provider requires it

### `repoUrl`

- type: `String`
- meaning: clone url or canonical repository url

### `defaultBranchName`

- type: `String`
- meaning: current trunk branch
- MVP expected default:
  - `master`

### `ownerUserId`

- type: `Long`
- meaning: responsible admin user
- relation:
  - `AdminUserDO.id`

### `status`

- type: `Integer`
- meaning: application availability
- recommendation:
  - reuse `CommonStatusEnum`

### `remark`

- type: `String`
- meaning: internal management note

## 6. Recommended DO Draft

Suggested Java class name:

- `ApplicationDO`

Suggested inheritance:

- extends `TenantBaseDO`

Suggested package:

- `cn.iocoder.yudao.module.devops.dal.dataobject.application`

Suggested annotations:

- `@TableName("dev_application")`
- `@KeySequence("dev_application_seq")`
- `@TableId`

## 7. Recommended Table Draft

Table:

- `dev_application`

### Columns

- `id BIGINT`
- `tenant_id BIGINT`
- `app_key VARCHAR(64)`
- `name VARCHAR(128)`
- `description VARCHAR(512)`
- `icon VARCHAR(512)`
- `repo_provider_type VARCHAR(32)`
- `repo_identifier VARCHAR(255)`
- `repo_url VARCHAR(512)`
- `default_branch_name VARCHAR(64)`
- `owner_user_id BIGINT`
- `status TINYINT`
- `remark VARCHAR(512)`
- `creator VARCHAR(64)`
- `create_time DATETIME`
- `updater VARCHAR(64)`
- `update_time DATETIME`
- `deleted BIT`

### Recommended indexes

- `PRIMARY KEY (id)`
- `UNIQUE KEY uk_app_key (tenant_id, app_key)`
- `UNIQUE KEY uk_repo_identifier (tenant_id, repo_identifier)`
- `KEY idx_owner_user_id (tenant_id, owner_user_id)`
- `KEY idx_status (tenant_id, status)`

## 8. Validation Rules

### Create validation

- `appKey` required
- `appKey` format valid
- `appKey` unique under tenant
- `name` required
- `repoProviderType` required
- `repoIdentifier` required
- `repoIdentifier` unique under tenant
- `repoUrl` required
- `defaultBranchName` required
- `ownerUserId` required

### Update validation

- `id` required
- cannot update to duplicate `appKey`
- cannot update to duplicate `repoIdentifier`

### Disable validation

Recommended behavior:

- allow disable anytime
- but once disabled:
  - cannot create new changes
  - cannot submit deployment

## 9. Suggested API Set

### Commands

- create application
- update application
- enable application
- disable application

### Queries

- get application
- page applications
- list applications by owner / status

## 10. Suggested Response Shape

### `DevAppRespVO`

Suggested fields:

- `id`
- `appKey`
- `name`
- `description`
- `icon`
- `repoProviderType`
- `repoIdentifier`
- `repoUrl`
- `defaultBranchName`
- `ownerUserId`
- `ownerUserNickname`
- `status`
- `remark`
- `createTime`

## 11. Cross-Entity Relations

### One-to-many

- `Application -> ApplicationEnv`
- `Application -> Change`

### Many-to-many

- `Application <-> Environment` through `ApplicationEnv`

### Reference-only

- `Application.ownerUserId -> AdminUserDO.id`

## 12. Open Follow-ups For Application

These are not blockers for the entity itself, but should be decided later:

1. whether repo credentials are stored on application or on separate integration config
2. whether application should support repository webhooks directly
3. whether application should support tags such as business line / team / language / deploy type
4. whether app-level secret configuration belongs here or in a separate app-config entity

## 13. MVP Recommendation

For MVP, keep `Application` deliberately narrow:

- identity
- repository binding
- owner
- trunk branch
- status

Do not overload it with:

- secret management
- deployment target details
- pipeline step definitions
- runtime execution metrics

Those should remain in dedicated downstream entities.
