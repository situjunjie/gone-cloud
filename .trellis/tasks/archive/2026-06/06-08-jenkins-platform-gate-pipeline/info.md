# Technical Design: Jenkins Platform Gate Pipeline

## Architecture Decision

采用“一条平台流水线发布版本对应一份完整 Jenkinsfile”的方案。Jenkins 持有中间流水线 build 的执行上下文，平台持有业务状态和最终状态源。

前台对用户屏蔽 Jenkins 概念。用户只操作平台流水线 run，Jenkins 触发属于平台后端内部编排动作，不提供单独的用户触发入口。

核心模型：

```text
平台固定 CODE_MERGE
  -> Jenkins 完整中间流水线
     -> 每个 Jenkins stage 对应一个平台 DSL node
     -> node 生命周期回调隐式包裹在 stage step 前后
     -> node handler 在 STARTED / COMPLETED / FAILED 接入平台逻辑
  -> 平台固定 DEPLOY
```

Jenkinsfile 不需要理解 `APPROVAL`、`RELEASE_WINDOW` 等平台节点的具体业务。它只负责把每个可视化编排块生成为一个 stage，并在 stage 的实际 step 前后注入统一生命周期 callback。

## Core Execution Model

### Stage = Node

平台可视化编排里的每个具体块就是一个 `PipelineSpec.Node`。发布后，这个 node 在 Jenkinsfile 中生成一个对应 stage，在运行态生成一个对应 node log。

```text
visual block
  -> PipelineSpec.Node
  -> Jenkins stage
  -> runtime node log
```

因此平台运行态不再额外维护一套独立的 stage 模型。Jenkins stage 的开始、完成、失败，就是平台 node 的开始、完成、失败。

### Implicit Lifecycle Callback

Jenkinsfile 生成器必须给每个 node 的真实 step 自动包裹生命周期 callback：

```text
before step  -> callback STARTED
real step    -> checkout/test/build/approval wait/custom action
after step   -> callback COMPLETED
catch error  -> callback FAILED
```

这些 callback 是隐式平台能力，不需要用户在可视化编排里手动配置。

### Node Handler Extension Point

平台每一步接入逻辑都由 node handler 实现：

```text
STARTED   -> nodeHandler.onStarted(context)
COMPLETED -> nodeHandler.onCompleted(context)
FAILED    -> nodeHandler.onFailed(context)
```

不同节点类型只需要实现自己的 handler：

```text
UNIT_TEST -> 更新测试节点状态 / 读取测试结果
BUILD_IMAGE -> 记录镜像元数据
APPROVAL -> 创建审批等待 / 完成审批节点 / 处理拒绝
QUALITY_GATE -> 读取平台质量门禁结果
```

callback controller 只负责认证、幂等、定位 node、分发 handler，不写具体节点业务。

## Current Code Fit

### 已可复用

- `PipelineSpec`：已支持节点、边、timeout、retry、failStrategy。
- `PipelineDefinitionServiceImpl`：已支持 draft/published version 和 Jenkinsfile 文本持久化。
- `JenkinsfileGeneratorServiceImpl`：已按发布 DSL 生成完整 Jenkinsfile。
- `PipelineNodeRegistryServiceImpl`：已具备节点 category，`APPROVAL` 已登记为 `PLATFORM` 类型。
- `dev_pipeline_run_log`：已是通用 `NODE` / `STEP` / `EVENT` 结构，可记录 DSL 节点。

### 必须调整

- `PipelineExecutionServiceImpl#finishCodeMerge`：代码合并完成后不能直接 run success，需要触发 Jenkins。
- `PipelineNodeTypeEnum`：不能只有 `CODE_MERGE`，需要支持 DSL 节点类型或允许字符串类型透传。
- `PipelineSpecValidationServiceImpl`：需要开放 `APPROVAL` 平台节点，并校验平台节点参数。
- `JenkinsfileGeneratorServiceImpl`：需要为 `PLATFORM` category 节点生成通用 gate stage。
- 新增 Jenkins client、Jenkins callback controller、platform gate service。

## Runtime Sequence

### 1. Start Run

```text
Application release submit
  -> create dev_pipeline_run
  -> create CODE_MERGE log
  -> execute code merge
```

### 2. Finish Code Merge

