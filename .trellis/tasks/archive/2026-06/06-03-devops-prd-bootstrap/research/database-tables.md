# DevOps Database Table Draft

## Scope

This draft translates the agreed MVP domain objects into a first-pass relational table design for the Yudao backend.

It follows the repository's current persistence conventions:

- business entities default to `TenantBaseDO`
- table-level tenant isolation via `tenant_id`
- DO classes should use `@TableName`, `@KeySequence`, and `@TableId`
- audit fields come from `BaseDO`

## Global Rules

### 1. Base Inherited Fields

Because this DevOps domain is tenant business data, all business DOs should extend:

- `cn.iocoder.yudao.framework.tenant.core.db.TenantBaseDO`

That implies every business table includes at least:

- `tenant_id`
- `create_time`
- `update_time`
- `creator`
- `updater`
- `deleted`

These fields are omitted from the per-table column lists below unless they need special notes.

### 2. ID Strategy

Use Yudao's standard pattern:

- `id BIGINT PRIMARY KEY`
- `@KeySequence("<table>_seq")` for Oracle/PostgreSQL/Kingbase/DB2/H2 compatibility

### 3. Suggested Table Prefix

Proposed prefix for the MVP DevOps domain:

- `dev_`

Reason:

- matches the user's current product naming decision for this subdomain
- stays separate from existing module prefixes such as `system_`, `iot_`, `pay_`
- leaves room for project management and requirement management to use their own prefixes later

## Table Draft

### 1. `dev_application`

Maps to: `Application`

#### Columns

- `id`
- `app_key` - unique business identifier
- `name`
- `description`
- `repo_provider_type`
- `repo_identifier`
- `repo_url`
- `default_branch_name`
- `owner_user_id`
- `status` - enabled / disabled / archived

#### Recommended indexes

- `uk_app_key (tenant_id, app_key)`
- `uk_repo_identifier (tenant_id, repo_identifier)`
- `idx_owner_user_id (tenant_id, owner_user_id)`

#### Notes

- One app binds one repo.
- `default_branch_name` is currently `master`, but keep it as a field for future flexibility.

### 2. `dev_environment`

Maps to: `Environment`

#### Columns

- `id`
- `env_key` - `test`, `pre`, `prod`
- `env_name`
- `env_stage` - test / pre / prod
- `infra_type` - k8s / host
- `description`
- `status` - enabled / disabled
- `remark`

#### Recommended indexes

- `uk_env_key (tenant_id, env_key)`
- `idx_env_stage (tenant_id, env_stage)`
- `idx_infra_type (tenant_id, infra_type)`
- `idx_status (tenant_id, status)`

#### Notes

- Shared tenant-level environment resource.
- Does not carry application-specific pipeline or approval config.

### 3. `dev_application_env`

Maps to: `ApplicationEnv`

#### Columns

- `id`
- `app_id`
- `env_id`
- `display_order`
- `deploy_branch_name_pattern`
- `pipeline_definition_id`
- `approval_required`
- `approval_config_json`
- `current_snapshot_id`
- `status` - enabled / disabled
- `remark`

#### Recommended indexes

- `uk_app_env (tenant_id, app_id, env_id)`
- `idx_env_id (tenant_id, env_id)`
- `idx_pipeline_definition_id (tenant_id, pipeline_definition_id)`
- `idx_current_snapshot_id (tenant_id, current_snapshot_id)`
- `idx_display_order (tenant_id, app_id, display_order)`
- `idx_status (tenant_id, app_id, status)`

#### Notes

- `prod` must set `approval_required = true`.
- `pre` may enable approvals, but is not required by default.

### 4. `dev_change`

Maps to: `Change`

#### Columns

- `id`
- `app_id`
- `change_key`
- `title`
- `description`
- `branch_name`
- `source_base_branch_name`
- `owner_user_id`
- `status` - active / released / discarded
- `latest_commit_sha`
- `latest_commit_message`
- `latest_commit_at`
- `released_at`
- `merged_to_master_at`
- `discarded_at`

#### Recommended indexes

- `uk_change_key (tenant_id, app_id, change_key)`
- `uk_branch_name (tenant_id, app_id, branch_name)`
- `idx_owner_user_id (tenant_id, owner_user_id)`
- `idx_status (tenant_id, app_id, status)`

#### Notes

- One change belongs to one app and one branch.
- `released` means entered production.

### 5. `dev_change_env`

Maps to: `ChangeEnvironmentRecord`

#### Columns

- `id`
- `change_id`
- `application_env_id`
- `mount_status` - mounted / unmounted / auto_cleaned
- `mounted_at`
- `mounted_by`
- `unmounted_at`
- `unmounted_by`
- `unmounted_reason`
- `last_pipeline_run_id`
- `last_snapshot_id`
- `last_merge_status`
- `last_build_status`
- `last_test_status`
- `last_deploy_status`
- `last_error_message`
- `approval_status`
- `approved_at`
- `approved_by`
- `included_in_current_snapshot`

#### Recommended indexes

