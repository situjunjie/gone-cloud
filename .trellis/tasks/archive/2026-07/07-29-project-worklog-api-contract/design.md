# 项目工时接口契约完善设计

## Boundaries

本次只调整 `yudao-module-project-server` 的工时范围解析、汇总响应装配、接口文档与测试。全局 Jackson 规则已满足无损序列化要求，因此只补充契约测试和文档，不修改 `yudao-framework` 的全局序列化行为。

## Scope Contract

`WorklogWorkItemScopeEnum` 增加 `SELF_AND_DESCENDANTS`。`WorklogServiceImpl.resolveWorkItemRange` 的语义为：

| 范围 | 解析结果 |
|---|---|
| `SELF` | 根工作项 |
| `DESCENDANTS` | 全部递归后代，不含根项 |
| `SELF_AND_DESCENDANTS` | 根工作项及全部递归后代 |

`page` 与 `summary` 都调用该解析方法，随后继续共用 `WorklogMapper.buildQueryWrapper`。递归 CTE 保持租户、项目、逻辑删除和循环保护条件；空后代在 Service 层短路，不能退化为项目全量查询。

为避免两次层级查询，`SELF_AND_DESCENDANTS` 复用现有“后代 ID 集合”，再把根 ID 加入稳定去重集合；根不存在或不属于请求项目时 CTE 为空，最终结果也为空，不能泄露数据。

## Summary Display Names

汇总 SQL 继续只返回稳定分组原始 `value` 与工时数，Service 在得到 groups 后按 groupBy 批量装配：

| groupBy | 批量来源 | displayName |
|---|---|---|
| `WORK_ITEM` | `ProjectWorkItemMapper.selectBatchIds` | `workItemNo + " - " + title` |
| `OWNER` | `AdminUserApi.getUserMap` | `nickname` |
| `SPRINT` | `ProjectSprintMapper.selectBatchIds` | `title` |
| `CATEGORY` | 无额外查询 | `null`，前端按 `project_worklog_category` 字典翻译 |

`value = null` 或已无法解析的关联值保留空 `displayName`。每种分组最多执行一次批量查询/RPC，不得按 group 逐条加载。

## ID And Permission Contract

`NumberSerializer` 已全局保证 Long 超出 JavaScript 安全整数范围时写出 JSON string；安全范围维持 JSON number。更新前端文档以 `string | number` 描述 ID，并加针对边界值的序列化测试。

`my-permissions` 已返回当前用户有效项目权限全集，包含 `WORKLOG_MANAGE`。不增加 `canUpdate`、`canDelete`：前端以系统权限 `project:worklog:update` / `project:worklog:delete`、项目权限 `WORKLOG_MANAGE` 和记录 `ownerUserId === currentUserId` + `WORKLOG_CREATE` 进行预判；后端仍是唯一授权裁决者。

## Compatibility And Rollback

- 新枚举值是向后兼容的请求扩展，现有请求和范围行为不变。
- `displayName` 从可空升级为可填充，不改变 `value`、总计或分组排序。
- ID 的动态 number/string 行为不变，避免全仓 API 破坏性迁移。
- 回滚时可移除新枚举分支和展示名装配；没有数据库结构或数据迁移。

## Validation

- 请求 DTO 测试：新范围合法，既有非法组合继续拒绝。
- Mapper/Service 测试：根项、子项、孙项、空后代、跨项目根项和脏循环；验证分页与汇总口径一致。
- 汇总测试：三类可翻译分组的 displayName、空值/缺失引用及无 N+1 的批量调用。
- Controller/权限测试：`my-permissions` 返回 `WORKLOG_MANAGE`；前端文档同步系统和项目权限判断。
- Jackson 测试：安全范围 Long 为 number，超安全范围 Long 为 string，数值文本无截断。
