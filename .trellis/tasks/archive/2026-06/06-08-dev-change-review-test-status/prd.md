# dev_change 增加测试与代码审核功能

## Goal

为 DevOps 变更 `dev_change` 增加测试状态与代码审核状态，并提供代码审核 diff 页面所需的后端接口。支持在变更详情和列表中读取状态、打开独立代码审核页面查看 diff、完成审核时记录审核通过 commit，并在变更分支出现新提交时自动使已有测试/审核结论失效。

## What I Already Know

* 当前变更实体为 `ChangeDO`，表为 `dev_change`，位于 `yudao-module-devops`。
* 当前已有 GitLab push hook 同步逻辑：`ChangeServiceImpl#syncLatestCommitFromGitLabPushHook` 会根据仓库、分支匹配 active 变更并更新 `latest_commit_sha/message/at`。
* 当前变更接口集中在 `ChangeController`，已有 `create/update/get/page/release/discard/mount-env/unmount-env`。
* 当前 SQL 主维护文件包含 `sql/mysql/devops.sql`、`devops-dict.sql`、`devops-menu.sql`。

## Assumptions

* 测试通过标记使用 `Integer`/`tinyint`：`0` 未测试或未通过，`1` 已通过。
* 测试通过 commitId 存储为提交 SHA 字段，命名为 `test_passed_commit_sha`。
* 审核状态使用 `Integer`/`tinyint`：`0` 打开中，`1` 进行中，`2` 审核通过。
* 审核通过 commitId 存储为提交 SHA 字段，命名为 `code_review_passed_commit_sha`。
* 分支有新提交时保留测试者和审核者人选，只重置结论类字段：测试通过标记回 `0`，测试通过 commit 清空，审核状态回 `0`，审核通过 commit 清空。
* 新增“设置测试者”和“设置代码审核者”接口。
* 代码审核 diff 基准：如果曾经审核通过，使用 `code_review_passed_commit_sha`；否则使用变更创建时记录的 `source_base_branch_name`，为空时 fallback 到应用 `default_branch_name`。
* 代码审核 diff 目标：优先使用 `latest_commit_sha`，为空时使用变更分支名展示 diff；审核完成必须有 `latest_commit_sha`，否则无法记录精确审核通过 commit。

## Requirements

* `dev_change` 新增测试者用户编号、测试通过标记、测试通过 commit SHA。
* `dev_change` 新增代码审核者用户编号、代码审核状态、审核通过 commit SHA。
* 新增简单设置测试者接口，入参仅包含变更 id 和测试者用户 id。
* 设置测试者前必须校验变更存在且处于 active 状态。
* 创建变更时测试通过默认 `0`，审核状态默认 `0`。
* GitLab push hook 发现变更分支有新提交时，更新最新提交信息，同时重置测试和审核结论。
* 新增代码审核 diff 查询接口，返回比较基准、目标和文件 diff 列表。
* 新增代码审核开始接口，前端进入审核页时可调用，将打开中状态置为进行中。
* 新增代码审核通过接口，记录审核通过 commit SHA 为当前 `latest_commit_sha`，审核状态置为通过。
* 新字段需要在响应 VO 中返回，并在保存 VO 中遵循现有 MapStruct/VO 风格。
* 更新 MySQL 初始化 SQL；如发现测试建表 SQL维护了同表，也同步更新。
* 前端在应用详情页变更 tab 列表增加“代码审核”按钮，打开无骨架新标签页；页面左侧是 diff 文件列表，右侧是 diff 内容。
* 前端文件列表停留 2 秒后本地标记为已查阅，不持久化数据库；所有 diff 文件查阅完成后才能点击“审核完成”。

## Acceptance Criteria

* [x] `ChangeDO`、`ChangeRespVO` 包含新增字段。
* [x] 有设置测试者请求 VO 和 `PUT /devops/change/set-tester` 接口，权限沿用 `devops:change:update`。
* [x] 有设置代码审核者请求 VO 和 `PUT /devops/change/set-code-reviewer` 接口，权限沿用 `devops:change:update`。
* [x] 有 `GET /devops/change/code-review-diff?id=...` 接口，返回基准 ref、目标 ref 和文件 diff 列表。
* [x] 有 `PUT /devops/change/code-review-start` 接口，打开中状态可置为进行中。
* [x] 有 `PUT /devops/change/code-review-approve` 接口，审核通过后写入当前最新 commit SHA。
* [x] `ChangeService`/`ChangeServiceImpl` 提供设置测试者方法。
* [x] GitLab push hook 同步新 commit 时，测试通过标记变为 `0`，测试通过 commit 清空，审核状态变为打开中，审核通过 commit 清空。
* [x] 单元测试覆盖设置测试者、设置审核者、diff 基准选择、审核状态流转和新提交重置状态。
* [x] 前端实现受限：当前仓库未包含实际前端源码时，需提供可执行接口契约和前端 agent prompt。
* [ ] 受影响模块编译/测试通过，至少运行 devops server 相关测试。

## Definition Of Done

* Tests added/updated.
* Maven test/compile for affected module passes, or failure原因明确记录。
* SQL、DO、VO、Service、Controller 跨层字段一致。
* 不引入不相关重构。

## Out Of Scope

* 不新增“测试通过”的独立接口。
* 不持久化单文件查阅状态。
* 不做在线评论、逐行评论、驳回或多级审批流。
* 不在缺少前端源码的当前仓库中伪造前端实现。
* 不改非 MySQL 引擎的旧全量 SQL，除非本任务中发现该 DevOps 表已在对应文件独立维护。

## Technical Notes

* Relevant specs:
  * `.trellis/spec/backend/index.md`
  * `.trellis/spec/backend/database-guidelines.md`
  * `.trellis/spec/backend/devops-change-guidelines.md`
* Relevant files:
  * `yudao-module-devops/yudao-module-devops-server/src/main/java/cn/iocoder/yudao/module/devops/dal/dataobject/change/ChangeDO.java`
  * `yudao-module-devops/yudao-module-devops-server/src/main/java/cn/iocoder/yudao/module/devops/controller/admin/change/ChangeController.java`
  * `yudao-module-devops/yudao-module-devops-server/src/main/java/cn/iocoder/yudao/module/devops/service/change/ChangeServiceImpl.java`
  * `yudao-module-devops/yudao-module-devops-server/src/test/java/cn/iocoder/yudao/module/devops/service/change/ChangeServiceImplTest.java`
  * `sql/mysql/devops.sql`
