# Design: 流水线执行上下文与 workspace 重构

## Overview

本设计把流水线执行期模型收敛为一个统一的 `PipelineExecutionContext`。handler 调用层只传一个 `ctx`；ctx 内部区分 run scope、job scope、step scope，避免并发 job 共享可变字段。

核心边界：

- run workspace 是 `PipelineRun` 级目录资源，执行上下文初始化时创建/解析。
- executor runtime 是按 job/step 需要懒创建的执行资源，作为 ctx 内部字段存在，不拥有 workspace 生命周期。
- CodeMerge 使用 run workspace 做 Git 合并，下游构建复用同一目录。
- step outputs 写入命名空间 registry，并持久化到 step log 的 `resultJson.outputs`，恢复时重建。

## Current Problems

1. `PipelineWorkspaceService.createWorkspace(...)` 当前只在 `PipelineExecutionEngine.ensureJobRuntime(...)` 中调用；平台步骤不需要 Docker runtime，因此默认没有 workspace。
2. `PipelineExecutionEngine.execute(...)` 每次开始都调用 `resetRunWorkspace(run)`，但 CodeMerge/BPM approval 恢复也会再次进入 `execute(...)`，可能清空冲突或合并状态。
3. `CommandStepHandler` 等 handler 通过 `ctx.getSharedState().get(CommandStepHandler.runtimeKey(ctx))` 读取 runtime，runtime 被塞进全局共享 Map。
4. `mergeStepOutputs(...)` 直接 `sharedState.putAll(outputs)`，并发 job 同名输出会覆盖，且恢复执行时 outputs 不可重建。
5. `GitWorkspaceServiceImpl` 当前只管理自己的随机 git workspace，并在 cleanup 时删除目录；CodeMerge 与 pipeline run workspace 是两套目录。

## Target Model

### PipelineExecutionContext

新增统一上下文类型，建议包路径：

`cn.iocoder.yudao.module.devops.service.pipeline.execution.PipelineExecutionContext`

字段分层：

```java
class PipelineExecutionContext {
    // Run scope: 共享且线程安全
    private PipelineRunDO run;
    private PipelineDefinitionVersionDO version;
    private PipelineSpec spec;
    private PipelineWorkspace workspace;
    private PipelineCacheConfig cacheConfig;
    private ConcurrentMap<String, Object> systemState;
    private PipelineOutputRegistry outputs;
    private Object sourcePrepareLock;
    private boolean sourcePrepared;
    private boolean sourcePreparedByCodeMerge;
    private Long userId;

    // Job scope: 每个 job 派生独立实例
    private PipelineSpec.ExecutableJob job;
    private PipelineRunJobDO jobRun;
    private PipelineJobRuntime executorRuntime;

    // Step scope: 每个 step 派生独立实例
    private PipelineSpec.ExecutableStep step;
}
```

实现建议：

- 使用 `forJob(job, jobRun)` 从 run ctx 派生 job ctx。
- 使用 `forStep(step)` 从 job ctx 派生 step ctx。
- 派生 ctx 共享 run-scope 对象，但 job/step/runtime 字段独立。
- handler 只接收 `PipelineExecutionContext`。
- 新 ctx 保留便捷 getter：`getRun/getVersion/getJobRun/getJob/getStep/getWorkspacePath/getSharedState/getResolvedWith/getResolvedStep`，降低 handler 修改成本。
- `getSharedState()` 可作为 `systemState` 的兼容命名，但不得再用于发布 step outputs 或存放 executor runtime。

### Handler Interface

直接迁移：

```java
StepResult handle(PipelineExecutionContext ctx);
default void cancel(PipelineExecutionContext ctx) {}
```

不保留长期 `PipelineStepContext` 兼容层。`PipelineVariableResolver` 改为接收 `PipelineExecutionContext`。

### Executor Runtime

保留现有 `PipelineJobRuntime` 数据结构，但只作为 ctx 内部执行资源：

- `ctx.getExecutorRuntime()`：返回 nullable runtime。
- `ctx.requireExecutorRuntime()`：由 engine 或 ctx helper 懒创建；非 runtime step 不调用。
- 不再通过 `sharedState` 的 `jobRuntime:{jobId}` 存取 runtime。

`PipelineJobRuntimeManager.createRuntime(run, job, workspace)` 继续接收 run-level `PipelineWorkspace`，只负责创建容器和挂载 workspace。

## Workspace Lifecycle

### Creation

`PipelineExecutionEngine.execute(...)` 开始时初始化 run ctx：

1. 读取 version/spec。
2. 初始化/加载 job records。
3. 创建或解析 run-level `PipelineWorkspace`。
4. 按首次执行判断决定是否 reset run workspace。
5. 构建 `systemState` 和 outputs registry。

`PipelineWorkspaceService` 需要拆分/补充 API：

```java
PipelineWorkspace createWorkspace(PipelineRunDO run, PipelineCacheConfig cacheConfig);
PipelineWorkspace createWorkspace(PipelineRunDO run, PipelineSpec.ExecutableJob job, PipelineCacheConfig cacheConfig); // 可保留兼容或内部复用
```

原 job 参数只用于创建 `jobs/{jobId}` 子目录。新设计中 run ctx 初始化创建通用目录；执行具体 job 前再确保 `jobs/{jobId}/tmp|reports|artifacts` 存在。

### Reset Policy

不能用 `runStatus` 判断首次执行，因为应用发布创建 run 时已设置 `RUNNING`。

推荐判断：

- `firstExecution = no existing PipelineRunJobDO for runId && no existing PipelineRunLogDO for runId`
- 只有 `firstExecution` 时调用 `resetRunWorkspace(run)`。
- resume、approval callback、CodeMerge continue 后重入 engine 时已有 job/log，不 reset。

