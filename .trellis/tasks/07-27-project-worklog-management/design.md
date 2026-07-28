# 项目工时管理技术设计

## 1. Design Summary

工时能力放在 `yudao-module-project-server` 内独立的 `worklog` 业务边界。`proj_worklog` 是预估工时和实际工时的唯一事实来源，工作项只保留聚合后的 `estimatedHours`、`actualHours` 响应，不再保存或接受 `estimatedWorkload`。

```text
WorklogController
  -> ProjectSecurityFrameworkService（系统权限 + 项目权限）
  -> WorklogService
       -> ProjectWorkItemMapper（解析真实 projectId / 当前 sprintId）
       -> ProjectMemberService（负责人资格）
       -> DictDataApi（工作类别有效性）
       -> WorklogMapper（明细、总计、单维度聚合）

ProjectWorkItemService / ProjectSprintService
  -> WorklogMapper（批量工作项合计 / 迭代实际工时合计）
```

工作项关联、明细、汇总和迭代已用工时共享同一张表、权限目录和聚合口径，拆成子任务会形成强顺序依赖并重复修改 SQL 与权限模型，因此本次保留为一个复杂任务。

## 2. Package Boundary

```text
controller/admin/worklog/
  WorklogController.java
  vo/WorklogCreateReqVO.java
  vo/WorklogUpdateReqVO.java
  vo/WorklogPageReqVO.java
  vo/WorklogSummaryReqVO.java
  vo/WorklogRespVO.java
  vo/WorklogSummaryRespVO.java
service/worklog/
  WorklogService.java
  WorklogServiceImpl.java
dal/dataobject/worklog/
  WorklogDO.java
  WorklogSummaryResult.java
dal/mysql/worklog/
  WorklogMapper.java
resources/mapper/worklog/
  WorklogMapper.xml
enums/
  WorklogTypeEnum.java
  WorklogGroupByEnum.java
```

当前没有跨模块调用需求，不在 `yudao-module-project-api` 暴露工时 RPC；模块 API 仅增加业务错误码。

## 3. Persistence Model

表名：`proj_worklog`。Java 实体 `WorklogDO` 继承 `TenantBaseDO`。

| Column | Type | Constraint | Meaning |
|---|---|---|---|
| `id` | bigint | PK | 工时编号 |
| `project_id` | bigint | not null | 由持久化工作项解析，客户端不可指定 |
| `work_item_id` | bigint | not null | 所属工作项，创建后不可修改 |
| `type` | varchar(32) | not null | `ESTIMATED` / `ACTUAL`，创建后不可修改 |
| `hours` | decimal(10,2) | not null | 小时数，范围 `0..99999999.99` |
| `category` | varchar(32) | not null | `project_worklog_category` 字典值 |
| `description` | varchar(1000) | null | 工作描述 |
| `owner_user_id` | bigint | not null | 工时负责人，必须是当前项目成员 |
| `work_date` | date | conditional | 实际工时必填，预估工时可空 |
| audit / delete / tenant columns | standard | | `TenantBaseDO` 标准字段 |

索引：

- `idx_tenant_project_work_date_type (tenant_id, project_id, work_date, type)`：项目日期范围总计。
- `idx_tenant_work_item_type (tenant_id, work_item_id, type)`：工作项合计、存在性和级联删除。
- `idx_tenant_project_owner_date (tenant_id, project_id, owner_user_id, work_date)`：负责人明细与汇总。
- `idx_tenant_project_category (tenant_id, project_id, category)`：类别筛选与分组。

`project_id` 保留用于稳定授权和项目汇总，但写入时只能从工作项解析。`sprint_id` 不冗余保存，迭代筛选和分组通过当前工作项的 `sprint_id` 关联；工作项在同一项目内调整迭代后，其工时随工作项进入新迭代口径。

## 4. Enum And Dictionary Contracts

- 工时类型由服务端枚举 `WorklogTypeEnum` 固定：`ESTIMATED`（预估工时）、`ACTUAL`（实际工时）。
- 工作类别使用系统字典 `project_worklog_category`：`DESIGN`（设计）、`DEVELOPMENT`（研发）、`TESTING`（测试）、`DOCUMENTATION`（文档）、`OTHER`（其他）。
- 保存时通过 `DictDataApi.validateDictDataList(...).checkError()` 校验字典值存在且启用；类型仍使用本地枚举，不能由可编辑字典代替。

