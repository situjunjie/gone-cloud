# 项目评论前端接口对接文档

## 1. 能力边界

项目评论 V1 用于工作项详情页协作讨论，当前只支持目标类型 `WORK_ITEM`。

- 评论采用扁平时间线，回复通过 `replyToCommentId` 引用另一条评论。
- 正文保存 Markdown 源文本，不接受原始 HTML。
- @ 用户由 `mentionUserIds` 显式提交，正文中的 `@昵称` 不会自动产生通知。
- 已删除评论保留占位、作者和时间，但正文及 @ 关系不可恢复或查询。
- 项目归档后可以查询历史评论，不能新增、修改或删除。
- V1 不支持附件、讨论树、点赞、全文检索或前端实时推送。

## 2. 接口总览

| 功能 | 方法 | 路径 | 项目权限 |
|---|---|---|---|
| 创建评论 | POST | `/admin-api/project/comment/create` | `COMMENT_CREATE` |
| 修改评论 | PUT | `/admin-api/project/comment/update` | `COMMENT_UPDATE` |
| 删除评论 | DELETE | `/admin-api/project/comment/delete` | `COMMENT_DELETE` |
| 评论分页 | GET | `/admin-api/project/comment/page` | `COMMENT_QUERY` |

评论是项目工作项内的协作资源，接口只校验对应的项目角色权限，不要求额外的系统菜单权限。非项目成员（包括系统超级管理员）不能访问私有项目评论。

## 3. 通用规则

### 3.1 返回包络

```json
{
  "code": 0,
  "msg": "",
  "data": {}
}
```

分页 `data` 结构：

```json
{
  "list": [],
  "total": 0
}
```

### 3.2 正文

- `content` 必填，去除空白后不能为空，最大 10,000 个字符。
- 按 Markdown 源文本提交。前端渲染必须禁用 Markdown 原始 HTML，并再次执行 XSS 白名单清理。
- 后端不会返回已删除评论的历史正文。

### 3.3 @ 用户

- `mentionUserIds` 最多 20 个，必须是当前项目成员。
- 重复编号会自动去重，评论作者本人会被移除且不发送提醒。
- 修改评论时，只对新增的 @ 用户发送站内信；保留的用户不会重复收到提醒。
- 通知在评论事务提交后尽力发送。通知模板缺失或发送失败不会让评论请求失败。

### 3.4 回复

- `replyToCommentId` 只能指向同一工作项下的正常评论。
- 已删除评论不能成为新的回复对象。
- 回复不会形成嵌套树；前端按时间线展示，并在单条评论上显示 `replyTo` 摘要。

## 4. 创建评论

```http
POST /admin-api/project/comment/create
Content-Type: application/json
```

```json
{
  "targetType": "WORK_ITEM",
  "targetId": 1024,
  "content": "请 @李四 确认验收口径。",
  "replyToCommentId": 9000,
  "mentionUserIds": [2]
}
```

| 字段 | 类型 | 必填 | 说明 |
|---|---|---|---|
| `targetType` | String | 是 | V1 固定 `WORK_ITEM` |
| `targetId` | Long | 是 | 工作项编号 |
| `content` | String | 是 | Markdown 源文本，最多 10,000 字符 |
| `replyToCommentId` | Long | 否 | 同目标正常评论编号 |
| `mentionUserIds` | Long[] | 否 | 当前项目成员编号，最多 20 个 |

成功响应 `data` 为评论编号：

```json
{"code": 0, "msg": "", "data": 9001}
```

## 5. 修改评论

只有评论作者本人可以修改；项目管理员也不能修改他人原话。

```http
PUT /admin-api/project/comment/update
Content-Type: application/json
```

```json
{
  "id": 9001,
  "content": "验收口径已确认，补充到描述中。",
  "mentionUserIds": [2, 3]
}
```

更新请求提交的是完整 @ 用户集合。传空数组会清除全部 @ 关系，但不会撤回已发送通知。

## 6. 删除评论

评论作者可以删除自己的评论，项目管理员可以删除项目内任意评论。

```http
DELETE /admin-api/project/comment/delete?id=9001
```

删除是不可恢复的业务删除：正文被清空，@ 关系被删除，评论记录保留为时间线占位。重复删除返回业务错误。

## 7. 评论分页

```http
GET /admin-api/project/comment/page?targetType=WORK_ITEM&targetId=1024&pageNo=1&pageSize=20
```

服务端固定按 `createTime DESC, id DESC` 返回，保证同一创建时间下顺序稳定。

```json
{
  "code": 0,
  "msg": "",
  "data": {
    "list": [
      {
        "id": 9001,
        "projectId": 100,
        "targetType": "WORK_ITEM",
        "targetId": 1024,
        "authorUserId": 1,
        "authorName": "张三",
        "authorAvatar": "https://example.com/avatar.png",
        "content": "请 @李四 确认验收口径。",
        "mentionUserIds": [2],
        "replyTo": {
          "id": 9000,
          "authorUserId": 2,
          "authorName": "李四",
          "content": "已补充验收标准。",
          "contentPreview": "已补充验收标准。",
          "deleted": false
        },
        "edited": false,
        "deleted": false,
        "createTime": 1784736000000,
        "updateTime": 1784736000000
      }
    ],
    "total": 1
  }
}
```

删除占位示例：

```json
{
  "id": 9001,
  "authorUserId": 1,
  "authorName": "张三",
  "content": null,
  "mentionUserIds": [],
  "edited": false,
  "deleted": true
}
```

`replyTo.content` 是被引用评论的完整当前正文；`contentPreview` 是总长度最多 120 字符的摘要，
正文过长时以 `...` 结尾。
如果被引用评论已经删除，`replyTo.deleted = true`，`content` 和 `contentPreview` 均为 `null`。
前端应显示“原评论已删除”，不要隐藏当前回复。

## 8. 主要业务错误

| 错误 | 场景 |
|---|---|
| 评论目标类型不支持 | `targetType` 不是 `WORK_ITEM` |
| 评论目标不存在 | 工作项不存在、已逻辑删除或项目不存在 |
| 回复的评论无效 | 跨目标回复或回复已删除评论 |
| 只能修改自己的评论 | 修改他人评论 |
| 无权删除该评论 | 非作者且不是项目管理员 |
| 被 @ 用户不是当前项目成员 | `mentionUserIds` 含外部用户 |
| 评论已删除或状态已变化 | 重复编辑/删除或并发状态变化 |
| 评论正文不允许包含原始 HTML | Markdown 中直接提交 HTML 元素 |

## 9. 上线依赖

执行 `sql/mysql/project-comment-incremental-20260722.sql` 创建表、补齐参与者评论权限和站内信模板。

已有项目的角色权限 Redis 缓存最长保留 30 分钟。部署后应清理 `project:role_permissions` 相关缓存，或等待缓存过期再验证既有参与者账号。
