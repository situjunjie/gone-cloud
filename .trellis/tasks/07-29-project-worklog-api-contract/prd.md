# 完善项目工时接口契约

## Goal

让项目工作项详情页能够准确展示“当前工作项 + 全部递归子项”的工时主统计，并使工时分组、编号序列化和权限展示具备可直接消费的稳定接口契约。

## Confirmed Facts

- `WorklogController` 已有 `/page` 与 `/summary`，仅支持 `SELF`、`DESCENDANTS` 两种工作项范围；两者共用 `WorklogFilterReqVO` 和服务端范围解析。
- 当前范围查询先解析范围，再复用同一筛选封装执行分页和汇总；`DESCENDANTS` 不包含根项，且递归 CTE 已具备循环防护。
- 全局 Jackson `NumberSerializer` 已将超过 JavaScript 安全整数范围的 `Long` 输出为字符串，安全范围内仍输出 JSON number。因此当前协议无精度丢失，但 ID 的 JSON 类型随数值而变化。
- `GET /admin-api/project/project/my-permissions` 已直接返回项目有效权限全集；`ProjectPermissionEnum` 已包含 `WORKLOG_MANAGE`，无需另增字段即可判定管理权限。
- 当前 `summary.groups[].displayName` 固定为 `null`，但 `WORK_ITEM`、`OWNER`、`SPRINT` 的 `value` 可分别通过工作项、用户、迭代批量查询装配展示名称。

## Requirements

### R1. 增加根项及后代范围

- 在 `WorklogWorkItemScopeEnum` 增加 `SELF_AND_DESCENDANTS`。
- `/admin-api/project/worklog/page` 与 `/admin-api/project/worklog/summary` 同步支持该值。
- 语义为根工作项本身加上全部递归后代；继续保持同租户、同项目、未删除和循环安全的范围解析。
- 相同筛选条件下，分页记录、`total`、总计和分组使用完全相同的范围与筛选口径。

### R2. 编号无损协议

- 所有面向前端的 Long ID 必须无损传输，禁止超过 JavaScript 安全整数范围的值作为 JSON number 输出。
- 保留既有全局协议：安全范围 Long 输出 JSON number，超过 JavaScript 安全整数范围的 Long 输出 JSON string。
- 不进行“所有 Long 均输出字符串”的全仓破坏性迁移。
- 前端接口文档和测试必须准确描述并验证最终协议。

### R3. 分组展示名

- 工时汇总按 `WORK_ITEM`、`OWNER`、`SPRINT` 分组时，为可解析的有效分组值返回 `displayName`。
- `WORK_ITEM` 使用“`工作项编号 - 工作项标题`”格式；`OWNER` 使用用户昵称；`SPRINT` 使用迭代标题。
- `CATEGORY` 维持由前端字典翻译，或明确其展示名策略；空分组保留 `value: null` 与空展示名。
- 使用批量装配，禁止形成按分组逐条查询的 N+1。

### R4. 工时操作权限消费

- 确认 `my-permissions.permissionCodes` 返回 `WORKLOG_MANAGE`，并更新前端文档说明其按钮判断方式。
- 工时明细不强制增加 `canUpdate`、`canDelete`，除非现有权限集合无法支持前端作出正确判断。
- 保持后端最终授权：`WORKLOG_MANAGE` 可管理项目内任意工时；同时具有 `WORKLOG_CREATE` 且为记录负责人时可管理自己的工时。前端还须沿用系统按钮权限 `project:worklog:update` / `project:worklog:delete`。

## Acceptance Criteria

- [ ] `SELF_AND_DESCENDANTS` 的分页和汇总都包含根项与任意深度后代，且相同筛选下列表、total、总计、分组一致。
- [ ] 根项不存在、跨项目、无后代、脏循环和空范围均不泄露数据或产生无限递归。
- [ ] ID 的最终 JSON 协议被自动化测试覆盖，任何超安全整数的 Long 均无损输出。
- [ ] `WORK_ITEM`、`OWNER`、`SPRINT` 有效分组包含正确 `displayName`，且装配不会引入 N+1。
- [ ] `my-permissions` 的有效权限集包含授权后的 `WORKLOG_MANAGE`，前端文档写明新增/编辑/删除按钮规则。
- [ ] 受影响的 project 模块测试和接口文档通过并保持同步。

## Out of Scope

- 新增工作项级 ACL 或修改既有工时写入授权规则。
- 修改工时类型、类别、日期、明细分页排序或已有 `SELF` / `DESCENDANTS` 语义。
- 未经明确批准的全仓 API ID 类型迁移。

## Key Decisions

- 新范围 `SELF_AND_DESCENDANTS` 包含根工作项与所有递归后代；现有 `SELF`、`DESCENDANTS` 语义不变。
- 保留安全范围 Long 输出 number、超安全范围 Long 输出 string 的全局无损序列化，不改为全部字符串。
- `WORK_ITEM` 分组展示“工作项编号 - 工作项标题”；`OWNER` 展示昵称；`SPRINT` 展示迭代标题。
- 利用既有 `my-permissions.permissionCodes` 中的 `WORKLOG_MANAGE` 控制项目级管理能力，不增加明细级 `canUpdate` / `canDelete` 字段。
