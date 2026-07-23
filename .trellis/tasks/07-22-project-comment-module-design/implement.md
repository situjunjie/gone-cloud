# Project 评论模块实施计划

## Preconditions

- `design.md` 中 D1-D8 已全部确认，PRD convergence pass 已完成。
- 用户明确批准进入实现后，才运行 `task.py start`。
- 实现前使用 `trellis-before-dev` 重新加载 backend、project RBAC、跨层与复用规范。

## Implementation Steps

1. Permission and error contracts
   - 扩展 `ProjectPermissionEnum` 与 project API `ErrorCodeConstants`。
   - 增加项目角色权限和已有项目参与者角色的幂等增量 SQL；评论不增加系统菜单权限。
   - 扩展 `ProjectSecurityFrameworkService` 的目标/评论资源归属解析。

2. Persistence
   - 新增 `CommentDO`、`CommentMentionDO`、Mapper 与目标时间线分页查询。
   - 更新 `sql/mysql/project.sql`，并按仓库实际支持范围检查其他数据库脚本是否需要同步。
   - 为目标时间线、回复引用和 mention 唯一性建立索引/约束。

3. Target resolution
   - 新增固定目标类型枚举、Resolver 接口与 `WORK_ITEM` 实现。
   - 实现不存在目标、跨租户、归档项目读写状态校验。

4. Comment service
   - 实现创建、编辑、业务删除和分页查询。
   - 实现作者所有权、回复同目标、状态条件更新、稳定排序和批量响应装配。
   - 实现 mention 规范化、项目成员批量校验与差异更新。

5. Notification
   - 增加 `NotifyMessageSendApi` Feign 配置。
   - 新增 AFTER_COMMIT 事件监听器、通知模板 SQL/配置和失败日志。
   - 保证通知异常不传播到已提交的评论请求。

6. HTTP contract
   - 新增 Controller 和请求/响应 VO，接入项目级 `@PreAuthorize`。
   - 增加 Bean Validation、OpenAPI 描述、`@TransMethodResult` 或等价批量用户装配。
   - 编写 `docs/comment-frontend-api.md`。

7. Focused tests
   - Mapper: 目标分页顺序、租户过滤、回复批量查询、mention 差异和删除占位。
   - Service: 创建/编辑/删除、所有权、管理员删除、回复边界、归档只读、mention 上限/去重/成员校验。
   - Security: 项目权限拒绝与通过、目标/评论真实归属解析、非成员无绕过。
   - Notification: AFTER_COMMIT、仅新增 mention、作者不提醒、单用户失败隔离。
   - Controller/VO: 参数校验和错误码契约。

8. Documentation and rollout review
   - 校对项目权限快照迁移、通知模板和前端定位参数。
   - 确认回滚不删除已有评论数据。

## Validation Commands

```bash
mvn -pl yudao-module-project/yudao-module-project-server -am test
git diff --check
```

若全仓 SQL/模块依赖发生变化，再补充：

```bash
mvn -pl yudao-server -am -DskipTests package
```

## Risk And Rollback Points

- 项目权限目录与项目角色快照迁移必须同时验证；遗漏会导致既有参与者缺少评论权限。
- 评论删除采用状态字段而非主表逻辑删除；实现时若误用 `deleteById` 会破坏回复引用。
- 通知必须在事务提交后触发；若放进主事务，远程失败会扩大评论写入故障面。
- 现有 infra 文件匿名下载不能直接视为私有评论附件能力。
- 回滚时只回滚应用入口和权限菜单，不删除新表，不回收已经写入的评论。

## Review Gates Before Activation

- [x] D1-D8 已由用户确认并写回规划文档。
- [x] PRD convergence pass 已完成，无重复事实或已解决问题残留。
- [x] 权限默认值与既有项目迁移策略已明确。
- [x] 通知可靠性等级已明确：AFTER_COMMIT 尽力发送，不引入 outbox/MQ。
- [x] 附件已明确排除，待私有文件授权能力完成后另行设计。
- [x] 用户明确批准进入实现，并已运行 `task.py start`。

## Execution Status

- [x] 权限、错误码、持久化、目标解析、评论生命周期、通知和 HTTP 契约已实现。
- [x] MySQL 全量脚本、幂等增量脚本、前端对接文档和 Project RBAC code-spec 已同步。
- [x] 评论子域类名已按包边界简化为 `Comment*`，无 `ProjectComment*` 残留。
- [x] 评论接口仅使用项目角色 `COMMENT_*` 权限，无系统菜单权限依赖。
- [x] 35 个评论/权限/通知/Mapper/工作项移动聚焦测试通过。
- [x] `git diff --check` 通过。
- [ ] 全量 `project-server` 测试：本次相关测试通过；7 个既有测试因外部增量编译器覆盖 MapStruct 生成类为 unresolved-compilation bytecode 而失败，非评论代码回归。
