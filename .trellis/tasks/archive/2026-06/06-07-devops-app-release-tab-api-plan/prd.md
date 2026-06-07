# 应用详情发布 Tab 接口规划

## Goal

在 DevOps 应用详情页新增“发布”tab。该 tab 内按应用已关联环境生成子 tab；每个环境页展示该应用环境关联的已发布流水线，并展示两个分支列表：已提交到当前应用环境的有效分支、不在当前应用环境的有效分支。先确定后端接口、权限点和前后端契约，便于前后端并行开发。

## What I Already Know

- 应用详情接口 `GET /devops/application/get?id={id}` 当前返回应用基础信息和 `ApplicationEnvRespVO` 列表。
- `ApplicationEnvRespVO` 有 `applicationEnvId`、`envId`、`displayOrder`、`deployBranchNamePattern`、`pipelineDefinitionId` 等字段，但没有环境名称、环境阶段等前端 tab 展示字段。
- 流水线定义以 `applicationEnvId` 为唯一业务维度，已有 `GET /devops/pipeline/get-by-application-env?applicationEnvId={id}`。
- 流水线版本 `PipelineDefinitionVersionRespVO` 已包含 `specJson`，`specJson` 是 `PipelineSpec` 的节点和边 DSL。
- 变更分支模型：`ChangeDO` 是应用级分支；`ChangeEnvDO` 是分支挂载到应用环境的关系。
- 有效分支建议定义为 `ChangeStatusEnum.ACTIVE`。
- “已提交到这个应用这个环境”建议定义为同应用 active change 且存在 `ChangeEnvDO(applicationEnvId, mountStatus=MOUNTED)`。
- “不在这个环境的这个应用”建议定义为同应用 active change 且不存在 mounted 关系；如存在 `UNMOUNTED` / `AUTO_CLEANED` 历史关系，也归入不在环境列表。

## Requirements

- 新增“发布”tab 所需的聚合查询接口，避免前端在应用、环境、流水线、变更之间做多次拼装。
- 环境子 tab 按 `ApplicationEnvDO.displayOrder ASC, id ASC` 排序。
- 流水线展示按已发布版本优先；没有已发布版本时返回空状态，不把草稿当成发布流水线展示。
- 流水线节点展示不复用画布坐标；后端返回按 DSL 拓扑排序后的线性节点，前端自左向右展示。
- 分支列表只展示 active 变更。
- 发布 tab 中“未在当前环境”的变更提交时，必须同时触发当前环境的已发布流水线，后端创建流水线运行记录并更新变更环境最近运行状态。
- 普通分支提交到环境、移出环境仍可复用现有 `mount-env` / `unmount-env` 接口，但发布 tab 的提交动作使用专用接口。

## Proposed APIs

### 1. 获得应用发布环境 Tab

`GET /devops/application/release/env-tabs?appId={appId}`

权限：`devops:application:query`

返回：`CommonResult<List<ApplicationReleaseEnvTabRespVO>>`

核心字段：

- `applicationEnvId`
- `appId`
- `envId`
- `envKey`
- `envName`
- `envStage`
- `infraType`
- `displayOrder`
- `deployBranchNamePattern`
- `pipelineDefinitionId`
- `hasPublishedPipeline`
- `status`

### 2. 获得某个应用环境的发布详情

`GET /devops/application/release/env-detail?applicationEnvId={applicationEnvId}`

权限：`devops:application:query`

返回：`CommonResult<ApplicationReleaseEnvDetailRespVO>`

核心字段：

- `env`: 同 `ApplicationReleaseEnvTabRespVO`
- `pipeline`: `ApplicationReleasePipelineRespVO`
- `mountedBranches`: `List<ApplicationReleaseBranchRespVO>`
- `unmountedBranches`: `List<ApplicationReleaseBranchRespVO>`

`pipeline` 字段：

- `definitionId`
- `definitionName`
- `definitionKey`
- `publishedVersionId`
- `versionNo`
- `versionName`
- `publishedAt`
- `publishedBy`
- `nodes`: 按拓扑排序后的节点列表
- `edges`: 可选，仅用于前端需要画简单连线时使用
- `emptyReason`: `NO_PIPELINE_DEFINITION` / `NO_PUBLISHED_VERSION`，正常有数据时为空

`nodes` 字段：

- `nodeId`
- `type`
- `name`
- `enabled`
- `displayOrder`
- `params`
- `timeoutSeconds`
- `retryTimes`
- `failStrategy`

`branch` 字段：

