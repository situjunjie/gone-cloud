# DevOps Pipeline Execution Engine: Code Merge MVP

## Goal

实现 DevOps 流水线执行引擎的第一阶段「代码合并」。当用户在应用发布页把目标变更集合提交到某个环境后，平台创建一次 `dev_pipeline_run`，并执行固定的内置第一节点 `CODE_MERGE`：从应用默认基准分支创建本次运行唯一的部署分支，按用户提交的变更顺序逐个合并变更分支。无冲突时产出可供 Jenkins 构建的远端部署分支；有冲突时暂停运行并提供 Web 冲突解决能力。

这次设计同时建立通用流水线执行日志模型。代码合并、Jenkins 构建、部署、审批等节点都复用同一套 `pipeline_run_log`，不为每类节点创建专用运行表。

## Background

* 流水线定义由平台持有，Jenkins 只作为构建执行器。
* 现有发布入口是 `POST /devops/application/release/submit-branch`。
* 该入口已经在非空 `changeIds` 场景创建 `dev_pipeline_run`，并更新目标 `dev_change_env.last_pipeline_run_id`。
* 现有 `dev_pipeline_run` 仍有单变更兼容锚点字段，环境级发布会使用第一个变更作为 anchor。
* 代码合并是所有项目流水线固定第一步，不需要用户在可视化流水线里拖拽配置。
* 代码合并是临时执行过程，不应该为 `merge item`、`merge conflict`、`resolution` 分别建立长期专用 DO；否则后续每种节点都会膨胀出专用表，执行引擎会变死板。

## Core Decisions

* `dev_pipeline_run` 是一次流水线运行主记录。
* 新增 `dev_pipeline_run_log` 作为通用节点/步骤/事件日志表。
* 代码合并节点是 `node_type=CODE_MERGE` 的内置处理器。
* 节点私有运行上下文放在 `context_json`，节点输出放在 `result_json`。
* 大文本冲突内容不入库，后端通过 `workspaceKey + blobSha/filePath` 从 Git 工作区实时读取。
* 部署分支采用每次运行唯一分支，建议格式：`deploy/{appKey}/{envKey}/{pipelineRunId}`。
* 部署分支只在全部 merge 成功后 push 到远端；冲突期间不推送半成品分支。
* 变更分支在运行开始时冻结 SHA，正常合并使用冻结 SHA。
* 遇到不支持在线解决的冲突时，允许用户外部修复当前冲突变更分支后，在原 run 上刷新当前变更 SHA 并重试当前变更；已成功合并的前序变更不重跑，后续未合并变更仍使用运行开始时冻结的 SHA。

## Scope

### In Scope

* 新增通用流水线运行日志模型。
* 发布提交后自动触发 `CODE_MERGE` 节点。
* 本地隔离 Git 工作区执行真实 Git merge。
* 多变更分支顺序合并。
* 文本冲突的 Web 查询、保存解决、继续合并。
* 非文本/复杂冲突识别为 `UNSUPPORTED`，支持外部修复后刷新当前变更并重试。
* 运行详情页展示节点、步骤、事件日志。
* 冲突解决页展示冲突文件列表、三方内容和 result 编辑区。

### Out Of Scope

* Jenkins 构建触发、构建日志、产物记录。
* 部署执行。
* 完整审批节点实现。
* 多人协同编辑同一个冲突文件。
* 二进制、重命名、删除/修改、文件模式、submodule 的在线解决。
* AI 自动解决冲突。
* VS Code Web / code-server 集成。

## Functional Requirements

* 用户提交环境目标变更集合后，平台创建 `dev_pipeline_run` 并自动创建/执行 `CODE_MERGE` 节点日志。
* 同一应用环境同一时刻只允许一个执行中的发布 run。
* `CODE_MERGE` 开始时创建隔离工作区，不复用脏目录。
* 后端记录 base branch、base commit、deploy branch、workspace key、目标变更列表、冻结 commit SHA。
* 后端按提交顺序逐个 merge 变更分支，每个变更分支生成或更新一条 `STEP` 日志。
* 全部成功后 push 唯一部署分支到远端，`CODE_MERGE` 节点置为 `SUCCESS`，`result_json` 输出 deploy branch 和 deploy commit。
* 文本冲突发生时，`CODE_MERGE` 节点置为 `WAITING_INPUT`，后续节点不执行。
* 前端可读取冲突列表、冲突详情、保存 resolution、继续合并。
* `UNSUPPORTED` 冲突发生时，前端不提供在线编辑，但提供“已外部修复，刷新并重试当前变更”。
* 用户 abort/cancel 后，当前节点和 run 进入终止状态，工作区尽量清理。
* 日志、异常、接口响应不得泄露 access token、带 token 的 clone URL、超大文件内容。