```text
push deploy branch
persist deployBranch + deployCommitSha in CODE_MERGE result_json
create DSL node logs as PENDING
trigger Jenkins build with published version Jenkinsfile/job by backend internal service
persist Jenkins build metadata
keep run RUNNING
cleanup git workspace after Jenkins trigger succeeds
```

### 3. Jenkins Internal Start

代码合并成功后，平台后端内部调用 Jenkins API 开启 Jenkins 流水线。

这是后端 service/client 能力，不是 controller，不暴露给前端：

```text
JenkinsPipelineClient.startPipeline(runId, pipelineVersionId, jenkinsfile, parameters)
```

平台记录：

```json
{
  "jobName": "gone-devops-app",
  "buildNumber": 58,
  "buildUrl": "http://jenkins/job/gone-devops-app/58/",
  "pipelineVersionId": 123
}
```

### 4. Jenkins Step Callback

Jenkins 每个 step/stage 的开始、完成、失败都调用统一 callback：

```http
POST /admin-api/devops/pipeline-runs/{runId}/jenkins/callback
```

调用方：Jenkins。该接口用于 Jenkins 把 step 执行事件回调给平台，不是前端触发 Jenkins 构建的接口。

每个 Jenkins stage 对应一个平台 node。Jenkinsfile 生成器会在该 stage 的真实 step 前后隐式注入 callback。平台根据 `action` 和 `nodeType` 分发到内部节点处理器：

```text
STARTED -> handler.onStarted(context)
COMPLETED -> handler.onCompleted(context)
FAILED -> handler.onFailed(context)
```

平台将对应 `dev_pipeline_run_log` 从 `PENDING` -> `RUNNING` -> `SUCCESS/FAILED/WAITING_INPUT`。

平台节点也是 stage/node 的一种。Jenkins 执行到平台节点时，发送 `STARTED`，对应 handler 将节点日志置为 `WAITING_INPUT` 并接入平台业务；平台节点完成后发送 `COMPLETED`；平台节点或 Jenkins step 异常时发送 `FAILED`。

统一 callback 请求体示例：

```json
{
  "action": "STARTED",
  "eventId": "uuid",
  "pipelineVersionId": 123,
  "nodeId": "approval-prod",
  "nodeType": "APPROVAL",
  "nodeName": "生产审批",
  "jenkinsJobName": "gone-devops-app",
  "jenkinsBuildNumber": 58,
  "jenkinsBuildUrl": "http://jenkins/job/gone-devops-app/58/",
  "inputId": "gate-10001-approval-prod",
  "commitSha": "abc123",
  "timestamp": "2026-06-08T10:00:00+08:00",
  "message": "optional summary"
}
```

### 5. Artifact Report

产物上报也走统一 callback，`nodeType=REPORT_ARTIFACTS` 或 `action=COMPLETED` 时携带 `artifacts`：

```http
POST /admin-api/devops/pipeline-runs/{runId}/jenkins/callback
```

调用方：Jenkins。该接口用于 Jenkins 构建完成阶段向平台上报制品/镜像元数据。

请求体：

```json
{
  "artifacts": [
    {
      "artifactType": "DOCKER_IMAGE",
      "artifactName": "gone-cloud/devops-server",
      "imageRepository": "gone-cloud/devops-server",
      "imageTag": "abc123",
      "commitSha": "abc123"
    }
  ],
  "jenkinsBuildUrl": "http://jenkins/job/gone-devops-app/58/"
}
```

### 6. Jenkins Internal Stop

用户取消平台流水线或部署流程需要停止时，如果 Jenkins build 正在运行，平台后端内部调用 Jenkins API 停止构建。

这是后端 service/client 能力，不是 controller，不暴露给前端：

```text
JenkinsPipelineClient.stopPipeline(jobName, buildNumber)
```

Jenkins 停止后仍可通过统一 callback 上报 `FAILED` 或平台主动同步 build 状态。

### 7. Jenkins Build Completed

Jenkins build 结束也走统一 callback，推荐使用 `nodeId=builtin.jenkins_build` 或 `nodeId` 为空、`nodeType=JENKINS_BUILD`、`action=COMPLETED/FAILED`。

平台动作：

```text
if result == SUCCESS:
  execute fixed deploy with artifact outputs
else:
  mark run FAILED/CANCELED
```

## Jenkinsfile Generation Contract

