# DevOps Pipeline YAML Spec

本文档是前后端联调用的流水线 YAML 契约。后端保存字段名仍为 `specJson`，但内容可以是 YAML 文本；JSON 仅作为兼容输入格式。

## API

### 获取应用环境流水线

`GET /devops/pipeline/get-by-application-env?applicationEnvId={applicationEnvId}`

返回当前应用环境的流水线定义、草稿版本、已发布版本。前端编辑时优先展示草稿版本；没有草稿时展示已发布版本；都没有时展示空编辑器。

版本对象包含 `rollbackFromVersionId`、`rollbackFromVersionNo`、`basedOnCurrentVersionId`、`basedOnCurrentVersionNo`、`rollbackReason`。普通发布版本这些字段为空；版本回退生成的新版本会记录回退来源和回退发生时的当前版本。

版本对象还包含 `cacheConfig`。该配置随版本保存，执行时使用 `PipelineRun.definitionVersionId` 对应版本的缓存目录快照。
当前 `cacheConfig.schemaVersion` 为 `1.1`，用于标记支持 `${WORKSPACE}` 工作目录相对缓存路径。

### 校验 YAML

`POST /devops/pipeline/validate`

```json
{
  "applicationEnvId": 1,
  "diagramJson": "{}",
  "specJson": "stages:\n  smoke_stage:\n    name: 冒烟测试\n    jobs:\n      smoke_job:\n        name: Docker Command 测试\n        runsOn:\n          group: local-docker/default\n          container: docker.m.daocloud.io/library/busybox:latest\n        steps:\n          command_step:\n            name: 执行命令\n            step: Command\n            with:\n              run: |\n                echo docker-command-ok\n",
  "cacheConfig": {
    "schemaVersion": "1.1",
    "directories": [
      { "id": "maven", "path": "/root/.m2", "enabled": true },
      { "id": "npm", "path": "/root/.npm", "enabled": true },
      { "id": "node-modules", "path": "${WORKSPACE}/node_modules", "enabled": true }
    ]
  }
}
```

响应：

```json
{
  "valid": true,
  "errors": [],
  "warnings": []
}
```

错误项结构：

```json
{
  "field": "stages.smoke_stage.jobs.smoke_job.steps.command_step.with.run",
  "nodeId": "command_step",
  "code": "PARAM_REQUIRED",
  "message": "命令步骤必须配置 run"
}
```

### 保存草稿

`POST /devops/pipeline/save-draft`

```json
{
  "applicationEnvId": 1,
  "name": "测试环境流水线",
  "diagramJson": "{}",
  "specJson": "stages:\n  smoke_stage:\n    name: 冒烟测试\n    jobs:\n      smoke_job:\n        name: Docker Command 测试\n        runsOn:\n          group: local-docker/default\n          container: docker.m.daocloud.io/library/busybox:latest\n        steps:\n          command_step:\n            name: 执行命令\n            step: Command\n            with:\n              run: |\n                echo docker-command-ok\n",
  "cacheConfig": {
    "schemaVersion": "1.1",
    "directories": [
      { "id": "maven", "path": "/root/.m2", "enabled": true },
      { "id": "npm", "path": "/root/.npm", "enabled": true },
      { "id": "node-modules", "path": "${WORKSPACE}/node_modules", "enabled": true }
    ]
  },
  "remark": "前端联调草稿"
}
```

返回草稿版本编号。保存草稿会执行校验并保存校验结果；即使校验失败也允许保存草稿，方便前端保留用户编辑内容。未提交 `cacheConfig` 时后端保存默认缓存目录配置。

### 发布版本

`POST /devops/pipeline/publish`

```json
{
  "definitionId": 1,
  "draftVersionId": 10,
  "versionName": "v1"
}
```

发布时后端会重新校验草稿 YAML。只有 `valid=true` 的草稿才能发布，发布成功后返回已发布版本编号。

### 回退版本

`POST /devops/pipeline/rollback`

```json
{
  "definitionId": 1,
  "targetVersionId": 6,
  "versionName": "v11",
  "rollbackReason": "发布后验证异常，回退到稳定版本"
}
```

回退不会修改历史版本，也不会把当前版本记录直接改回目标版本。后端会复制 `targetVersionId` 指向的已发布版本内容，生成一个新的已发布版本并立即更新为当前生效版本。新版本号继续按已发布版本递增，并记录：

- `rollbackFromVersionId` / `rollbackFromVersionNo`：被回退到的历史版本。
- `basedOnCurrentVersionId` / `basedOnCurrentVersionNo`：发起回退时的当前已发布版本。
- `rollbackReason`：本次回退原因。

返回新生成的已发布版本编号。

### 清理流水线缓存

`POST /devops/pipeline/cache/clear`

```json
{
  "definitionId": 1,
  "paths": ["/root/.m2"]
}
```

`paths` 为空时清理当前流水线定义下所有已知缓存目录。后端只接受缓存配置中的容器路径，宿主机目录由平台派生；流水线存在 `QUEUED/RUNNING/WAITING_INPUT` 运行时会拒绝清理。

## Cache Config Contract

- `cacheConfig.directories[].path` 支持两种写法：
  - 容器绝对路径，例如 `/root/.m2`
  - 工作目录相对缓存 `${WORKSPACE}/<subdir>`，例如 `${WORKSPACE}/node_modules`
- `${WORKSPACE}` 大小写敏感，只允许出现在路径开头。
- `${WORKSPACE}` 单独使用不合法，必须带子路径；`${WORKSPACE}/../x` 这类路径穿越也会被拒绝。
- 绝对路径仍然禁止直接写 `/workspace/...`，统一要求使用 `${WORKSPACE}/...` 表达工作目录内缓存。
- 校验失败统一返回 `PIPELINE_CACHE_CONFIG_INVALID`。
- 工作目录相对缓存位于 `/workspace` 子树内，流水线脚本如果执行 `rm -rf`、`git clean -fdx` 等破坏性操作，会真实删除对应缓存内容。

### 运行日志

结构化运行状态仍通过 `GET /devops/pipeline-run/{runId}/logs` 获取，返回阶段、任务、步骤状态以及 `logFileUrl`、`logTruncated`、`logSizeBytes`、`logLineCount`、`logArchiveStatus` 等日志元数据。

日志行查询按步骤查看，`stepId` 必填：

- `GET /devops/pipeline-run/{runId}/log-lines?stepId={stepId}&afterId={id}&limit={limit}`
- `GET /devops/pipeline-run/{runId}/log-lines/stream?stepId={stepId}&afterId={id}`

