# DevOps 发布页流水线拓扑改为 job 粒度

## Goal

发布页主流程拓扑由后端直接返回 YAML job 粒度节点，让前端继续按 `nodes` 一项一卡片渲染，但卡片数量、标题和连线语义都对应 YAML jobs，而不是 execution steps。

用户价值：
- 发布页主流程表达产品上的流水线任务，而不是内部执行步骤。
- 前端不需要解析 YAML，也不需要从 step 日志反推 job。
- `needs` 的并行 DAG 语义在发布页接口中保持真实，不被展示顺序错误串成线性流程。

## Confirmed Facts

- `GET /devops/application/release/env-detail` 返回 `pipeline.nodes`，由 `ApplicationServiceImpl#getApplicationReleaseEnvDetail` 调用 `buildReleasePipeline` 构造。
- `GET /devops/application/release/current-run` 返回 `nodes`，由 `ApplicationServiceImpl#getApplicationReleaseCurrentRun` 调用 `buildReleasePipeline` 后再调用 `buildCurrentRunNodes` 构造。
- 当前 `buildReleasePipeline` 使用 `pipelineSpecValidationService.sortExecutableSteps(spec)` 取得 YAML steps 的扁平列表，再用 `buildReleasePipelineNodes(executableSteps)` 构造一级 nodes。
- 当前 `buildReleasePipelineEdges(executableSteps)` 按 step 列表顺序生成线性 edges，不能表达 `job.needs` 的 DAG。
- `PipelineSpec` 已有 `ExecutableGraph` / `ExecutableJob` / `ExecutableStep` 两层模型，可从 `spec.toExecutableGraph()` 取得 jobs 和每个 job 下的 steps。
- 运行引擎已使用 job DAG：`PipelineExecutionEngine` 从 `spec.toExecutableGraph()` 初始化 `dev_pipeline_run_job`，并按 `ExecutableJob.needs` 判断依赖成功后调度。
- `dev_pipeline_run_job` 已持久化 job 状态、stageId/stageName/jobId/jobName/needsJson/sort/startedAt/finishedAt/durationMillis/summary/errorMessage。
- `dev_pipeline_run_log` 仍是 step 级执行明细，包含 stage/job/step 字段，可用于填充 `node.steps`。

## Requirements

- `env-detail.pipeline.nodes` 必须返回 YAML jobs 级节点，保持字段名 `nodes` 不变。
- `current-run.nodes` 必须返回 YAML jobs 级节点，粒度与 `env-detail.pipeline.nodes` 一致。
- 每个一级 node 对应一个 YAML job：
  - `nodeId` 使用 `jobId`。
  - `type` 返回 `JOB`。
  - 新增 `nodeType` 返回 `JOB`。
  - `name` 优先使用 `job.name`。
  - 新增 `stageId`、`stageName`、`jobId`。
  - 保持 `nodeId/name/type/displayOrder/status/executionStatus/summary/errorMessage/durationMillis/detailType/detailRef/actions` 等当前前端已用字段兼容。
- job 下的 YAML steps 保留在 `node.steps` 子数组中；step 不再作为发布页一级 node 返回。
- `env-detail.pipeline.edges` 和 `current-run.edges` 都应返回 job DAG edges，来源是 `job.needs`：
  - 每个 `needs` 生成一条 `{source: upstreamJobId, target: currentJobId}`。
  - 多入边、多出边、多个起始节点都必须保留。
  - 不得用 `displayOrder` 或 YAML 声明顺序伪造线性 edges。
- `displayOrder` 仅用于稳定排序，不表达依赖。
- current-run 运行态应优先使用 job 级运行状态；step 级日志、错误、输出作为 job 内部明细保留。
- 点击 job 节点时，`detailRef` 应能定位到该 job 下所有 step 明细，默认提供 `runId` + `jobId`。如果保留旧 `runLogId/nodeId` 字段，不能破坏新 job 语义。
- 当没有运行态时，`current-run` 返回静态 job nodes，状态为 `PENDING` / `NOT_STARTED`。
- 发布页接口层不能把已有 job/step 两层模型展平成 step。

## Acceptance Criteria

- [ ] 一个 job 包含多个 steps 时，`env-detail.pipeline.nodes` 一级只返回一个 job node。
- [ ] 一个 job 包含多个 steps 时，`current-run.nodes` 一级只返回一个 job node。
- [ ] step 不再作为发布页一级 node 返回。
- [ ] 一级 node 标题使用 `job.name`，不是 `step.name`。
- [ ] `node.type` 和 `node.nodeType` 明确返回 `JOB`。
- [ ] `node.steps` 包含该 job 下的 step 明细。
- [ ] 单个 `needs` 生成一条 edge。
- [ ] 多个 `needs` 生成多条入边。
- [ ] 多个 job 依赖同一个上游 job 生成多条出边。
- [ ] 两个没有 `needs` 的 job 都是起始节点，不被串成前后关系。
- [ ] `displayOrder` 改变不影响 edges 依赖语义。
- [ ] 两个互不依赖 job 可以同时以 `RUNNING` 状态返回。
- [ ] step failed 时，job 状态和 `errorMessage` 正确聚合或使用 job run 状态正确反映失败。
- [ ] `current-run` 和 `env-detail` 节点粒度一致。
- [ ] 发布页主流程卡片数量等于 YAML job 数量，而不是 step 数量。
- [ ] 现有前端依赖的兼容字段仍存在。

## Out Of Scope

- 前端解析 YAML。
- 新增完整 job 日志持久化层级表结构；当前优先复用 `dev_pipeline_run_job` + step logs。
- 实现新的 `granularity=JOB/STEP` 参数，除非兼容现有调用时发现必须保留旧 step 粒度入口。

## Scope Update: 执行引擎 ready job 并行

用户用两个 job 同时 `needs: code_merge_job` 验证时发现运行态仍然串行。排查确认 YAML 和 `needs` 语义正确，但 `PipelineExecutionEngine#execute` 在同一轮 ready jobs 中同步逐个调用 `executeJob`，导致并行 DAG 在执行层被串行化。

本任务追加一个小范围执行引擎修复：
- 同一轮所有依赖已满足的 `PENDING` jobs 作为 ready 批次并发执行。
- 下一层 DAG 仍等待当前 ready 批次结束后再重新加载 job 状态推进。
- 不把 `displayOrder` 或声明顺序作为隐式依赖。
- run 级 source workspace 准备保持一次性，并发场景下加锁防止重复 checkout。
- 使用独立 job 执行线程池，避免复用外层 run 启动线程池造成父任务等待子任务时互相占满。

## Open Questions

- 无阻塞问题。用户已明确发布页默认期望 job 粒度，仓库证据也支持复用现有 job/step 两层模型。