### Stage Wrapping Rule

Jenkinsfile 生成器对每个 `PipelineSpec.Node` 生成一个 stage，并统一使用生命周期包裹结构：

```groovy
stage('<nodeId>__<nodeType>') {
  steps {
    script {
      goneDevopsCallback(nodeId: '<nodeId>', nodeType: '<nodeType>', action: 'STARTED')
      try {
        // node 对应的真实 Jenkins step
        goneDevopsCallback(nodeId: '<nodeId>', nodeType: '<nodeType>', action: 'COMPLETED')
      } catch (err) {
        goneDevopsCallback(nodeId: '<nodeId>', nodeType: '<nodeType>', action: 'FAILED', message: err.getMessage())
        throw err
      }
    }
  }
}
```

这条规则是隐式生成规则，不暴露给前端配置。前端只配置 node，本次 node 的生命周期回调由 Jenkinsfile generator 自动加上。

### Parameters

```groovy
parameters {
  string(name: 'PIPELINE_RUN_ID')
  string(name: 'PIPELINE_VERSION_ID')
  string(name: 'REPO_URL')
  string(name: 'BRANCH_NAME')
  string(name: 'COMMIT_SHA', defaultValue: '')
  string(name: 'APP_KEY')
  password(name: 'CALLBACK_TOKEN')
}
```

### Jenkins Node Stage

```groovy
stage('unit_test__UNIT_TEST') {
  steps {
    script {
      goneDevopsCallback(runId: params.PIPELINE_RUN_ID, nodeId: 'unit_test', nodeType: 'UNIT_TEST', action: 'STARTED')
      try {
        goneDevopsUnitTest(command: 'mvn test')
        goneDevopsCallback(runId: params.PIPELINE_RUN_ID, nodeId: 'unit_test', nodeType: 'UNIT_TEST', action: 'COMPLETED')
      } catch (err) {
        goneDevopsCallback(runId: params.PIPELINE_RUN_ID, nodeId: 'unit_test', nodeType: 'UNIT_TEST', action: 'FAILED', message: err.getMessage())
        throw err
      }
    }
  }
}
```

### Platform Gate Stage

```groovy
stage('approval-prod__APPROVAL') {
  steps {
    script {
      def gateInputId = "gate-${params.PIPELINE_RUN_ID}-approval-prod"
      goneDevopsCallback(
        runId: params.PIPELINE_RUN_ID,
        pipelineVersionId: params.PIPELINE_VERSION_ID,
        nodeId: 'approval-prod',
        nodeType: 'APPROVAL',
        action: 'STARTED',
        nodeName: '生产审批',
        inputId: gateInputId,
        callbackToken: params.CALLBACK_TOKEN
      )
      timeout(time: 24, unit: 'HOURS') {
        input id: gateInputId, message: '等待平台节点完成', ok: '继续'
      }
      goneDevopsCallback(
        runId: params.PIPELINE_RUN_ID,
        nodeId: 'approval-prod',
        nodeType: 'APPROVAL',
        action: 'COMPLETED',
        callbackToken: params.CALLBACK_TOKEN
      )
    }
  }
}
```

## Jenkins Callback Contract Design

这里的设计基准不是“理想化后端接口”，而是 Jenkins Pipeline 里最容易稳定发出的请求形态。当前默认按 Jenkins `httpRequest` step 和 `input` step 能力设计。

参考：

- Jenkins HTTP Request step: https://www.jenkins.io/doc/pipeline/steps/http_request/
- Jenkins HTTP Request plugin: https://plugins.jenkins.io/http_request
- Jenkins Input step: https://www.jenkins.io/doc/pipeline/steps/pipeline-input-step/

### Why

- Jenkins `httpRequest` 适合发简单 HTTP 请求，支持 `POST`、header、自定义 body、超时和响应码判断。
- Jenkins `httpRequest` 在请求发出后、响应回来前如果 Jenkins 重启，请求会失败，因此平台回调接口必须快速返回，不能做长事务阻塞。
- HTTP Request plugin 文档明确提示执行时会记录参数，因此敏感信息不能放在 query string，也不要裸放在 body 中用于日志排查；更适合放在 masked header。
- Jenkins `input` step 支持自定义 `id`，而这个 `id` 可以用于外部系统机械化地继续或中止 input，因此平台 gate 需要稳定 `inputId`。

