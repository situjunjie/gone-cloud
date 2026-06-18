# fix pipeline source workspace clone failure

## Goal

修复流水线启动时源码签出失败的问题。当前两级 workspace 实现会在 run workspace 下预先创建 `artifacts`、`reports`、`tmp`、`jobs/...` 等目录，而 GitLab 源码准备器仍然假设目标目录必须为空并执行 `git clone ... .`，导致 Git 拒绝签出。目标是在不破坏现有 `/workspace` 工作目录契约的前提下，让源码准备逻辑能在已存在的 run workspace 中稳定完成首次签出，并在同一次 run 中复用已有源码。

## What I already know

* 报错发生在 `GitlabPipelineSourceWorkspacePreparer.prepare`，异常文本为 `fatal: destination path '.' already exists and is not an empty directory.`。
* `PipelineExecutionEngine.prepareSourceOnce(...)` 会在首次需要 `JOB_RUNTIME` 时调用源码准备逻辑，并通过 `sourceWorkspacePrepared:{runId}` 保证同一次 run 只准备一次。
* `LocalPipelineWorkspaceService#createWorkspace(...)` 会先创建 run workspace 以及 `artifacts`、`reports`、`tmp`、`jobs/{jobId}/...` 等子目录，因此 run workspace 在签出前天然不是空目录。
* 当前实现检测 `.git` 是否存在；不存在时直接在 `workspace` 根目录执行 `git clone --depth 1 --branch <branch> <repo> .`。
* 既有两级 workspace PRD 明确记录了 MVP 约束：保持容器工作目录仍为 `/workspace`，避免要求用户把脚本工作目录切到 `/workspace/source`。
* 现有单测 `GitlabPipelineSourceWorkspacePreparerTest` 仍然断言 clone 目标为 `.`，需要与修复后的行为同步。

## Assumptions (temporary)

* 本次只修复“已存在非空 run workspace 导致首次签出失败”的问题，不调整容器工作目录，也不引入新的前端/配置契约。
* 同一次 run 内后续 job 继续复用第一次准备好的源码目录，不重复 clone。
* run workspace 根目录中已有的平台子目录（如 `artifacts`、`reports`、`tmp`、`jobs`）必须被保留，不应在源码准备前整体删除。

## Open Questions

* 无阻塞问题。按现有 workspace 设计，优先采用“在已有目录中初始化/拉取仓库”而不是切换到 `/workspace/source`。

## Requirements

* `GitlabPipelineSourceWorkspacePreparer` 首次准备源码时，不能再依赖目标目录为空。
* 修复后仍要保持源码位于当前 run workspace 的工作根语义下，避免破坏现有 command step 默认在 `/workspace` 执行的契约。
* 如果 run workspace 已有 `.git`，源码准备逻辑应继续视为已完成，不重复 clone。
* 错误处理继续走现有 `IllegalStateException("Prepare source workspace failed: ...")` 包装，并保留 token 脱敏。
* 为修复新增/更新聚焦单测，覆盖“非空 workspace 也能成功准备源码”的命令序列。

## Acceptance Criteria

* [ ] run workspace 预先存在 `artifacts`、`reports` 等平台目录时，源码准备不会因为目标目录非空而失败。
* [ ] 同一次 run 后续 job 再次进入 `prepareSourceOnce(...)` 不会重复执行 clone/checkout。
* [ ] `GitlabPipelineSourceWorkspacePreparerTest` 覆盖新的签出命令路径并通过。
* [ ] DevOps 模块相关聚焦测试通过。

## Definition of Done

* 仅修改必要的源码准备逻辑和对应测试。
* 保持 `/workspace` 兼容，不引入新的运行时配置。
* 运行聚焦单测验证行为。

## Out of Scope (explicit)

* 不把源码 checkout 目录迁移到 `/workspace/source`。
* 不在本任务中调整 Docker runtime 的工作目录或挂载约定。
* 不处理跨 run 的源码复用，run workspace 仍然按 runId 隔离。

## Technical Notes

* 相关实现：
  * `yudao-module-devops/.../GitlabPipelineSourceWorkspacePreparer.java`
  * `yudao-module-devops/.../PipelineExecutionEngine.java`
  * `yudao-module-devops/.../LocalPipelineWorkspaceService.java`
* 相关 spec：
  * `.trellis/spec/backend/devops-pipeline-guidelines.md`
  * `.trellis/spec/backend/devops-repository-guidelines.md`
  * `.trellis/spec/backend/quality-guidelines.md`
* 参考任务：
  * `.trellis/tasks/06-18-pipeline-two-level-workspace/prd.md`
