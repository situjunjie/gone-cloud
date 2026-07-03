# Implement: 流水线执行上下文与 workspace 重构

## Preconditions

- 先完成 PRD/design review。
- Phase 2 开始前读取 `trellis-before-dev` 和 backend/devops pipeline 相关 spec。
- Inline Codex workflow 不需要 `implement.jsonl/check.jsonl`。

## Implementation Checklist

### 1. Execution Context Model

- 新增 `PipelineExecutionContext`。
- 新增 `PipelineOutputRegistry`。
- 将 `PipelineVariableResolver` 改为接收 `PipelineExecutionContext`。
- 将 `PipelineStepHandler#handle/cancel` 参数从 `PipelineStepContext` 改为 `PipelineExecutionContext`。
- 删除或停用 `PipelineStepContext`，避免长期双上下文。

Validation:

- `PipelineVariableResolverTest`
- 编译暴露所有 handler 迁移点。

### 2. Workspace Lifecycle In Engine

- 调整 `PipelineWorkspaceService`，支持 run 级 workspace 初始化和 job 子目录准备。
- `PipelineExecutionEngine.execute` 中先创建 run ctx/workspace。
- 用“是否已有 job/log”判断首次执行，只在首次执行 reset workspace。
- `ensureJobRuntime` 改为使用 ctx.workspace，不再创建 workspace。
- `PipelineStepLogHelper` 从 ctx.workspace 填 `workspacePath`。

Validation:

- `PipelineExecutionEngineTest` 新增/调整：
  - 首次执行 reset workspace。
  - resume 执行不 reset workspace。
  - 平台 step handler 能拿到 workspace。
  - 并发 job ctx 独立。

### 3. Runtime As Context Internal State

- `CommandStepHandler` 使用 `ctx.getExecutorRuntime()`，不再从 sharedState 读 runtime。
- Docker image import/export 等需要 runtime 的 handler 同步迁移。
- runtime 创建后仍填充 `PipelineRunJobDO` runtime 字段。
- cancel 使用 ctx 中的 run/job 信息，不依赖 `PipelineStepContext`。

Validation:

- `CommandStepHandlerTest`
- `DockerImageArchiveImportStepHandlerTest`
- `DockerImageExportObjectStorageStepHandlerTest`
- `PipelineExecutionEngineTest` runtime 字段断言。

### 4. Outputs Registry And Persistence

- engine 成功处理 step 后，调用 `ctx.publishOutputs(result.getOutputs())`。
- 将 outputs merge 到当前 step log `resultJson.outputs`，保留 handler 已写入的 result metadata。
- run ctx 初始化时从 SUCCESS step logs 的 `resultJson.outputs` 重建 outputs registry。
- 不改 YAML 表达式语法。

Validation:

- 并发 A/B 同名 key 不覆盖。
- 恢复执行时能读取已成功步骤 outputs。
- handler 已写 `resultJson` 时 outputs merge 不丢字段。

### 5. GitWorkspaceService External Workspace

- 扩展 `GitWorkspaceService`/`GitWorkspaceServiceImpl` 支持 external run workspace path。
- 复用 merge/conflict/read/continue/abort/push 逻辑。
- external workspace cleanup 不删除 run workspace。
- prepare external workspace 使用 in-place git init/fetch/checkout，不能要求空目录。

Validation:

- `GitWorkspaceServiceImplTest`：
  - external workspace 不 clone 到随机 root。
  - external cleanup 不删除目录。
  - external prepare fetch/checkout 逻辑正确。
  - managed workspace 旧行为不回退。

### 6. CodeMerge On Run Workspace

- `CodeMergeStepHandler`/`CodeMergeService` 使用 ctx.workspace 或 external workspace handle。
- CodeMerge 成功后标记 ctx source prepared by CodeMerge。
- CodeMerge 成功不 cleanup run workspace。
- 冲突读取/保存/继续/重试都使用稳定 external workspace key。
- 更新 tests 中 `gitWorkspaceService.prepareWorkspace` mock 和 cleanup 断言。

Validation:

- `CodeMergeStepHandlerTest`
- `PipelineExecutionServiceImplTest`
- 新增 CodeMerge 成功后下游 source preparation 不覆盖 workspace 的 engine 测试。

### 7. Source Preparation Consolidation

- 把 source preparation 从 job runtime creation 和 `PrivateRegistryDockerBuildStepHandler.prepareWorkspace` 中收敛到 ctx/engine helper。
- 无 CodeMerge 流程仍能 checkout 应用仓库或 YAML source。
- CodeMerge 已准备 workspace 后，下游 Command/Docker build 不再 checkout default branch。
- 修复 fallback 优先级：`mergedBranch -> systemState.branchName -> run.branchName -> application.defaultBranchName -> source.branch`，作为兼容保护。

Validation:

- `GitlabPipelineSourceWorkspacePreparerTest`
- `PrivateRegistryDockerBuildStepHandlerTest`
- `PipelineExecutionEngineTest`

### 8. Final Sweep

- 删除不再使用的 `PipelineStepContext` imports/usages。
- 更新 `PIPELINE_DOCKER_RUNTIME_DESIGN.md` 或 `PIPELINE_YAML_SPEC.md` 仅在契约变化需要时进行；本任务不改 YAML 语法，优先避免文档 churn。
- 更新 `.trellis/spec/backend/devops-pipeline-guidelines.md` 若本次形成新的稳定约定。

## Validation Commands

Focused:

```bash
mvn -pl yudao-module-devops/yudao-module-devops-server -am \
  -Dtest='PipelineExecutionEngineTest,GitWorkspaceServiceImplTest,GitlabPipelineSourceWorkspacePreparerTest,PipelineExecutionServiceImplTest,CodeMergeStepHandlerTest,CommandStepHandlerTest,PrivateRegistryDockerBuildStepHandlerTest,DockerImageArchiveImportStepHandlerTest,DockerImageExportObjectStorageStepHandlerTest,PipelineVariableResolverTest' \
  -Dsurefire.failIfNoSpecifiedTests=false test
```

Broader module check:

```bash
mvn -pl yudao-module-devops/yudao-module-devops-server -am -Dsurefire.failIfNoSpecifiedTests=false test
```

## Rollback Points

- Context interface migration touches many handlers; keep changes in one coherent commit if possible.
- GitWorkspace external path can be reverted independently if CodeMerge migration is not complete.
- Outputs persistence is isolated around engine + resultJson merge and can be disabled by reverting registry publication.

## Review Gates

- No handler stores runtime in `sharedState`.
- No `sharedState.putAll(result.getOutputs())`.
- `resetRunWorkspace` is not called on resume/re-entry.
- Platform steps can access ctx workspace.
- CodeMerge no longer uses a separate random git workspace for application release pipeline execution.
- YAML syntax remains unchanged.
