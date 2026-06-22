# Design

## Problem Restatement

需要为 system 模块新增一套可复用的“当前管理后台用户收藏关系”能力。它不能把收藏硬塞进
`AdminUserDO` 单表字段，因为收藏目标来自不同业务模块，并且一个用户会有多条收藏关系。

## Design Decision

### Data model

- 新增 `UserFavoriteDO`，放在 `system` 模块的 `user` 领域下。
- 表名：`system_user_favorite`
- 继承 `TenantBaseDO`
- 核心字段：
  - `id`
  - `userId`
  - `bizType`
  - `bizId`
  - 基础审计字段与 `tenantId`

### Why relation table instead of AdminUserDO field

- 一个用户会收藏多个业务对象，天然是一对多关系。
- `bizType + bizId` 可以让 DevOps 应用、环境等对象复用同一模型。
- 新业务接入时不需要修改 `AdminUserDO` 结构。
- 逻辑删除与恢复更容易做幂等处理。

## API Contract

新增管理后台接口，默认作用于当前登录用户：

- `GET /system/user-favorite/list?bizType=...`
  - 返回当前用户指定业务类型的收藏记录列表
- `POST /system/user-favorite/create`
  - 请求体：`bizType`、`bizId`
- `DELETE /system/user-favorite/delete?bizType=...&bizId=...`
  - 取消当前用户指定业务对象的收藏

响应返回通用收藏记录，不在 system 模块内聚合业务详情。业务侧可再根据
`bizType + bizId` 做批量查询和展示。

## Service Behavior

### Add favorite

1. 按 `tenantId + userId + bizType + bizId` 查询有效收藏。
2. 如果已存在，直接返回，保持幂等。
3. 如果不存在，再查询包含逻辑删除记录的历史收藏：
   - 若存在已删除记录，则恢复该记录；
   - 若不存在，则插入新记录。

### Cancel favorite

- 按 `tenantId + userId + bizType + bizId` 逻辑删除有效收藏。
- 若当前不存在有效收藏，直接返回，保持幂等。

### List favorites

- 按 `tenantId + userId + bizType` 查询有效收藏列表。
- 默认按 `createTime desc, id desc` 返回。

## Persistence Notes

- 为避免重复有效收藏，主库 SQL 增加唯一约束：`tenant_id + user_id + biz_type + biz_id`
- 因为表使用逻辑删除，新增时不能盲目插入；需要先查包含已删除记录的数据，再决定恢复还是插入。
- 单测 H2 表结构与主数据库脚本同步新增该表。

## Risks and Mitigations

- 风险：逻辑删除后再次收藏触发唯一键冲突。
  - 缓解：新增“查已删除并恢复”的路径。
- 风险：多租户环境下误读别的租户收藏。
  - 缓解：所有 mapper 查询和删除条件显式带 `tenantId`。
- 风险：业务方误以为 system 接口会直接返回应用/环境详情。
  - 缓解：接口只返回关系记录，PRD 中明确不做业务详情聚合。

## Rollback Shape

- 若接口契约需要调整，可保留数据表不动，仅调整 controller/service 层。
- 若上线后决定把收藏能力拆到独立业务模块，可沿用 `system_user_favorite` 数据表做迁移，不影响现有数据。