### Request Shape Requirements

- 只用 `POST`，不依赖 `PUT/PATCH/DELETE`。
- `Content-Type` 固定 `application/json`。
- 不要求 `multipart/form-data`、不要求复杂嵌套表单。
- 不依赖 cookie / session / csrf 浏览器语义。
- 不依赖重定向；Jenkins 端应直接命中最终 URL。
- 回调 body 保持扁平、字段稳定，避免大量可选层级。
- 所有请求都允许带 `eventId` 或 `requestId` 做幂等。

### Authentication Requirements

- Jenkins 使用 header 传递 callback token，例如：

```http
X-Devops-Callback-Token: ***
X-Devops-Event-Id: 8b0e5d1c-...
```

- 不使用 query string 传 token。
- Jenkins Pipeline 中 header 需要使用 masked header，避免日志泄露。
- 平台只保存 token hash，不保存明文。

### Response Requirements

- 平台必须在短时间内返回，建议 1~2 秒内完成响应。
- 响应 body 尽量固定简单：

```json
{
  "code": 0,
  "message": "OK",
  "accepted": true,
  "duplicate": false
}
```

- 不返回大对象，不返回 HTML，不返回需要 Jenkins 解析的复杂内容。
- 重复回调、已处理回调、顺序稍乱但可忽略的回调，优先返回 200 + `duplicate/accepted` 标记，不要轻易返回 409。
- 只有这些情况再返回非 2xx：
  - 鉴权失败：401/403
  - payload 严重缺失：400
  - run 或节点根本不存在：404
  - 平台内部异常：500

### Retry / Idempotency Requirements

- Jenkins 网络抖动或平台短暂失败时，请求允许重试。
- 平台必须按 `eventId` 或 `pipelineRunId + nodeId + action + buildNumber` 幂等去重。
- Jenkins 回调接口不能把“重复 started / completed / resumed”视为错误中断点。
- 对于已完成节点的重复成功回调，应返回 200 并标记 `duplicate=true`。

### Callback Payload Recommendation

建议所有 Jenkins step 回调共享一套基础字段：

```json
{
  "action": "STARTED",
  "eventId": "uuid",
  "pipelineVersionId": 123,
  "nodeId": "unit_test",
  "nodeType": "UNIT_TEST",
  "nodeName": "单元测试",
  "jenkinsJobName": "gone-devops-app",
  "jenkinsBuildNumber": 58,
  "jenkinsBuildUrl": "http://jenkins/job/gone-devops-app/58/",
  "timestamp": "2026-06-08T10:00:00+08:00",
  "message": "optional summary"
}
```

其中：

- `action` 必填，只允许 `STARTED`、`COMPLETED`、`FAILED`。
- `eventId` 必填，便于幂等。
- `nodeId/nodeType` 必填，平台根据 `nodeType` 分发到内部节点处理器。
- `jenkinsJobName/buildNumber/buildUrl` 必填，便于排障。
- `timestamp` 建议必填，便于后续核对事件顺序。
- `message` 只放简短摘要，不放大段 console log。

### Controller Design Implication

推荐直接采用单一事件入口，降低 Jenkins shared library 和后端 controller 的扩展成本：

```http
POST /admin-api/devops/pipeline-runs/{runId}/jenkins/callback
```

由 `action` 区分 step 开始、完成、失败，由 `nodeType` 区分节点类型。

这样新增节点类型时，Jenkins 侧仍然只调用一个 `goneDevopsCallback(...)`，后端根据 `nodeType` 分发给不同节点处理器。

如果保留多个历史 endpoint，也必须在 controller 层转换成统一事件对象后进入同一套 dispatcher，不允许每个 endpoint 写一套业务逻辑。

## Node Handler Abstraction

流水线节点会持续扩展，后端运行时不能把所有节点类型写死在 Jenkins callback controller 或执行 service 中。推荐抽象节点处理器接口。

### Core Interfaces

```java
public interface PipelineNodeRuntimeHandler {

    String getNodeType();

    String getExecutionCategory(); // JENKINS / PLATFORM

    void validateConfig(PipelineSpec.Node node, PipelineNodeValidationContext context);

    void appendJenkinsStage(JenkinsfileBuildContext context, PipelineSpec.Node node);

    void onStarted(PipelineNodeCallbackContext context);

    void onCompleted(PipelineNodeCallbackContext context);

    void onFailed(PipelineNodeCallbackContext context);

}
```

