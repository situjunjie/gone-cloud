# DevOps Environment Entity Draft

## Scope

This document refines the `Environment` entity only.

Goal:

- freeze the shared `Environment` model under the new rule that `Application` and `Environment` are many-to-many
- separate tenant-level environment identity from application-level deployment configuration

## 1. Business Meaning

`Environment` is a long-lived deployment target resource under a tenant.

It is not owned by one application.

It can be shared by multiple applications in the same tenant, such as:

- test
- pre
- prod

In product language, `Environment` expresses the stable target boundary.

The application-specific deployment behavior is carried by the relation entity between application and environment.

## 2. Boundary

### Environment includes

- tenant-scoped environment identity
- environment key and display name
- environment stage
- infrastructure type
- activation state
- shared description and remark

### Environment does not include directly

- application-specific deploy branch naming
- application-specific pipeline binding
- application-specific approval strategy
- application-specific current snapshot
- application-specific display order under one application

Those belong to the relation entity `ApplicationEnv`.

## 3. Core Business Rules

1. One environment belongs to one tenant.
2. One environment can be bound by many applications.
3. One application can bind many environments.
4. `envKey` should be unique within a tenant.
5. Disabled environments should not accept new deployment submissions from bound applications.
6. Sensitive deployment behavior should be configured at the application-environment relation layer, not the shared environment layer.

## 4. Status Model

Recommended status values:

- `ENABLE` / `0`
- `DISABLE` / `1`

### Recommendation

The shared environment entity itself can reuse `CommonStatusEnum`.

Because at this layer it is only an activation switch for the tenant-scoped environment resource.

## 5. Field Design

## 5.1 Required Fields

- `id`
- `tenantId` (inherited from `TenantBaseDO`)
- `envKey`
- `envName`
- `envStage`
- `infraType`
- `status`

## 5.2 Optional Fields

- `description`
- `remark`

## 5.3 Suggested Field Definitions

### `id`

- type: `Long`
- meaning: environment primary key

### `envKey`

- type: `String`
- meaning: stable business identifier under the tenant
- recommendation:
  - lowercase letters, digits, hyphen
- examples:
  - `test`
  - `pre`
  - `prod`

### `envName`

- type: `String`
- meaning: human-readable environment name
- examples:
  - `测试环境`
  - `预发环境`
  - `正式环境`

### `envStage`

- type: `String`
- meaning: business stage of the environment
- candidate values:
  - `TEST`
  - `PRE`
  - `PROD`
  - `DEV`

### `infraType`

- type: `String`
- meaning: infrastructure target type of the environment
- candidate values:
  - `K8S`
  - `HOST`
- explanation:
  - `K8S` means Kubernetes cluster environment
  - `HOST` means host cluster environment

### `status`

- type: `Integer`
- meaning: environment availability
- recommendation:
  - reuse `CommonStatusEnum`

### `description`

- type: `String`
- meaning: brief description for the shared environment resource

### `remark`

- type: `String`
- meaning: internal management note

## 6. Recommended DO Draft

Suggested Java class name:

- `EnvironmentDO`

Suggested inheritance:

- extends `TenantBaseDO`

Suggested package:

- `cn.iocoder.yudao.module.devops.dal.dataobject.environment`

Suggested annotations:

- `@TableName("dev_environment")`
- `@KeySequence("dev_environment_seq")`
- `@TableId`

## 7. Application-Environment Relation

Because `Application` and `Environment` are many-to-many, a dedicated relation entity is required.

Recommended relation entity:

- `ApplicationEnvDO`
- table: `dev_application_env`

This relation should carry application-specific deployment configuration:

- `appId`
- `envId`
- `displayOrder`
- `deployBranchNamePattern`
- `pipelineDefinitionId`
- `approvalRequired`
- `approvalConfigJson`
- `currentSnapshotId`
- `status`

### Important modeling consequence

Under this rule:

- `Environment` is the shared target definition
- `ApplicationEnv` is the actual deployment entry used by UI and runtime

So later:

- environment tab in application detail should be driven by `ApplicationEnv`
- `ChangeEnvironmentRecord` is better to reference `applicationEnvId`

## 8. Recommended Table Draft