## 5. HTTP Contracts

基础路径：`/admin-api/project/worklog`。

| Method | Path | Contract | Result |
|---|---|---|---|
| POST | `/create` | `workItemId,type,hours,category,description?,ownerUserId?,workDate?` | worklog id |
| PUT | `/update` | `id,hours,category,description?,ownerUserId?,workDate?` | boolean |
| DELETE | `/delete?id=` | worklog id | boolean |
| GET | `/get?id=` | worklog id | `WorklogRespVO` |
| POST | `/page` | required `projectId` + filters + page | `PageResult<WorklogRespVO>` |
| POST | `/summary` | required `projectId` + filters + `groupBy?` | `WorklogSummaryRespVO` |

明细和汇总共享筛选字段：`projectId`、`sprintId`、`workItemId`、`type`、`category`、`ownerUserId`、`workDateStart`、`workDateEnd`。日期起止同时存在时必须满足开始日期不晚于结束日期。

`WorklogRespVO` 返回 `id,projectId,workItemId,workItemNo,workItemTitle,type,hours,category,description,ownerUserId,ownerUserName,workDate,creator,createTime,updateTime`。页面先分页查询工时，再批量装配工作项编号/标题；负责人昵称使用 Easy-Trans，禁止逐条 RPC 查询。

汇总响应：

```text
estimatedHours, actualHours,
groupBy?, groups[{ value, displayName?, estimatedHours, actualHours }]
```

`groupBy` 只允许 `WORK_ITEM`、`OWNER`、`CATEGORY`、`SPRINT`。`value` 来自服务端枚举控制的列或关联字段，客户端值不能成为任意 SQL 标识符。未指定 `groupBy` 时只返回总计和空分组列表。

## 6. Validation And Ownership

- `hours` 使用 `BigDecimal`，最多两位小数，不得为负；汇总空集合返回 `0.00`。
- `ACTUAL.workDate` 必填；`ESTIMATED.workDate` 可空。带日期范围时，空日期的预估记录自然不参与范围结果。
- 创建、更新和删除先从工作项或持久化工时获得真实 `projectId`，校验项目可写后再校验字典、负责人、所有权和条件日期。
- 普通 `WORKLOG_CREATE` 用户的负责人固定为登录用户；请求未传时自动填入当前用户，传入他人时拒绝。
- `WORKLOG_MANAGE` 用户可代填和变更负责人，但目标用户仍须为有效项目成员。
- 更新请求不包含 `workItemId` 和 `type`，从协议层保证两者不可变；Service 仍只根据持久化记录授权。
- 负责人可以更新、逻辑删除自己的记录；`WORKLOG_MANAGE` 可以操作项目内任意记录。

## 7. Authorization Design

系统菜单权限：

```text
project:worklog:query
project:worklog:create
project:worklog:update
project:worklog:delete
```

项目固定权限：

| Permission | participantDefault | participantRequired | Purpose |
|---|---:|---:|---|
| `WORKLOG_QUERY` | true | false | 查看项目全部明细和汇总 |
| `WORKLOG_CREATE` | true | false | 为自己登记并维护自己的工时 |
| `WORKLOG_MANAGE` | false | false | 代填、变更负责人、维护他人工时 |

ADMIN 继续通过既有机制获得全部固定权限。已有项目的参与人角色是权限快照，增量 SQL 为内置参与人补齐默认 `WORKLOG_QUERY`、`WORKLOG_CREATE`，不得在应用启动时静默写权限。

`ProjectSecurityFrameworkService` 增加工时资源解析方法。Controller 执行系统权限与项目权限的第一道校验；Service 再按持久化工作项/工时的真实 `projectId`、负责人和登录用户复核，避免非 HTTP 调用绕过所有权规则。

## 8. Query And Aggregation

