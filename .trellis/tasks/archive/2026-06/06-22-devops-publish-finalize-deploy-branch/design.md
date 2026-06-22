# Design

## Summary

将发布收尾的 merge 粒度从“每个变更”改为“每次发布运行”。步骤处理器仍负责解析本次发布涉及的变更集合，但不再为每个变更单独触发 merge，而是将 `PipelineRunDO.branchName` 作为唯一 merge 源分支传入变更服务，由变更服务完成：

- merge 部署分支到基准分支
- 批量/逐个更新发布变更状态
- 逐个删除远端变更分支
- 删除本次发布的部署分支

## Boundaries

- `ChangePublishFinalizeStepHandler`
  - 继续从 `PipelineRunDO` 中解析发布快照变更 ids。
  - 改为调用新的“按运行发布收尾”服务接口，而不是对每个 change id 循环逐个 finalize。
- `ChangeService`
  - 新增按 `PipelineRunDO` 发布收尾的方法，内部统一处理 merge 与状态更新。
  - 旧的 `finalizePublishedChange(Long id)` 保留或收敛为内部复用方法，避免打断现有测试和调用方过多。
- `RepositoryProviderService`
  - 远端删除分支能力保持不变，但会被用于清理变更分支和部署分支。

## Data Flow

1. step handler 从 `PipelineRunDO.changeSnapshotJson` 或 `changeId` 收集变更 ids。
2. step handler 校验存在待收尾变更；为空时直接返回 continue。
3. step handler 将 `PipelineRunDO.branchName` 与变更 ids 交给 `ChangeService`。
4. `ChangeService`：
   - 加载首个变更确认应用、仓库提供方、基准分支；
   - 校验 `PipelineRunDO.branchName` 非空；
   - 用部署分支 merge 到基准分支；
   - 对所有相关变更更新 `RELEASED/releasedAt/mergedToMasterAt`；
   - 对所有相关变更逐个删除远端变更分支；
   - 删除本次运行使用的部署分支。

## Compatibility

- YAML 节点类型仍为 `ChangePublishFinalize`，前端和 pipeline spec 不需要结构性变更。
- 步骤输出仍然返回 `finalizedChangeCount` 和 `finalizedChangeIds`，避免前端结果展示失配。
- 业务语义变化体现在：
  - merge 来源从 `ChangeDO.branchName` 改为 `PipelineRunDO.branchName`
  - 清理范围从“仅变更分支”扩展为“变更分支 + 部署分支”

## Tradeoffs

- 优点：发布收尾只 merge 一次，更符合“发布分支已经承载了本次发布合并结果”的语义，也减少重复冲突和重复 push；同时可以自动回收临时部署分支。
- 代价：步骤处理器和变更服务之间的接口要从单变更 finalize 调整为按 run finalize，相关测试需要重写；部署分支删除后不能再依赖远端保留该分支做排查。

## Rollback Shape

- 如果改动导致问题，可回退到前一个实现：逐变更 merge。
- 这次不涉及数据库 schema 变更，回滚只需代码回退。
