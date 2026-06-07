# Frontend Implementation Plan

## Goal

在应用发布页提交变更后，前端能够展示流水线运行详情、代码合并进度，并在发生文本冲突时提供 Web 冲突解决页面。前端不关心后端是否用 Git 还是其他实现，只消费统一的 pipeline run log API。

## User Flow

1. 用户在应用详情「发布」Tab 选择目标环境和目标变更集合。
2. 点击提交。
3. 后端返回 `pipelineRunId`。
4. 前端进入或弹出运行详情视图。
5. 运行详情显示节点列表：
   * 代码合并
   * 后续 Jenkins 构建（本阶段可显示为等待/未实现）
   * 后续部署（本阶段可显示为等待/未实现）
6. 如果代码合并成功，展示部署分支和提交 SHA。
7. 如果代码合并进入 `WAITING_INPUT`，展示冲突入口。
8. 用户进入冲突解决页，逐个解决文本冲突。
9. 用户保存所有 resolution 后点击继续合并。
10. 如果冲突不支持在线解决，用户外部修复源变更分支后点击“刷新并重试当前变更”。

## Pages / Components

### Application Release Tab

Existing page extension:

* Submit target change ids.
* Receive `pipelineRunId`.
* Show latest run status near environment or branch list.
* Provide entry: “查看流水线运行”.

### Pipeline Run Detail

Route suggestion:

* `/devops/pipeline/run/detail?id={pipelineRunId}`

Main areas:

* Header:
  * run id
  * environment
  * status
  * trigger user/time
  * deploy branch if available
* Timeline / step list:
  * render `pipeline_run_log` as tree.
  * `NODE` row as major stage.
  * `STEP` row as node detail, e.g. each change branch.
  * `EVENT` row as user actions, e.g. saved conflict resolution.
* Right panel or detail drawer:
  * selected log summary
  * `context_json` important fields rendered as structured UI, not raw JSON.

### Code Merge Panel

Displayed when `nodeType=CODE_MERGE`.

Sections:

* Base:
  * base branch
  * base commit
  * deploy branch
* Merge items:
  * change key/title
  * branch name
  * frozen SHA
  * status
  * merge commit SHA
* Conflicts:
  * file path
  * type
  * status
  * text / unsupported
  * action button

### Conflict Resolve Page

Route suggestion:

* `/devops/pipeline/run/conflict?id={pipelineRunId}&filePath=...`

Layout:

* Left sidebar: conflict file list.
* Main editor:
  * base readonly
  * ours/current readonly
  * theirs/incoming readonly
  * result editable
* Toolbar:
  * accept current
  * accept incoming
  * accept both
  * save resolution
  * continue merge

Use Monaco Editor for text editing/diff. Do not embed code-server or VS Code Web in MVP.

For unsupported conflict:

* no editor.
* show conflict type and explanation.
* show current change branch.
* button: “已在源分支修复，刷新并重试当前变更”.

## API Contract

### Run Logs

`GET /devops/pipeline-run/{runId}/logs`

Frontend expects:

```json
[
  {
    "id": 1,
    "parentId": null,
    "nodeId": "builtin.code_merge",
    "nodeType": "CODE_MERGE",
    "nodeName": "代码合并",
    "logLevel": "NODE",
    "status": "WAITING_INPUT",
    "sort": 10,
    "summary": "src/App.java 存在冲突",
    "context": {},
    "result": {},
    "startedAt": 1717651234567,
    "finishedAt": null
  }
]
```

### Conflict List

`GET /devops/pipeline-run/{runId}/code-merge/conflicts`

```json
[
  {
    "filePath": "src/App.java",
    "conflictType": "TEXT",
    "status": "UNRESOLVED",
    "isText": true,
    "contentSize": 1024,
    "lineCount": 80,
    "unsupportedReason": null
  }
]
```

### Conflict Detail

`GET /devops/pipeline-run/{runId}/code-merge/conflict-detail?filePath=src/App.java`

```json
{
  "filePath": "src/App.java",
  "conflictType": "TEXT",
  "status": "UNRESOLVED",
  "baseContent": "...",
  "oursContent": "...",
  "theirsContent": "...",
  "workingContent": "...",
  "resultContent": "...",
  "contentTooLarge": false,
  "supportedActions": ["ACCEPT_OURS", "ACCEPT_THEIRS", "ACCEPT_BOTH", "MANUAL"]
}
```

### Save Resolution

`PUT /devops/pipeline-run/{runId}/code-merge/conflict-resolution`

```json
{
  "filePath": "src/App.java",
  "resolutionType": "MANUAL",
  "resolvedContent": "...",
  "comment": "保留新接口并兼容旧逻辑"
}
```

### Continue Merge

`POST /devops/pipeline-run/{runId}/code-merge/continue`

Behavior:

* disabled until all current text conflicts are resolved.
* refresh logs after success.

### Retry Current Change

`POST /devops/pipeline-run/{runId}/code-merge/retry-current-change`

Behavior:

* only visible for unsupported conflict or explicit retry state.
* calls backend to fetch current branch latest SHA and retry current merge.

### Cancel Run

`POST /devops/pipeline-run/{runId}/cancel`

Behavior:

* confirm modal required.
* refresh logs after cancel.

## UI States

### Status Mapping

* `PENDING`: grey waiting.
* `RUNNING`: blue spinner/progress.
* `WAITING_INPUT`: orange warning, action required.
* `SUCCESS`: green success.
* `FAILED`: red failure.
* `CANCELED`: grey canceled.

### Conflict File Status

* `UNRESOLVED`: action required.
* `RESOLVED`: saved, can continue if all resolved.
* `UNSUPPORTED`: external fix required.

## Frontend Data Handling

* Treat `context` and `result` as typed by `nodeType`.
* For unknown `nodeType`, show generic log row and summary.
* For `CODE_MERGE`, parse known fields into dedicated UI.
* Never render raw token, clone URL, or workspace path. Backend should not return them, frontend should not assume they exist.
* Poll run logs while run is `RUNNING` or `WAITING_INPUT`; stop polling on terminal states.

## Validation / UX Rules

* Save resolution disabled when result content is empty only if backend marks the file as requiring content. Empty file can be a valid resolution, so avoid blanket frontend rejection.
* Continue disabled until all current conflicts are `RESOLVED`.
* Unsupported conflict shows retry button, not editor.
* If conflict detail `contentTooLarge=true`, show fallback message instead of Monaco.
* If API returns state invalid, refresh logs and show current state.

## Implementation Order

1. Add pipeline run API client methods.
2. Add run detail route and timeline/log tree component.
3. Add `CODE_MERGE` panel for context/result rendering.
4. Add conflict list drawer/page.
5. Integrate Monaco conflict detail editor.
6. Add save resolution / continue / retry / cancel actions.
7. Add polling and terminal-state handling.
8. Hook release submit response to run detail entry.

## Acceptance Checklist

* [ ] Submit release navigates or links to run detail.
* [ ] Run detail renders node/step/event logs from generic API.
* [ ] Code merge context shows base branch, deploy branch, and branch merge items.
* [ ] Text conflict page loads three sides and editable result.
* [ ] Save resolution updates conflict status.
* [ ] Continue merge is available only when all conflicts are resolved.
* [ ] Unsupported conflict shows external-fix retry path.
* [ ] UI handles `RUNNING`, `WAITING_INPUT`, `SUCCESS`, `FAILED`, `CANCELED`.
