# 基线分支新提交触发环境重新部署

## Goal

当应用默认基线分支有新提交时，DevOps 发布页能提示当前环境部署分支已落后，并且用户重新提交变更触发流水线后，后端会按最新基线重新生成部署分支。

部署分支的产品契约：部署分支是“当前基线提交 + 当前环境目标变更集合”的构建产物；当基线提交变化或目标变更集合减少时必须重建部署分支，只有基线不变且目标集合不减少时才允许复用原部署分支做增量合并。

## Confirmed Facts

- 当前变更分支新提交通过 GitLab push hook 写入 `ChangeDO.latestCommitSha`，发布页通过当前 run 的 `changeSnapshotJson` 与变更最新提交对比来提示重新部署。
- 当前代码合并准备阶段 `GitWorkspacePrepareResult.baseRemoteCommitSha` 已能拿到远端基线分支最新提交。
- 简单版只支持应用默认分支作为基线状态来源，字段统一使用 `base` 命名。
- 当基线分支有新提交时，不能复用旧部署分支继续 merge；应从最新基线分支签出新的部署分支，再合并当前目标变更分支。

## Requirements

- 在 `dev_application` 记录应用默认基线分支的最新提交信息：
  - `base_branch_latest_commit_sha`
  - `base_branch_latest_commit_message`
  - `base_branch_latest_commit_at`
- 在 `dev_pipeline_run` 记录本次运行使用的基线分支快照：
  - `base_branch_name`
  - `base_commit_sha`
- GitLab push hook 推送到应用默认分支时，更新应用基线最新提交字段，并清理该应用所有环境的 current-run 缓存。
- 发布页 current-run 接口返回基线快照、基线最新提交和 `baseBranchOutdated`。
- 提交变更触发流水线时，部署分支选择规则为：
  - 基线分支有新提交：从最新基线签出新的部署分支。
  - 存在卸载变更：从最新基线签出新的部署分支。
  - 没有历史部署分支：从最新基线签出新的部署分支。
  - 其他情况：复用原有部署分支。
- 新建部署分支时仍合并所有本次目标变更分支；复用部署分支时保持现有增量合并行为。
- 兼容老数据：历史 run 没有基线快照时不误报 `baseBranchOutdated`。

## Out of Scope

- 不新增通用分支状态表。
- 不支持非默认分支作为多个独立基线的精确状态跟踪。
- 不改前端 UI，本任务只提供后端字段和行为。

## Acceptance Criteria

- [x] 默认分支 push 新 commit 后，`dev_application.base_branch_latest_commit_sha` 等字段更新。
- [x] 默认分支 push 后，该应用所有应用环境的 `APPLICATION_RELEASE_CURRENT_RUN` 缓存失效。
- [x] 新触发的代码合并 run 写入 `base_branch_name` 和 `base_commit_sha`。
- [x] 当前 run 基线快照落后于应用基线最新提交时，current-run 接口返回 `baseBranchOutdated=true`。
- [x] 基线已更新后再次提交同一目标变更集合，后端生成新的 `deploy/{env}/{timestamp}` 部署分支，而不是复用旧部署分支。
- [x] 发生卸载变更时，后端继续生成新的部署分支并只合并剩余目标变更。
- [x] 基线未变化且没有卸载变更时，后端继续复用最近部署分支。
- [x] 老 run 缺少基线快照时，current-run 接口不提示基线过期。

## Open Questions

None.