`PipelineRunLogLineRespVO.id` 是后端游标，前端只应保存最后一个返回的 `id` 并作为下一次请求的 `afterId`，不要假设它一定是数据库行编号。当前文件日志实现中，`id` 是步骤日志文件写入后的字节偏移游标。旧运行如果没有文件日志，后端会回退读取 `dev_pipeline_run_log_line`，此时 `id` 仍可能是旧表行编号。

SSE 运行中发送 `log-line` 事件；当指定步骤进入终态后发送 `complete` 事件，事件数据包含 `pipelineRunId`、`runLogId`、`stepId`、`status`、`logFileUrl`、`logArchiveStatus` 等字段。前端完成后使用 `logFileUrl` 查看持久化完整日志；如需刷新步骤状态和所有日志 URL，继续调用 `GET /devops/pipeline-run/{runId}/logs`。

步骤原始日志可通过 `GET /devops/pipeline-run/{runId}/logs/{runLogId}/raw` 读取本地文件；步骤完成后后端会按配置尝试归档到对象存储并写入 `logFileUrl`。

### 节点动作

`GET /devops/pipeline-run/{runId}/logs` 的每条步骤日志可在 `result.actions` 返回当前节点可操作点。前端应按通用动作契约渲染，不应按 `stepType` 写死按钮。

动作字段：

- `key`: 稳定动作编码，例如 `CODE_MERGE_RESOLVE_CONFLICT`、`APPROVAL_OPEN_PROCESS`、`IMAGE_ARCHIVE_DOWNLOAD`
- `label`: 按钮文案
- `type`: `ROUTE`、`DOWNLOAD`、`API`、`EXTERNAL_LINK`
- `style`: `PRIMARY`、`DEFAULT`、`DANGER`、`LINK`
- `permission`: 前端权限提示，后端接口仍会独立鉴权
- `enabled` / `disabledReason`: 可用状态和不可用原因
- `target`: 路由、下载或 API 目标
- `payload`: 展示辅助数据，不包含凭据
- `order`: 排序

内置 `CODE_MERGE_RESOLVE_CONFLICT` 动作的 `payload` 包含 `conflictCount`、`unsupportedCount`、`deployBranch`、`deployBranchPushed`、`currentBranchName`、`currentCommitSha`。发生冲突时后端会先把冲突前的部署分支 HEAD 推送到远端，`deployBranchPushed=true` 表示前端可提示用户 fetch/checkout 该部署分支；用户仍需在本地执行 `git merge <currentCommitSha>` 才能复现冲突工作区。`currentBranchName/currentCommitSha` 表示当前冲突来源变更分支和提交快照。

示例：

```json
{
  "stepId": "approval",
  "status": "WAITING_INPUT",
  "result": {
    "actions": [
      {
        "key": "APPROVAL_OPEN_PROCESS",
        "label": "去审批",
        "type": "ROUTE",
        "style": "PRIMARY",
        "permission": "devops:pipeline:update",
        "enabled": true,
        "target": {
          "routeName": "BpmProcessInstanceDetail",
          "query": { "id": "pi-1" }
        },
        "payload": {
          "runId": 800,
          "stepId": "approval",
          "processInstanceId": "pi-1"
        }
      }
    ]
  }
}
```

旧运行没有 `result.actions` 时按空数组处理。未知 `type` 或无法执行的 target 必须隐藏或降级，不影响节点状态展示。动作 payload 和 target 不得包含密码、token、accessKeySecret 等敏感值。

`GET /devops/application/release/current-run` 是发布页轮询用的轻量读模型，不透传完整 `dev_pipeline_run_log.result_json`。节点/步骤 `result` 只保留少量卡片展示字段，例如 `image`、`storagePath`、`logTruncated` 等；`logLines`、`outputs`、完整执行上下文、workspace/runtime/env 等大字段或敏感字段必须通过日志/详情接口按需获取。`result.actions` 会被转换为节点/步骤顶层 `actions`，如果一个 job 包含多个 step，job 节点会聚合其 step actions。

## YAML Shape

顶层结构：

```yaml
sources:
  <source_id>:
    type: <string>
    name: <string>
    endpoint: <string>
    branch: <string>
    with: {}
stages:
  <stage_id>:
    name: <string>
    enabled: <boolean>
    jobs:
      <job_id>:
        name: <string>
        enabled: <boolean>
        runsOn:
          group: <string>
          container: <string>
        needs:
          - <job_id>
        timeoutSeconds: <integer>
        retryTimes: <integer>
        failStrategy: failFast
        plugins:
          - name: <string>
            plugin: DingTalkPlugin
            triggerState:
              - success
              - fail
            with:
              webhook: <ding-talk-webhook-url>
              secret: <optional-sign-secret>
              noticeContent:
                - pipelineName
                - operator
                - pipelineTask
                - pipelineStage
                - status
              customContent: <plain-text>
              at:
                - <mobile-phone>
        steps:
          <step_id>:
            name: <string>
            step: Command
            enabled: <boolean>
              timeoutSeconds: <integer>
              retryTimes: <integer>
              failStrategy: failFast
              with:
                run: |
                  echo hello
              env:
                KEY: VALUE
```

## Fields

