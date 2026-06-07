# Visual pipeline orchestration MVP technical design

## Scope

第一期目标是先实现流水线可视化编排能力，并为后续 Jenkins 执行、产物收集、审批、部署留好模型。

MVP 做：

* 应用环境绑定流水线定义。
* Vue Flow 画布拖拽编排。
* 后端保存草稿、校验、发布版本。
* 后端从 DSL 生成 Jenkinsfile 预览和版本快照。
* Jenkins 定位为构建/测试/产物生成执行器。
* 先定义 run/stage/artifact 模型和 API 边界，但第一阶段实现可先只做到定义/版本/预览。

MVP 不做：

* 发布分支聚合合并和在线冲突解决。
* 真正 K8S 部署执行。
* 复杂 DAG 并行、循环、条件分支。
* Jenkins webhook，只预留轮询/回调字段。
* BPM/Flowable 复杂审批。

## Product flow

### Configure

1. 用户进入应用详情页。
2. 选择某个环境，例如 TEST、PRE、PROD。
3. 点击“流水线配置”。
4. 进入可视化编排页。
5. 从节点面板拖入节点：Checkout、Unit Test、Build Artifact、Build Image、Report Artifacts、Approval、Deploy。
6. 配置节点参数。
7. 点击“校验”。
8. 后端返回拓扑、参数、Jenkinsfile 生成校验结果。
9. 点击“发布版本”。
10. 后端创建不可变版本，并把 `dev_application_env.pipeline_definition_id` 绑定到 definition。

### Execute later

1. 发布操作创建 release branch 或得到 release commit。
2. 平台创建 `PipelineRun`。
3. 平台按已发布 version 触发 Jenkins 构建。
4. Jenkins 运行构建/测试/产物阶段。
5. 平台同步 stage、日志、产物。
6. 平台侧审批。
7. 平台侧部署。

## Domain model

### Existing model reuse

`dev_application_env` already has:

* `pipeline_definition_id`
* `approval_required`
* `approval_config_json`
* `current_snapshot_id`

Use `pipeline_definition_id` as application-environment binding.

`dev_change_env` already has:

* `last_pipeline_run_id`
* `last_merge_status`
* `last_build_status`
* `last_test_status`
* `last_deploy_status`
* `approval_status`

Use these as run summary fields.

### New tables

#### `dev_pipeline_definition`

Logical pipeline definition. Mutable metadata, immutable execution is in versions.

Fields:

* `id`
* `name`
* `definition_key`
* `app_id`
* `application_env_id`
* `status`: 0 enabled, 1 disabled
* `draft_version_id`
* `published_version_id`
* `remark`
* standard audit, deleted, tenant fields

Indexes:

* unique `tenant_id, application_env_id`
* unique `tenant_id, definition_key`
* index `tenant_id, app_id`
* index `tenant_id, published_version_id`

#### `dev_pipeline_definition_version`

Versioned immutable pipeline content.

Fields:

* `id`
* `definition_id`
* `version_no`
* `version_name`
* `version_status`: DRAFT, PUBLISHED, ARCHIVED
* `diagram_json`
* `spec_json`
* `node_schema_version`
* `jenkinsfile_text`
* `jenkinsfile_checksum`
* `validation_result_json`
* `published_at`
* `published_by`
* standard audit, deleted, tenant fields

Indexes:

* unique `tenant_id, definition_id, version_no`
* index `tenant_id, definition_id, version_status`

#### `dev_jenkins_provider`

Jenkins instance configuration.

Fields:

* `id`
* `name`
* `server_url`
* `username`
* `api_token` encrypted
* `token_mask`
* `default_job_path`
* `crumb_required`
* `status`
* `last_check_time`
* `last_check_status`
* `last_check_message`
* `remark`
* standard audit, deleted, tenant fields

Security:

* `api_token` must use `EncryptTypeHandler`.
* response VO exposes only `tokenMask`, never raw token.

#### `dev_pipeline_run`

