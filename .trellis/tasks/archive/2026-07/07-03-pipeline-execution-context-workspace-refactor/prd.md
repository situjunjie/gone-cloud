# 流水线执行上下文与 workspace 重构

## Goal

重构 DevOps 流水线执行期上下文模型，让一次 `PipelineRun` 拥有统一的执行上下文和稳定的 run workspace。所有步骤处理器统一接收同一种上下文对象，平台步骤和容器步骤都能访问同一个 workspace；需要容器/执行器资源的步骤再通过上下文按需创建运行资源。

本任务要修正当前“workspace 创建依附于 job runtime”的实现耦合，避免 CodeMerge 合并后下游构建 workspace 仍可能回到应用默认分支（例如 `master`）的行为。

## Background / Evidence

- 当前 `PipelineStepContext` 是步骤级上下文，包含 `run/version/jobRun/job/step/workspace/sharedState/userId`，但 `workspace` 只有需要 job runtime 的步骤才会被填充：`yudao-module-devops/yudao-module-devops-server/src/main/java/cn/iocoder/yudao/module/devops/service/pipeline/execution/handler/PipelineStepContext.java:21`、`:46`、`:51`。
- 当前 `PipelineExecutionEngine` 在每次 `execute` 开始都会调用 `resetRunWorkspace(run)`，然后创建 `sharedState`：`yudao-module-devops/yudao-module-devops-server/src/main/java/cn/iocoder/yudao/module/devops/service/pipeline/execution/PipelineExecutionEngine.java:114`、`:115`。这对 resume/挂起恢复场景有风险，因为恢复不应清空已有冲突或合并工作区。
- 当前 workspace 创建发生在 `ensureJobRuntime` 内：`PipelineWorkspace workspace = pipelineWorkspaceService.createWorkspace(run, job, cacheConfig)`，随后才准备源码并创建 Docker runtime：`yudao-module-devops/yudao-module-devops-server/src/main/java/cn/iocoder/yudao/module/devops/service/pipeline/execution/PipelineExecutionEngine.java:285`、`:286`。平台步骤不走这条路径，因此默认没有 workspace。
- 当前 ready jobs 会并发执行，并共享同一个 `sharedState`：`yudao-module-devops/yudao-module-devops-server/src/main/java/cn/iocoder/yudao/module/devops/service/pipeline/execution/PipelineExecutionEngine.java:346`、`:356`。后续设计必须保证上下文统一但并发 job 的可变字段互相隔离。
- 当前 step outputs 直接 `sharedState.putAll(result.getOutputs())`：`yudao-module-devops/yudao-module-devops-server/src/main/java/cn/iocoder/yudao/module/devops/service/pipeline/execution/PipelineExecutionEngine.java:598`、`:602`。并发 job 下不同步骤输出可能覆盖同名 key，缺少命名空间和合并策略。
- 当前源码准备器只优先识别 `mergedBranch/mergedCommitSha`，没有把 `run.branchName` 或 sharedState 的 `branchName` 纳入应用上下文 checkout 优先级：`yudao-module-devops/yudao-module-devops-server/src/main/java/cn/iocoder/yudao/module/devops/service/pipeline/execution/GitlabPipelineSourceWorkspacePreparer.java:92`、`:93`、`:94`。
- 现有 `GitlabPipelineSourceWorkspacePreparerTest` 覆盖了无 sources 时 checkout 应用默认分支，以及 `mergedBranch` 优先；没有覆盖 CodeMerge 完成后只依赖 `run.branchName` 的恢复/重入场景：`yudao-module-devops/yudao-module-devops-server/src/test/java/cn/iocoder/yudao/module/devops/service/pipeline/execution/GitlabPipelineSourceWorkspacePreparerTest.java:61`、`:99`、`:174`。
- 已有任务 `06-25-pipeline-workspace-distributed-lock` 只处理 `LocalPipelineWorkspaceService.createWorkspace/resetRunWorkspace` 的分布式锁，不处理执行上下文建模。本任务应复用其“同一 workspace 路径互斥”的文件系统安全约束，不重复设计锁。
- 恢复入口会再次调用执行引擎：CodeMerge 成功后 `resumePipelineAsync(runId, userId)` 最终调用 `pipelineExecutionEngine.execute(run, userId)`；BPM 审批通过也直接调用 `pipelineExecutionEngine.execute(result.getRun(), null)`。因此 engine 内不能无条件 reset workspace：`yudao-module-devops/yudao-module-devops-server/src/main/java/cn/iocoder/yudao/module/devops/service/pipeline/execution/PipelineExecutionAsyncService.java:50`、`:65`，`yudao-module-devops/yudao-module-devops-server/src/main/java/cn/iocoder/yudao/module/devops/controller/admin/pipelinerun/PipelineRunController.java:151`。
- `StepResult.outputs` 目前只由 `PipelineExecutionEngine.mergeStepOutputs` 写入当次内存 `sharedState`，没有统一持久化 registry；恢复执行时 `sharedState` 会重新构建，已完成上游步骤的 outputs 不会自动恢复：`yudao-module-devops/yudao-module-devops-server/src/main/java/cn/iocoder/yudao/module/devops/service/pipeline/execution/handler/StepResult.java:36`，`yudao-module-devops/yudao-module-devops-server/src/main/java/cn/iocoder/yudao/module/devops/service/pipeline/execution/PipelineExecutionEngine.java:598`、`:602`。

