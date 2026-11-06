# Work Item Change Log Guidelines

## Scenario: Work Item Field Change Activity

### 1. Scope / Trigger

- Apply when changing work-item full updates, quick field updates, custom-field value storage, activity-log payloads, or work-item change-log queries.
- This contract is for an eventually consistent activity feed. It is not a complete, transactionally atomic audit ledger.
- Create/delete activity, history backfill, and frontend rendering are outside this contract unless a task explicitly expands the scope.

### 2. Signatures

Write entry points:

```text
ProjectWorkItemServiceImpl.updateWorkItem(ProjectWorkItemSaveReqVO)
ProjectWorkItemServiceImpl.updateWorkItemField(ProjectWorkItemUpdateFieldReqVO)
```

Log annotation and context:

```java
@LogRecord(type = PROJECT_WORK_ITEM_TYPE,
        subType = PROJECT_WORK_ITEM_UPDATE_SUB_TYPE,
        bizNo = "{{#updateReqVO.id}}",
        success = "{{#workItemChange.summary}}",
        extra = "{{#workItemChange.extra}}",
        condition = "{{#workItemChange.changed}}")
```

Query API:

```text
GET /admin-api/project/work-item/change-log/page
    ?workItemId=<Long>&pageNo=<Integer>&pageSize=<Integer>
-> PageResult<WorkItemChangeLogRespVO>
```

### 3. Contracts

- Do not use `_DIFF{#updateReqVO}` for quick updates. Its dynamic `fields[]` contains both fixed names and `custom:<fieldId>` values and does not represent the typed persisted state.
- Capture normalized snapshots before and after the update. Compare fixed fields by `ProjectWorkItemUpdateFieldEnum`; compare custom selected options by sorted option ids so order-only changes are ignored.
- One successful request creates at most one activity. Put `changed`, `summary`, and `extra` in a single `workItemChange` context object. If no effective field changed, set `changed=false`.
- Resolve category, priority, status, assignee, custom `USER`, and option values to stable display text at write time. User lookup is batched; lookup failure falls back to the id.
- Treat the built-in `description` as redacted rich text in activity storage. Compare the complete persisted value to decide whether a change occurred, but write only the field code/name: the action uses `【描述】发生了修改`, and `extra.changes[]` leaves `oldValue`, `newValue`, `oldDisplay`, and `newDisplay` null. Other fields keep their before/after values.
- `action` and `extra` must stay below the `system_operate_log` 2000-character columns. Truncate values and change count, and emit `extra.schemaVersion=1` with a `truncated` flag.
- A custom-field snapshot failure degrades the request to fixed-field changes. A comparison or display-resolution failure clears the change context and skips the activity; neither failure may roll back the work-item update.
- Query only through `OperateLogApi`, always setting fixed `type=项目工作项` and `bizId=workItemId`. Never expose a caller-controlled log type or access a system mapper from project-server.
- The controller must combine `project:work-item:query` with `WORK_ITEM_QUERY` resolved from the persisted work-item id.
- Parse `changes` only when `extra.schemaVersion == 1`. Missing, malformed, or unknown-version `extra` returns the textual action with `changes=[]`.

### 4. Validation & Error Matrix

| Condition | Required result |
|---|---|
| Same fixed/custom value or multi-select order-only change | Update may succeed; no activity is written |
| Description changes, including HTML or base64 images | Record that `description` changed without persisting old/new content in `action` or `extra` |
| Custom snapshot read fails | Record fixed-field changes only; do not fail the update |
| User nickname lookup fails | Keep the activity and display user ids |
| Change comparison/serialization fails | Clear `workItemChange`, skip activity, keep the business update |
| `extra` is missing, invalid JSON, or not schema version 1 | Return `action` and `changes=[]` |
| System or project query permission is absent | Spring Security access denied before log retrieval |
| System log RPC returns an error | Propagate the checked RPC failure through the standard API error boundary |
| Immediate post-update query sees no row | Treat as expected eventual consistency; callers may refresh |

### 5. Good / Base / Bad Cases

- Good: one quick update changes priority and a custom user field, then writes one activity containing two readable changes.
- Good: a description update containing rich HTML writes `【描述】发生了修改` and a structured change with field/name only.
- Good: a custom-field snapshot fails while priority changes; the update succeeds and the activity contains only priority.
- Base: a request writes the same value or only reorders selected option ids; `condition=false` prevents a log row.
- Bad: feed `ProjectWorkItemUpdateFieldReqVO.fields` into `_DIFF`, which compares transport data instead of normalized persisted values.
- Bad: copy description HTML or a `data:image/...;base64` payload into operation-log `action` or `extra`.
- Bad: accept `type` from the query request, which turns a work-item endpoint into a generic operation-log reader.
- Bad: throw a log comparison or nickname-resolution failure back into the transactional update.

### 6. Tests Required