One execution instance.

Fields:

* `id`
* `definition_id`
* `definition_version_id`
* `application_env_id`
* `change_env_id`
* `jenkins_provider_id`
* `run_status`: PENDING, RUNNING, WAITING_APPROVAL, SUCCESS, FAILED, CANCELED
* `trigger_type`: MANUAL, RELEASE, RETRY
* `triggered_by`
* `source_branch`
* `source_commit_sha`
* `release_branch`
* `release_commit_sha`
* `jenkins_job_path`
* `jenkins_queue_url`
* `jenkins_build_number`
* `jenkins_build_url`
* `started_at`
* `finished_at`
* `error_message`
* standard audit, deleted, tenant fields

Indexes:

* index `tenant_id, change_env_id`
* index `tenant_id, application_env_id, create_time`
* index `tenant_id, run_status`
* index `tenant_id, jenkins_provider_id, jenkins_build_number`

#### `dev_pipeline_stage_run`

One node execution result.

Fields:

* `id`
* `run_id`
* `node_id`
* `node_type`
* `node_name`
* `stage_name`
* `stage_order`
* `stage_status`: PENDING, RUNNING, WAITING_APPROVAL, SUCCESS, FAILED, SKIPPED, CANCELED
* `jenkins_stage_id`
* `started_at`
* `finished_at`
* `duration_millis`
* `log_url`
* `error_message`
* `input_json`
* `output_json`
* standard audit, deleted, tenant fields

Indexes:

* unique `tenant_id, run_id, node_id`
* index `tenant_id, run_id, stage_order`
* index `tenant_id, stage_status`

#### `dev_pipeline_artifact`

Build/test/deployable artifact.

Fields:

* `id`
* `run_id`
* `stage_run_id`
* `artifact_type`: JAR, IMAGE, CHART, TEST_REPORT, OTHER
* `name`
* `version`
* `url`
* `image_tag`
* `image_digest`
* `checksum`
* `metadata_json`
* standard audit, deleted, tenant fields

Indexes:

* index `tenant_id, run_id`
* index `tenant_id, stage_run_id`
* index `tenant_id, artifact_type`

## Status enums and dicts

Add dict types:

* `dev_pipeline_definition_version_status`
* `dev_pipeline_run_status`
* `dev_pipeline_stage_run_status`
* `dev_pipeline_artifact_type`
* `dev_pipeline_trigger_type`

Existing `dev_pipeline_stage_status` can be reused for run summaries on `dev_change_env`, but detailed tables should use clearer status names if product needs WAITING_APPROVAL/SKIPPED.

## DSL design

### `diagram_json`

Only for UI rendering.

```json
{
  "viewport": { "x": 0, "y": 0, "zoom": 1 },
  "nodes": [
    {
      "id": "unit_test",
      "type": "UNIT_TEST",
      "position": { "x": 260, "y": 120 },
      "width": 180,
      "height": 64
    }
  ],
  "edges": [
    {
      "id": "edge-checkout-unit-test",
      "source": "checkout",
      "target": "unit_test"
    }
  ]
}
```

### `spec_json`

Backend-executable canonical DSL.