## Decisions Already Agreed

- 对 handler 调用层统一传一个执行上下文对象，不继续把 `StepContext`、`JobRuntime` 等概念暴露给 handler 作为主要模型。
- workspace 是一次流水线运行级别的目录资源，平台步骤和容器步骤都应该能访问。
- 容器、进程、取消句柄、runtime id/name、executor image/group、mount 信息等执行器运行态仍然需要存在，但应作为统一执行上下文里的内部字段或子结构，而不是把 workspace 生命周期挂到它上面。
- 并发 job 不能共享同一个可变上下文实例；应共享 run-scope 状态，并为每个 job/step 派生独立上下文实例。
- 并发 job 完成后不合并整个 ctx，只发布带命名空间的 step outputs，后续 job 通过显式引用读取。
- 本任务不调整 YAML 用户引用语法；outputs registry 和命名空间合并策略先作为后端内部能力落地，用户可见表达式语法另起任务。
- CodeMerge 迁到 run workspace 时，复用并扩展现有 `GitWorkspaceService` 支持外部 run workspace path，不新增第二套完整 Git 操作接口。
- 本任务直接迁移 `PipelineStepHandler` 接口到新的 `PipelineExecutionContext`，不保留长期 `PipelineStepContext` 兼容层；新上下文提供现有常用 getter 和 runtime/output helper 来降低 handler 改动面。
- 本任务不新增 outputs 表；step outputs 先复用 `dev_pipeline_run_log.result_json.outputs` 持久化，并在恢复执行时从已成功步骤日志重建 run-scope outputs registry。

## Requirements

### R1. Unified Execution Context

- 引入统一的流水线执行上下文模型，例如 `PipelineExecutionContext`。
- 所有新/迁移后的 step handler 都只接收统一上下文对象。
- `PipelineStepHandler#handle/cancel` 应直接接收 `PipelineExecutionContext`；旧 `PipelineStepContext` 不作为长期兼容层保留。
- 上下文需表达 run scope、job scope、step scope，但调用层只传一个 `ctx`。
- 并发 job 必须使用从 run context 派生出来的独立 job/step context，避免 `currentJob/currentStep/executorRuntime` 互相覆盖。

### R2. Run-Level Workspace Lifecycle

- run workspace 必须在 `PipelineRun` 执行上下文初始化阶段创建/解析，而不是只在创建 job runtime 时创建。
- 平台步骤（例如 `CodeMerge`、`APPROVAL`、`K8sDeploy`、`PrivateRegistryDockerBuild`）和容器步骤都能通过统一上下文访问同一个 run workspace。
- workspace reset 只能发生在新 run 首次执行或明确重试需要清理的时机；恢复 `WAITING_INPUT/BLOCKED` 的 run 时不能清空 workspace。
- 保留并复用既有 `LocalPipelineWorkspaceService` 的路径计算、cache workspace、分布式锁和安全约束。

### R3. Runtime As Context Internal State

