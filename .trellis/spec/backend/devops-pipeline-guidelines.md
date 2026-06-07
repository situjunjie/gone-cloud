# DevOps Pipeline Guidelines

DevOps 流水线定义由平台持有，Jenkins 在当前阶段只作为构建/测试/Jenkinsfile 执行器的下游目标。不要把流水线定义所有权下放到 Jenkins Job 配置里。

## Scenario: Visual Pipeline Definition MVP

### 1. Scope / Trigger

- Trigger: adding or changing visual pipeline definition, pipeline DSL validation, Jenkinsfile preview generation, or application-environment pipeline linkage.
- Scope: `yudao-module-devops` pipeline controllers, VOs, services, DSL model, mappers, SQL bootstrap scripts, dict/menu scripts, and focused tests.

### 2. Signatures

- APIs:
  - `GET /devops/pipeline/node-types`
  - `GET /devops/pipeline/command-templates`
  - `GET /devops/pipeline/get-by-application-env?applicationEnvId={id}`
  - `POST /devops/pipeline/save-draft`
  - `POST /devops/pipeline/validate`
  - `POST /devops/pipeline/publish`
  - `GET /devops/pipeline/version/jenkinsfile?id={versionId}`
  - `GET /devops/pipeline/version/list?definitionId={definitionId}`
- DB:
  - `dev_pipeline_definition` stores one pipeline definition per `dev_application_env`.
  - `dev_pipeline_definition_version` stores mutable draft version `version_no=0` plus immutable published versions from `version_no=1`.
  - `dev_application_env.pipeline_definition_id` links the environment to the active platform-owned definition.
- Dict:
  - `dev_pipeline_definition_version_status`: `0` draft, `1` published, `2` archived.
- Menu:
  - Pipeline designer is a hidden `system_menu` entry under the DevOps application menu.
  - Route path is `/devops/pipeline/designer`, component is `devops/pipeline/designer`, component name is `DevopsPipelineDesigner`, and permission is `devops:pipeline:query`.
  - `devops:pipeline:create` and `devops:pipeline:delete` may be bootstrapped as reserved button permissions even when the current frontend does not render them.

### 3. Contracts

- Save draft accepts `applicationEnvId`, `name`, `diagramJson`, `specJson`, and optional `remark`.
- Save draft may persist an invalid DSL so the frontend can keep user edits; it must also persist `validationResultJson`.
- Publish only accepts a valid draft belonging to the same definition; publish creates a new published version and updates `dev_application_env.pipeline_definition_id`.
- Build/test/image commands must come from backend command templates via `params.commandTemplateKey`; frontend must not submit arbitrary raw shell commands.
- Disabled future nodes such as approval and deploy can be returned by `/node-types`, but validation must reject them until the backend implements execution semantics.
- Phase 1 Jenkinsfile is a generated preview/published artifact. Jenkins build-run creation, stage callbacks, logs, and platform approval resume are Phase 2 contracts.
- The backend menu table does not carry a frontend `activeMenu` / `active_menu` field. Hidden designer route activation must be configured in frontend route meta, not in `sql/mysql/devops-menu.sql`.

### 4. Validation & Error Matrix

| Condition | Expected behavior |
|---|---|
| `applicationEnvId` does not exist on save | Throw `APPLICATION_ENV_NOT_EXISTS` |
| Definition id does not exist | Throw `PIPELINE_DEFINITION_NOT_EXISTS` |
| Draft version id does not exist | Throw `PIPELINE_VERSION_NOT_EXISTS` or `PIPELINE_DRAFT_NOT_EXISTS` depending on ownership/status |
| `specJson` is blank or invalid JSON | Return validation error such as `SPEC_JSON_REQUIRED` or `SPEC_JSON_INVALID` |
| Node id is blank, duplicate, or invalid shape | Return validation errors; do not throw runtime exceptions |
| Node type is unknown or disabled | Return validation error; do not generate Jenkinsfile |
| Build/test/image node lacks a template | Return `COMMAND_TEMPLATE_REQUIRED` |
| Template does not exist or type mismatches node | Return template validation error |
| Graph has multiple starts, multiple terminals, or a cycle | Return topology validation error |
| Publish invalid draft | Throw `PIPELINE_SPEC_INVALID` |

### 5. Good / Base / Bad Cases

- Good: platform stores DSL and generated Jenkinsfile preview; Jenkins receives generated content later as an execution input.
- Base: visual editor can show disabled approval/deploy nodes as unavailable placeholders while backend rejects them on validation.
- Bad: frontend sends raw `mvn` or `docker` commands directly in node params, because this bypasses backend governance and makes future audit unsafe.

### 6. Tests Required

