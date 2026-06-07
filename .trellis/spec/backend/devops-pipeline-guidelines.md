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