| 字段 | 必填 | 当前支持 | 说明 |
|---|---:|---|---|
| `sources` | 否 | 最多 1 个 | 代码源配置。存在时用于准备本次运行的共享工作目录；不存在且 run 关联应用时，使用应用实体配置的仓库地址、代码源和默认分支。 |
| `sources.<source_id>.type` | 有 source 时必填 | `gitlab` | 代码源类型，当前仅支持 `gitlab`。 |
| `sources.<source_id>.name` | 否 | 任意字符串 | 展示名称。 |
| `sources.<source_id>.endpoint` | 有 source 时必填 | 任意非空字符串 | 代码源地址。 |
| `sources.<source_id>.branch` | 有 source 时必填 | 任意非空字符串 | 默认分支。 |
| `sources.<source_id>.with` | 否 | 对象 | 扩展参数。 |
| `stages` | 是 | 对象 | 阶段集合，key 为 `stageId`。 |
| `stages.<stage_id>.name` | 否 | 字符串 | 阶段展示名称。 |
| `stages.<stage_id>.enabled` | 否 | `true/false` | 默认 `true`。为 `false` 时阶段下任务/步骤不会执行。 |
| `jobs` | 是 | 对象 | 阶段内任务集合，key 为 `jobId`。 |
| `jobs.<job_id>.name` | 否 | 字符串 | 任务展示名称。 |
| `jobs.<job_id>.enabled` | 否 | `true/false` | 默认 `true`。 |
| `jobs.<job_id>.runsOn.group` | 容器型 step 必填 | `local-docker/default` | 执行资源池。当前只支持本地 Docker。仅包含平台 step 的 job 可不配置。 |
| `jobs.<job_id>.runsOn.container` | 容器型 step 必填 | Docker 镜像名 | 执行容器镜像。后端当前不自动 pull，测试前请确保镜像已存在。 |
| `jobs.<job_id>.needs` | 否 | 字符串或字符串数组 | 依赖任务编号。为空时同阶段/跨阶段其他任务不会自动成为前置依赖。 |
| `jobs.<job_id>.timeoutSeconds` | 否 | 正整数 | 任务超时时间，第一版默认 1800 秒。 |
| `jobs.<job_id>.retryTimes` | 否 | `0` | 第一版不做自动重试。 |
| `jobs.<job_id>.failStrategy` | 否 | `failFast` | 默认 `failFast`。 |
| `jobs.<job_id>.plugins` | 否 | 数组 | Job 到达匹配终态后异步执行的旁路插件；未配置时行为不变。 |
| `plugins[].name` | 是 | 字符串 | 插件展示名称。 |
| `plugins[].plugin` | 是 | `DingTalkPlugin` | 稳定插件类型标识。未注册类型校验失败。 |
| `plugins[].triggerState` | 是 | `success` / `fail` 数组 | `success` 仅匹配 Job `SUCCESS`，`fail` 仅匹配 `FAILED`；重复值校验失败。 |
| `plugins[].with.webhook` | `DingTalkPlugin` 必填 | 钉钉官方 HTTPS webhook | 必须包含机器人 `access_token`；完整地址属于敏感配置。 |
| `plugins[].with.secret` | 否 | 字符串 | 钉钉机器人加签密钥；配置后生成 `timestamp/sign`。 |
| `plugins[].with.noticeContent` | `DingTalkPlugin` 必填 | 字符串数组 | 支持 `pipelineName/operator/pipelineTask/pipelineStage/status`，按配置顺序输出。 |
| `plugins[].with.customContent` | 否 | 字符串 | 作为普通文本原样追加，不解析 `${VAR}` 或模板表达式。 |
| `plugins[].with.at` | 否 | 手机号或手机号数组 | 标量和数组均归一化为钉钉 `atMobiles`。 |
| `steps` | 是 | 对象 | 任务内步骤集合，key 为 `stepId`。 |
| `steps.<step_id>.name` | 否 | 字符串 | 步骤展示名称。 |
| `steps.<step_id>.step` | 是 | `Command` / `CodeMerge` / `APPROVAL` / `K8sDeploy` / `K8sImageUpgrade` / `PrivateRegistryDockerBuild` / `DockerImageExportObjectStorage` / `DockerImageArchiveImport` | 当前可执行步骤类型。 |
| `steps.<step_id>.enabled` | 否 | `true/false` | 默认 `true`。 |
| `steps.<step_id>.with.run` | `Command` 必填 | Shell 脚本 | 默认在 job 容器 `/workspace` 执行；指定 `sshHostId` 时在映射后的远端源码目录执行。 |
| `steps.<step_id>.with.sshHostId` | 否 | 固定正整数 | 已配置的环境主机记录 id（不是 hostKey），不能使用变量。省略时保持 Docker 行为；详见 SSH Command。 |
| `steps.<step_id>.with.env` | 否 | 对象 | 环境变量，key 需匹配 `[A-Za-z_][A-Za-z0-9_]*`。 |
| `steps.<step_id>.with.baseBranch` | `CodeMerge` 必填 | 字符串 | 合并基础分支，支持 `${SOURCE_BRANCH}`。 |
| `steps.<step_id>.with.targetBranch` | `CodeMerge` 必填 | 字符串 | 合并目标分支，支持 `${BRANCH_NAME}`。 |
| `steps.<step_id>.with.branches` | 否 | 字符串数组 | 待合并分支列表。配置后优先使用该数组。 |
| `steps.<step_id>.with.branchesFromSubmit` | 否 | `true/false` | 默认 `true`。未配置 `branches` 时，使用本次发布提交携带的变更分支。 |
| `steps.<step_id>.with.pushOnSuccess` | 否 | `true/false` | 默认 `true`。合并成功后是否推送目标分支。 |
| `steps.<step_id>.with.processDefinitionKey` | `APPROVAL` 必填 | 字符串 | BPM 流程定义 key。审批步骤由平台执行，不创建 Docker 容器。 |
| `steps.<step_id>.with.deployMode` | `K8sDeploy` 必填 | `RAW_MANIFEST` / `ORCHESTRATION` | 默认 RAW_MANIFEST；ORCHESTRATION 引用流水线所属应用的 K8S_DEPLOYMENT 编排草稿。 |
| `steps.<step_id>.with.manifestYaml` | 仅 `RAW_MANIFEST` 必填 | 字符串 | RAW_MANIFEST 仅支持 Service 和一个 Deployment；多文档按顺序提交。ORCHESTRATION 不读取此字段。两种模式沿用 `${IMAGE}`、`${IMAGE_URI}`、`${APP_KEY}`、`${COMMIT_SHA}`、`${BRANCH_NAME}`、`${PIPELINE_RUN_ID}`、`${ENV_KEY}`、`${NAMESPACE}` 渲染。 |
| `steps.<step_id>.with.workloadKind` | `K8sImageUpgrade` 必填 | `Deployment` | 镜像升级目标工作负载类型，当前仅支持已存在的 Deployment。 |
| `steps.<step_id>.with.workloadName` | `K8sImageUpgrade` 必填 | 字符串 | 已存在的 Deployment 名称。不存在时步骤失败，不做自动创建。 |
| `steps.<step_id>.with.containerName` | `K8sDeploy` / `K8sImageUpgrade` 必填 | 字符串 | 目标容器名称。 |
| `steps.<step_id>.with.image` | `K8sDeploy` / `K8sImageUpgrade` 必填 | 字符串 | 目标镜像，支持 `${APP_KEY}`、`${COMMIT_SHA}`、`${BRANCH_NAME}`、`${PIPELINE_RUN_ID}`、`${ENV_KEY}`。 |
| `steps.<step_id>.with.replicas` | 否 | 整数 | 目标副本数；为空时保留 YAML 或现有 Deployment 副本数。 |
| `steps.<step_id>.with.rolloutTimeoutSeconds` | 否 | 整数 | 等待 Deployment rollout ready 的超时时间，默认 300 秒。 |
| `steps.<step_id>.with.artifact` | `PrivateRegistryDockerBuild` 必填 | 字符串 | 制品名称，成功后写入 step outputs/resultJson，供后续部署步骤选择。 |
| `steps.<step_id>.with.image` | `PrivateRegistryDockerBuild` 必填 | 字符串 | Docker 镜像上传地址，例如 `registry.cn-hangzhou.aliyuncs.com/ns/demo:1.0`。 |
| `steps.<step_id>.with.certificate.type` | `PrivateRegistryDockerBuild` 必填 | `usernamePassword` | 当前仅支持用户名密码；`serviceConnection` 暂不实现且校验会拒绝。 |
| `steps.<step_id>.with.certificate.username` | `certificate.type=usernamePassword` 必填 | 字符串 | 私有镜像仓库用户名。 |
| `steps.<step_id>.with.certificate.password` | `certificate.type=usernamePassword` 必填 | 字符串 | 私有镜像仓库密码。后端不会写入 resultJson；前端展示和保存时也应按敏感字段处理。 |
| `steps.<step_id>.with.dockerfilePath` | `PrivateRegistryDockerBuild` 必填 | 字符串 | Dockerfile 相对代码库根目录路径。 |
| `steps.<step_id>.with.contextPath` | 否 | 字符串 | Docker build 执行上下文，相对代码库根目录；为空时默认使用 Dockerfile 所在目录。 |
| `steps.<step_id>.with.noCache` | 否 | `true/false` | 默认 `false`。为 `true` 时追加 `--no-cache=true`。 |
| `steps.<step_id>.with.variables` | 否 | 数组 | 构建参数数组，每项 `{ key, value }` 会转换为 `--build-arg key=value`。 |
| `steps.<step_id>.with.buildkitVersion` | 否 | `v0.8.0` / `v0.9.0` / `v0.11.6` | 当前只做参数校验和前端展示；执行由后端平台 DockerClient 完成，暂不按该字段切换 BuildKit 版本。 |
| `steps.<step_id>.with.image` | `DockerImageExportObjectStorage` 必填 | 字符串 | 待导出的源镜像地址，支持 `${VAR}` 变量替换。 |
| `steps.<step_id>.with.archiveFormat` | 否 | `docker-archive` | 镜像归档格式，支持 `docker-archive`、`oci-archive`。 |
| `steps.<step_id>.with.compression` | 否 | `none` | 归档压缩方式，支持 `none`、`gzip`、`zstd`。压缩后下游导入前需先解压。 |
| `steps.<step_id>.with.outputFileName` | 否 | 根据镜像地址派生 | 归档文件名，只能是文件名，不能包含路径。 |
| `steps.<step_id>.with.registryTlsVerify` | 否 | `true` | 拉取源镜像时是否校验镜像仓库 TLS。 |
| `steps.<step_id>.with.registryCertificate.type` | `DockerImageExportObjectStorage` 必填 | `usernamePassword` | 当前仅支持用户名密码。 |
| `steps.<step_id>.with.registryCertificate.username` | `DockerImageExportObjectStorage` 必填 | 字符串 | 源镜像仓库用户名。 |
| `steps.<step_id>.with.registryCertificate.password` | `DockerImageExportObjectStorage` 必填 | 字符串 | 源镜像仓库密码。后端不会写入 resultJson。 |
| `steps.<step_id>.with.storage.type` | `DockerImageExportObjectStorage` 必填 | `s3` | 对象存储类型，当前仅支持 S3 协议。 |
| `steps.<step_id>.with.storage.endpoint` | `DockerImageExportObjectStorage` 必填 | 字符串 | S3 协议 endpoint，例如 `https://oss-cn-hangzhou.aliyuncs.com` 或 `https://minio.example.com`。 |
| `steps.<step_id>.with.storage.path` | `DockerImageExportObjectStorage` 必填 | `s3://bucket/path/file` | 目标对象路径，支持 `${VAR}` 变量替换。 |
| `steps.<step_id>.with.storage.region` | 否 | 字符串 | S3 Region；某些兼容实现可留空。 |
| `steps.<step_id>.with.storage.forcePathStyle` | 否 | `true/false` | 是否强制使用 path-style 访问。 |
| `steps.<step_id>.with.storage.certificate.type` | `DockerImageExportObjectStorage` 必填 | `accessKey` | 当前仅支持 AccessKey。 |
| `steps.<step_id>.with.storage.certificate.accessKeyId` | `DockerImageExportObjectStorage` 必填 | 字符串 | 对象存储 AccessKey ID。 |
| `steps.<step_id>.with.storage.certificate.accessKeySecret` | `DockerImageExportObjectStorage` 必填 | 字符串 | 对象存储 AccessKey Secret。后端不会写入 resultJson。 |
| `steps.<step_id>.with.overwrite` | 否 | `false` | 目标对象已存在时是否覆盖。 |

