# Project 评论模块技术设计（初版）

## 1. Design Summary

评论能力放在 `yudao-module-project-server` 内独立的 `comment` 业务边界，采用“受控目标类型 + 目标解析器”模型。首期目标类型只有 `WORK_ITEM`，但数据结构和服务接口不写死 `work_item_id`，后续接入项目或迭代时无需迁移评论主表。

首期推荐交互是扁平时间线：回复通过 `reply_to_comment_id` 引用另一条评论，页面展示“回复某人”，不构建任意深度树。这样分页不会切断讨论树，也避免一次根评论携带无限回复。

```text
CommentController
  -> CommentTargetResolver (WORK_ITEM: exists + real projectId)
  -> ProjectSecurity / ProjectPermissionService
  -> CommentService
       -> CommentMapper
       -> CommentMentionMapper
       -> AFTER_COMMIT CommentMentionEvent
            -> NotifyMessageSendApi
```

## 2. Package Boundary

```text
controller/admin/comment/
  CommentController.java
  vo/CommentCreateReqVO.java
  vo/CommentUpdateReqVO.java
  vo/CommentPageReqVO.java
  vo/CommentRespVO.java
service/comment/
  CommentService.java
  CommentServiceImpl.java
  CommentTargetResolver.java
  WorkItemCommentTargetResolver.java
  CommentMentionNotifier.java
dal/dataobject/comment/
  CommentDO.java
  CommentMentionDO.java
dal/mysql/comment/
  CommentMapper.java
  CommentMentionMapper.java
enums/
  CommentTargetTypeEnum.java
  CommentStatusEnum.java
```

不在 `project-api` 子模块暴露评论 RPC，除非后续确认其他微服务需要直接创建或查询评论。当前能力只服务 project HTTP 接口，避免过早形成跨模块契约。

## 3. Domain Model

### 3.1 Comment

建议表名：`proj_comment`。

| Column | Type | Constraint | Meaning |
|---|---|---|---|
| `id` | bigint | PK | 评论编号 |
| `project_id` | bigint | not null | 从目标解析得到的真实项目编号 |
| `target_type` | varchar(32) | not null | 首期固定 `WORK_ITEM` |
| `target_id` | bigint | not null | 目标编号 |
| `reply_to_comment_id` | bigint | null | 回复引用的评论编号 |
| `author_user_id` | bigint | not null | 评论作者，不依赖字符串审计字段反解 |
| `content` | text | null | 正常评论正文；删除后清空 |
| `status` | varchar(16) | not null | `NORMAL` / `DELETED` |
| `creator/create_time/updater/update_time/deleted/tenant_id` | standard | | `TenantBaseDO` 标准字段 |

索引：

- `idx_target_timeline (tenant_id, project_id, target_type, target_id, create_time, id)`
- `idx_reply_to (tenant_id, reply_to_comment_id)`
- `idx_author (tenant_id, project_id, author_user_id, create_time)`，仅在存在按作者治理或审计查询需求时增加。

`project_id` 看似可由目标反查，但保留它有三个目的：快速项目授权、跨目标项目清理/审计、目标删除后的归属稳定。写入时它只能由服务端解析，客户端不提交。

### 3.2 Mention

建议表名：`proj_comment_mention`。

| Column | Type | Constraint | Meaning |
|---|---|---|---|
| `id` | bigint | PK | 关系编号 |
| `comment_id` | bigint | not null | 评论编号 |
| `mentioned_user_id` | bigint | not null | 被 @ 的 Admin 用户 |
| standard audit/tenant columns | | | `TenantBaseDO` 标准字段 |

唯一约束：`uk_comment_user (tenant_id, comment_id, mentioned_user_id, deleted)` 是否可用取决于项目既有逻辑删除唯一键策略。首期更稳妥的方案是差异更新：恢复已有逻辑删除关系、插入全新关系、逻辑删除移除关系，避免重复键冲突。

### 3.3 Deletion State

业务删除使用 `status = DELETED`，同时清空 `content` 并删除 @ 关系；不直接设置主记录 `deleted = 1`。主记录的逻辑删除字段保留给数据纠错或目标级物理治理。这样其他评论的 `reply_to_comment_id` 仍能解析为“已删除评论”。

首期不保存正文历史版本。若法规或审计要求必须恢复删除内容，应另建不可由普通查询访问的 revision/audit 表，不能继续把正文留在普通评论表中并误称“已删除”。

## 4. Target Resolver

定义受控接口，禁止把 `targetType` 转成表名或动态 SQL：

