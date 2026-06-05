# DevOps Domain Objects Draft

## Scope

This draft refines the MVP domain model for the change-centered DevOps design already agreed in `prd.md`.

It is intentionally one step above database DDL:

- concrete enough to drive table design and API design
- still flexible on exact column type, index, and normalization choices

## Design Principles

1. `Change` is the business object developers reason about.
2. `Environment` is the long-lived deployment target.
3. The real execution unit is an `Environment Snapshot`, not a single change.
4. Build artifacts and rollback targets belong to snapshots.
5. Approval and deployment history must be auditable.
6. This DevOps domain is tenant business data, so persistence objects should default to `TenantBaseDO`, not plain `BaseDO`.

## Tenant Boundary

These DevOps entities belong to tenant business scope rather than platform-core scope.

Therefore, when translated into Yudao DO classes:

- business entities should extend `cn.iocoder.yudao.framework.tenant.core.db.TenantBaseDO`
- `tenantId` is inherited and should be treated as mandatory tenant partition data
- only system-core/global metadata would qualify for direct `BaseDO`, which is not the default for this DevOps domain

Examples in the current codebase:

- tenant-scoped business DO: `IotProductDO`, `AdminUserDO`, `RoleDO`
- system-core exception: `TenantDO` extends `BaseDO` and is marked `@TenantIgnore`

## Core Entities

### 1. Application

Represents a deliverable application container.

#### Key fields

- `id`
- `appKey`
- `name`
- `description`
- `repoProviderType` - github / gitlab / gitee / self-hosted
- `repoIdentifier` - unique repo path or repo id
- `repoUrl`
- `defaultBranchName` - current MVP fixed as `master`
- `status` - enabled / disabled / archived
- `ownerUserId`
- `createdBy`
- `createdAt`
- `updatedAt`

#### Persistence note

- DO class should extend `TenantBaseDO`
- `tenantId` is inherited, not listed again below

#### Notes

- One application binds exactly one code repository.
- One application owns many environments.
- One application owns many changes.

### 2. Environment

Represents a tenant-scoped long-lived deployment target.

#### Key fields

- `id`
- `envKey` - e.g. `test`, `pre`, `prod`
- `envName`
- `envStage` - test / pre / prod
- `infraType` - k8s / host
- `description`
- `status`
- `remark`
- `createdAt`
- `updatedAt`

#### Persistence note

- DO class should extend `TenantBaseDO`

#### Notes

- Environment is a shared tenant resource.
- Application and Environment are many-to-many.

### 3. Application Environment

Represents the binding between one application and one shared environment.

This is the actual deployment entry used by the application detail page.

#### Key fields

- `id`
- `appId`
- `envId`
- `displayOrder`
- `deployBranchNamePattern` - e.g. `test-release`
- `pipelineDefinitionId`
- `approvalRequired` - hard requirement flag
- `approvalConfigJson` - optional approver strategy / quorum
- `status`
- `currentSnapshotId` - latest successful deployed snapshot
- `createdAt`
- `updatedAt`

#### Persistence note

- DO class should extend `TenantBaseDO`

#### Notes

- `prod` must require approval.
- `pre` supports approval configuration, but MVP default is not required.
- `test` default is no approval.
- Later `ChangeEnvironmentRecord` should reference this binding rather than a naked app-env pair.

### 4. Change

Represents one application-level change stream, bound to one feature branch.

#### Key fields

- `id`
- `appId`
- `changeKey` - human-readable serial number
- `title`
- `description`
- `branchName` - e.g. `featA`
- `sourceBaseBranchName` - usually `master`
- `ownerUserId`
- `status` - active / released / discarded
- `latestCommitSha`
- `latestCommitMessage`
- `latestCommitAt`
- `releasedAt`
- `mergedToMasterAt`
- `discardedAt`
- `createdAt`
- `updatedAt`

#### Persistence note

- DO class should extend `TenantBaseDO`

#### Notes

- `released` means the change has entered production.
- `discarded` means the change is no longer intended to continue.
- The change itself does not encode test/pre/prod progression state.

### 5. Change Environment Record

Represents the relationship between one change and one environment.

This is the key relation object that carries environment-specific facts.

#### Key fields

- `id`
- `changeId`
- `applicationEnvId`
- `mountStatus` - mounted / unmounted / auto-cleaned
- `mountedAt`
- `mountedBy`
- `unmountedAt`
- `unmountedBy`
- `unmountedReason`
- `lastPipelineRunId`
- `lastSnapshotId`
- `lastMergeStatus` - pending / success / conflicted / failed
- `lastBuildStatus`
- `lastTestStatus`
- `lastDeployStatus`
- `lastErrorMessage`
- `approvalStatus` - not_required / pending / approved / rejected
- `approvedAt`
- `approvedBy`
- `includedInCurrentSnapshot` - whether current deployed snapshot contains this change
- `createdAt`
- `updatedAt`