- Comparator tests: fixed-field change, same value, custom value clear/change, multi-select reorder, enum display, assignee display, custom `USER` display, and description change detection with value redaction.
- Degradation tests: custom snapshot exception still records fixed changes; nickname lookup failure uses ids; record exception leaves `changed=false`.
- Entry-point tests: both full and quick update initialize context, capture before/after snapshots, and invoke one record operation.
- Query tests: assert exact `type`, `bizId`, page parameters, response mapping, invalid JSON, missing version, and unknown version behavior.
- Method-security tests are required when the controller expression or work-item permission resolver changes; assert system denial, project denial, and dual-layer success.
- Run `mvn -pl yudao-module-project/yudao-module-project-server -am test` and `git diff --check`.

### 7. Wrong vs Correct

#### Wrong

```java
@LogRecord(success = "{{_DIFF{#updateReqVO}}}")
public void updateWorkItemField(ProjectWorkItemUpdateFieldReqVO updateReqVO) {
    // fields[] is dynamic and custom values are not yet normalized.
}
```

#### Correct

```java
@LogRecord(success = "{{#workItemChange.summary}}",
        extra = "{{#workItemChange.extra}}",
        condition = "{{#workItemChange.changed}}")
public void updateWorkItemField(ProjectWorkItemUpdateFieldReqVO updateReqVO) {
    Snapshot before = changeLogService.capture(oldWorkItem, true);
    // Validate and persist fixed and custom values.
    changeLogService.record(before, changeLogService.capture(updatedWorkItem, true));
}
```

For description activity rendering:

```text
Wrong: 【描述】从【<p>旧正文...</p>】修改为【<p><img src="data:image/...">...</p>】
Correct: 【描述】发生了修改
```

## Scenario: Unified Work Item Activity and Comment Creation

### 1. Scope / Trigger

- Apply when adding or changing a work-item activity feed that combines comments and operation logs, or a work-item-specific comment creation endpoint.
- The two activity sources remain independently persisted. The unified endpoint is a read-time projection and inherits the eventual consistency of asynchronous operation logs.
- Do not introduce a third activity store or duplicate comment creation rules merely to expose a work-item-oriented API.

### 2. Signatures

Unified activity query:

```text
GET /admin-api/project/work-item/activity/page
    ?workItemId=<Long>&pageNo=<Integer>&pageSize=<Integer>
-> PageResult<WorkItemActivityRespVO>
```

Reply comment payload inside a `COMMENT` activity:

```json
{
  "comment": {
    "replyTo": {
      "id": 7001,
      "authorUserId": 2,
      "authorName": "李四",
      "content": "被回复评论的完整当前正文",
      "contentPreview": "总长度最多 120 字符的摘要",
      "deleted": false
    }
  }
}
```

Work-item comment creation:

```text
POST /admin-api/project/work-item/comment/create
Content-Type: application/json

{
  "workItemId": <Long>,
  "content": <String>,
  "replyToCommentId": <Long|null>,
  "mentionUserIds": [<Long>, ...]
}
-> Long commentId
```

Operation-log timeline RPC:

```java
GET /rpc-api/system/operate-log/timeline-page
    ?type=<String>&bizId=<Long>&pageNo=<Integer>&pageSize=<Integer>
-> PageResult<OperateLogRespDTO> // createTime DESC, id DESC
```

### 3. Contracts

- `WorkItemActivityRespVO.type` is explicitly `COMMENT` or `OPERATION`; `(type, sourceId)` is the activity identity because source ids come from independent tables.
- Every item returns `sourceId`, `createTime`, `operatorUserId`, `operatorName`, and `operatorAvatar`. A comment item sets only `comment`; an operation item sets only `operation`.
- Sort the merged feed by `createTime DESC`, then `COMMENT` before `OPERATION`, then `sourceId DESC`. The type tie-breaker is stability only and does not imply causality.
- For page `pageNo/pageSize`, fetch the first `pageNo * pageSize` records from each source, merge and sort them, then slice the requested global offset. Never push the requested page number independently to both sources.
- `total` is `commentTotal + operationTotal`. Reject requests where `pageNo * pageSize > 200`; correct deep pagination requires a future cursor contract on both sources.
- Fix comment lookup to `targetType=WORK_ITEM,targetId=workItemId`. Fix operation-log lookup to `type=PROJECT_WORK_ITEM_TYPE,bizId=workItemId` and use the dedicated `OperateLogApi#getOperateLogTimelinePage` RPC for the unified timeline query.
- Do not encode required ordering as an optional query parameter on the generic operation-log RPC: an older system node can ignore an unknown parameter and return an apparently successful, wrongly ordered prefix. A missing timeline endpoint instead fails the query visibly during mixed-version deployment.
- Query permission is system `project:work-item:query` plus project `WORK_ITEM_QUERY` and `COMMENT_QUERY`, all resolved from the persisted work item. Comment creation requires project `COMMENT_CREATE` and intentionally has no system-menu permission dependency.
- The work-item comment endpoint maps `workItemId` to `WORK_ITEM + targetId` and delegates to `CommentService#createComment`. The shared service remains authoritative for content, reply, mention, archived-project, transaction, notification, tenant, and permission rules.
- A reply comment reuses `CommentRespVO.Reply`: return the referenced comment's complete current `content` for expandable rendering and keep `contentPreview` as the compatible summary with total length at most 120 characters (overlength values end in `...`). Assemble both fields in the comment service's existing batch reply lookup; the activity service only passes the comment payload through and must not add per-item reply queries.
- Deleted comments retain the existing placeholder representation. Unknown or malformed operation-log `extra` retains textual `action` with an empty `changes` list. A downstream source failure fails the whole query rather than returning a falsely complete page.