```json
{
  "dslVersion": "1.0",
  "executionMode": "SEQUENTIAL",
  "nodes": [
    {
      "id": "checkout",
      "type": "CHECKOUT",
      "name": "拉取代码",
      "enabled": true,
      "params": {
        "repoUrl": "${application.repoUrl}",
        "branchName": "${run.sourceBranch}",
        "commitSha": "${run.sourceCommitSha}"
      },
      "timeoutSeconds": 600,
      "retryTimes": 0,
      "failStrategy": "FAIL_PIPELINE"
    },
    {
      "id": "unit_test",
      "type": "UNIT_TEST",
      "name": "单元测试",
      "enabled": true,
      "params": {
        "command": "mvn test",
        "reportPattern": "**/surefire-reports/*.xml"
      },
      "timeoutSeconds": 1800,
      "retryTimes": 0,
      "failStrategy": "FAIL_PIPELINE"
    },
    {
      "id": "build_artifact",
      "type": "BUILD_ARTIFACT",
      "name": "构建制品",
      "enabled": true,
      "params": {
        "command": "mvn -DskipTests package",
        "artifactPattern": "**/target/*.jar"
      },
      "timeoutSeconds": 1800,
      "retryTimes": 0,
      "failStrategy": "FAIL_PIPELINE"
    },
    {
      "id": "build_image",
      "type": "BUILD_IMAGE",
      "name": "构建镜像",
      "enabled": true,
      "params": {
        "dockerfile": "Dockerfile",
        "context": ".",
        "imageName": "${application.appKey}",
        "imageTag": "${run.sourceCommitSha}"
      },
      "timeoutSeconds": 1800,
      "retryTimes": 0,
      "failStrategy": "FAIL_PIPELINE"
    },
    {
      "id": "report_artifacts",
      "type": "REPORT_ARTIFACTS",
      "name": "上报产物",
      "enabled": true,
      "params": {},
      "timeoutSeconds": 300,
      "retryTimes": 1,
      "failStrategy": "FAIL_PIPELINE"
    }
  ],
  "edges": [
    { "source": "checkout", "target": "unit_test" },
    { "source": "unit_test", "target": "build_artifact" },
    { "source": "build_artifact", "target": "build_image" },
    { "source": "build_image", "target": "report_artifacts" }
  ]
}
```

## Node registry

Backend owns the node registry. Frontend obtains it from API and renders property forms.

MVP node types:

| Type | Runs in | Purpose |
|---|---|---|
| `CHECKOUT` | Jenkins | checkout repo branch/commit |
| `UNIT_TEST` | Jenkins | run test command and publish junit report |
| `BUILD_ARTIFACT` | Jenkins | build jar/war/zip artifacts |
| `BUILD_IMAGE` | Jenkins | build and push image |
| `REPORT_ARTIFACTS` | Jenkins | report artifacts back to platform |
| `APPROVAL` | Platform | disabled placeholder in Phase 1; wait platform approval in later phase |
| `DEPLOY_K8S` | Platform | disabled placeholder in Phase 1; deploy selected artifact in later phase |

Node registry API response:

```json
{
  "types": [
    {
      "type": "UNIT_TEST",
      "name": "单元测试",
      "category": "JENKINS",
      "icon": "test-tube",
      "defaultName": "单元测试",
      "defaultParams": {
        "command": "mvn test",
        "reportPattern": "**/surefire-reports/*.xml"
      },
      "paramSchema": {
        "type": "object",
        "required": ["command"],
        "properties": {
          "command": {
            "type": "string",
            "title": "测试命令"
          },
          "reportPattern": {
            "type": "string",
            "title": "测试报告路径"
          }
        }
      }
    }
  ]
}
```

Validation rules:

* Node id unique.
* Edge source/target must exist.
* No cycles in MVP.
* Exactly one start node and one terminal node for MVP.
* `REPORT_ARTIFACTS` must be terminal for Jenkins-only pipeline.
* `CHECKOUT` should be first for Jenkins build pipeline.
* Required params must be present.
* Commands must not contain disallowed tokens if using command whitelist mode.
* Node ids must match `[a-zA-Z][a-zA-Z0-9_-]{0,63}`.
* Build/test commands must be selected from backend-provided templates; Phase 1 does not allow arbitrary raw command input.
* Disabled placeholder node types, such as `APPROVAL` and `DEPLOY_K8S`, can be displayed in the palette but cannot be added to a published executable pipeline until their backend executors are implemented.

## Command templates

Build/test command parameters are template-based, not free-form.

Rationale:

* Avoid arbitrary shell command injection through visual pipeline configuration.
* Keep generated Jenkinsfile predictable and auditable.
* Make node property panels easier to validate.
* Allow per-application defaults later.

Phase 1 command template model:

