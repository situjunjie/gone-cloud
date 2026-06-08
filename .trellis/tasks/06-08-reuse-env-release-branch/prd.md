# 复用环境部署分支

## Goal

调整 `devops/application/release/submit-branch` 发布提交逻辑，使同一应用环境在没有剔除已挂载变更时尽量复用现有部署分支，避免每次提交都从基线分支重新签出而重复解决历史冲突；当存在剔除变更时重新创建部署分支，确保目标变更集合干净重建。

## What I Already Know

* 发布入口为 `ApplicationController#submitApplicationReleaseBranch`，服务实现为 `ApplicationServiceImpl#submitApplicationReleaseBranch`。
* 当前提交会将目标变更集同步到 `ChangeEnvDO`：已挂载但不在本次目标列表中的变更会被标记为 unmounted。
* 当前代码合并执行在 `PipelineExecutionServiceImpl#executeCodeMerge` 中调用 `GitWorkspaceService#prepareWorkspace`。
* 现有 `GitWorkspaceServiceImpl#prepareWorkspace` 总是从 `origin/<baseBranch>` checkout 出部署分支。
* 现有部署分支命名为 `deploy/<appKey>/<envKey>/<pipelineRunId>`。
* `PipelineRunDO.branchName` 当前保存的是锚点变更分支，不是实际部署分支。

## Assumptions

* “有变更要剔除”指本次目标变更集合不包含某些当前已挂载变更，即 `unmountedChangeIds` 非空。
* “没有要剔除的变更”包括新增变更 C、或重复提交 A/B/C 这类目标集合未减少的场景。
* 可复用部署分支应来自同一应用环境最近一次成功/运行中的发布记录里保存的部署分支名；如果找不到则退化为新建分支。
* 部署分支名称中的最后一段改为 `yyyyMMddHHmmss`，替代原来的运行编号。

## Requirements

* 提交发布时，如果 `unmountedChangeIds` 为空，应优先复用当前应用环境最近的部署分支。
* 提交发布时，如果 `unmountedChangeIds` 非空，应创建新的部署分支，从应用基线分支重新合并目标变更集合。
* 首次发布、没有历史部署分支时，应创建新的部署分支。
* 复用分支时，代码合并工作区应从远端现有部署分支签出，再合并本次目标变更的最新提交。
* 复用分支但远端部署分支缺失时，应使用同一个部署分支名从基线分支重建。
* 新建分支时，代码合并工作区仍从应用默认基线分支签出。
* 部署分支命名应使用 `deploy/<appKey>/<envKey>/<yyyyMMddHHmmss>`。
* Jenkins 触发仍使用代码合并完成后的部署分支和提交 SHA。

## Acceptance Criteria

* [ ] A/B 首次提交时创建时间戳部署分支，并从基线分支开始合并 A、B。
* [ ] A/B 已挂载后再提交 A/B/C，未剔除变更，复用上一条部署分支并继续合并 A、B、C。
* [ ] A/B/C 已挂载后提交 A/C，剔除 B，新建时间戳部署分支并从基线分支重新合并 A、C。
* [ ] 部署分支命名不再使用 `pipelineRunId`，末尾为 `yyyyMMddHHmmss`。
* [ ] 单元测试覆盖复用分支、新建分支和 Git checkout 起点差异。

## Definition Of Done

* 后端相关单元测试新增或更新。
* 受影响 Maven 模块测试通过，至少覆盖 DevOps server 相关测试。
* 行为变化不要求数据库结构变更，除非实现确认必须新增字段。

## Out Of Scope

* 不改变前端交互和请求参数。
* 不新增手工选择部署分支功能。
* 不改变冲突解决页面能力。
* 不改变 Jenkins 参数契约。

## Technical Notes

* 主要文件：
  * `ApplicationServiceImpl`
  * `PipelineExecutionServiceImpl`
  * `GitWorkspaceService`
  * `GitWorkspaceServiceImpl`
  * `PipelineRunMapper`
  * 相关单元测试
* 需读取 `.trellis/spec/backend/index.md` 和相关后端规范后再编码。
