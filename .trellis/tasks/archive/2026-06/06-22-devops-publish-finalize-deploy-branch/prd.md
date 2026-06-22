# devops 发布收尾改为合并部署分支

## Goal

将 `ChangePublishFinalize` 的发布收尾行为从“逐个把参与发布的变更分支 merge 到应用基准分支”调整为“把本次发布使用的部署分支 merge 到应用基准分支”，避免重复逐变更 merge。

## Confirmed Facts

- 当前 `ChangePublishFinalize` 已是平台步骤，后端处理器位于 `ChangePublishFinalizeStepHandler`。
- 当前实现会从 `PipelineRun.changeSnapshotJson` 或 `PipelineRun.changeId` 解析变更，再对每个变更调用 `ChangeService.finalizePublishedChange(Long id)`。
- 当前 `ChangeService.finalizePublishedChange(Long id)` 会：
  - 校验变更与应用；
  - 解析基准分支 `ChangeDO.sourceBaseBranchName` 或 `ApplicationDO.defaultBranchName`；
  - 将 `ChangeDO.branchName` merge 到基准分支；
  - 更新变更状态为 `RELEASED`；
  - 删除远端变更分支。
- 现有发布运行中的“部署分支”由 `PipelineRunDO.branchName` 表示，发布流水线前序 `CodeMerge` 也使用该字段作为目标分支。

## Requirements

- `ChangePublishFinalize` 在发布收尾时，必须只执行一次代码合并：
  - merge 源分支：`PipelineRunDO.branchName`（本次发布部署分支）
  - merge 目标分支：变更基准分支，优先 `ChangeDO.sourceBaseBranchName`，回退 `ApplicationDO.defaultBranchName`
- 参与发布的变更仍需全部更新为 `RELEASED`，并写入 `releasedAt` 与 `mergedToMasterAt`。
- 参与发布的变更远端分支仍需逐个删除，删除操作保持幂等。
- 发布收尾成功后，还需要删除本次发布使用的部署分支，删除操作保持幂等。
- 如果发布收尾遇到 merge 冲突或 push 失败，步骤必须失败，不能静默继续。
- YAML 节点类型和前端接入语义不变，仍然使用 `ChangePublishFinalize`。

## Acceptance Criteria

- [ ] `ChangePublishFinalize` 执行时，针对一次发布只 merge 一次部署分支，而不是按每个 change branch 分别 merge。
- [ ] 发布中的所有变更都被更新为 `RELEASED`，并保留时间字段写入行为。
- [ ] 发布中的所有变更远端分支都仍然会被清理；已删除分支按成功处理。
- [ ] 发布成功后，本次发布部署分支也会被删除；已删除分支按成功处理。
- [ ] 现有 focused tests 已更新并覆盖成功、已发布幂等、分支缺失/merge 失败等主要分支。
- [ ] `.trellis/spec/backend/devops-change-guidelines.md` 与 `.trellis/spec/backend/devops-pipeline-guidelines.md` 中 `ChangePublishFinalize` 语义与实现一致。

## Out Of Scope

- 不新增新的 YAML 字段或步骤类型。
- 不在本轮修改前端节点配置界面。