- `uk_change_app_env (tenant_id, change_id, application_env_id)`
- `idx_app_env_mount_status (tenant_id, application_env_id, mount_status)`
- `idx_app_env_included_snapshot (tenant_id, application_env_id, included_in_current_snapshot)`
- `idx_last_pipeline_run_id (tenant_id, last_pipeline_run_id)`

#### Notes

- This is the most important relation table for UI and execution state.
- "Current environment changes" and "available changes" are derived from here.

### 6. `dev_pipeline_def`

Maps to: `PipelineDefinition`

#### Columns

- `id`
- `app_id`
- `env_id`
- `name`
- `version`
- `is_built_in_default`
- `stage_config_json`
- `status` - enabled / disabled

#### Recommended indexes

- `uk_env_version (tenant_id, env_id, version)`
- `idx_app_env (tenant_id, app_id, env_id)`

#### Notes

- MVP can seed one default definition per environment.
- Later versions can version pipeline definitions explicitly.

### 7. `dev_pipeline_run`

Maps to: `PipelineRun`

#### Columns

- `id`
- `app_id`
- `env_id`
- `pipeline_definition_id`
- `trigger_type` - submit_deploy / retry / rollback / manual
- `trigger_change_id`
- `triggered_by`
- `status` - pending / running / success / failed / canceled
- `current_stage_key`
- `started_at`
- `finished_at`
- `failure_stage_key`
- `failure_reason`
- `approval_gate_status`

#### Recommended indexes

- `idx_env_status_started_at (tenant_id, env_id, status, started_at)`
- `idx_trigger_change_id (tenant_id, trigger_change_id)`
- `idx_pipeline_definition_id (tenant_id, pipeline_definition_id)`

#### Notes

- One clicked change may trigger a run, but the run executes the environment's whole mounted set.

### 8. `dev_env_snapshot`

Maps to: `EnvironmentSnapshot`

#### Columns

- `id`
- `app_id`
- `env_id`
- `pipeline_run_id`
- `base_branch_name`
- `base_commit_sha`
- `deploy_branch_name`
- `snapshot_commit_sha`
- `change_count`
- `change_ids_json`
- `snapshot_status` - created / built / tested / deployed / failed

#### Recommended indexes

- `idx_env_pipeline_run_id (tenant_id, env_id, pipeline_run_id)`
- `idx_env_snapshot_status (tenant_id, env_id, snapshot_status)`
- `idx_snapshot_commit_sha (tenant_id, snapshot_commit_sha)`

#### Notes

- This is the canonical deploy and rollback unit.
- Snapshot content should be immutable after creation.

### 9. `dev_artifact`

Maps to: `Artifact`

#### Columns

- `id`
- `snapshot_id`
- `app_id`
- `env_id`
- `artifact_type`
- `artifact_version`
- `artifact_uri`
- `checksum`
- `metadata_json`

#### Recommended indexes

- `idx_snapshot_id (tenant_id, snapshot_id)`
- `uk_artifact_version (tenant_id, app_id, env_id, artifact_version)`

#### Notes

- Artifacts belong to snapshots, not to single changes.

### 10. `dev_deploy_record`

Maps to: `DeploymentRecord`

#### Columns

- `id`
- `app_id`
- `env_id`
- `pipeline_run_id`
- `snapshot_id`
- `artifact_id`
- `status` - success / failed / rolling_back / rolled_back
- `operator_user_id`
- `deployed_at`
- `finished_at`
- `rollback_from_deploy_id`
- `deployment_target_json`
- `message`

#### Recommended indexes

- `idx_env_deployed_at (tenant_id, env_id, deployed_at)`
- `idx_snapshot_id (tenant_id, snapshot_id)`
- `idx_pipeline_run_id (tenant_id, pipeline_run_id)`
- `idx_rollback_from_deploy_id (tenant_id, rollback_from_deploy_id)`

#### Notes

- `current_snapshot_id` on environment should point to the latest successful deployment target.

### 11. `dev_approval_record`

Maps to: `ApprovalRecord`

#### Columns

- `id`
- `app_id`
- `env_id`
- `pipeline_run_id`
- `change_id`
- `status` - pending / approved / rejected / skipped
- `approver_user_id`
- `comment`
- `acted_at`
- `rule_source` - env_rule / manual_gate

#### Recommended indexes

- `idx_pipeline_run_id (tenant_id, pipeline_run_id)`
- `idx_env_status (tenant_id, env_id, status)`
- `idx_change_id (tenant_id, change_id)`

#### Notes

- `prod` should always generate approval records.
- `pre` only does when approval is enabled.

## Suggested DO Naming

If the future Java module is named along the DevOps direction, a consistent DO naming set could be:

- `ApplicationDO`
- `EnvironmentDO`
- `ChangeDO`
- `ChangeEnvDO`
- `PipelineDefDO`
- `PipelineRunDO`
- `EnvSnapshotDO`
- `ArtifactDO`
- `DeployRecordDO`
- `ApprovalRecordDO`

All of the above should extend `TenantBaseDO`.

## Immediate Next Step

Translate this draft into:

1. Java DO class drafts
2. enum drafts for status fields
3. API contract draft