* `templateKey`
* `templateName`
* `nodeType`
* `command`
* `artifactPattern`
* `reportPattern`
* `description`
* `enabled`

Initial built-in templates:

| Template key | Node type | Command |
|---|---|---|
| `maven_test` | `UNIT_TEST` | `mvn test` |
| `maven_package_skip_tests` | `BUILD_ARTIFACT` | `mvn -DskipTests package` |
| `npm_test` | `UNIT_TEST` | `npm run test` |
| `npm_build` | `BUILD_ARTIFACT` | `npm run build` |
| `docker_build` | `BUILD_IMAGE` | template-backed Docker build command |

DSL stores the selected template and safe parameters:

```json
{
  "id": "unit_test",
  "type": "UNIT_TEST",
  "params": {
    "commandTemplateKey": "maven_test",
    "reportPattern": "**/surefire-reports/*.xml"
  }
}
```

The backend expands `commandTemplateKey` during Jenkinsfile generation. The frontend must not submit arbitrary `command` text in Phase 1.

## Jenkinsfile generation

Generation happens only on backend.

Input:

* `spec_json`
* application repo metadata
* Jenkins provider defaults
* generator version

Output:

* `jenkinsfile_text`
* `jenkinsfile_checksum`
* validation result

Template approach:

* One generator service coordinates.
* Each node type contributes a Jenkins declarative stage snippet.
* Generated stage name is `${nodeId}__${nodeType}` for stable mapping.
* Use Shared Library calls where possible.

Example generated Jenkinsfile:

```groovy
@Library('gone-devops-shared') _

pipeline {
  agent any
  options {
    timestamps()
    disableConcurrentBuilds()
  }
  parameters {
    string(name: 'PIPELINE_RUN_ID')
    string(name: 'PIPELINE_VERSION_ID')
    string(name: 'REPO_URL')
    string(name: 'BRANCH_NAME')
    string(name: 'COMMIT_SHA', defaultValue: '')
    string(name: 'APP_KEY')
  }
  stages {
    stage('checkout__CHECKOUT') {
      steps {
        goneDevopsCheckout(
          repoUrl: params.REPO_URL,
          branchName: params.BRANCH_NAME,
          commitSha: params.COMMIT_SHA
        )
      }
    }
    stage('unit_test__UNIT_TEST') {
      steps {
        goneDevopsUnitTest(command: 'mvn test')
      }
      post {
        always {
          junit allowEmptyResults: true, testResults: '**/surefire-reports/*.xml'
        }
      }
    }
    stage('build_artifact__BUILD_ARTIFACT') {
      steps {
        goneDevopsBuildArtifact(command: 'mvn -DskipTests package')
        archiveArtifacts artifacts: '**/target/*.jar', fingerprint: true
      }
    }
    stage('build_image__BUILD_IMAGE') {
      steps {
        script {
          env.IMAGE_TAG = goneDevopsBuildImage(
            imageName: params.APP_KEY,
            imageTag: params.COMMIT_SHA ?: env.BUILD_NUMBER
          )
        }
      }
    }
    stage('report_artifacts__REPORT_ARTIFACTS') {
      steps {
        goneDevopsReportArtifacts(
          pipelineRunId: params.PIPELINE_RUN_ID,
          imageTag: env.IMAGE_TAG
        )
      }
    }
  }
}
```

Storage:

* DB stores generated Jenkinsfile for audit and preview.
* Jenkins handoff for MVP can be one of:
  * fixed bootstrap job reading plan by `PIPELINE_RUN_ID`;
  * or generated Jenkinsfile written to pipeline Git repository.

Recommended for first implementation if only visual orchestration is in scope:

* Store Jenkinsfile in DB and expose preview.
* Defer Jenkins handoff mechanics until execution implementation starts.
* Decision confirmed: Phase 1 only stores generated Jenkinsfile in platform DB and exposes preview. Jenkins execution handoff will be decided in Phase 2.

## Backend module structure

Packages:

```text
cn.iocoder.yudao.module.devops.controller.admin.pipeline
cn.iocoder.yudao.module.devops.controller.admin.pipeline.vo
cn.iocoder.yudao.module.devops.convert.pipeline
cn.iocoder.yudao.module.devops.dal.dataobject.pipeline
cn.iocoder.yudao.module.devops.dal.mysql.pipeline
cn.iocoder.yudao.module.devops.service.pipeline
cn.iocoder.yudao.module.devops.framework.jenkins
cn.iocoder.yudao.module.devops.framework.pipeline
```

Core services:

* `PipelineDefinitionService`
* `PipelineNodeRegistryService`
* `PipelineSpecValidationService`
* `JenkinsfileGeneratorService`
* `PipelineRunService` later
* `JenkinsProviderService` later

Core framework interfaces:

```java
public interface PipelineNodeDescriptor {
    String getType();
    String getName();
    Map<String, Object> getDefaultParams();
    Map<String, Object> getParamSchema();
    void validate(PipelineNodeSpec node);
    String generateJenkinsStage(PipelineNodeSpec node);
}
```

```java
public interface JenkinsfileGenerator {
    JenkinsfileGenerateResult generate(PipelineSpec spec, JenkinsfileGenerateContext context);
}
```

## Backend API design

### Node registry

`GET /devops/pipeline/node-types`

Permission:

* `devops:pipeline:query`

Response:

* node descriptors and param schemas.

### Get application environment pipeline

`GET /devops/pipeline/get-by-application-env?applicationEnvId=1`

Returns:

* definition metadata
* draft version if exists
* published version if exists
* current binding state

### Save draft

`POST /devops/pipeline/save-draft`

Request:

```json
{
  "applicationEnvId": 1,
  "name": "测试环境流水线",
  "diagramJson": "{}",
  "specJson": "{}",
  "remark": "optional"
}
```

Behavior:

* Validate application env exists.
* Create definition if missing.
* Create or update draft version.
* Generate Jenkinsfile preview.
* Save validation result.
* Do not affect published version.

### Validate draft

`POST /devops/pipeline/validate`

Request:

* same as save draft without persistence, or by version id.

Response:

```json
{
  "valid": true,
  "errors": [],
  "warnings": [],
  "jenkinsfileText": "pipeline {...}",
  "jenkinsfileChecksum": "sha256..."
}
```

### Publish

`POST /devops/pipeline/publish`

Request:

```json
{
  "definitionId": 1,
  "draftVersionId": 10,
  "versionName": "v1"
}
```

Behavior:

* Validate draft is valid.
* Mark a new immutable published version.
* Update definition `published_version_id`.
* Update `dev_application_env.pipeline_definition_id`.
* Archive old published version if needed, or keep it as published history with only latest pointer.

### Get Jenkinsfile preview

`GET /devops/pipeline/version/jenkinsfile?id=10`

Returns generated Jenkinsfile text.

### Version history

`GET /devops/pipeline/version/list?definitionId=1`

Returns versions and publish metadata.

## Frontend design

Page route:

* `devops/pipeline/designer`
* opened from application detail environment row.

Layout:

```text
Header: app/env breadcrumb, draft status, buttons
Left: node palette
Center: Vue Flow canvas
Right: selected node property panel
Bottom drawer: validation result / Jenkinsfile preview
```

Controls:

* save draft
* validate
* preview Jenkinsfile
* publish
* reset to published
* version history

Phase 1 node availability:

* Enabled: Checkout, Unit Test, Build Artifact, Build Image, Report Artifacts.
* Disabled but visible: Approval, Deploy K8S.
* Disabled nodes should show a tooltip such as “后续阶段开放：平台审批” or “后续阶段开放：平台部署”.

Node palette:

* Source: Checkout
* Quality: Unit Test
* Build: Build Artifact, Build Image
* Platform: Report Artifacts, Approval, Deploy K8S

Interaction rules:

* Drag node from palette to canvas.
* Connect source handle to target handle.
* Selecting node opens properties.
* Delete node also deletes connected edges.
* Publishing requires valid backend validation.
* Frontend does lightweight validation only; backend validation is authoritative.

Jenkinsfile preview:

* read-only code editor with syntax highlighting.
* show checksum and generator version.
* show warnings inline.

## Security

* Never execute frontend-submitted Jenkinsfile text.
* Frontend submits DSL only.
* Backend generated Jenkinsfile is audit artifact.
* Jenkins provider credentials encrypted.
* Response VOs must not expose raw Jenkins token.
* Build commands are high-risk; Phase 1 only supports backend-defined command templates.
* All pipeline APIs tenant scoped.
* Publish requires `devops:pipeline:update`.
* Trigger requires `devops:pipeline:trigger` later.

## Error codes

Add error constants:

* `PIPELINE_DEFINITION_NOT_EXISTS`
* `PIPELINE_APPLICATION_ENV_NOT_EXISTS`
* `PIPELINE_DRAFT_NOT_EXISTS`
* `PIPELINE_VERSION_NOT_EXISTS`
* `PIPELINE_SPEC_INVALID`
* `PIPELINE_NODE_TYPE_NOT_SUPPORTED`
* `PIPELINE_NODE_PARAM_INVALID`
* `PIPELINE_GRAPH_HAS_CYCLE`
* `PIPELINE_GRAPH_START_NODE_INVALID`
* `PIPELINE_GRAPH_TERMINAL_NODE_INVALID`
* `PIPELINE_JENKINSFILE_GENERATE_FAIL`
* `JENKINS_PROVIDER_NOT_EXISTS`
* `JENKINS_PROVIDER_CONNECTION_FAIL`

## Implementation phases

### Phase 1: Designer foundation

Backend:

* SQL tables for definition/version.
* DO/Mapper/Service/Controller for draft/publish/version.
* Node registry with static descriptors.
* DSL validation.
* Jenkinsfile generator preview.
* Update `ApplicationEnvDO.pipelineDefinitionId` binding on publish.

Frontend:

* Vue Flow designer page.
* Node palette.
* Property panel.
* Save/validate/publish.
* Jenkinsfile preview.

Verification:

* Service tests for save draft, validation failure, publish, version immutability.
* Generator tests for stable Jenkinsfile output.

### Phase 2: Jenkins execution

Backend:

* Jenkins provider table/service.
* Run/stage/artifact tables.
* Trigger Jenkins job.
* Poll Jenkins status.
* Sync build/test statuses to `dev_change_env`.

Frontend:

* Run history.
* Stage timeline.
* Logs.
* Artifact list.

### Phase 3: Platform approval/deploy

Backend:

* Approval task model.
* Deploy K8S executor.
* Artifact-based deployment.

Frontend:

* Approval panel.
* Deployment detail.
* Rollback entry.

### Phase 4: Release merge

Backend:

* Release merge session.
* Git workspace.
* Conflict extraction.

Frontend:

* Monaco conflict editor.

## Acceptance criteria for Phase 1

* A user can configure a pipeline for one application environment.
* Draft save does not affect published version.
* Publish creates immutable version and binds application env.
* Backend rejects invalid graph or unsupported node type.
* Backend generates deterministic Jenkinsfile from DSL.
* Frontend can preview generated Jenkinsfile.
* Existing application/change/environment behavior remains unchanged.

## Open decisions

1. Jenkinsfile handoff in Phase 2: pipeline Git repo vs bootstrap job.
2. Whether one application environment can have only one pipeline definition, or support multiple named definitions with one active binding.

## Confirmed decisions

* Phase 1 Jenkinsfile handling: store generated Jenkinsfile in platform DB and expose preview only.
* Command editing: users select from backend-provided command templates; no arbitrary raw command input in Phase 1.
* Future nodes: `APPROVAL` and `DEPLOY_K8S` are visible but disabled in Phase 1.
