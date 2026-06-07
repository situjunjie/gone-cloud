# Frontend API contract: DevOps visual pipeline designer

## Scope

Phase 1 only supports visual pipeline design, validation, publish, and Jenkinsfile preview.

No Jenkins execution, deployment, approval execution, or release-branch merge conflict resolution in Phase 1.

## Permissions

Use these permissions:

* `devops:pipeline:query`
* `devops:pipeline:create`
* `devops:pipeline:update`
* `devops:pipeline:delete`
* `devops:pipeline:publish`

## Common response wrapper

Backend follows Yudao `CommonResult<T>`:

```ts
interface CommonResult<T> {
  code: number
  data: T
  msg: string
}
```

## Data types

```ts
type PipelineNodeType =
  | 'CHECKOUT'
  | 'UNIT_TEST'
  | 'BUILD_ARTIFACT'
  | 'BUILD_IMAGE'
  | 'REPORT_ARTIFACTS'
  | 'APPROVAL'
  | 'DEPLOY_K8S'

type PipelineVersionStatus = 0 | 1 | 2 // 0 Draft, 1 Published, 2 Archived

interface PipelineNodeTypeRespVO {
  type: PipelineNodeType
  name: string
  category: 'JENKINS' | 'PLATFORM'
  icon: string
  enabled: boolean
  disabledReason?: string
  defaultName: string
  defaultParams: Record<string, unknown>
  paramSchema: Record<string, unknown>
}

interface PipelineCommandTemplateRespVO {
  templateKey: string
  templateName: string
  nodeType: PipelineNodeType
  command: string
  artifactPattern?: string
  reportPattern?: string
  description?: string
  enabled: boolean
}

interface PipelineValidationMessageRespVO {
  field?: string
  nodeId?: string
  code: string
  message: string
}

interface PipelineValidationRespVO {
  valid: boolean
  errors: PipelineValidationMessageRespVO[]
  warnings: PipelineValidationMessageRespVO[]
  jenkinsfileText?: string
  jenkinsfileChecksum?: string
}

interface PipelineDefinitionRespVO {
  id: number
  name: string
  definitionKey: string
  appId: number
  applicationEnvId: number
  status: number
  draftVersionId?: number
  publishedVersionId?: number
  remark?: string
  draftVersion?: PipelineDefinitionVersionRespVO
  publishedVersion?: PipelineDefinitionVersionRespVO
}

interface PipelineDefinitionVersionRespVO {
  id: number
  definitionId: number
  versionNo: number
  versionName?: string
  versionStatus: PipelineVersionStatus
  diagramJson: string
  specJson: string
  nodeSchemaVersion: string
  jenkinsfileText?: string
  jenkinsfileChecksum?: string
  validationResultJson?: string
  publishedAt?: string
  publishedBy?: number
  createTime?: string
}
```

## API endpoints

### Get node types

```http
GET /admin-api/devops/pipeline/node-types
```

Response:

```ts
CommonResult<PipelineNodeTypeRespVO[]>
```

Notes:

* `APPROVAL` and `DEPLOY_K8S` are visible but disabled in Phase 1.
* Disabled nodes should appear in the palette with tooltip, but cannot be dropped onto an executable canvas.

### Get command templates

```http
GET /admin-api/devops/pipeline/command-templates
```

Response:

```ts
CommonResult<PipelineCommandTemplateRespVO[]>
```

Notes:

* Build/test commands are selected from templates only.
* The UI must not send arbitrary raw command text.

### Get pipeline by application environment

```http
GET /admin-api/devops/pipeline/get-by-application-env?applicationEnvId=123
```

Response:

```ts
CommonResult<PipelineDefinitionRespVO | null>
```

### Save draft

```http
POST /admin-api/devops/pipeline/save-draft
Content-Type: application/json
```

Request:

```ts
interface PipelineSaveDraftReqVO {
  applicationEnvId: number
  name: string
  diagramJson: string
  specJson: string
  remark?: string
}
```

Response:

```ts
CommonResult<number> // draft version id
```

Behavior:

* Creates definition if missing.
* Creates or updates draft version.
* Generates Jenkinsfile preview and validation result.
* Does not affect published version.

### Validate

```http
POST /admin-api/devops/pipeline/validate
Content-Type: application/json
```

Request:

```ts
interface PipelineValidateReqVO {
  applicationEnvId?: number
  diagramJson: string
  specJson: string
}
```

Response:

```ts
CommonResult<PipelineValidationRespVO>
```

### Publish draft

```http
POST /admin-api/devops/pipeline/publish
Content-Type: application/json
```

Request:

```ts
interface PipelinePublishReqVO {
  definitionId: number
  draftVersionId: number
  versionName?: string
}
```

Response:

```ts
CommonResult<number> // published version id
```

Behavior:

* Draft must pass backend validation.
* Published version is immutable.
* Updates `dev_application_env.pipeline_definition_id`.

### Get Jenkinsfile preview

```http
GET /admin-api/devops/pipeline/version/jenkinsfile?id=456
```

Response:

```ts
CommonResult<string>
```

### List versions

```http
GET /admin-api/devops/pipeline/version/list?definitionId=123
```

Response:

```ts
CommonResult<PipelineDefinitionVersionRespVO[]>
```

## DSL contract

`diagramJson` is only for Vue Flow rendering.

`specJson` is the backend-authoritative pipeline DSL.

Example:

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
      "params": {},
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
        "commandTemplateKey": "maven_test",
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
        "commandTemplateKey": "maven_package_skip_tests",
        "artifactPattern": "**/target/*.jar"
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
    { "source": "build_artifact", "target": "report_artifacts" }
  ]
}
```

Validation rules:

* Exactly one start node.
* Exactly one terminal node.
* No cycles in Phase 1.
* `CHECKOUT` should be first.
* `REPORT_ARTIFACTS` should be terminal for Jenkins pipeline.
* `APPROVAL` and `DEPLOY_K8S` cannot be published in Phase 1.
* Build/test nodes must use `commandTemplateKey`.