`DockerImageExportObjectStorage` 成功后会在步骤 outputs/resultJson 中输出 `storagePath`（原始 `s3://bucket/key` 对象定位）和 `downloadUrl`（根据 `storage.endpoint`、`storage.path` 与 `forcePathStyle` 生成的公开 HTTP(S) 下载地址）。下载动作的 `target.url` 与 `downloadUrl` 一致；该约定要求目标对象存储桶已公开，后端不会在结果中写入任何 AccessKey/SecretKey。

| `steps.<step_id>.with.fileUrl` | `DockerImageArchiveImport` 必填 | 字符串 | 镜像包文件 URL，上传镜像部署触发时通常配置为 `${FILE_URL}`。 |
| `steps.<step_id>.with.image` | `DockerImageArchiveImport` 必填 | 字符串 | 目标镜像地址，例如 `registry.cn-hangzhou.aliyuncs.com/ns/demo:${runId}`。 |
| `steps.<step_id>.with.archiveFormat` | 否 | `docker-archive` | 镜像归档格式，支持 `docker-archive`、`oci-archive`；需与导出步骤配置一致。 |
| `steps.<step_id>.with.compression` | 否 | `none` | 归档压缩方式，支持 `none`、`gzip`、`zstd`。 |
| `steps.<step_id>.with.registryTlsVerify` | 否 | `true` | 推送目标镜像时是否校验镜像仓库 TLS。 |
| `steps.<step_id>.with.certificate.type` | `DockerImageArchiveImport` 必填 | `usernamePassword` | 当前仅支持用户名密码。 |
| `steps.<step_id>.with.certificate.username` | `DockerImageArchiveImport` 必填 | 字符串 | 目标镜像仓库用户名。 |
| `steps.<step_id>.with.certificate.password` | `DockerImageArchiveImport` 必填 | 字符串 | 目标镜像仓库密码。后端不会写入 resultJson。 |

## ID Rules

`source_id`、`stage_id`、`job_id`、`step_id` 必须满足：

