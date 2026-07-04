# Pipeline 节点日志与动作契约优化设计

## Existing Boundaries

后端当前有三层运行状态：

- `dev_pipeline_run`: 整次运行聚合状态。
- `dev_pipeline_run_job`: DAG job 调度状态。
- `dev_pipeline_run_log`: step/event 明细，`GET /devops/pipeline-run/{runId}/logs` 返回该表转换后的 `PipelineRunLogRespVO`。

本次优化以 step log 为第一落点：

- 平台步骤日志继续写入 `dev_pipeline_run_log`，`runtimeType=PLATFORM`。
- 可操作点写入 `dev_pipeline_run_log.result_json.actions`。
- 前端如果以 job 卡片作为节点渲染单元，可以从该 job 下的 step log 聚合 actions；如果页面直接以 step 为卡片，则直接使用 step actions。

## Data Flow

```text
PipelineStepHandler / domain service
  -> PipelineStepLogHelper / PipelineStepAction helper
  -> dev_pipeline_run_log.context_json/result_json + step log file/line stream
  -> GET /devops/pipeline-run/{runId}/logs
  -> frontend generic action renderer
  -> route / download / API action dispatcher
```

## Platform Step Logs

新增轻量日志写入能力，供平台型步骤记录低频过程事件：

- `PipelineStepLogHelper.prepareStepLog(ctx)` 或扩展现有 `getOrCreateLog(ctx)` 后由调用方显式准备日志文件。
- 新增 `appendPlatformLine(runLog, level, message)`，内部复用 `PipelineStepLogWriter` / `PipelineRunLogLineService` 的发布能力。
- 平台步骤关键节点写日志：
  - `CodeMerge`: 准备工作区、开始合并分支、冲突文件数量、等待冲突解决、继续合并、推送完成。
  - `APPROVAL`: 创建审批单、流程实例 id、等待审批、审批通过/拒绝/取消。
  - `K8sDeploy/K8sImageUpgrade`: 连接集群、校验工作负载、应用资源、等待结果。
  - `DockerImageExportObjectStorage`: 已有执行输出，保持现状并补动作。

日志原则：

- 平台日志是人可读过程信息，不承载敏感参数。
- 高频命令输出继续走文件日志；平台日志只记录阶段性事件。
- `summary` 仍用于卡片摘要，日志行用于详情面板。

## Action Contract

统一动作字段放在 `PipelineRunLogRespVO.result.actions`：

```json
[
  {
    "key": "CODE_MERGE_RESOLVE_CONFLICT",
    "label": "解决冲突",
    "type": "ROUTE",
    "style": "PRIMARY",
    "permission": "devops:pipeline:update",
    "enabled": true,
    "disabledReason": null,
    "target": {
      "routeName": "DevopsPipelineCodeMergeConflict",
      "path": "/devops/pipeline-run/code-merge/conflicts",
      "params": { "runId": 161 },
      "query": { "stepId": "merge" }
    },
    "payload": {
      "runId": 161,
      "stepId": "merge",
      "conflictCount": 3
    }
  }
]
```

字段约定：

- `key`: 稳定动作编码，前端可用于埋点、排序或图标映射，但不能依赖它写业务判断。
- `label`: 按钮文案，由后端给出。
- `type`: `ROUTE`、`DOWNLOAD`、`API`、`EXTERNAL_LINK`。第一版推荐只实现 `ROUTE` 和 `DOWNLOAD`，`API` 仅用于已有受控接口且必须带确认文案。
- `style`: `PRIMARY`、`DEFAULT`、`DANGER`、`LINK`。
- `permission`: 前端可用于权限按钮指令；后端接口仍必须二次鉴权。
- `enabled/disabledReason`: 后端基于当前状态判断动作是否可用。
- `target`: 通用目标。`ROUTE` 使用 `routeName/path/params/query`；`DOWNLOAD` 使用 `url/fileName` 或 `apiPath/method`；`API` 使用 `method/path/body/confirmText`。
- `payload`: 展示辅助数据，不放敏感信息。

## Built-In Actions

### CodeMerge 解决冲突

生成条件：

- step log status = `WAITING_INPUT`。
- `CodeMergeContext.conflicts` 非空。

动作：

- `key=CODE_MERGE_RESOLVE_CONFLICT`
- `type=ROUTE`
- `permission=devops:pipeline:update`
- target 指向前端冲突解决页，携带 `runId`、`stepId`。
- payload 包含 `conflictCount`、`unsupportedCount`、`deployBranch`、`deployBranchPushed`、`currentBranchName`、`currentCommitSha`。发生冲突时后端会先把冲突前的部署分支 HEAD 推送到远端，`deployBranchPushed=true` 表示用户本地可 fetch/checkout 该部署分支；用户仍需在本地执行 `git merge <currentCommitSha>` 才能复现冲突工作区。`currentBranchName/currentCommitSha` 是当前触发冲突的变更分支和提交快照。

刷新规则：