说明：

- `validateConfig`：节点配置校验。
- `appendJenkinsStage`：生成 Jenkinsfile stage。平台节点生成 gate stage，Jenkins 节点生成真实执行 stage。
- `onStarted`：处理 Jenkins step 开始事件。
- `onCompleted`：处理 Jenkins step 完成事件。
- `onFailed`：处理 Jenkins step 失败事件。
- 后续新增节点时新增 handler，不改核心 callback controller。

### Handler Registry

```text
PipelineNodeRuntimeHandlerRegistry
  -> Map<nodeType, PipelineNodeRuntimeHandler>
  -> getRequired(nodeType)
```

启动时注册所有 handler：

```text
CHECKOUT -> CheckoutNodeHandler
UNIT_TEST -> UnitTestNodeHandler
BUILD_ARTIFACT -> BuildArtifactNodeHandler
BUILD_IMAGE -> BuildImageNodeHandler
REPORT_ARTIFACTS -> ReportArtifactsNodeHandler
APPROVAL -> ApprovalPlatformGateHandler
```

### Unified Callback Dispatch

统一 callback controller 只做通用工作：

```text
1. parse request
2. verify callback token
3. validate run/version/build
4. idempotency check by eventId
5. load pipeline version spec
6. find node by nodeId
7. get handler by nodeType
8. dispatch by action: onStarted/onCompleted/onFailed
9. return simple response
```

controller 不直接写 `if APPROVAL`、`if BUILD_IMAGE` 这类业务分支。

### Callback Action Model

```text
STARTED
COMPLETED
FAILED
```

action 语义：

| Action | Handler method |
|---|---|
| `STARTED` | `handler.onStarted(context)` |
| `COMPLETED` | `handler.onCompleted(context)` |
| `FAILED` | `handler.onFailed(context)` |

### Platform Node Contract

所有平台节点在 Jenkinsfile 中生成同一种 gate stage，但业务由 handler 决定：

```text
APPROVAL -> 创建审批待办
RELEASE_WINDOW -> 等待发布窗口
QUALITY_GATE -> 检查平台质量门禁
MANUAL_CONFIRM -> 创建人工确认任务
```

Jenkins 不关心这些差异，只发送：

```text
STARTED
COMPLETED
FAILED
```

平台根据 `nodeType` 和 DSL params 决定业务动作。

## Data Model

### No New Runtime Tables for MVP

MVP 不新增 `dev_pipeline_jenkins_build`、`dev_pipeline_platform_gate`、`dev_pipeline_artifact`。

原因：

- Jenkins build 对用户不可见，本质对应平台的流水线 run 和 node 执行记录，不需要单独构建记录表。
- Platform gate 是平台 node handler 的一种实现，不需要单独门禁记录表。
- Artifact 暂时不是独立查询/审计对象，需要记录时可先写入构建 node 的 `result_json` 或平台构建记录字段，后续有独立产物管理需求再建模。

### Existing Table Mapping

#### dev_pipeline_run

作为整条平台流水线运行主记录：

```text
pipeline_run_id
application_env_id
pipeline_definition_version_id
run_status
started_at
finished_at
```

Jenkins build 级别的轻量元数据可以放在 run 的扩展字段或 context/result JSON 中：

```json
{
  "jenkinsJobName": "gone-devops-app",
  "jenkinsBuildNumber": 58,
  "jenkinsBuildUrl": "http://jenkins/job/gone-devops-app/58/",
  "callbackTokenHash": "hash"
}
```

#### dev_pipeline_run_log

作为每个 node 的运行记录：

```text
pipeline_run_id
node_id
node_type
node_name
status
context_json
result_json
started_at
finished_at
```

节点生命周期状态直接落这里：

```text
STARTED   -> status RUNNING / WAITING_INPUT
COMPLETED -> status SUCCESS
FAILED    -> status FAILED
```

平台节点状态、Jenkins step 摘要、inputId、错误信息都放在对应 node log 的 `context_json/result_json`。

产物信息暂时写到产生它的构建 node 或 `REPORT_ARTIFACTS` node 的 `result_json`：