如果后续实现 job retry，需要单独定义 retry 的 workspace 策略；本任务不扩大做 job retry 语义。

### Source Preparation

`PipelineExecutionContext` 持有 source preparation 状态：

- `sourcePrepared`
- `sourcePreparedByCodeMerge`
- `sourcePrepareLock`

规则：

- 无 CodeMerge 且第一个需要源码的 step 执行前，准备应用/YAML source 到 run workspace。
- CodeMerge 成功后标记 `sourcePrepared=true`、`sourcePreparedByCodeMerge=true`，下游不再 checkout 覆盖合并结果。
- 并发 jobs 同时需要源码时，在 run ctx 锁内只准备一次。
- `PrivateRegistryDockerBuildStepHandler` 不再单独 `createWorkspace/prepare`，直接使用 ctx.workspace 并调用统一 source preparation helper。

## CodeMerge On Run Workspace

### GitWorkspaceService Extension

复用并扩展现有 `GitWorkspaceService`，支持 external workspace：

```java
GitWorkspacePrepareResult prepareWorkspace(Path workspace, String workspaceKey,
                                           String repoUrl, String accessToken,
                                           String baseBranch, String deployBranch,
                                           boolean managed);
```

或引入 handle 类型：

```java
GitWorkspaceHandle managed(String workspaceKey);
GitWorkspaceHandle external(String workspaceKey, Path path);
```

关键要求：

- managed workspace 仍由 `GitWorkspaceServiceImpl` 在 `git-workspace-root` 下创建并 cleanup 删除。
- external workspace 使用 pipeline run workspace path，`cleanup(workspaceKey)` 不删除 run workspace。
- merge、continueMerge、abortMerge、readConflictContent、pushDeployBranch 复用同一套 Git 命令逻辑。
- external workspace 必须使用 `git init/fetch/checkout` 或可复用的 in-place prepare；不能 `git clone ... .` 假设空目录，因为 run workspace 有平台目录。

### CodeMergeService Changes

`CodeMergeStepHandler` 调用 `CodeMergeService.executeCodeMergeStep(...)` 时传入 ctx 或 workspace path。

`CodeMergeContext.workspaceKey` 对 external workspace 使用稳定 key，例如：

```text
pipeline-run-{runId}
```

继续/冲突读取接口通过 `workspaceKey` 找到同一 run workspace。由于 external workspace 不由 GitWorkspaceService cleanup 删除，CodeMerge 成功后不得清理 run workspace，只能清理临时 Git 状态（如需要）。

## Outputs Registry

### In-Memory Shape

新增 `PipelineOutputRegistry`：

```java
class PipelineOutputRegistry {
    void publish(String jobId, String stepId, Map<String, Object> outputs);
    Object get(String jobId, String stepId, String key);
    Map<String, Object> flattenForVariables(); // 仅系统内部使用，不改变 YAML 语法
}
```

内部结构：

```json
{
  "jobId": {
    "stepId": {
      "key": "value"
    }
  }
}
```

合并规则：

- 不合并整个 ctx。
- 不将 step outputs 直接 putAll 到 `systemState`。
- 同一个 step 内相同 key 后写覆盖前写。
- 不同 job/step 天然隔离。
- 本任务不改变用户 YAML 引用语法；变量解析先保持现有简单 `${VAR}` 能力。

### Persistence

不新增 outputs 表。

engine 在 step 成功后将 `StepResult.outputs` 写入当前 step log 的 `resultJson.outputs`。如果 handler 已写 `resultJson`，engine 需要 merge JSON 字段而不是覆盖。

恢复执行时：

1. 查询 run 下 SUCCESS step logs。
2. 解析 `resultJson.outputs`。
3. 按 `jobId/stepId/key` 重建 `PipelineOutputRegistry`。

## Execution Flow

```text
execute(run)
  -> load version/spec
  -> graph = spec.toExecutableGraph()
  -> existingJobs/logs = load
  -> initializeJobs(...)
  -> workspace = workspaceService.createWorkspace(run, cacheConfig)
  -> if firstExecution reset workspace
  -> runCtx = PipelineExecutionContext.create(...)
  -> runCtx.outputs.restoreFromSuccessLogs(...)
  -> loop ready jobs
       -> for each ready job in parallel:
            jobCtx = runCtx.forJob(job, jobRun)
            executeJob(jobCtx)
              -> for step:
                   stepCtx = jobCtx.forStep(step)
                   if handler needs runtime:
                       stepCtx.ensureExecutorRuntime()
                       stepCtx.ensureSourcePreparedIfNeeded()
                   result = handler.handle(stepCtx)
                   if CONTINUE:
                       publish outputs to registry
                       merge outputs into run_log.resultJson.outputs
```

## Compatibility

- Existing `PipelineJobRuntime` class can remain, but no longer appears as the primary handler-facing concept.
- Existing `PipelineWorkspace` class remains the run workspace/cache carrier.
- Existing `StepRuntimeRequirement` can remain to let engine decide when to create executor runtime.
- Existing logs and job runtime DB fields remain.
- Existing YAML syntax remains unchanged.

## Risks

- CodeMerge external workspace must not delete run workspace on cleanup.
- Resume must not reset workspace. Tests must cover CodeMerge conflict and BPM approval re-entry.
- Parallel jobs share one source directory; destructive commands can still interfere. Existing contract requires job-private output under `jobs/{jobId}/...`; this task does not solve arbitrary user script conflicts.
- Persisting outputs in `resultJson` requires careful merge so handler-written result metadata is not overwritten.