- 容器/执行器运行态保留为统一上下文的内部字段或子结构，例如 executor runtime。
- 只有需要容器/执行器资源的 step 才按需创建 executor runtime。
- 平台步骤不能为了拿 workspace 而创建 Docker runtime。
- job runtime 相关落库字段（runtime type/id/name、executor group/image、workspace path）仍需正确填充。

### R4. CodeMerge Uses Run Workspace

- `CodeMerge` 应能在 run workspace 内准备 Git 仓库、合并变更、处理冲突和推送部署分支。
- CodeMerge 成功后，下游构建直接复用同一 workspace 中的合并结果，不应重新 checkout 到应用默认分支。
- CodeMerge 冲突挂起后，保存冲突、继续合并、重试当前变更都应基于同一 run workspace 恢复。

### R5. Source Preparation Semantics

- 源码准备必须从“创建 job runtime 的副作用”中解耦。
- 同一次 run 内源码准备/合并状态必须可判定，避免并发 job 重复 checkout 或互相覆盖。
- 如果 CodeMerge 已经在 run workspace 产出部署分支，后续源码准备不能覆盖掉合并结果。
- 对没有 CodeMerge 的流水线，源码准备仍应使用应用仓库或 YAML `sources` 准备 workspace。

### R6. Outputs Registry And Merge Strategy

- 跨 job 传递的数据不得直接通过无命名空间 `sharedState.putAll` 合并。
- step outputs 必须按 jobId/stepId/key 命名空间发布，避免并发 job 同名 key 互相覆盖。
- outputs registry 必须能在恢复执行时重建，避免 WAITING_INPUT 后继续执行丢失已完成上游步骤的 outputs。
- outputs 持久化优先复用 `dev_pipeline_run_log.result_json.outputs`，不新增 `dev_pipeline_run_output` 表。
- 系统级状态（如 `branchName`、`commitSha`、source prepared flag）与用户/步骤 outputs 分离。
- 后续 job 的内部读取必须基于 jobId/stepId/key 这类明确坐标，不从模糊全局 key 猜测。

### R7. Compatibility And Migration

- 保持现有 YAML DAG、`needs`、并发 job、fail-fast、BLOCKED/resume、cancel、日志归档等行为不回退。
- 保持现有平台步骤可逐步迁移；必要时可先提供兼容适配层，但最终 handler 调用模型应收敛到统一上下文。
- 不引入新的外部基础设施依赖。

## Acceptance Criteria

- [ ] `PipelineExecutionEngine` 初始化一次 run 执行上下文时即可得到 run workspace；平台步骤和容器步骤在 handler 内都能访问同一路径。
- [ ] `CodeMerge` 在 run workspace 内完成合并；合并成功后下游 Docker build/command 看到的是部署分支合并后的代码，不是应用默认分支。
- [ ] BLOCKED/WAITING_INPUT 恢复执行不会调用会清空 run workspace 的逻辑；冲突恢复能继续使用挂起前的 Git 工作区。
- [ ] 并发 ready jobs 使用独立派生上下文，测试证明 A/B 并发不会互相覆盖 current job/step/runtime 字段。
- [ ] step outputs 采用命名空间发布；测试证明并发 job 写同名输出 key 不会互相覆盖，依赖它们的 job C 能读取明确引用的 A/B 输出。
- [ ] 需要容器的 step 仍按需创建 executor runtime，并正确落库 runtime 字段；平台 step 不创建 Docker runtime。
- [ ] 无 CodeMerge 的普通应用环境流水线仍能准备应用仓库源码并执行 Command / Docker build。
- [ ] 现有 workspace 分布式锁语义保持不变。
- [ ] 受影响服务和处理器有 focused 单测覆盖：执行上下文初始化、并发派生、CodeMerge workspace、resume 不 reset、outputs 合并、Docker runtime 懒创建。

## Out Of Scope

- 不改变 YAML 的 DAG 基本结构和 `needs` 语义。
- 不把所有平台步骤强制改成 Docker 容器执行。
- 不在本任务中引入多后端实例调度/worker lease 的完整能力。
- 不重新设计 `LocalPipelineWorkspaceService` 的分布式锁实现。
- 不改变 YAML 中用户引用上游 outputs 的语法。
- 不新增专门的 pipeline outputs 数据表。