- `changeId`
- `changeKey`
- `title`
- `branchName`
- `sourceBaseBranchName`
- `ownerUserId`
- `latestCommitSha`
- `latestCommitMessage`
- `latestCommitAt`
- `createTime`
- `changeEnvId`
- `mountStatus`
- `mountedAt`
- `mountedBy`
- `lastPipelineRunId`
- `lastMergeStatus`
- `lastBuildStatus`
- `lastTestStatus`
- `lastDeployStatus`
- `lastErrorMessage`
- `approvalStatus`
- `includedInCurrentSnapshot`

### 3. 发布 tab 同步环境变更集合并触发流水线

`POST /devops/application/release/submit-branch`

权限：`devops:application:release-submit`

请求：

- `applicationEnvId`
- `changeIds`

返回：`CommonResult<ApplicationReleaseSubmitBranchRespVO>`

核心字段：

- `applicationEnvId`
- `mountedChangeIds`
- `unmountedChangeIds`
- `pipelineRunId`
- `runStatus`

行为：

- 接口语义是“同步当前环境最终部署变更集合”，不是“对单个变更执行动作”。
- 校验 `changeIds` 内所有变更存在且为 `ACTIVE`。
- 校验应用环境关系存在，且所有变更都属于该应用。
- 当 `changeIds` 非空时，校验应用环境存在已发布流水线版本；没有则返回业务错误。
- 请求中的 `changeIds` 会成为该环境最终的已提交部署集合：
  - 已存在且仍在集合中的变更保持/恢复为 `MOUNTED`；
  - 新进入集合的变更创建或恢复 `dev_change_env` 挂载关系；
  - 原来已挂载、但本次不在集合中的变更会被标记为 `UNMOUNTED`。
- 当 `changeIds` 非空时，创建一条 `dev_pipeline_run` 运行记录，并把目标集合中的 `dev_change_env.last_pipeline_run_id` 更新为本次运行记录，同时将最近构建状态置为运行中。
- 当 `changeIds` 为空数组时，表示该环境全部退出部署，不创建新的 `dev_pipeline_run`。

示例：

- 当前环境已提交 `A B C`，新增 `D E`：前端传 `A B C D E`
- 当前环境已提交 `A B C`，退出 `B C`：前端传 `A`
- 当前环境已提交 `A B C`，全部退出：前端传 `[]`

### 4. 普通提交分支到环境

复用现有：

`POST /devops/change/mount-env`

请求：

- `changeId`
- `applicationEnvId`

权限建议从当前 `devops:change:update` 调整为 `devops:change:mount-env`。

### 5. 从环境移出分支

复用现有：

`PUT /devops/change/unmount-env`

请求：

- `changeId`
- `applicationEnvId`
- `unmountedReason`

权限建议从当前 `devops:change:update` 调整为 `devops:change:unmount-env`。

## Permission Plan

- 发布 tab 查询：复用 `devops:application:query`，因为它是应用详情页的一部分。
- 发布 tab 提交并触发流水线：使用 `devops:application:release-submit`。
- 提交分支到环境：使用 SQL 中已有 `devops:change:mount-env`。
- 从环境移出分支：使用 SQL 中已有 `devops:change:unmount-env`。
- 流水线配置入口仍使用 `devops:pipeline:query` / `devops:pipeline:update` / `devops:pipeline:publish` 等已有权限。
- 当前已新增平台侧流水线运行记录；Jenkins queue/build 适配后续接入 `dev_pipeline_run`。

## Open Questions

- “有效分支”是否只等于 `ACTIVE`，还是 `RELEASED` 但未合主干的分支也要展示？当前建议只取 `ACTIVE`。
- `RELEASED` 是 `dev_change.status` 的业务状态，表示变更已发布；它不是 Git 分支类型。本轮有效分支仍按 `ACTIVE` 口径进入发布 tab。

## Acceptance Criteria

- 前端进入应用详情发布 tab 时，可以一次拿到环境 tab 所需名称、阶段、排序和是否有已发布流水线。
- 切换环境 tab 后，可以拿到该环境的线性流水线节点，以及两个分支列表。
- 无流水线定义或无已发布版本时，接口返回明确空状态，而不是报错。
- 已提交/未提交分支列表互斥且只包含当前应用的有效分支。
- 前端通过同一个发布 tab 专用接口即可完成新增部署、部分退出部署、全部退出部署三种动作。
- 未在当前环境的变更可以通过发布 tab 专用接口加入当前环境，并创建流水线运行记录。
- 提交和移出环境动作使用细粒度权限点。