```java
interface CommentTargetResolver {
    CommentTargetTypeEnum getType();
    CommentTarget resolve(Long targetId); // targetId, projectId, displayNo/title, writable
}
```

`WorkItemCommentTargetResolver` 使用 `ProjectWorkItemMapper.selectById` 获取工作项，返回真实 `projectId` 和项目是否可写。创建/查询时均调用解析器；更新/删除先按 comment id 获取持久化归属，再按评论记录的 `targetType + targetId` 验证目标状态。

扩展新目标类型必须完成：枚举、Resolver、授权测试、目标删除语义、前端定位 URL 五项，不允许仅向数据库写入新字符串。

## 5. API Contract

基础路径：`/admin-api/project/comment`。

| Method | Path | Payload | Result |
|---|---|---|---|
| POST | `/create` | `targetType,targetId,content,replyToCommentId?,mentionUserIds?` | comment id |
| PUT | `/update` | `id,content,mentionUserIds?` | boolean |
| DELETE | `/delete?id=` | comment id | boolean |
| GET | `/page` | `targetType,targetId,pageNo,pageSize` | `PageResult<CommentRespVO>` |

推荐响应字段：

```json
{
  "id": 9001,
  "targetType": "WORK_ITEM",
  "targetId": 1024,
  "authorUserId": 1,
  "authorName": "张三",
  "authorAvatar": "...",
  "content": "请 @李四 确认验收口径",
  "mentionUserIds": [2],
  "replyTo": {
    "id": 8998,
    "authorUserId": 2,
    "authorName": "李四",
    "contentPreview": "已补充到描述中",
    "deleted": false
  },
  "edited": false,
  "deleted": false,
  "createTime": 1784707200000,
  "updateTime": 1784707200000
}
```

正文预览必须服务端截断到固定长度（建议 120 字符），删除引用不返回历史正文。作者和被 @ 用户通过 `AdminUserApi` 批量装配；回复对象通过一次 `selectBatchIds` 装配。

分页推荐 `create_time DESC, id DESC`，前端按返回顺序展示最新讨论并支持“加载更多”。若产品要求从旧到新阅读，可在前端对当前页反转，但服务端分页锚点仍保持倒序稳定。

## 6. Authorization Design

### 6.1 Permission Catalog

项目固定权限新增：

- `COMMENT_QUERY`
- `COMMENT_CREATE`
- `COMMENT_UPDATE`
- `COMMENT_DELETE`

内置 `PARTICIPANT` 快照默认包含四项评论权限；现存项目是权限快照，不应在应用启动时静默改变。需要提供显式增量 SQL 为已有项目的内置参与者角色补齐评论权限，并保证可重复执行。

评论属于具体项目工作项内的协作资源，不新增系统菜单权限；接口仅依赖上述项目角色权限。

### 6.2 Method Security

目标型操作：

```java
@PreAuthorize("@projectSecurity.hasCommentTargetPermission(#reqVO.targetType, #reqVO.targetId, 'COMMENT_CREATE')")
```

ID 型操作：

```java
@PreAuthorize("@projectSecurity.hasCommentPermission(#id, 'COMMENT_DELETE')")
```

方法安全负责项目归属和项目角色权限边界，Service 再负责所有权规则：

- update: `author_user_id == loginUserId`，项目管理员也不能篡改他人原话。
- delete: 作者本人或 `ProjectPermissionService.isAdmin(projectId, loginUserId)`。

Service 的目标解析与所有权校验不能省略，因为未来可能出现非 HTTP 调用入口。

## 7. Mention And Notification Flow

1. 创建/编辑时规范化 `mentionUserIds`：去重、移除作者、限制 20 人。
2. 通过项目成员 Mapper 一次查询验证所有用户都是当前项目成员。
3. 事务内写评论与 mention 差异。
4. 记录新增 mention 用户集合，发布领域事件。
5. `@TransactionalEventListener(phase = AFTER_COMMIT)` 中逐用户调用 `NotifyMessageSendApi.sendSingleMessageToAdmin`。
6. 单个用户发送失败只记录 `commentId,targetType,targetId,recipientUserId`，继续其他用户。

project RPC 配置需增加 `NotifyMessageSendApi`。站内信模板建议使用固定代码 `PROJECT_COMMENT_MENTION`，模板参数只传作者名、工作项编号/标题、正文短摘要和前端定位参数。

本地 AFTER_COMMIT 事件解决“通知失败回滚主事务”的问题，但进程在提交后、监听前崩溃仍可能丢通知。如果通知必须保证最终到达，应升级为 outbox/MQ；首期不建议为普通协作提醒引入该复杂度。