#### Persistence note

- DO class should extend `TenantBaseDO`

#### Notes

- The UI-facing "current environment changes" and "available changes" lists are derived primarily from this entity.
- After production success and merge-back to `master`, records in lower environments can be auto-cleaned.

### 6. Pipeline Definition

Represents the environment-owned pipeline definition.

#### Key fields

- `id`
- `appId`
- `envId`
- `name`
- `version`
- `isBuiltInDefault`
- `stageConfigJson`
- `isEnabled`
- `createdBy`
- `createdAt`
- `updatedAt`

#### Persistence note

- DO class should extend `TenantBaseDO`

#### Notes

- MVP can seed the default four stages:
  - merge
  - build
  - unit test
  - deploy
- Later versions can support custom orchestration.

### 7. Pipeline Run

Represents one concrete execution of an environment pipeline.

#### Key fields

- `id`
- `appId`
- `envId`
- `pipelineDefinitionId`
- `triggerType` - submit_deploy / retry / rollback / manual
- `triggerChangeId` - nullable; the initiating change from the UI
- `triggeredBy`
- `status` - pending / running / success / failed / canceled
- `currentStageKey`
- `startedAt`
- `finishedAt`
- `failureStageKey`
- `failureReason`
- `approvalGateStatus` - not_required / waiting / passed / rejected
- `createdAt`

#### Persistence note

- DO class should extend `TenantBaseDO`

#### Notes

- A single clicked change may trigger a run, but the run executes against the whole mounted change set of the environment.

### 8. Environment Snapshot

Represents one immutable environment execution snapshot.

This is the canonical deployment and rollback unit.

#### Key fields

- `id`
- `appId`
- `envId`
- `pipelineRunId`
- `baseBranchName` - `master`
- `baseCommitSha`
- `deployBranchName`
- `snapshotCommitSha` - merge result commit or equivalent resolved revision
- `changeCount`
- `changeIdsJson` - ordered mounted change list used for this run
- `snapshotStatus` - created / built / tested / deployed / failed
- `createdAt`

#### Persistence note

- DO class should extend `TenantBaseDO`

#### Notes

- Snapshot content should be treated as immutable after creation.
- Rollback targets point to snapshots, not to single changes.

### 8. Artifact

Represents build output produced from one environment snapshot.

#### Key fields

- `id`
- `snapshotId`
- `appId`
- `envId`
- `artifactType` - image / jar / package / helm / other
- `artifactVersion`
- `artifactUri`
- `checksum`
- `metadataJson`
- `createdAt`

#### Persistence note

- DO class should extend `TenantBaseDO`

#### Notes

- One snapshot may produce one or many artifacts.
- Artifact ownership belongs to the snapshot.

### 9. Deployment Record

Represents the actual deployment result for a snapshot.

#### Key fields

- `id`
- `appId`
- `envId`
- `pipelineRunId`
- `snapshotId`
- `artifactId`
- `status` - success / failed / rolling_back / rolled_back
- `operatorUserId`
- `deployedAt`
- `finishedAt`
- `rollbackFromDeploymentId`
- `deploymentTargetJson`
- `message`

#### Persistence note

- DO class should extend `TenantBaseDO`

#### Notes

- The environment's `currentSnapshotId` points to the last successful deployment target.

### 10. Approval Record

Represents approval or gate actions for protected environments.

#### Key fields

- `id`
- `appId`
- `envId`
- `pipelineRunId`
- `changeId` - optional direct initiator reference
- `status` - pending / approved / rejected / skipped
- `approverUserId`
- `comment`
- `actedAt`
- `ruleSource` - env_rule / manual_gate

#### Persistence note

- DO class should extend `TenantBaseDO`

#### Notes

- `prod` should always produce approval records.
- `pre` only does so when approval is enabled.

## Recommended Aggregate Boundaries

### Application aggregate

Owns:

- application
- environments
- pipeline definitions

### Change aggregate

Owns:

- change
- change environment records

### Environment execution aggregate

Owns:

- pipeline run
- environment snapshot
- artifact
- deployment record
- approval record

## Derived Views

### Environment Current Changes

Derived from:

- mounted `change_environment_record`
- latest successful `environment_snapshot`

### Available Changes For Environment

Derived from:

- `change.status = active`
- same `appId`
- no active mounted relation for the target environment

### Production Released Changes

Derived from:

- `change.status = released`
- or existence in successful production snapshot

## MVP Persistence Priority

For MVP, the minimum durable entities should be:

1. `application`
2. `environment`
3. `change`
4. `change_environment_record`
5. `pipeline_definition`
6. `pipeline_run`
7. `environment_snapshot`
8. `artifact`
9. `deployment_record`
10. `approval_record`

## Suggested Next Step

Translate these entities into:

1. table draft
2. API draft
3. page interactions