```json
{
  "artifacts": [
    {
      "artifactType": "DOCKER_IMAGE",
      "artifactName": "gone-cloud/devops-server",
      "imageRepository": "gone-cloud/devops-server",
      "imageTag": "abc123",
      "commitSha": "abc123"
    }
  ]
}
```

后续如果出现产物检索、跨 run 复用、审计追踪、制品晋级等需求，再新增独立产物表。

## Service Design

### JenkinsBuildService

职责：

- 由平台后端内部服务触发 Jenkins build。
- 取消或停止正在运行的 Jenkins build。
- 根据需要查询 Jenkins build 状态。
- 平台节点的审批/拒绝等业务动作由对应 node handler 内部处理，不在本文档展开独立接口。

### PipelineJenkinsCallbackService

职责：

- 校验 callback token。
- 处理 Jenkins 统一 step callback。
- 根据 `action` 和 `nodeType` 分发到节点 handler。
- 幂等更新 run log。

### PipelinePlatformGateService

职责：

- 平台节点运行状态维护。
- 平台节点业务分派。
- 审批类型节点业务处理。
- gate 超时和取消。

### PipelineDeployOrchestrator

职责：

- Jenkins build 成功后读取 artifact。
- 执行平台固定部署。
- 更新 run 最终状态。

## API Draft

### Internal Integration APIs

说明：

- 这里不是前台用户接口。
- 分成两类：
  - 平台后端主动调用 Jenkins 的内部 client，不走平台 controller 暴露给前端。
  - Jenkins 回调平台的内部接入接口，需要保留 controller 供 Jenkins 调用。

### Backend Internal Jenkins Client

由平台后端服务内部调用 Jenkins Remote API，不对前端暴露：

```text
startPipeline(runId, pipelineVersionId, jenkinsfile, parameters)
stopPipeline(jobName, buildNumber)
```

这是平台对 Jenkins 的两个核心内部接口：

- 开启 Jenkins 流水线：代码合并完成后开启后续 Jenkins 节点执行。
- 停止 Jenkins 流水线：用户取消平台流水线、部署流程终止或平台判断需要中断时停止对应 Jenkins build。

`proceed input`、`abort input` 是平台节点审批动作的内部 Jenkins 操作，可以由 `stopPipeline` 或 gate service 封装，不作为前台接口，也不作为用户可见能力。

### Jenkins Callback API

```http
POST /admin-api/devops/pipeline-runs/{runId}/jenkins/callback
```

该接口虽然暂时放在 `/admin-api` 路径下，但调用方是 Jenkins，不是前端用户。它的语义是 Jenkins 回调平台：

- 前端不能调用该接口来触发 Jenkins 构建或伪造节点状态。
- 接口使用 callback token 鉴权，不使用普通后台用户登录态。
- 后端需要通过权限/拦截器/接口文档标注把它和普通用户后台接口区分开。
- Swagger 或前端 API 生成时应避免把该接口作为用户可操作能力暴露。
- 接口实现必须适配 Jenkins `httpRequest` 的调用方式：简单 JSON body、header 鉴权、快速 2xx 返回、可重试幂等。

请求体通过 `action` 区分开始、完成、失败，不为每种 step 事件新增独立 controller 方法。

### Platform User APIs

```http
POST /admin-api/devops/pipeline-runs/{runId}/cancel
```

这些接口面向平台用户，使用现有后台登录态和权限。

说明：

- 用户通过现有发布/流水线运行入口触发整条 run。
- 不新增 `trigger-jenkins-build` 之类的前台 controller。
- 审批节点的用户操作入口由审批类型 node 的业务实现决定，本文档不单独定义 platform gate approve/reject API。

## Idempotency Rules

- `jenkins build callback`：同一个 `pipelineRunId + jobName + buildNumber + action` 重复回调只更新 buildUrl/status。
- `callback STARTED`：节点已 `SUCCESS/FAILED/CANCELED` 时不得回退到 `RUNNING`，平台节点可进入 `WAITING_INPUT`。
- `callback COMPLETED`：重复成功保持成功；失败后再成功必须拒绝，除非存在明确 retry attempt。
- `callback FAILED`：重复失败保持失败，不重复触发终止动作。
- `platform node STARTED`：同一个 `pipelineRunId + nodeId` 只创建一个 gate。
- `artifact callback`：同一个 `pipelineRunId + artifactType + artifactName + imageTag` 幂等 upsert。
- `jenkins callback`：建议额外引入 `eventId` 唯一约束或幂等记录，避免 Jenkins 重试时重复落库。