### 8.1 Shared Environment Table

Table:

- `dev_environment`

### Columns

- `id BIGINT`
- `tenant_id BIGINT`
- `env_key VARCHAR(64)`
- `env_name VARCHAR(128)`
- `env_stage VARCHAR(32)`
- `infra_type VARCHAR(32)`
- `description VARCHAR(512)`
- `status TINYINT`
- `remark VARCHAR(512)`
- `creator VARCHAR(64)`
- `create_time DATETIME`
- `updater VARCHAR(64)`
- `update_time DATETIME`
- `deleted BIT`

### Recommended indexes

- `PRIMARY KEY (id)`
- `UNIQUE KEY uk_env_key (tenant_id, env_key)`
- `KEY idx_env_stage (tenant_id, env_stage)`
- `KEY idx_infra_type (tenant_id, infra_type)`
- `KEY idx_status (tenant_id, status)`

### 8.2 Application Environment Relation Table

Table:

- `dev_application_env`

### Columns

- `id BIGINT`
- `tenant_id BIGINT`
- `app_id BIGINT`
- `env_id BIGINT`
- `display_order INT`
- `deploy_branch_name_pattern VARCHAR(128)`
- `pipeline_definition_id BIGINT`
- `approval_required BIT`
- `approval_config_json VARCHAR(2000)`
- `current_snapshot_id BIGINT`
- `status TINYINT`
- `remark VARCHAR(512)`
- `creator VARCHAR(64)`
- `create_time DATETIME`
- `updater VARCHAR(64)`
- `update_time DATETIME`
- `deleted BIT`

### Recommended indexes

- `PRIMARY KEY (id)`
- `UNIQUE KEY uk_app_env (tenant_id, app_id, env_id)`
- `KEY idx_env_id (tenant_id, env_id)`
- `KEY idx_pipeline_definition_id (tenant_id, pipeline_definition_id)`
- `KEY idx_current_snapshot_id (tenant_id, current_snapshot_id)`
- `KEY idx_display_order (tenant_id, app_id, display_order)`
- `KEY idx_status (tenant_id, app_id, status)`

## 9. Validation Rules

### Create environment validation

- `envKey` required
- `envKey` unique under tenant
- `envName` required
- `envStage` required
- `infraType` required

### Bind environment to application validation

- `appId` required
- `envId` required
- application must be enabled
- environment must be enabled
- relation `(appId, envId)` must be unique under tenant
- `deployBranchNamePattern` required

### Approval rule validation

Recommended behavior:

- if bound environment stage is `PROD`, `approvalRequired` must be `true`
- if bound environment stage is `PRE`, approval can be enabled optionally
- if bound environment stage is `TEST`, approval defaults to `false`

## 10. Suggested API Set

### Shared environment commands

- create environment
- update environment
- enable environment
- disable environment

### Application binding commands

- bind environment to application
- update application environment config
- unbind environment from application

### Queries

- get environment
- page environments
- list available environments
- list bound environments by application

## 11. Suggested Response Shape

### `DevEnvironmentRespVO`

Suggested fields:

- `id`
- `envKey`
- `envName`
- `envStage`
- `infraType`
- `description`
- `status`
- `remark`
- `createTime`

### `DevApplicationEnvRespVO`

Suggested fields:

- `id`
- `appId`
- `envId`
- `envKey`
- `envName`
- `envStage`
- `infraType`
- `displayOrder`
- `deployBranchNamePattern`
- `pipelineDefinitionId`
- `approvalRequired`
- `approvalConfigJson`
- `currentSnapshotId`
- `status`
- `remark`

## 12. Cross-Entity Relations

### Many-to-many

- `Application <-> Environment` through `ApplicationEnv`

### One-to-many

- `Application -> ApplicationEnv`
- `Environment -> ApplicationEnv`

### Reference-only

- `ApplicationEnv.appId -> ApplicationDO.id`
- `ApplicationEnv.envId -> EnvironmentDO.id`

## 13. MVP Recommendation

For MVP, keep `Environment` deliberately narrow:

- shared identity
- shared stage
- shared infrastructure type
- shared activation state

Put runtime and application-specific deployment behavior into `ApplicationEnv`.
