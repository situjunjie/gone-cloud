# 自动更新变更分支远端 commitId

## Goal

开发者向代码库推送代码后，平台自动更新变更关联分支的最新远端 commitId，让前端能判断“当前应用环境部署/流水线运行使用的 commitId”和“变更分支最新远端 commitId”是否一致，并在不一致时提示用户分支有更新、需要重新部署。

## What I Already Know

* 用户目标：代码推送后自动维护变更分支的最新远端 commitId，供前端判断是否需要重新部署。
* 当前 DevOps 模块只支持 GitLab 代码源：`RepositoryProviderTypeEnum.GITLAB`。
* `dev_change` 表已有 `latest_commit_sha`、`latest_commit_message`、`latest_commit_at` 字段，对应 `ChangeDO.latestCommitSha/latestCommitMessage/latestCommitAt`。
* `ChangeRespVO` 和应用发布页 `ApplicationReleaseBranchRespVO` 已返回 `latestCommitSha/latestCommitMessage/latestCommitAt`。
* 应用发布提交流水线时，`PipelineRunDO.commitSha` 来自 `ChangeDO.latestCommitSha`，可作为当次部署/运行使用的 commitId 记录。
* 当前没有 Git webhook 接收入口；`RepositoryProviderController` 只有管理后台代码源 CRUD、连接检查、项目列表接口。
* 安全配置支持 `@PermitAll` 免登录接口，可用于 webhook 回调；需要配合 webhook token 或其他校验避免公开写入。

## Assumptions

* MVP 只支持 GitLab Push Hook，因为现有代码源能力和依赖均以 GitLab 为主。
* GitLab Push Hook 使用 `project.path_with_namespace` 或 project id 与 `dev_application.repo_identifier` 定位应用，使用 `ref` 提取分支名并匹配 `dev_change.branch_name`。
* 只更新状态为“有效”的变更，已发布/废弃变更不因后续 push 改写最新 commit 信息。
* 删除分支事件或没有 commits 的 push 不更新 commit 信息。
* 如果 webhook 找不到匹配应用或变更，接口幂等返回成功并记录日志，不抛业务异常阻断 GitLab webhook。
* 本期不重构“每个变更在环境上的已部署 commit”模型，改为在 `dev_pipeline_run` 上新增一个 JSON 快照字段，记录本次发布提交时所选变更列表及其当时的 commit 信息。
* `changeId` 只能标识“是哪一个变更”，不能标识“该变更发布时对应的是哪一个版本”；因此运行快照中必须保留每个变更发布时的 `commitSha`。

## Open Questions

* webhook 鉴权：是否需要本期加入 GitLab `X-Gitlab-Token` 校验，并把 token 存在代码源配置里？
* 前端对比“当前环境已部署版本”时，应取最近一次成功的 `dev_pipeline_run`，还是取最近一次运行记录（包括失败/进行中）？

## Requirements

* 新增 GitLab Push Hook 接收入口，开发者 push 变更分支时平台可接收回调。
* 解析 push payload 中的分支名、最新 commit sha、commit message、commit timestamp、项目标识。
* 按代码源/项目标识定位应用，再按应用和分支名定位有效变更。
* 更新匹配变更的 `latestCommitSha/latestCommitMessage/latestCommitAt`。
* 在 `dev_pipeline_run` 新增 JSON 快照字段，记录本次发布提交时的变更列表，至少包含 `changeId`、`commitSha`，可选包含 `branchName`、`changeKey`、`title` 方便前端直接比对展示。
* 创建 `dev_pipeline_run` 时，从提交的变更集合和当时 `dev_change.latestCommitSha` 生成并持久化该快照。
* 发布页继续返回变更最新远端 commit 信息，同时返回当前环境对应运行记录中的变更快照，前端按 `changeId` 比较当前 `latestCommitSha` 与运行快照 `commitSha` 是否一致。
* `dev_change.latest_commit_sha/latest_commit_message/latest_commit_at` 作为 webhook 同步后的最新远端版本信息继续保留，不做清理。
* webhook 处理应具备幂等性：重复推送相同 commit 不产生错误。
* 对不支持事件、无效 payload、未匹配应用/变更的情况，返回成功或可控错误并记录必要日志，不能泄露 token。

## Acceptance Criteria

* [ ] GitLab push 到应用关联仓库的变更分支后，`dev_change.latest_commit_sha` 更新为 push payload 的 latest commit id。
* [ ] `latest_commit_message` 和 `latest_commit_at` 同步更新。
* [ ] 非变更分支、未关联应用、未匹配有效变更的 webhook 不改写任何变更。
* [ ] 创建发布运行记录时，`dev_pipeline_run` 会落下本次变更快照 JSON，且包含每个被提交变更的 `changeId` 和当时的 `commitSha`。
* [ ] 发布页环境详情或当前运行接口可同时拿到变更最新远端 commit 和已部署运行快照 commit，前端可按 `changeId` 做差异判断。
* [ ] 有单元测试覆盖成功更新、未匹配忽略、无 commit/删除分支忽略、运行快照生成正确、鉴权失败或鉴权跳过策略。

## Definition of Done

* Tests added/updated for affected service/controller logic.
* DevOps module Maven tests pass for the touched module where feasible.
* Error handling follows project `CommonResult` / `ServiceException` conventions.
* Logs avoid leaking webhook token and payload secrets.
* Rollout note: GitLab 项目需要配置 Push Hook 指向平台新增接口。

## Out of Scope

* 本期不实现 GitHub/Gitee webhook。
* 本期不主动轮询远端分支；只由 webhook 触发。
* 本期不改变前端提示逻辑，只保证后端字段可用于判断。
* 本期不把 `dev_pipeline_run` 彻底重构成多变更关联表；先用 JSON 快照字段承载本次发布提交的变更集。
* 本期不删除 `dev_change` 和 `dev_pipeline_run` 上现有 SHA 字段；先在兼容现有接口和逻辑的前提下补齐多变更快照能力。

## Technical Notes

* 相关表结构：`sql/mysql/devops.sql` 中 `dev_change` 已有 latest commit 字段，`dev_pipeline_run` 当前只有单变更视角的 `change_id/change_env_id/branch_name/commit_sha`，与一次运行可提交多个变更的行为存在错位。
* 如果仅在运行记录里保存 `changeId` 列表，无法判断“同一个 changeId 在部署后是否又有新的 push”，因此仍需保留每个变更在发布当时对应的 `commitSha` 快照。
* 相关接口：`ApplicationServiceImpl#getApplicationReleaseEnvDetail` 构造 `ApplicationReleaseBranchRespVO`，当前已透出 `latestCommitSha`。
* 相关创建逻辑：`ApplicationServiceImpl#submitApplicationReleaseBranch` 接收 `changeIds` 集合，但创建 `PipelineRunDO` 时只取第一个 change 作为 anchor；本期可补充 run 级 JSON 快照以表达完整变更集。
* 相关执行逻辑：`PipelineExecutionServiceImpl#executeCodeMerge` 实际按 `changeIds` 顺序逐个合并，说明运行上下文本身就是多变更。
* 相关创建逻辑：`ChangeServiceImpl#createChangeFromApplication` 创建 GitLab 分支，但没有初始化最新 commit。
* 相关依赖：`RepositoryProviderServiceImpl` 使用 gitlab4j，现有 provider 仅支持 GitLab。
* 相关安全机制：`YudaoWebSecurityConfigurerAdapter` 会将 `@PermitAll` 方法加入免登录 URL。
