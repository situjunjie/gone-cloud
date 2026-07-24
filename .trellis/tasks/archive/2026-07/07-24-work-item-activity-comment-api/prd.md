# 工作项动态查询与发表评论

## Goal

为工作项详情页提供一个统一的动态分页接口，将工作项操作记录与评论按时间倒序合并返回；同时提供语义明确的工作项发表评论接口，让前端无需了解通用评论目标协议即可完成评论发布。

## Background

- 工作项评论已经通过 `proj_comment`、`CommentService` 和 `WORK_ITEM` 目标解析器实现，评论支持回复、@ 项目成员、删除占位、作者信息与提交后通知。
- 工作项字段修改动态已经通过 `@LogRecord` 异步写入 `system_operate_log`，并可使用固定 `type=项目工作项 + bizId=workItemId` 通过 `OperateLogApi` 查询。
- 评论与操作日志位于不同模块/数据源边界，主键空间独立，且操作日志最终一致；统一动态属于面向展示的活动流，不是强一致审计账本。

## Requirements

### R1. 统一动态分页

- 新增按 `workItemId` 查询动态的管理后台接口，响应类型为 `PageResult`，动态类型固定为 `COMMENT` 或 `OPERATION`。
- 每条动态必须包含来源类型、来源编号、创建时间和操作者基础信息；评论携带现有评论响应，操作记录携带现有变更日志响应，禁止让前端根据字段是否为空猜测类型。
- 动态按统一的时间倒序语义排序；同一时间使用固定的类型顺序及来源编号倒序作为稳定兜底。不同来源的相同主键不得被视为同一条动态。
- `total` 等于该工作项评论总数与操作记录总数之和；指定页应按全局合并顺序截取，而不是分别对两个来源使用同一页码后拼接。
- 动态查询必须固定评论目标为 `WORK_ITEM`、固定操作日志 type 为 `项目工作项`，调用方不能指定任意目标类型或日志类型。

### R2. 工作项发表评论

- 新增工作项专用发表评论接口，请求只暴露 `workItemId`、`content`、可选 `replyToCommentId` 和 `mentionUserIds`，成功返回评论编号。
- Controller 必须把目标固定映射为 `WORK_ITEM + workItemId` 并委托现有 `CommentService#createComment`；不得复制内容、回复、@、归档项目、事务或通知规则。
- 通用评论创建接口保持兼容，不修改既有路径和载荷。

### R3. 权限与隔离

- 查询动态要求系统权限 `project:work-item:query`，并根据持久化工作项归属同时校验项目权限 `WORK_ITEM_QUERY` 与 `COMMENT_QUERY`，避免通过聚合接口绕过既有评论读取权限。
- 工作项发表评论只校验项目权限 `COMMENT_CREATE`，保持评论模块“不依赖系统菜单权限”的既有契约；Service 仍执行独立的目标解析和权限复核。
- 两个接口均不得信任客户端提交的 `projectId`，租户隔离沿用 Mapper、Feign 与当前租户上下文。

### R4. 兼容与降级

- 已删除评论继续作为动态占位返回，正文和 @ 用户保持不可见。
- 无法解析或未知版本的操作记录 `extra` 仍返回文本 `action`，字段变化列表为空。
- 操作日志仍为异步最终一致；刚完成更新后短时间查不到该条操作动态属于允许行为。
- 本任务不新增或迁移动态数据，不修改通用操作日志写入机制。

## Acceptance Criteria

- [x] AC1: `GET /admin-api/project/work-item/activity/page` 能返回评论与操作记录合并后的 `PageResult`，类型、载荷、操作者及时间字段完整。（R1）
- [x] AC2: 混合数据跨页时严格遵循统一倒序和稳定同时间排序，`total` 为两来源总量之和，空来源与超出页范围返回正确。（R1）
- [x] AC3: `POST /admin-api/project/work-item/comment/create` 将目标固定为当前工作项，并完整复用评论正文、回复、@、归档状态和通知规则。（R2）
- [x] AC4: 通用评论四个既有接口和工作项修改动态接口保持兼容，既有测试不回归。（R2、R4）
- [x] AC5: 动态查询在系统权限、`WORK_ITEM_QUERY` 或 `COMMENT_QUERY` 任一缺失时拒绝；发表评论在 `COMMENT_CREATE` 缺失时拒绝且不要求系统菜单权限。（R3）
- [x] AC6: 已删除评论、未知操作日志 schema、操作日志最终一致与租户隔离语义保持现有行为。（R3、R4）
- [x] AC7: 新增聚合服务、Controller 方法、VO 映射与参数校验测试通过，project-server 聚焦测试及 `git diff --check` 通过。（R1-R4）

## Out Of Scope

- 不实现前端页面、WebSocket/SSE 实时推送、动态筛选或按类型统计。
- 不新增评论附件、点赞、富文本协议或操作记录创建/删除事件。
- 不把活动流升级为强一致审计账本，也不回填上线前缺失的操作记录。
- 不在本任务改造 system 模块为跨表 SQL `UNION`、新增游标 RPC 或同步所有数据库索引；若单工作项动态增长到深分页性能不可接受，再单独演进查询协议。

## Notes

- 统一分页的实现将受现有跨模块 RPC 上限约束，技术取舍记录在 `design.md`，不改变上述产品契约。

## Verification

- `mvn -pl yudao-module-project/yudao-module-project-server -am -Dtest='cn.iocoder.yudao.module.project.**.*Test' -Dsurefire.failIfNoSpecifiedTests=false test` passed: 133 tests.
- `mvn -pl yudao-module-system/yudao-module-system-server -am -Dtest=OperateLogMapperTest,OperateLogServiceImplTest -Dsurefire.failIfNoSpecifiedTests=false test` passed: 5 tests.
- `git diff --check` passed.
- Review follow-up: the activity feed now calls a dedicated system `timeline-page` RPC, so an older system node fails visibly instead of silently ignoring an optional ordering parameter and returning an invalid cross-source page.
