# 技术设计：工作项动态与发表评论

## 1. 设计结论

在 project-server 内增加 `WorkItemActivityService` 聚合边界，不新建动态表：评论继续由 `CommentService`/`proj_comment` 提供，操作记录继续由 `WorkItemChangeLogService`/`OperateLogApi` 提供。Controller 只负责 HTTP 契约和方法权限，聚合服务负责跨来源取数、统一映射、排序和分页。

工作项发表评论接口是通用评论创建的薄适配器：固定 `targetType=WORK_ITEM`，将 `workItemId` 映射为 `targetId`，随后完整委托 `CommentService#createComment`。

## 2. API 契约

### 2.1 查询动态

```http
GET /admin-api/project/work-item/activity/page
    ?workItemId=1024&pageNo=1&pageSize=20
```

响应 `PageResult<WorkItemActivityRespVO>`。统一外层字段：

- `type`: `COMMENT` / `OPERATION`
- `sourceId`: 来源表编号；必须和 `type` 共同标识动态
- `operatorUserId`, `operatorName`, `operatorAvatar`
- `createTime`（评论取评论创建时间，操作记录取日志创建时间）
- `comment`: `COMMENT` 时为现有 `CommentRespVO`，否则为空
- `operation`: `OPERATION` 时为现有 `WorkItemChangeLogRespVO`，否则为空

显式使用两个可选载荷，避免复制现有 VO 字段并形成第三套评论/操作日志契约。评论作者即操作者；操作日志用户通过一次批量 `AdminUserApi#getUserMap` 补齐头像，并在 RPC 未翻译昵称时补齐昵称。

Controller 权限：

```java
@PreAuthorize("@ss.hasPermission('project:work-item:query') and "
        + "@projectSecurity.hasWorkItemPermissions(#reqVO.workItemId, "
        + "'WORK_ITEM_QUERY', 'COMMENT_QUERY')")
```

### 2.2 发表评论

```http
POST /admin-api/project/work-item/comment/create
Content-Type: application/json

{
  "workItemId": 1024,
  "content": "请确认验收口径",
  "replyToCommentId": 9000,
  "mentionUserIds": [2, 3]
}
```

返回 `CommonResult<Long>`。权限只使用：

```java
@PreAuthorize("@projectSecurity.hasWorkItemPermission(#reqVO.workItemId, 'COMMENT_CREATE')")
```

为避免校验注解漂移，抽取 `CommentCreateBaseReqVO` 持有 `content/replyToCommentId/mentionUserIds`；现有 `CommentCreateReqVO` 与新增 `WorkItemCommentCreateReqVO` 分别补充目标字段。Controller 使用 `BeanUtils.toBean` 后固定目标并调用现有 Service。

## 3. 数据流

```text
GET activity/page
  -> 方法安全解析真实工作项归属
  -> WorkItemActivityService
       -> CommentService.getCommentPage(WORK_ITEM, workItemId, prefix)
       -> WorkItemChangeLogService.getPage(workItemId, prefix)
       -> map COMMENT / OPERATION
       -> sort(createTime DESC, type rank, sourceId DESC)
       -> slice requested offset/pageSize
       -> total = commentTotal + operationTotal

POST comment/create
  -> 方法安全按 workItemId 校验 COMMENT_CREATE
  -> map WorkItemCommentCreateReqVO -> CommentCreateReqVO(WORK_ITEM, workItemId)
  -> CommentService.createComment
       -> resolve real target/project
       -> service-level COMMENT_CREATE + project usability
       -> content/reply/mention validation
       -> transaction insert + AFTER_COMMIT mention notification
```

## 4. 合并分页策略

两个来源处于独立持久化边界，当前 system RPC 只支持 `type + bizId + pageNo/pageSize`，不存在跨表 `UNION` 或时间游标。不能把相同 `pageNo/pageSize` 分别下推后再合并，否则更靠前的一个来源会挤占另一来源，跨页会漏项或重复。

本次采用“前缀取数后全局截取”：

1. 计算 `required = pageNo * pageSize`，对两个来源分别查询 `pageNo=1,pageSize=required`。
2. 将两份前缀映射并按统一比较器排序。评论已经使用 `createTime DESC,id DESC`；操作日志查询通过专用 `timeline-page` RPC 使用相同顺序。
3. 取 `[offset, min(offset + pageSize, merged.size))`。
4. `total` 使用两个来源各自返回的总量之和。

正确性依据：全局前 `required` 条不可能包含某来源中排名超过该来源前 `required` 的记录，因此每个来源各取前 `required` 条足以构造目标页。该结论要求两个来源均按聚合比较器的主顺序取前缀。

`PageParam` 单次最大 200，因此统一动态限制 `pageNo * pageSize <= 200`，通过自定义 Bean Validation 约束在入口明确拒绝超过可保证正确性的深分页，避免悄悄返回错误结果或无界查询。后续若产品需要超过 200 条，优先扩展 comment/system 两边的 `(createTime,id)` 游标 API，再将统一接口改为游标合并；不使用 `PAGE_SIZE_NONE` 拉取无限历史。

排序规则：

1. `createTime DESC`（空时间排最后，正常持久化数据不应为空）；
2. 同时间 `COMMENT` 在 `OPERATION` 前（固定稳定规则，不宣称因果先后）；
3. 同类型按 `sourceId DESC`。

为保持现有消费者兼容，保留原 `page` RPC 的 `id DESC` 排序，并新增专用 `timeline-page` RPC 使用 `createTime DESC,id DESC`。工作项既有修改动态接口不改变排序，只有统一动态前缀查询调用时间线 RPC。不能把排序要求编码为通用 RPC 的可选参数：project 先升级时，旧 system 节点会忽略未知参数并静默返回错误前缀；缺少专用端点应显式失败，直到 system 节点完成升级。

操作日志异步落库，`createTime` 表示日志持久化时间，不保证与业务更新事务形成严格因果顺序；此接口定位仍是最终一致活动流。

## 5. 兼容、性能与安全

- 保留 `/project/comment/*` 和 `/project/work-item/change-log/page`，新接口纯增量。
- 聚合服务复用现有评论批量装配（mention、reply、用户）和操作日志 `extra.schemaVersion=1` 解析，不读 Mapper 内部实现。
- 两个下游调用任一失败时沿标准异常边界失败，不返回来源不完整却声称完整的分页结果。
- 请求不能控制评论 `targetType`、操作日志 `type` 或 `projectId`。
- `ProjectSecurityFrameworkService#hasWorkItemPermissions` 只读取一次持久化工作项，再逐项验证 `WORK_ITEM_QUERY` 与 `COMMENT_QUERY`；聚合 Service 调用评论 Service 时保留其 `COMMENT_QUERY` 复核。
- 当前评论已有匹配目标时间线索引；system 操作日志缺少 `tenant_id,type,biz_id` 复合索引是已知性能风险。本任务保持已有变更日志的决策，不扩散到多数据库脚本；上线后依据数据量和 Explain 单独治理。
- 统一动态只暴露展示字段，不暴露操作日志 IP、UA、请求地址或 trace 信息。

## 6. 回滚

- 新增 API、VO 和聚合 Service 可整体删除，不影响评论表、操作日志写入和既有接口。
- 评论基础请求 VO 的抽取必须保持 JSON 字段及校验不变；若出现兼容问题，可改回重复声明字段而无需数据回滚。
- 不删除已经产生的评论或操作日志，不需要数据库回滚。