## 8. Content And XSS

正文协议确定为 Markdown 源文本：后端只校验长度、空白和禁止原始 HTML，不负责生成可信 HTML。前端渲染必须禁用原始 HTML并做白名单清理。

`mentionUserIds` 是通知和权限事实来源，正文中的 `@显示名` 只是展示文本。这样昵称重名、改名和手工输入 `@xxx` 都不会错误触发通知。

若选择富文本 JSON，必须先固定编辑器中立的文档 schema、版本字段、mention node 结构、纯文本摘要算法和迁移策略，否则评论数据会与某个前端编辑器实现强耦合。

## 9. Attachment Boundary

当前 infra 文件上传返回 URL 或文件记录 id，但内容下载接口是匿名访问。对私有项目评论直接存 URL，会让“能拿到 URL 的非项目成员”绕过 project RBAC 下载附件。

因此初版默认不包含附件。若必须首期支持，建议先补齐安全文件访问方案：comment 只存 `file_id`，下载走 project 授权代理或带短时签名的受控 URL；不要以“URL 难猜”代替授权。

## 10. Transactions And Concurrency

- create: 评论 + mention 同一事务。
- update: 校验作者和 `NORMAL` 状态，条件更新正文，再差异更新 mention；事件只包含新增接收人。
- delete: 条件更新 `NORMAL -> DELETED`、清空正文并删除 mention，同一事务。
- 删除和编辑使用带 `status = NORMAL` 的条件更新，受影响行数为 0 时重新判断不存在/已删除/并发冲突。
- 查询不持锁；相同时间使用 id 作为稳定次序。

## 11. Compatibility And Rollout

- 新表和新接口是纯增量，不修改现有工作项请求/响应。
- `ProjectPermissionEnum` 扩展会让 `ADMIN` 自动获得新权限；既有 `PARTICIPANT` 项目快照需要增量 SQL。
- 评论接口没有系统菜单权限依赖；发布时只需同步项目角色权限目录和既有参与者权限迁移。
- 站内信模板缺失或停用时评论仍成功；上线检查需验证模板存在。
- 回滚应用前先停止前端入口；新表可保留，避免丢失已产生的评论数据。

## 12. Observability

- 记录创建/编辑/删除的 comment id、target type/id、project id 和操作者 id，不记录完整正文。
- 通知失败日志携带 comment id 与 recipient id。
- 首期不新增高基数指标；可使用接口耗时、错误码和通知失败日志定位问题。

## 13. Confirmed Decisions

以下决策已于 2026-07-22 由用户统一确认，作为 V1 实施基线。表中保留主要取舍，便于后续变更时追溯。

| ID | 已确认方案 | 取舍说明 |
|---|---|---|
| D1 目标范围 | 通用目标模型，首期只开放 `WORK_ITEM` | 直接使用 `work_item_id` 更简单，但未来接入项目/迭代需迁表和复制服务；首期同时开放多目标则测试和权限面显著扩大。 |
| D2 回复模型 | 扁平时间线 + `replyToCommentId` 引用 | 二级讨论串更适合长讨论，但根分页、回复分页和删除展示都更复杂；任意深度树不建议。 |
| D3 正文协议 | Markdown 源文本，禁原始 HTML | 富文本 JSON 体验更强，但必须先固定 schema 与编辑器；原始 HTML 有持续 XSS 风险。 |
| D4 @ 通知 | 首期包含站内信，事务提交后尽力发送 | 若暂缓，首期更轻，但历史需求中的“评论与 @ 提醒”只完成一半；若要求绝不丢失，需要额外 outbox/MQ。 |
| D5 附件 | 首期不做，先解决私有文件授权 | 直接复用现有 URL 上线快，但下载端匿名，破坏私有项目边界；接受此风险需要你明确批准。 |
| D6 编辑/删除 | 不设时间窗；作者可编辑/删除自己，管理员仅可删除他人；删除即清正文 | 限时编辑更利于审计但增加产品规则；保留删除正文需独立审计表和访问权限。 |
| D7 交付范围 | 首期后端 + SQL + 测试 + 前端接口文档 | 同时实现前端会扩大任务，需要确认前端仓库/技术栈和页面入口后拆子任务。 |
| D8 权限模型 | 评论只校验项目角色 `COMMENT_*` 权限 | 评论已位于具体项目工作项内，不再增加或校验系统菜单权限；私有项目成员边界仍保持。 |