```text
[a-zA-Z][a-zA-Z0-9_-]{0,63}
```

额外约束：

- `job_id` 必须在整条流水线内全局唯一。
- `step_id` 必须在整条流水线内全局唯一。
- `needs` 填写的是 `job_id`。
- `needs` 可以跨 stage 引用任务。
- `needs` 不能引用不存在的任务。
- `needs` 不能依赖自身。
- `needs` 不能形成循环依赖。

## Variable Substitution

- 后端会在 step 执行前，对 `with` 参数中的字符串值执行简单 `${VAR}` 替换；嵌套对象和数组中的字符串同样会递归替换。
- 变量来源包括：本次 run 元数据、应用上下文初始变量、当前 stage/job/step 元数据，以及同一 run 内已成功完成的上游 step outputs。
- 变量名同时支持 camelCase 和大写下划线形式，例如 `${commitSha}` 与 `${COMMIT_SHA}` 等价。
- 当前只支持完整变量名替换，不支持默认值、表达式、条件或函数。
- 静态校验允许枚举、布尔、路径等字段使用 `${VAR}` 占位符；替换后的真实值仍会在运行时由对应 step handler 校验。
- 凭证类字段可以通过变量传入，但 handler 不会把密码、AccessKey Secret 等敏感字段写入 `resultJson`。

## Execution Semantics

- 每次流水线运行创建一个 run workspace，所有 stage/job 都挂载同一个目录到容器 `/workspace`。
- `sources` 当前最多只能定义 1 个来源，`type` 当前只支持 `gitlab`。
- 通过 `submit-branch` 触发运行时，如果 YAML 未配置 `sources`，源码 checkout 使用应用绑定的 GitLab 代码源和应用默认分支；不执行隐藏的前置代码合并。
- 源码 checkout 每次 run 只执行一次；后续 job 复用同一个 run workspace。
- 没有配置 `sources` 且 run 没有关联应用时，后端只创建空临时 workspace，Docker `Command` step 仍在容器 `/workspace` 执行；SSH Command 不支持缺少应用环境的 run。
- stage 只用于展示分组，不是隐式执行屏障。
- 没有 `needs` 的 job 立即具备调度条件。
- 有 `needs` 的 job 只有在依赖 job 全部成功后才会执行。
- 当前后端调度实现按 DAG 顺序推进，后续再扩展真正并发 worker。
- 任一 job 失败时，默认 `failFast`，剩余待执行 job 会被标记为跳过。
- Job 状态成功持久化为 `SUCCESS/FAILED` 后，后端按 `plugins[].triggerState` 提交插件事件；`SKIPPED/CANCELED/BLOCKED` 不触发插件。
- 插件通过独立有界线程池异步执行，流水线不等待插件 HTTP 请求完成；插件异常、HTTP 超时、线程池拒绝或钉钉业务错误不改变 Job/Pipeline 状态，也不阻断后续 DAG 调度。
- 第一版插件发送是 fire-and-forget：不保存执行记录、不自动重试，服务重启或队列拒绝时通知可能丢失。
- webhook、access_token、secret 和请求体不会写入流水线日志、context、result 或插件异常消息；`specJson` 仍按现有流水线定义权限保存和读取原文。
- 同一个 job 内多个 Docker `Command` step 共享同一个容器和 `/workspace`；SSH Command 不创建 Docker runtime。
- 不同 job 使用不同容器，但挂载同一个 run workspace；job 私有临时文件应写入 `jobs/{jobId}/tmp`、`jobs/{jobId}/reports`、`jobs/{jobId}/artifacts` 等子目录。
- 依赖缓存与 run workspace 分离。缓存目录来自版本化 `cacheConfig`；宿主机路径由平台按 `tenantId + appId + applicationEnvId + definitionId + sha256(cachePath)` 派生，其中普通绝对路径的 `cachePath` 就是容器路径，`${WORKSPACE}/...` 则使用保留变量字面量的 canonical 路径。
- 默认缓存目录包括 `/root/.m2`、`/root/.gradle/caches`、`/root/.npm`、`/root/.pnpm-store`、`/root/.yarn`、`/go/pkg/mod`、`/root/.cache`。禁用目录不会挂载。
- `${WORKSPACE}/<subdir>` 会在 Docker 容器中解析为 `/workspace/<subdir>`，并作为嵌套 bind mount 挂载到共享 run workspace 子目录。
- `CodeMerge` 是平台 step，不创建 Docker 容器，不要求 `runsOn`。
- `CodeMerge` 合并成功后会输出 `mergedBranch`、`mergedCommitSha`、`branchName`、`commitSha`；下游容器型 job 的源码 checkout 会优先使用合并后的目标分支和提交。
- `CodeMerge` 遇到冲突时 step log 进入 `WAITING_INPUT`，所属 job 进入 `BLOCKED`，继续使用现有代码冲突查看、保存解决结果、继续合并、重试当前分支接口处理。
- `APPROVAL` 是平台 step，不创建 Docker 容器，不要求 `runsOn`。
- `APPROVAL` 发起 BPM 流程实例后 step log 进入 `WAITING_INPUT`，所属 job 进入 `BLOCKED`；审批通过后再次调度会从该 step 继续向下执行，审批拒绝或取消会使流水线失败或取消。
- `K8sDeploy` 和 `K8sImageUpgrade` 是平台 step，不创建 Docker 容器，不要求 `runsOn`。
- `K8sDeploy` 会创建部署单。RAW_MANIFEST 渲染并按文档顺序提交 Service 和唯一 Deployment，继续检查 Deployment/Pod/Service 健康。
- `K8sDeploy` 的 ORCHESTRATION 模式在首次创建部署单时读取所属应用的当前 K8S_DEPLOYMENT 草稿，过滤禁用组件并按 applyOrder 升序提交 Secret、ConfigMap、PersistentVolumeClaim、Service、Deployment、Ingress；必须恰好一个启用的 Deployment。启用组件不能依赖禁用组件，资源身份不能重复，错误在集群提交前失败，不回退为 RAW_MANIFEST。
- ORCHESTRATION 仍要求 containerName 和 image；目标命名空间来自环境配置，镜像及可选 replicas 覆盖唯一 Deployment。健康门禁仅检查该 Deployment/ReplicaSet/Pod/容器，不检查 Service Endpoints、PVC Bound 或 Ingress 连通性。
- 编排来源 id/updateTime、原始及渲染 YAML 保存在部署单内部快照；同一部署单重试不重读草稿，新部署读取最新草稿。Secret data/stringData 在日志 context 和部署详情中被移除，内部快照保留执行内容；解析/apply 错误不回显 Secret payload。
- `K8sImageUpgrade` 会创建部署单，只升级已存在 Deployment 的目标容器镜像和可选副本数；Deployment 或容器不存在时步骤失败，不自动创建工作负载。
- 部署单只更新自身和 step log 状态；整条流水线的 job/run 状态由 `PipelineExecutionEngine` 聚合。
- 取消流水线时，未结束的 `APPROVAL` step 会主动取消关联的 BPM 流程实例，未结束的 K8s 部署 step 会主动取消关联部署单。
- `PrivateRegistryDockerBuild` 是平台 step，不创建 Docker job runtime，不要求 `runsOn`。后端会准备源码 workspace，并通过平台 DockerClient 构建和推送镜像。
- `PrivateRegistryDockerBuild` 不会主动把 `certificate.password` 写入 step result；前端展示和日志处理时不要回显密码输入值。
- `DockerImageExportObjectStorage` 是容器型 step，会在 job runtime 中执行 `/usr/local/bin/export-image-to-object-storage`，要求 `runsOn.container` 使用包含 `skopeo`、`mc`、`gzip`、`zstd` 的执行镜像，推荐 `gone-cloud/pipeline-builder:java17-node24-maven3.9`。
- `DockerImageExportObjectStorage` 不使用 Docker-in-Docker，不挂载宿主机 Docker socket，不执行 `docker pull` 或 `docker save`。
- `DockerImageExportObjectStorage` 的 `image`、`archiveFormat`、`compression`、`outputFileName`、`storage.path`、`overwrite`、`registryTlsVerify` 等 `with` 参数会在执行前经过共享变量 resolver。
- `DockerImageArchiveImport` 是容器型 step，会在 job runtime 中执行 `/usr/local/bin/import-image-archive-to-registry`，要求 `runsOn.container` 使用通用 `pipeline-builder` 或其他包含 `curl`、`skopeo`、`gzip`、`zstd` 的执行镜像。
- `DockerImageArchiveImport` 的 `fileUrl`、`image`、`archiveFormat`、`compression`、`registryTlsVerify` 等 `with` 参数会在执行前经过共享变量 resolver；通过 `/devops/application/release/upload-image` 触发时，后端会把 `fileUrl` 持久化到 `PipelineRun.inputContextJson` 并注入 `${FILE_URL}`。

