# 应用详情页轻量新建变更接口

## Goal

为 DevOps 应用详情页的“关联变更” tab 提供一个轻量新建变更接口，让前端只提交应用编号、变更名称和分支 slug，后端统一生成完整分支名、变更标识、基线分支和负责人。

## What I Already Know

* 应用详情接口已存在：`GET /admin-api/devops/application/get?id={appId}`。
* 应用关联变更分页接口已存在：`GET /admin-api/devops/change/page?appId={appId}`。
* 现有变更创建接口 `POST /admin-api/devops/change/create` 要求 `changeKey/title/branchName/sourceBaseBranchName/ownerUserId`。
* 应用 DO/VO 中已有 `appKey/defaultBranchName/ownerUserId` 等字段，可作为后端默认值来源。

## Requirements

* 新增应用详情页轻量新建变更接口。
* 入参包含 `appId`、`title`、`branchSlug`、`openTimestamp`。
* 后端生成完整分支名：`feat/{branchSlug}-{openTimestamp}`。
* 分支 slug 不允许中文，并且最终分支名必须满足 Git 分支名基本规范。
* `sourceBaseBranchName` 使用应用的 `defaultBranchName`。
* `ownerUserId` 使用当前登录用户。
* `changeKey` 后端生成，避免前端承担业务标识规则。

## Acceptance Criteria

* [ ] 前端可通过一个接口完成应用详情页新建变更。
* [ ] 非法分支 slug 会返回业务错误。
* [ ] 成功创建后复用现有变更唯一性校验。
* [ ] 单元测试覆盖成功创建、非法分支、重复分支。

## Out Of Scope

* 不实现前端页面。
* 不实际调用 Git 服务创建远端分支。
* 不调整现有变更列表、详情、发布、废弃接口。

## Technical Notes

* 主要影响 `yudao-module-devops/yudao-module-devops-server`。
* 需要遵循现有 Controller + VO + Service + Mapper 分层。
