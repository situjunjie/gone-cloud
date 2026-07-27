# 项目迭代创建接口实施计划

## Implementation

- [x] 新增 `ProjectSprintCreateReqVO`、`ProjectSprintController`、`ProjectSprintService`、`ProjectSprintServiceImpl` 和 Sprint Convert。
- [x] 扩展 `ProjectSprintDO` 的 `capacityHours`、`description` 字段，并实现日期映射与 `NOT_STARTED` 初始状态。
- [x] 在 `ProjectMemberService` 增加负责人项目成员校验能力，Sprint 创建时同时校验项目可用性、成员关系和日期顺序。
- [x] 增加 Sprint 专用错误码。
- [x] 增加 `SPRINT_CREATE` 项目权限和 `project:sprint:create` 系统权限 SQL，确保参与人默认不授权。
- [x] 同步 `sql/mysql/project.sql`，新增 `sql/mysql/project-sprint-incremental-20260727.sql`。

## Tests

- [x] 新增 `ProjectSprintServiceImplTest`，覆盖成功映射、初始状态、日期倒置、非项目成员和不可用项目委托校验。
- [x] 扩展项目角色权限测试，断言 `SPRINT_CREATE` 在固定目录中但不在参与人默认/必需集合中。
- [x] 扩展方法安全测试，覆盖系统权限短路、项目权限拒绝、双层通过及参数映射。
- [x] 运行 `mvn -pl yudao-module-project/yudao-module-project-server -am -DskipITs test`。
- [x] 运行 `git diff --check` 并检查变更范围。

## Risk And Rollback Points

- 权限目录是缓存及角色授权的共享契约；测试必须验证 ADMIN 前向兼容和参与人默认集合不变。
- 增量 SQL 必须可在目标 MySQL 版本执行且避免重复添加列/权限。
- 日期请求是自然日而持久层为 `datetime`；转换统一在 Convert/Service 边界完成，不暴露时区推断。