## SSH Command（环境主机构建）

`Command.with.sshHostId` 引用当前流水线应用环境对应的 HOST 环境主机，复用该记录的密码或私钥认证。主机必须同时属于当前租户、当前 run 的应用环境；缺少/忽略租户上下文、跨租户、跨环境、未关联应用环境的 run 均拒绝。调用权限仍使用现有流水线定义/发布/触发权限，不增加任意主机命令 API。

- SSH `with` 仅允许 `run`、`env`、`sshHostId`。禁止 `password`、`privateKey`、`passphrase`、地址/用户覆盖或工作目录覆盖；不要把凭据放进 `run`/`env`。凭据不会写入命令上下文字符串、结果或连接异常消息；用户脚本主动打印的内容仍会成为日志。
- 仅 SSH / 平台步骤的 job 不需要 `runsOn`；同 job 含 Docker 步骤时仍需配置 `runsOn`，Docker 生命周期和缓存挂载不变。SSH 使用远端用户的登录 shell 环境和工具，不注入 Docker 的 `/root/.m2` 等缓存环境变量，也不使用 Docker `cacheConfig` 挂载。
- 远端要求 Linux、`bash`、`setsid`、`cat`、`sleep`。执行无 PTY，用户脚本 stdin 为 `/dev/null`，stdout/stderr 实时进入现有 step log/SSE；脚本自己产生的输出缓冲不由平台强制刷新。
- 退出码保留远端脚本状态；平台超时为 `124`，取消/中断为 `130`，工作区/依赖检查失败为 `125`，连接/执行异常为 `-1`。`steps.<id>.timeoutSeconds`（默认 1800 秒）控制执行期限；Job 总期限仍由引擎控制。取消、中断或客户端断连会撤销 stdin 心跳租约；远端最多约 10 秒无心跳后 TERM 进程组、1 秒后 KILL。不得用 `nohup`/二次 `setsid`/守护进程等主动脱离进程组，需长期运行的服务应交给部署系统管理。
- 取消以持久化的流水线状态为准：源码/运行时准备完成后重新检查，SSH 连接前后、命令启动前和执行等待期间也检查。执行器注册之前收到的取消不会被丢弃；取消后的 SSH 步骤日志保持 `CANCELED`，不覆盖为失败。
- 认证复用 `HostSshClient`，没有新增宽松主机密钥设置。现有凭据模型无可信 host key/fingerprint 字段，故仍兼容既有 `StrictHostKeyChecking=no`，**存在中间人风险**。仅允许管理员显式配置映射的主机，部署于可信网络并使用最小权限构建用户；上线前应评估风险，后续独立增加 host key 模型和校验，不能宣称当前有主机身份校验。

### 显式共享 workspace 映射（服务端配置，不是 Pipeline YAML）

源码仍由平台准备。SSH 不会自动 checkout、复制或 rsync；仅支持同一宿主机 bind mount 或已由管理员配置的共享文件系统。容器路径不等于主机路径，未配置映射必须失败。配置示例：DevOps 容器把宿主机 `/srv/gone/pipeline` 挂载为 `/var/lib/gone/pipeline`，workspace 服务所生成目录位于该挂载内：

```yaml
# application.yaml / 外部 Spring 配置。42 是环境主机记录 id。
yudao:
  devops:
    pipeline:
      ssh:
        hosts:
          42:
            local-root: /var/lib/gone/pipeline
            remote-root: /srv/gone/pipeline
```

解析方式：`remoteRoot + relative(localRoot, currentSourceWorkspace)`。两个 root 必须为非根目录绝对路径，localRoot 必须包含当前 workspace；同路径复用也必须显式配置两个 root。管理员需确保读写权限一致。每次执行在当前 workspace 写入随机探针，远端 `cd` 后校验探针内容，错误映射/非共享目录在用户脚本执行前以 `125` 失败；执行完成后删除探针。没有共享 workspace 的独立远程构建机不受支持，应拒绝而不是在 home 或同名空目录执行。

```yaml
stages:
  build:
    jobs:
      host_build:
        steps:
          compile:
            step: Command
            timeoutSeconds: 1800
            with:
              sshHostId: 42
              env:
                BUILD_PROFILE: test
              run: |
                set -eu
                java -version
                mvn -B -DskipTests package
```

完整工具链示例见 `examples/ssh-host-build-pipeline.yaml`。YAML 校验负责参数形状和静态主机 id；主机权限、映射和共享目录身份在运行时校验，发布校验成功不等于远端可用。