- 保存部分冲突解决后动作保留。
- 全部解决并继续成功后动作移除。
- 存在不支持在线解决的冲突时动作仍可展示，但 `payload.unsupportedCount > 0`；是否禁用取决于产品选择，推荐不禁用，让用户进入详情看到原因。

### Approval 去审批

生成条件：

- approval step 首次创建 BPM process instance 后。
- step log status = `WAITING_INPUT`。
- `PipelineApprovalContext.processInstanceId` 非空。

动作：

- `key=APPROVAL_OPEN_PROCESS`
- `type=ROUTE`
- `permission=devops:pipeline:update` 或 BPM 审批页自身权限。
- target 指向 `BpmProcessInstanceDetail`，并通过 `query.id` 携带 `processInstanceId`；前端 BPM 详情页不读取 `params.processInstanceId` 或 `query.processInstanceId`。
- 第一版跳转到 BPM 流程实例/审批详情页；BPM 页面内部负责根据当前用户定位可办理任务。DevOps 动作契约不额外依赖 `taskId` 查询，避免流水线节点对 BPM 待办任务模型产生强耦合。

刷新规则：

- 审批通过、拒绝、取消后移除“去审批”，可选保留一个 `APPROVAL_VIEW_PROCESS` 查看审批记录动作。

### 镜像下载

生成条件：

- `DockerImageExportObjectStorage` 执行成功。
- `storagePath/outputFileName` 非空。

动作：

- `key=IMAGE_ARCHIVE_DOWNLOAD`
- `type=DOWNLOAD`
- `permission=devops:pipeline:query`
- target 使用后端下载接口或安全 URL。

推荐新增后端代理下载接口：

```http
GET /devops/pipeline-run/{runId}/steps/{stepId}/artifact-download?actionKey=IMAGE_ARCHIVE_DOWNLOAD
```

第一版如果对象存储已返回可直接访问 URL，也可以使用 `target.url`，但不得包含临时凭据以外的敏感信息。

## Backend API Shape

保持现有日志 API 不变：

```http
GET /devops/pipeline-run/{runId}/logs
```

返回示例：

```json
{
  "stepId": "approval",
  "status": "WAITING_INPUT",
  "summary": "等待审批：人工审批",
  "runtimeType": "PLATFORM",
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
          "query": { "id": "abc123" }
        }
      }
    ]
  }
}
```

建议新增 Java 模型：

- `PipelineStepAction`: action DTO/model。
- `PipelineStepActionTarget`: target DTO/model。
- `PipelineStepActionTypeEnum`: `ROUTE/DOWNLOAD/API/EXTERNAL_LINK`。
- `PipelineStepActionKeyConstants`: 内置 action key。
- `PipelineStepActionHelper`: 合并、替换、清理 `resultJson.actions`，避免各 handler 手写 JSON。

`StepResult` 可扩展：

```java
private List<PipelineStepAction> actions;
```

执行引擎在处理 `StepResult` 时把 `outputs` 和 `actions` 一起合并到 `resultJson`。对于 `APPROVAL` 这类在领域服务内部直接更新 log 的步骤，可以直接使用 `PipelineStepActionHelper` 写入。

## Frontend Rendering Contract

前端节点渲染单元只需要：

- 读取每个 step log 的 `result.actions || []`。
- 过滤 `enabled=false` 或显示禁用态。
- 按 `type` 分发：
  - `ROUTE`: `router.push(routeName/path + params/query)`。
  - `DOWNLOAD`: 打开 `url` 或调用 `apiPath` 下载。
  - `API`: 显示 `confirmText` 后调用指定 method/path，再刷新 run logs。
  - 未知类型隐藏。
- 如果一个 job 卡片包含多个 step，聚合该 job 下所有 enabled actions；排序优先级可由后端增加 `order` 字段，默认按 step 顺序。

前端不需要再写：

- `if stepType == APPROVAL then show 去审批`
- `if stepType == CodeMerge and status == WAITING_INPUT then show 解决冲突`
- `if result.storagePath then show 下载镜像`

## Compatibility

- 老数据没有 `result.actions`，前端按空数组处理。
- 不新增 `dev_pipeline_run_job` 字段，避免第一版动作能力扩散到调度表。
- `PipelineRunLogRespVO` 可不新增强类型字段，继续通过 `result` 暴露；如果前端类型生成需要，可补充 `actions` 字段但仍从 `resultJson` 映射。
- `nodeId/nodeType/nodeName` 是旧兼容字段，本次设计不新增依赖，后续继续使用 `stepId/stepType/stepName`。

## Risks

- 如果后端下发任意 API/path，前端会变成远程指令执行器。第一版必须限制 action type 和 target schema。
- 如果动作只在 handler 返回时写入，审批这类服务内直接更新日志的场景会漏写。需要统一 helper，允许 handler 和领域服务都能调用。
- 如果下载直接暴露对象存储地址，需要确认 URL 权限与有效期；推荐后端代理或签名 URL。
