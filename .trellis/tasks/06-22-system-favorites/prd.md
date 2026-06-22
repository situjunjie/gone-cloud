# system module 收藏夹能力

## Goal

为 system 模块新增面向 AdminUser 的通用收藏夹能力，支持按业务类型查询、添加、取消收藏，供 devops 等模块接入

## Confirmed Facts

- `AdminUserDO` 当前是用户主数据模型，已继承 `TenantBaseDO`，不适合直接在单行字段里承载多业务收藏关系。
- system 模块现有“我的数据”接口（例如站内信）默认按当前登录用户工作，不额外暴露 `userId` 入参。
- 仓库已使用 `TenantBaseDO + BaseMapperX + LambdaQueryWrapperX + CommonResult` 的标准后端模式。
- 项目维护了多数据库初始化 SQL，新增持久化对象时除了单测 H2 表结构，还需要同步主 SQL 脚本。
- DevOps 后续需要复用这项能力来收藏不同业务对象，例如应用、环境，因此收藏模型不能绑定单一业务表。

## Requirements

- 收藏能力归属 system 模块，面向管理后台登录用户。
- 收藏模型必须支持通用业务维度，至少包含 `bizType` 和 `bizId`，以便不同模块复用。
- 基础接口包括：
  - 根据业务类型查询当前用户收藏列表
  - 添加收藏
  - 取消收藏
- 接口按当前登录用户生效，不允许前端传入任意用户编号操作他人收藏。
- 收藏关系需要具备租户隔离能力。
- 重复添加同一收藏应保持幂等，不产生重复数据。
- 对已取消的历史收藏再次添加时，应恢复原关系，而不是持续插入新重复行。
- 结果需要让业务侧能识别被收藏对象，至少返回收藏记录主键、业务类型、业务对象编号和创建时间。

## Acceptance Criteria

- [ ] system 模块新增用户收藏数据模型、DAL、Service 和管理后台接口。
- [ ] 当前登录用户可按 `bizType` 获取自己的收藏列表。
- [ ] 当前登录用户可添加收藏、取消收藏，重复调用不产生重复有效记录。
- [ ] 收藏关系按租户和用户隔离，不会误读或误改其他租户/用户数据。
- [ ] 单测覆盖新增收藏、重复收藏、取消后恢复、按类型查询等核心场景。
- [ ] 单测表结构和项目维护的主数据库 SQL 脚本已同步新增收藏表。

## Out of Scope

- 根据 `bizType/bizId` 反查业务详情并做聚合展示。
- 为 DevOps 或其他业务模块直接新增“收藏应用/收藏环境”的专用接口。
- 收藏排序、分组、备注、自定义标签等扩展能力。

## Open Questions

- 本次先返回通用收藏关系记录，由业务模块自行根据 `bizType/bizId` 补全详情；后续若要做统一收藏内容聚合，再另行扩展契约。

## Notes

- Keep `prd.md` focused on requirements, constraints, and acceptance criteria.
- Lightweight tasks can remain PRD-only.
- For complex tasks, add `design.md` for technical design and `implement.md` for execution planning before `task.py start`.