## 应用编排部署步骤示例

先在应用编排保存 Kubernetes 配置，然后在该应用的流水线中配置以下步骤（放入 `stages.<stageId>.jobs.<jobId>.steps`）：

```yaml
deploy:
  name: 应用编排部署
  step: K8sDeploy
  with:
    deployMode: ORCHESTRATION
    containerName: app
    image: registry.example.com/demo:${COMMIT_SHA}
    rolloutTimeoutSeconds: 300
```

该模式不要求 runsOn 或 manifestYaml。应用编排保存校验仍沿用现有规则，不新增变量组语法。部署不是事务：中途失败时已提交资源保留，不自动回滚，也不删除从草稿中移除的旧资源。

## Minimal Runnable YAML

用于前后端联调保存、发布、运行的最小配置：

```yaml
sources:
  my_repo:
    type: gitlab
    name: "应用代码源"
    endpoint: https://example.com/group/repo.git
    branch: master
stages:
  smoke_stage:
    name: "冒烟测试"
    jobs:
      smoke_job:
        name: "Docker Command 测试"
        runsOn:
          group: local-docker/default
          container: docker.m.daocloud.io/library/busybox:latest
        steps:
          command_step:
            name: "执行命令"
            step: Command
            with:
              run: |
                echo docker-command-ok
                uname -m
                pwd
```

运行前请确保后端所在机器 Docker daemon 可访问，并且镜像已存在：

```bash
docker pull docker.m.daocloud.io/library/busybox:latest
```

## CodeMerge + Build YAML

用于多个变更分支先合并到部署分支，再基于合并结果构建：

```yaml
sources:
  my_repo:
    type: gitlab
    name: "应用代码源"
    endpoint: https://example.com/group/repo.git
    branch: master
stages:
  merge_stage:
    name: "代码合并"
    jobs:
      code_merge_job:
        name: "合并变更分支"
        steps:
          code_merge_step:
            name: "合并到部署分支"
            step: CodeMerge
            with:
              baseBranch: "${SOURCE_BRANCH}"
              targetBranch: "${BRANCH_NAME}"
              pushOnSuccess: true
              branches:
                - feature/order-api
                - feature/order-ui
  build_stage:
    name: "构建验证"
    jobs:
      maven_build_job:
        name: "Maven 构建"
        needs:
          - code_merge_job
        runsOn:
          group: local-docker/default
          container: gone-cloud/pipeline-builder:java17-node24-maven3.9
        steps:
          build_step:
            name: "Maven 构建"
            step: Command
            with:
              run: |
                java -version
                node -v
                mvn -v
                mvn -B clean package -DskipTests
```

如果希望 `CodeMerge` 直接使用 `submit-branch` 本次提交的变更分支，不手写分支数组：

```yaml
with:
  baseBranch: "${SOURCE_BRANCH}"
  targetBranch: "${BRANCH_NAME}"
  branchesFromSubmit: true
  pushOnSuccess: true
```

## Approval + Build YAML

用于发布前先走人工审批，审批通过后再执行构建：

```yaml
sources:
  my_repo:
    type: gitlab
    name: "应用代码源"
    endpoint: https://example.com/group/repo.git
    branch: master
stages:
  approval_stage:
    name: "发布审批"
    jobs:
      approval_job:
        name: "人工审批"
        steps:
          approval_step:
            name: "发布审批"
            step: APPROVAL
            with:
              processDefinitionKey: devops_deploy_approval
  build_stage:
    name: "构建验证"
    jobs:
      maven_build_job:
        name: "Maven 构建"
        needs:
          - approval_job
        runsOn:
          group: local-docker/default
          container: alibaba-cloud-linux-3-registry.cn-hangzhou.cr.aliyuncs.com/alinux3/alinux3:220901.1
        steps:
          build_step:
            name: "安装 Maven 并构建"
            step: Command
            with:
              run: |
                if ! command -v java >/dev/null 2>&1; then
                  dnf install -y java-17-openjdk-devel
                fi
                if ! command -v mvn >/dev/null 2>&1; then
                  dnf install -y maven
                fi
                java -version
                mvn -version
                mvn -B clean package -DskipTests
```

## Private Registry Docker Build YAML

构建镜像并推送至自定义私有镜像仓库。当前仅支持 `certificate.type=usernamePassword`，不支持 `serviceConnection`。

```yaml
stages:
  build_stage:
    name: 镜像构建
    jobs:
      build_job:
        name: 构建任务
        steps:
          private_registry_docker_build:
            step: PrivateRegistryDockerBuild
            name: 镜像构建并推送至自定义镜像仓库
            with:
              artifact: my_image
              image: registry.cn-hangzhou.aliyuncs.com/ns/demo:1.0
              certificate:
                type: usernamePassword
                username: <your-registry-username>
                password: <your-registry-password>
              dockerfilePath: Dockerfile
              contextPath: .
              noCache: false
              variables:
                - key: PROFILE
                  value: prod
              buildkitVersion: v0.8.0
```

## Docker Image Export OSS YAML

从镜像仓库导出镜像归档，并上传到 S3 协议对象存储。该步骤是容器型 step，必须配置 `runsOn`，且执行镜像需要预置 `skopeo` 和 `mc`。

```yaml
stages:
  export_stage:
    name: 镜像导出
    jobs:
      export_job:
        name: 导出镜像并上传对象存储
        runsOn:
          group: local-docker/default
          container: gone-cloud/pipeline-builder:java17-node24-maven3.9
        steps:
          export_image:
            step: DockerImageExportObjectStorage
            name: 导出镜像到对象存储
            timeoutSeconds: 1800
            with:
              image: registry.cn-hangzhou.aliyuncs.com/ns/demo:1.0
              archiveFormat: oci-archive
              compression: none
              outputFileName: demo-1.0.oci.tar
              registryTlsVerify: true
              registryCertificate:
                type: usernamePassword
                username: <your-registry-username>
                password: <your-registry-password>
              storage:
                type: s3
                endpoint: https://oss-cn-hangzhou.aliyuncs.com
                path: s3://release-bucket/images/demo-1.0.oci.tar
                region: cn-hangzhou
                forcePathStyle: true
                certificate:
                  type: accessKey
                  accessKeyId: <your-access-key-id>
                  accessKeySecret: <your-access-key-secret>
              overwrite: false
```

## Docker Image Archive Import YAML

从前端上传得到的镜像包 URL 导入到目标镜像仓库。该步骤是容器型 step，必须配置 `runsOn`，且执行镜像需要预置 `curl`、`skopeo`、`gzip`、`zstd`。