## Security

- Jenkins callback token 由平台触发 build 时生成，只展示给本次 build。
- DB 仅保存 token hash，不保存明文 token。
- Jenkins 回调必须校验：
  - runId 存在且运行中。
  - pipelineVersionId 匹配当前 run。
  - callback token 匹配。
  - nodeId 存在于发布版本 spec。
- 平台调用 Jenkins 使用专用凭证，最小化权限。
- current-run、logs API 不返回 token、Jenkins 凭证、workspace path。
- 前台 API 响应不暴露 Jenkins job 名称、Jenkins 用户名、callback token 等内部集成细节；是否展示 buildUrl 可作为单独产品决策控制。
- Jenkins callback token 通过 header 传递，Jenkins 侧必须使用 masked header，避免插件日志打印敏感值。

## Failure Handling

| Failure | Handling |
|---|---|
| Jenkins trigger failed | run `FAILED`，CODE_MERGE 保持 `SUCCESS`，新增 Jenkins event 失败日志 |
| Jenkins callback auth failed | 返回 401/403，不改变状态 |
| Jenkins node failed | node log `FAILED`，run `FAILED` |
| Jenkins callback duplicate | 返回 200，标记 duplicate，不重复执行业务 |
| Jenkins callback out-of-order but ignorable | 返回 200，标记 accepted=false/duplicate=true，不让 Jenkins 因 409 直接失败 |
| Platform node started handling failed after Jenkins stage starts | Jenkins shared lib 应抛错，build fail |
| Approval node business handling failed | 审批类型 handler 通过 `onFailed` 处理节点失败并终止 run |
| Jenkins build aborted externally | Jenkins 通过统一 callback 上报 `FAILED/CANCELED`，或平台主动同步 Jenkins build 状态后将 run 标记 `CANCELED` |
| Gate timeout | Jenkins timeout 抛错，build fail；平台将 gate 标记 `TIMEOUT`，run `FAILED` |

## Rollout Plan

### Phase 1: Contract and Generation

- 开放 `APPROVAL` 节点配置。
- Jenkinsfile generator 支持 platform gate stage。
- 增加 Jenkins shared library 调用占位。
- 单元测试覆盖 Jenkinsfile 输出。

### Phase 2: Jenkins Build Runtime

- 代码合并成功后触发 Jenkins。
- 增加 Jenkins build metadata。
- 支持统一 Jenkins callback，覆盖 step started/completed/failed。
- current-run 展示 Jenkins 节点状态。

### Phase 3: Platform Gate Runtime

- 实现审批类型 node handler。
- 审批节点通过 `onStarted/onCompleted/onFailed` 管理节点状态。
- 支持取消 run 联动 Jenkins。

### Phase 4: Artifacts and Deploy

- 增加 artifact 上报和存储。
- Jenkins 成功后触发平台固定部署。
- run 最终状态由部署结果决定。

## Test Plan

- `JenkinsfileGeneratorServiceImplTest`
  - Jenkins 节点生成真实 stage。
  - `APPROVAL` 节点生成 Platform Gate stage。
  - gate inputId 使用稳定格式。
  - timeoutSeconds 生成 Jenkins timeout。
- `PipelineSpecValidationServiceImplTest`
  - `APPROVAL` enabled 后可通过验证。
  - 平台节点缺少必要参数时返回 validation error。
- `PipelineExecutionServiceImplTest`
  - 代码合并成功后不再 run success。
  - 代码合并成功后触发 Jenkins。
  - Jenkins 触发失败时 run failed。
- `PipelineJenkinsCallbackServiceTest`
  - 回调鉴权。
  - callback action started/completed/failed 幂等。
  - callback 根据 nodeType 分发到 handler。
  - artifact upsert 幂等。
- `PipelinePlatformGateServiceTest`
  - 审批类型 node handler 的 started/completed/failed 处理。
  - 状态冲突拒绝。

## Open Decisions

1. 审批节点第一期是否复用现有审批模块，还是 DevOps 内部轻量审批。
2. Jenkins job 模型：每个应用环境一个 job，还是共享一个参数化 job。