### 4. Validation & Error Matrix

| Condition | Required result |
|---|---|
| `workItemId` is missing | Bean Validation rejects the request |
| `pageNo < 1`, `pageSize` outside `1..200`, or `pageNo * pageSize > 200` | Bean Validation rejects the request before source queries |
| Comment content is blank or exceeds 10000 characters | Shared request validation rejects comment creation |
| Reply target, mention user, project state, or target is invalid | Propagate the existing `CommentService` business error |
| Referenced reply comment is deleted or missing during query | Return `replyTo.deleted=true` with `content=null` and `contentPreview=null`; never expose deleted text |
| System query permission is absent | Deny the activity query without invoking the aggregation service |
| Either `WORK_ITEM_QUERY` or `COMMENT_QUERY` is absent | Deny the activity query |
| `COMMENT_CREATE` is absent | Deny comment creation; do not add a system-menu permission requirement |
| A comment or operation-log source call fails, including an old system node without `timeline-page` | Propagate the failure; do not return a partial page |
| Operation log has not yet been persisted | Return currently visible activities; clients may refresh |

### 5. Good / Base / Bad Cases

- Good: comments and operation logs interleave across a page boundary; the requested page matches the globally sorted combined prefix and `total` sums both sources.
- Good: a work-item comment with a reply and mentions delegates unchanged to the shared comment service while fixing the target to `WORK_ITEM`.
- Good: a reply activity returns author metadata, full current content, and the existing preview from one batch-assembled `replyTo` payload.
- Base: one or both sources are empty, or the requested offset is beyond the merged rows; return a valid empty page and the combined total.
- Base: a normal comment has `replyTo=null`; a deleted reply remains visible as a placeholder without either content field.
- Base: two source rows share a timestamp and source id; keep both and order the comment first because identity includes the type.
- Bad: query page 2 from each source and concatenate the results; globally earlier rows from a dominant source can be skipped or duplicated.
- Bad: resolve the referenced comment again inside `WorkItemActivityService` or return deleted reply content, which respectively introduces N+1/duplicate assembly and violates deletion semantics.
- Bad: accept `targetType`, operation-log `type`, or `projectId` from the client; this permits cross-target reads or authorization against untrusted ownership.

### 6. Tests Required

- Aggregation tests: mixed-source global ordering, same-time type/id tie-breakers, cross-page slicing, empty sources, beyond-range pages, and combined total.
- Prefix tests: assert both source requests use `pageNo=1,pageSize=pageNo*pageSize`, fixed target/type values, and timeline operation-log ordering.
- Mapping tests: comment and operation discriminators, mutually exclusive payloads, operator profile fallback, deleted-comment pass-through, and unknown operation schema pass-through.
- Reply mapping tests: assert full `replyTo.content`, unchanged `contentPreview`, activity-service pass-through, and null content fields for deleted references.
- Controller mapping tests: assert fixed `WORK_ITEM + workItemId`, unchanged content/reply/mentions, delegated service call, and returned comment id.
- Method-security tests: system denial, either project-query permission denial, query success, comment-create denial, and comment-create success without a system permission check.
- Compatibility tests: the generic comment endpoints and generic operation-log RPC ordering remain `id DESC`; the dedicated timeline RPC uses `createTime DESC,id DESC`.
- Run focused project tests, focused system operation-log tests, and `git diff --check`.

### 7. Wrong vs Correct

#### Wrong

```java
PageResult<CommentRespVO> comments = commentService.getPage(pageNo, pageSize);
PageResult<OperateLogDTO> operations = operateLogApi.getPage(pageNo, pageSize);
return concatenateAndSort(comments, operations); // Not a global page.
```

#### Correct

```java
int required = pageNo * pageSize;
List<Activity> prefix = mergeAndSort(
        commentService.getPage(1, required),
        workItemChangeLogService.getTimelinePage(1, required));
return slice(prefix, (pageNo - 1) * pageSize, pageSize,
        commentTotal + operationTotal);
```

For reply payload assembly:

```java
// Wrong: activity aggregation repeats reply lookup per comment.
commentMapper.selectById(commentDO.getReplyToCommentId()); // Inside the activity loop.

// Correct: CommentService batch-assembles Reply once; activity aggregation passes it through.
activity.setComment(comment);
```