## Data Model Requirement

### `dev_pipeline_run`

继续作为运行主记录。可在后续实现中追加通用输出字段：

* `deploy_branch_name`
* `deploy_commit_sha`

但第一版也可以只把这些输出放在 `dev_pipeline_run_log.result_json`，避免过早扩主表。

### `dev_pipeline_run_log`

一张表承接所有节点、步骤、事件：

* `id`
* `pipeline_run_id`
* `parent_id`
* `node_id`
* `node_type`
* `node_name`
* `log_level`: `NODE` / `STEP` / `EVENT`
* `status`: `PENDING` / `RUNNING` / `SUCCESS` / `FAILED` / `CANCELED` / `WAITING_INPUT`
* `sort`
* `started_at`
* `finished_at`
* `summary`
* `context_json`
* `result_json`
* `error_message`
* standard audit / tenant fields

### `CODE_MERGE.context_json`

```json
{
  "baseBranch": "master",
  "baseCommitSha": "abc",
  "deployBranch": "deploy/app/test/100",
  "workspaceKey": "run-100",
  "currentChangeId": 11,
  "items": [
    {
      "changeId": 11,
      "branchName": "feat/a",
      "commitSha": "def",
      "status": "CONFLICTING",
      "mergeCommitSha": null,
      "startedAt": 1717651234567,
      "finishedAt": null
    }
  ],
  "conflicts": [
    {
      "filePath": "src/App.java",
      "conflictType": "TEXT",
      "status": "UNRESOLVED",
      "baseBlobSha": "1",
      "oursBlobSha": "2",
      "theirsBlobSha": "3",
      "isText": true,
      "contentSize": 1024,
      "lineCount": 80,
      "charset": "UTF-8",
      "unsupportedReason": null
    }
  ],
  "resolutions": [
    {
      "filePath": "src/App.java",
      "resolutionType": "MANUAL",
      "resolvedBy": 1,
      "resolvedAt": 1717651234567
    }
  ]
}
```

### `CODE_MERGE.result_json`

```json
{
  "deployBranch": "deploy/app/test/100",
  "deployCommitSha": "abc",
  "mergedChangeIds": [11, 12]
}
```

## State Model

### Run Status

* `QUEUED`: run created, waiting for first node.
* `RUNNING`: at least one node running or waiting for user input.
* `SUCCESS`: all nodes succeeded.
* `FAILED`: node failed and cannot continue automatically.
* `CANCELED`: user canceled.

### Log Status

* `PENDING`: created but not started.
* `RUNNING`: executing.
* `WAITING_INPUT`: paused for user resolution or external fix.
* `SUCCESS`: finished successfully.
* `FAILED`: failed.
* `CANCELED`: canceled.

## Acceptance Criteria

* [ ] `submit-branch` creates run and starts `CODE_MERGE`.
* [ ] `dev_pipeline_run_log` records `NODE` / `STEP` / `EVENT` logs for code merge.
* [ ] Successful merge outputs unique deploy branch and deploy commit.
* [ ] Deploy branch is pushed only after all changes merge successfully.
* [ ] Text conflict pauses node with `WAITING_INPUT` and exposes conflict list/detail.
* [ ] Saved resolution updates `context_json.resolutions[]` and appends `EVENT` log.
* [ ] Continue writes resolved content to workspace and resumes current merge.
* [ ] Unsupported conflict can refresh only the current conflict change branch and retry in the same run.
* [ ] Same app env concurrent active run is rejected.
* [ ] Git credentials are not exposed in logs or API responses.

## Technical References

* Existing trigger: `ApplicationServiceImpl.submitApplicationReleaseBranch`.
* Existing run model: `PipelineRunDO`, `dev_pipeline_run`.
* Existing repository model: `ApplicationDO.repositoryProviderId`, `repoIdentifier`, `repoUrl`, `defaultBranchName`.
* Relevant specs:
  * `.trellis/spec/backend/devops-pipeline-guidelines.md`
  * `.trellis/spec/backend/devops-repository-guidelines.md`
  * `.trellis/spec/backend/devops-change-guidelines.md`
* Prior research:
  * `.trellis/tasks/archive/2026-06/06-06-devops-visual-pipeline-orchestration/research/release-branch-merge-conflict-resolution.md`