- `WorklogMapper` 提供统一筛选构造，明细页、总计和分组必须共享相同过滤语义。
- 需要迭代条件时关联 `proj_work_item`，同时约束租户、项目和未删除状态；不接受请求中的任意列名。
- 总计使用条件 `SUM` 分别计算 `ESTIMATED` 与 `ACTUAL`，单次 SQL 返回两个值。
- 分组 SQL 仅通过 `WorklogGroupByEnum` 选择工作项、负责人、类别或迭代表达式；按 value 稳定排序，空迭代作为 `null` 分组保留。
- 工作项详情和分页响应新增 `estimatedHours`、`actualHours`。详情查询一次合计；分页按本页工作项 id 批量分组查询，禁止 N+1。
- 迭代列表用一次按 `sprint_id` 分组的实际工时聚合查询装配全部 `usedHours`；没有记录的迭代补零。

## 9. Work Item Lifecycle Integration

- 删除 `proj_work_item.estimated_workload`，并清理 DO、保存/响应 VO、快捷更新枚举、查询字段枚举、Service 校验/复制/赋值、变更日志展示和前端接口文档中的 `estimatedWorkload`。
- 工作项全量更新和快捷 `projectId` 更新均调用 `WorklogService.hasWorklog(workItemId)`；存在未删除工时时抛出 `PROJECT_WORK_ITEM_HAS_WORKLOGS`。
- 工作项删除在原事务中先调用 `WorklogService.deleteByWorkItemId(workItemId)` 逻辑删除全部工时，再删除自定义字段和工作项；任一步失败整体回滚。
- 工时创建、更新、删除使用独立 `@LogRecord` 类型记录操作日志，日志只写工时编号、工作项编号和字段摘要，不写完整描述；首期不并入现有“评论 + 工作项修改”统一动态，因为该时间线当前只有两种固定来源，扩展属于单独产品范围。

## 10. SQL And Rollout

- 更新全量脚本 `sql/mysql/project.sql`、`project-dict.sql`、`project-menu.sql`。
- 新增日期化幂等增量脚本，包含建表、删除未上线字段 `estimated_workload`、字典、系统权限和既有参与人默认项目权限补齐。
- 项目尚未上线，不迁移 `estimated_workload` 数据，也不保留兼容读取/写入。
- 发布回滚优先回滚应用入口和菜单权限；新表可保留。删除旧列不可自动恢复，但当前无上线数据，回滚需要重新执行加列 DDL 后才能运行旧应用。

## 11. Error Matrix

| Condition | Result |
|---|---|
| 工时不存在 | `PROJECT_WORKLOG_NOT_EXISTS` |
| 类型无效、实际工时无日期、小时数或日期范围非法 | Bean Validation 或 `PROJECT_WORKLOG_VALUE_INVALID` |
| 工作类别不存在或停用 | 传播标准字典校验错误 |
| 负责人不是项目成员 | `PROJECT_WORKLOG_OWNER_NOT_MEMBER` |
| 普通用户为他人登记或操作他人记录 | `PROJECT_WORKLOG_OPERATION_DENIED` |
| 无系统权限或项目权限 | Spring Security 拒绝，Service 入口仍复核项目权限 |
| 工作项存在工时却跨项目移动 | `PROJECT_WORK_ITEM_HAS_WORKLOGS` |

## 12. Confirmed Decisions

| ID | Decision |
|---|---|
| D1 | 工时使用独立实体，`ESTIMATED` 与 `ACTUAL` 共用一表。 |
| D2 | 删除未上线的 `estimated_workload`，不迁移、不兼容，新实体为唯一来源。 |
| D3 | 实际工时工作日期必填，预估工时工作日期可空。 |
| D4 | 首期项目内筛选，返回总计并支持工作项、负责人、类别、迭代单维度分组。 |
| D5 | 普通用户只能登记本人；`WORKLOG_MANAGE` 可代填和管理他人。 |
| D6 | 不设修改删除时间窗；工作项和类型不可改，负责人仅管理者可改。 |
| D7 | 有工时的工作项禁止跨项目移动。 |
| D8 | 删除工作项时事务内逻辑级联删除工时。 |
| D9 | `WORKLOG_QUERY` 默认授予参与人但可移除，持有者可查看项目全部工时。 |
