# Pipeline 节点日志与动作契约优化

## Goal

优化 DevOps 流水线运行页的节点信息表达能力：

- 平台型步骤也能提供可查看的日志输出，而不是只展示一句状态摘要。
- 步骤处理器在执行过程中或执行完成后，可以把当前节点可执行的操作下发给前端节点渲染单元，前端按通用契约渲染按钮并执行跳转、下载或调用接口，不再为每种步骤写死业务判断。

## Background

- 当前流水线运行页展示来自后端运行日志，后端入口为 `GET /devops/pipeline-run/{runId}/logs`。
- `PipelineRunLogRespVO.result` 已经暴露 `dev_pipeline_run_log.result_json`，可承载扩展字段。
- `PipelineStepLogHelper` 已能为平台型步骤创建 `runtimeType=PLATFORM` 的步骤日志。
- `Command`、镜像导出等执行型步骤已有行级日志能力；平台型步骤如 `CodeMerge`、`APPROVAL`、K8s 部署等通常只写状态摘要，缺少统一的结构化过程日志。
- 现有 `StepResult` 只包含 `type/summary/error/outputs`，不能统一表达前端可操作点。
- 审批节点已经在 `contextJson` 中记录 `processInstanceId/businessKey`，代码合并冲突已有冲突解决 API，镜像导出对象存储节点已有 `storagePath/outputFileName` 等结果数据。

## Requirements

- R1: 平台型步骤必须能写入步骤日志。
  - 至少支持开始、关键过程、等待外部处理、成功、失败、取消等事件。
  - 日志应继续归属于具体 `pipelineRunId + stepId`，前端仍通过现有 step 级日志能力查看。
  - 低频平台事件可以写入同一套行级日志/文件日志，不应只依赖 `summary`。

- R2: 后端提供统一的节点动作契约。
  - 动作由步骤处理器或步骤相关领域服务生成并写入运行日志结果。
  - 前端只识别通用动作类型和通用目标，不根据 `stepType` 写死业务按钮。
  - 动作至少覆盖：代码合并冲突的“解决冲突”、审批节点的“去审批”、镜像导出对象存储完成后的“下载镜像”。

- R3: 动作必须可控、可验证、可降级。
  - 后端只下发当前状态真正可用的动作。
  - 动作应包含权限标识、禁用原因或状态提示，避免前端猜测。
  - 前端遇到未知动作类型或未知动作 key 时必须隐藏或降级，不影响节点状态展示。

- R4: 保持现有 API 和存量数据兼容。
  - 不破坏 `GET /devops/pipeline-run/{runId}/logs` 现有字段。
  - 优先复用 `resultJson` 承载 `actions`，避免为了第一版动作能力新增数据库列。
  - 旧运行没有 `actions` 时前端不显示动作按钮。

- R5: 安全边界清晰。
  - 动作不能携带密码、token、对象存储密钥等敏感值。
  - 下载动作应优先指向后端受权限保护的下载接口或已授权的文件 URL，不把存储凭据交给前端。
  - API 动作必须显式声明 method/path/confirm/permission，前端不得执行任意后端未定义的脚本。

## Decisions

- D1: 审批节点的“去审批”动作第一版携带 `processInstanceId`，跳转到 BPM 流程实例/审批详情页；是否存在当前用户待办任务由 BPM 页面内部处理，不在 DevOps 流水线节点动作中额外查询 `taskId`。

## Acceptance Criteria

- [ ] `CodeMerge` 冲突挂起时，对应步骤日志 `result.actions` 包含“解决冲突”动作；冲突解决完成后该动作消失或变为禁用。
- [ ] `APPROVAL` 创建 BPM 审批单并进入等待状态后，对应步骤日志 `result.actions` 包含“去审批”动作，目标携带 `processInstanceId` 并跳转 BPM 流程实例/审批详情页。
- [ ] 镜像导出并上传对象存储成功后，对应步骤日志 `result.actions` 包含“下载镜像”动作，目标不暴露存储密钥。
- [ ] 平台型步骤执行期间能产生可查看的步骤日志行，前端选择该步骤后能看到关键过程，而不是只有节点卡片摘要。
- [ ] 旧流水线运行无 `actions` 字段时，运行页继续正常展示。
- [ ] 后端单元测试覆盖动作生成、状态变更后动作刷新、平台步骤日志写入以及敏感字段不进入动作 payload。

## Out of Scope

- 不重构流水线 YAML DSL。
- 不在第一版引入用户自定义动作脚本。
- 不要求前端支持任意后端 URL 执行；第一版仅支持受控动作类型。
- 不把所有历史平台步骤日志补写到旧运行。