- Compile DevOps server with reactor: `mvn -pl yudao-module-devops/yudao-module-devops-server -am -DskipTests compile`.
- Run focused pipeline tests with reactor: `mvn -pl yudao-module-devops/yudao-module-devops-server -am -Dtest='*Pipeline*Test,JenkinsfileGeneratorServiceImplTest' -Dsurefire.failIfNoSpecifiedTests=false test`.
- Cover:
  - draft creation creates definition and draft version;
  - publish creates a new published version and links application environment;
  - disabled nodes and missing command templates fail validation;
  - malformed/null DSL content returns validation errors instead of throwing;
  - Jenkinsfile generation contains the expected ordered stages and template commands.

### 7. Wrong vs Correct

#### Wrong

```java
String command = String.valueOf(node.getParams().get("command"));
builder.append("sh '").append(command).append("'");
```

#### Correct

```java
String templateKey = String.valueOf(node.getParams().get("commandTemplateKey"));
PipelineCommandTemplateRespVO template = pipelineNodeRegistryService.getCommandTemplate(templateKey);
builder.append("goneDevopsUnitTest(command: '").append(template.getCommand()).append("')");
```

## Design Decision: Platform-Owned Definition, Jenkins-Executable Output

**Context**: The platform needs visual editing, versioning, validation, approval, deployment orchestration, and stage visibility. Jenkins is useful as an executor, but it should not own product-level pipeline definition state.

**Decision**: Store pipeline definition/version/spec/Jenkinsfile in the platform database. Generate Jenkinsfile from backend-approved DSL and templates. Defer Jenkins job invocation and run-stage sensing to the deployment execution phase.

**Extensibility**: Later execution can add `pipeline_run` and `pipeline_run_stage` models, Jenkins queue/build mapping, log retrieval, and approval resume callbacks without changing the visual definition ownership model.

## Scenario: Application Detail Release Tab Read Model

### 1. Scope / Trigger

- Trigger: adding or changing the application-detail "发布" tab, release environment tabs, read-only pipeline display, or branch mount lists.
- Scope: `ApplicationController`, application release response VOs, `ApplicationService`, application/change/environment/pipeline mappers, and focused application service tests.

### 2. Signatures

- APIs:
  - `GET /devops/application/release/env-tabs?appId={appId}`
  - `GET /devops/application/release/env-detail?applicationEnvId={applicationEnvId}`
  - `POST /devops/application/release/submit-branch`
  - Existing action APIs reused by the page:
    - `POST /devops/change/mount-env`
    - `PUT /devops/change/unmount-env`
- Permissions:
  - Release tab read APIs use `devops:application:query`.
  - Release tab submit-and-trigger action uses `devops:application:release-submit`.
  - Mount action uses `devops:change:mount-env`.
  - Unmount action uses `devops:change:unmount-env`.
- DB ownership:
  - `dev_application_env` owns app-to-environment linkage and display order.
  - `dev_pipeline_definition.application_env_id` owns the pipeline definition for one app environment.
  - `dev_pipeline_definition.published_version_id` identifies the released pipeline shown in the tab.
  - `dev_change` owns app-level effective branches.
  - `dev_change_env` owns branch-to-application-environment mount status.
  - `dev_pipeline_run` owns platform-side pipeline run records created by release-tab submit actions.

### 3. Contracts

- `env-tabs` validates the application exists, then returns application environments ordered by `display_order ASC, id ASC`.
- Environment tab payload must include both relation fields and display fields: `applicationEnvId`, `appId`, `envId`, `envKey`, `envName`, `envStage`, `infraType`, `displayOrder`, `deployBranchNamePattern`, `pipelineDefinitionId`, `hasPublishedPipeline`, and `status`.
- `env-detail` validates the application-environment relation and environment exist, then returns:
  - `env`: same shape as one `env-tabs` item.
  - `pipeline`: only the published pipeline version. Draft versions must not be shown as release pipelines.
  - `mountedBranches`: active app changes with `dev_change_env.mount_status = MOUNTED` for the current `applicationEnvId`.
  - `unmountedBranches`: active app changes with no current mounted relation for the current `applicationEnvId`; historical `UNMOUNTED` / `AUTO_CLEANED` rows belong here.
- Effective branch means `dev_change.status = ChangeStatusEnum.ACTIVE`.
- Pipeline display must be linearized from published `specJson` via topological sort. Do not use designer canvas coordinates for the release tab.
- If no definition or no published version exists, return a pipeline payload with empty `nodes` / `edges` and `emptyReason` instead of throwing.
- `submit-branch` request body is `applicationEnvId + changeIds[]`, where `changeIds` means the final desired mounted set for the current environment.
- `submit-branch` validates every requested change exists, is `ACTIVE`, and belongs to the same application as the target app-environment.
- `submit-branch` treats the request as a full-set sync:
  - requested ids are mounted/restored into `dev_change_env`;
  - currently mounted ids that are absent from the request are marked `UNMOUNTED`;
  - historical `UNMOUNTED` rows can be restored by including their change id again.