```yaml
stages:
  import_stage:
    name: 镜像包导入
    jobs:
      import_job:
        name: 导入镜像包
        runsOn:
          group: local-docker/default
          container: gone-cloud/pipeline-builder:java17-node24-maven3.9
        steps:
          import_image:
            step: DockerImageArchiveImport
            name: 导入镜像包到仓库
            timeoutSeconds: 1800
            with:
              fileUrl: ${FILE_URL}
              image: registry.cn-hangzhou.aliyuncs.com/ns/demo:${runId}
              archiveFormat: oci-archive
              compression: none
              registryTlsVerify: true
              certificate:
                type: usernamePassword
                username: <your-registry-username>
                password: <your-registry-password>
```

## DingTalk Plugin YAML

```yaml
stages:
  notify_stage:
    name: 构建与通知
    jobs:
      build_job:
        name: 构建任务
        runsOn:
          group: local-docker/default
          container: gone-cloud/pipeline-builder:java17-node24-maven3.9
        steps:
          build_step:
            name: 执行构建
            step: Command
            with:
              run: mvn -B package
        plugins:
          - name: 钉钉机器人通知插件
            plugin: DingTalkPlugin
            triggerState: [success, fail]
            with:
              webhook: https://oapi.dingtalk.com/robot/send?access_token=<your-access-token>
              secret: <your-sign-secret>
              noticeContent:
                - pipelineName
                - operator
                - pipelineTask
                - pipelineStage
                - status
              customContent: 请及时关注流水线结果
              at:
                - "13800000001"
                - "13800000002"
```

钉钉插件发送 `text` 消息。`customContent` 中的 `${COMMIT_SHA}` 等文本保持字面值；动态标准字段只能通过 `noticeContent` 白名单选择。

## Validation Error Codes

| code | 含义 |
|---|---|
| `SPEC_REQUIRED` | YAML 内容为空。 |
| `SPEC_INVALID` | YAML/JSON 格式错误或无法解析。 |
| `SOURCE_COUNT_UNSUPPORTED` | 当前版本最多支持一个代码源。 |
| `SOURCE_TYPE_REQUIRED` | 代码源类型为空。 |
| `SOURCE_TYPE_UNSUPPORTED` | 当前版本仅支持 `gitlab` 代码源。 |
| `STAGE_REQUIRED` | 缺少 stage 或 stage 配置为空。 |
| `JOB_REQUIRED` | 缺少 job 或 job 配置为空。 |
| `STEP_REQUIRED` | 缺少 step 或 step 配置为空。 |
| `SOURCE_ID_INVALID` / `STAGE_ID_INVALID` / `JOB_ID_INVALID` / `STEP_ID_INVALID` | ID 格式错误。 |
| `JOB_ID_DUPLICATE` | jobId 全局重复。 |
| `STEP_ID_DUPLICATE` | stepId 全局重复。 |
| `RUNS_ON_GROUP_REQUIRED` | `runsOn.group` 为空。 |
| `RUNS_ON_GROUP_UNSUPPORTED` | 当前版本不支持该执行资源池。 |
| `RUNS_ON_CONTAINER_REQUIRED` | `runsOn.container` 为空。 |
| `STEP_TYPE_NOT_SUPPORTED` | 未注册的 step 类型。 |
| `STEP_TYPE_UNSUPPORTED` | 已识别但当前版本还不能执行的 step 类型。 |
| `PARAM_REQUIRED` | 必填参数缺失，例如 `Command.with.run`、`CodeMerge.with.baseBranch`、`CodeMerge.with.targetBranch`、`APPROVAL.with.processDefinitionKey`、K8s 部署参数、`PrivateRegistryDockerBuild.with.artifact/image/certificate/dockerfilePath`、`DockerImageExportObjectStorage` 的镜像/凭证/对象存储参数、`DockerImageArchiveImport.with.fileUrl/image/certificate`。 |
| `PARAM_VALUE_UNSUPPORTED` | 参数值当前版本不支持，例如 `K8sImageUpgrade.with.workloadKind` 不是 `Deployment`，或 `PrivateRegistryDockerBuild.with.certificate.type` 不是 `usernamePassword`，或 `DockerImageExportObjectStorage.with.archiveFormat/compression/storage.path`、`DockerImageArchiveImport.with.archiveFormat/compression/certificate.type` 不合法。 |
| `PARAM_ENV_KEY_INVALID` | 环境变量名格式错误。 |
| `PARAM_TYPE_INVALID` | 参数类型错误。 |
| `JOB_NEEDS_NOT_FOUND` | 依赖任务不存在。 |
| `JOB_NEEDS_SELF` | 任务依赖自身。 |
| `JOB_NEEDS_CYCLE` | 任务依赖存在循环。 |
| `FAIL_STRATEGY_UNSUPPORTED` | 当前版本不支持该失败策略。 |
| `PLUGIN_REQUIRED` / `PLUGIN_NAME_REQUIRED` / `PLUGIN_TYPE_REQUIRED` | 插件配置、名称或类型缺失。 |
| `PLUGIN_NOT_SUPPORTED` | 插件类型未注册。 |
| `PLUGIN_TRIGGER_STATE_REQUIRED` | 插件触发状态为空。 |
| `PLUGIN_TRIGGER_STATE_UNSUPPORTED` | 插件触发状态不是 `success/fail`。 |
| `PLUGIN_TRIGGER_STATE_DUPLICATE` | 插件触发状态重复。 |

## Current Limits

- 第一版执行 `Command`、`CodeMerge`、`APPROVAL`、`K8sDeploy`、`K8sImageUpgrade`、`PrivateRegistryDockerBuild`、`DockerImageExportObjectStorage` 和 `DockerImageArchiveImport` step。
- `SetupJava`、`SetupMavenSettings`、`UnitTestReport`、`ArtifactUpload`、`JavaP3CScan` 可作为后续扩展类型，但当前发布校验会拒绝执行。
- K8s 部署当前仅支持 Deployment，且使用应用环境绑定的 Kubernetes Namespace，不支持节点级 namespace 覆盖。
- 后端当前不会自动拉取 `runsOn.container` 镜像。
- YAML 参数变量替换当前仅支持 `${VAR}` 简单替换，不支持表达式、默认值或跨 run 变量。
- 配置 `sources` 后，后端会在本次运行的共享 run workspace 中自动 checkout 源码一次；未配置 `sources` 且 run 关联应用时，使用应用实体里的仓库配置 checkout；未关联应用时 workspace 为空目录。
- `diagramJson` 暂由前端自管，后端只保存，不参与 YAML 执行。
