# 根据项目查询迭代列表接口

## Goal

为 `project` 模块增加按 `projectId` 查询迭代列表的接口，供项目内页面展示或选择迭代。

## Background

- 项目已具备 Sprint 创建接口和 `ProjectSprintDO` 持久化模型。
- 迭代是租户隔离、项目归属的数据，查询必须受项目访问权限约束。

## Requirements

- 新增管理员端项目迭代列表接口，必填参数为 `projectId`。
- 仅返回请求项目下未逻辑删除的迭代，不得泄露其他项目或租户数据。
- 查询前校验目标项目存在。
- 接口为 `GET /project/sprint/list?projectId={projectId}`，返回 `CommonResult<List<ProjectSprintRespVO>>`。
- 响应字段包含 `id`、`projectId`、`title`、`status`、`ownerUserId`、`ownerUserName`、`ownerUserAvatar`、`startDate`、`endDate`、`capacityHours`、`usedHours`、`totalWorkItemCount`、`completedWorkItemCount` 和 `description`。
- `ownerUserName`、`ownerUserAvatar` 使用现有 Easy-Trans 用户翻译机制批量填充，不在 Service 中逐条调用用户 RPC。
- 持久层的计划开始/结束时间转换为响应中的自然日 `startDate`、`endDate`。
- 工作项完成度数据按 `sprintId` 批量统计；`totalWorkItemCount` 统计迭代内全部未删除工作项，`completedWorkItemCount` 统计状态为 `COMPLETED` 的工作项。
- 当前系统尚未记录实际工时，`usedHours` 暂时固定返回 `0.0`，后续工时功能接入时替换为真实统计。
- 接口使用系统权限 `project:project:query` 与项目内权限 `PROJECT_QUERY` 双层校验；归档项目仍允许读取。
- 列表按迭代编号倒序返回，保证最新创建的迭代优先且顺序稳定。

## Acceptance Criteria

- [x] 合法 `projectId` 返回该项目的迭代列表，空项目返回空数组。
- [x] 缺少 `projectId`、项目不存在或没有项目访问权限时，请求被拒绝。
- [x] 列表不包含其他项目或租户的迭代。
- [x] 响应包含完整展示字段，负责人昵称和头像可由 Easy-Trans 填充。
- [x] 列表按 `id DESC` 排序。
- [x] 查询和权限行为具备自动化测试。
- [x] 每个迭代返回工作项总数和已完成工作项数；无工作项时均返回 `0`。
- [x] 工作项统计使用一次按迭代分组的聚合查询，不产生逐迭代 N+1 查询。
- [x] `usedHours` 暂时稳定返回 `0.0`，`capacityHours` 继续返回迭代配置值。

## Out of Scope

- 分页、关键词筛选和状态筛选。
- 迭代详情、更新、删除和状态流转。
