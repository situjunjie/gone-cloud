# 工作项活动回复评论内容

## Goal

让工作项动态接口在评论为回复时返回前端渲染引用区域所需的被回复评论信息，支持展示“回复 @作者”并展开引用正文，同时保持普通评论、操作动态和现有评论接口兼容。

## Background

- `GET /admin-api/project/work-item/activity/page` 的评论动态直接复用 `CommentRespVO` 作为 `comment` 载荷。
- `CommentService#getCommentPage` 已批量查询被回复评论，并返回 `comment.replyTo`，当前字段包含 `id`、作者信息、最多 120 字的 `contentPreview` 和 `deleted`。
- 动态接口当前没有回复评论示例或针对回复载荷的聚合服务回归测试，前端文档示例仅展示 `replyTo: null`。
- 被回复评论已删除时，现有契约返回 `replyTo.deleted = true` 且正文摘要为空；本任务不应恢复已删除内容。

## Requirements

### R1. 回复引用载荷

- 评论动态不是回复时，`comment.replyTo` 保持 `null`。
- 评论动态是回复时，`comment.replyTo` 必须提供引用评论编号、作者编号、作者昵称、完整当前正文 `content`、最多 120 字的 `contentPreview` 和删除状态。
- 引用正文沿用查询时的被回复评论当前状态；被回复评论已删除或不可查询时不返回正文，并明确标记删除状态。

### R2. 兼容与复用

- 动态接口继续复用评论服务的批量装配能力，不在活动聚合服务中逐条查询评论或复制回复拼装逻辑。
- 普通评论、操作动态、动态排序和分页规则保持不变。
- 在共享的 `CommentRespVO.Reply` 中增量新增 `content`；评论分页与工作项动态均返回该字段，已有 `contentPreview` 字段、长度和语义保持不变。

### R3. 文档与验证

- 工作项动态接口文档增加回复评论响应示例和字段语义。
- 自动化测试覆盖动态接口返回回复内容，以及被回复评论删除时正文不可见的既有安全语义。

## Acceptance Criteria

- [x] AC1: 回复型评论动态返回足够渲染“回复 @作者”和引用正文的 `comment.replyTo` 数据。（R1）
- [x] AC2: 普通评论的 `replyTo` 仍为 `null`，被回复评论已删除时不泄露正文。（R1）
- [x] AC3: 回复信息通过评论服务批量装配，动态聚合不新增 N+1 查询。（R2）
- [x] AC4: 现有评论接口只增 `replyTo.content`，`contentPreview` 的字段、长度和语义均不变。（R2）
- [x] AC5: 工作项动态前端文档和聚焦测试与最终响应契约一致。（R3）

## Verification

- 聚焦测试：`CommentServiceImplTest` 与 `WorkItemActivityServiceImplTest`，18/18 通过。
- 项目测试：`cn.iocoder.yudao.module.project.**.*Test`，135/135 通过。
- 响应契约覆盖完整正文、最多 120 字符且超长以 `...` 结尾的摘要，以及删除引用不返回正文。

## Out Of Scope

- 不实现前端页面交互或样式。
- 不新增回复树、评论快照表或修改回复关系。
- 不改变评论删除、编辑、权限、动态排序和分页语义。
