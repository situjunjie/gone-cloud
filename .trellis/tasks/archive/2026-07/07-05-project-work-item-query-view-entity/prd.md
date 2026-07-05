# Project 工作项查询视图实体设计

## Goal

在 project 模块新增“工作项查询视图”持久化实体，用于保存工作项列表的常用查询条件，并支持系统级视图与用户级视图区分。

## Requirements

- 工作项查询视图必须保存视图自身信息：名称、稳定编码、级别、归属用户、排序、备注。
- 工作项查询视图必须区分系统级和用户级：
  - 系统级视图面向租户内所有用户，一般用于菜单路由入口。
  - 用户级视图归属具体用户，用于个人常用查询。
- 查询条件用一个 JSON 字段保存当前工作项分页查询条件快照，结构兼容当前 `ProjectWorkItemPageReqVO`；后续工作项新增查询字段时不要求同步改查询视图表结构。
- 设计需符合 project 模块现有实体结构：`TenantBaseDO`、`@TableName`、`@KeySequence`、`BaseMapperX`、`sql/mysql/project.sql`。
- 本任务只落地实体、Mapper、枚举和 MySQL 表结构，不实现完整 CRUD API。

## Acceptance Criteria

- [ ] 新增工作项查询视图 DO，用 JSON 字段保存工作项查询条件快照。
- [ ] 新增系统级/用户级枚举，枚举风格与 project 模块现有枚举一致。
- [ ] 新增 Mapper，提供按编码、按用户视图列表、按系统视图列表的基础查询能力。
- [ ] `sql/mysql/project.sql` 新增对应表、唯一键和常用查询索引。
- [ ] project server 模块编译通过。

## Notes

- `code` 用作稳定标识，系统级视图可作为菜单路由参数。
- 用户级视图需要 `userId`；系统级视图的 `userId` 固定为 `0`，避免 MySQL 唯一键对 `NULL` 放行重复系统视图。