- `submit-branch` requires an existing published pipeline version only when `changeIds` is non-empty.
- `submit-branch` with a non-empty `changeIds` creates one `dev_pipeline_run` row, updates every target `dev_change_env.last_pipeline_run_id`, and marks the latest build status as running.
- `submit-branch` with an empty `changeIds` means "remove all deployed branches from this environment"; it updates `dev_change_env` mount status only and does not create a new pipeline run.
- Current `dev_pipeline_run` schema still has single-change anchor fields (`change_id`, `change_env_id`, `branch_name`), so the environment-level sync run stores the first requested change as the compatibility anchor. Jenkins queue/build triggering and callbacks should still attach to `dev_pipeline_run` instead of creating another run concept.

### 4. Validation & Error Matrix

| Condition | Expected behavior |
|---|---|
| `appId` does not exist on `env-tabs` | Throw `APPLICATION_NOT_EXISTS` |
| `applicationEnvId` does not exist on `env-detail` | Throw `APPLICATION_ENV_NOT_EXISTS` |
| linked environment does not exist | Throw `ENVIRONMENT_NOT_EXISTS` |
| no pipeline definition | Return `pipeline.emptyReason = NO_PIPELINE_DEFINITION` |
| definition exists but no published version | Return `pipeline.emptyReason = NO_PUBLISHED_VERSION` |
| published version references invalid or cyclic `specJson` | Return `pipeline.emptyReason = SPEC_INVALID` |
| active change has `UNMOUNTED` relation for the current environment | Include it in `unmountedBranches` |
| active change has no relation for the current environment | Include it in `unmountedBranches` |
| `submit-branch` any requested change is not `ACTIVE` | Throw `CHANGE_STATUS_NOT_ACTIVE` |
| `submit-branch` any requested change belongs to another app | Throw `APPLICATION_ENV_NOT_EXISTS` |
| `submit-branch` non-empty `changeIds` has no published pipeline version | Throw `PIPELINE_PUBLISHED_VERSION_NOT_EXISTS` |

### 5. Good / Base / Bad Cases

- Good: frontend loads environment tabs from one application-scoped read API, then loads the selected environment detail from one application-environment-scoped read API.
- Good: frontend uses `POST /devops/application/release/submit-branch` as the single release-tab write API and always submits the final target change-id set for the environment.
- Good: frontend renders `pipeline.nodes` from left to right using `displayOrder`; it may use `edges` for simple connectors.
- Base: existing `/devops/pipeline/get-by-application-env` remains the designer/read API for full pipeline definition and draft/published metadata.
- Base: existing `/devops/change/mount-env` remains a plain mount API and must not be treated as a pipeline trigger.
- Bad: frontend joins application envs, environments, pipeline versions, and change-env rows through several independent table APIs and reimplements branch mount rules.
- Bad: release tab displays a draft pipeline as if it were released.

### 6. Tests Required

- Service test that `env-tabs` returns environment display fields and `hasPublishedPipeline`.
- Service test that `env-detail` returns published pipeline metadata and topologically ordered nodes.
- Service test that active branches split into mounted and unmounted lists using `ChangeEnvMountStatusEnum.MOUNTED`.
- Service test that no published version returns `NO_PUBLISHED_VERSION` with empty node/edge lists.
- Service test that `submit-branch` can add new changes by syncing the target set, creates/restores `ChangeEnvDO`, creates one `PipelineRunDO`, updates all target `lastPipelineRunId` values, and returns mounted/unmounted change ids.
- Service test that `submit-branch` can remove partial changes by syncing the target set and marks removed rows as `UNMOUNTED`.
- Service test that `submit-branch` with empty `changeIds` unmounts all current rows and does not create a pipeline run.
- Service test that `submit-branch` without a published version throws `PIPELINE_PUBLISHED_VERSION_NOT_EXISTS`.
- Compile or run the DevOps server module after controller/VO changes:
  `mvn -pl yudao-module-devops/yudao-module-devops-server -am test`

### 7. Wrong vs Correct

#### Wrong

```java
// Release tab treats a draft as a deployable/released pipeline.
respVO.setSpecJson(definition.getDraftVersion().getSpecJson());
```

#### Correct

```java
if (definition.getPublishedVersionId() == null) {
    pipeline.setEmptyReason("NO_PUBLISHED_VERSION");
    pipeline.setNodes(List.of());
    return pipeline;
}
```

#### Wrong

```java
// Plain mount does not create a run anchor, so later callbacks have nowhere stable to attach.
changeService.mountChangeEnv(reqVO, userId);
```

#### Correct

```java
PipelineRunDO run = createPipelineRun(definition, publishedVersion, changeEnv, change, userId);
changeEnv.setLastPipelineRunId(run.getId());
```
