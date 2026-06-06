# 应用变更创建同步创建 Git 分支

## Goal

创建应用变更时，同时在应用绑定的代码库中从默认基线分支创建对应 Git 分支，保证变更记录和真实代码分支一致。

## What I Already Know

* 应用详情页轻量创建变更接口已存在：`POST /devops/change/create-from-application`。
* 该接口后端生成 `branchName = feat/{branchSlug}-{openTimestamp}`。
* 应用已绑定代码源和代码库：`repositoryProviderId`、`repoIdentifier`、`defaultBranchName`。
* 当前接口只创建变更记录，没有创建远端 Git 分支。

## Requirements

* 创建变更时，在应用绑定代码库创建同名 Git 分支。
* 分支来源为应用的 `defaultBranchName`。
* 远端分支创建成功后再插入变更记录；失败则不插入变更记录。
* 复用现有代码源配置和认证能力。
* 对不支持的代码源类型返回业务错误。

## Acceptance Criteria

* [ ] `create-from-application` 成功时会调用代码源创建分支。
* [ ] Git 分支创建失败时不会插入变更记录。
* [ ] 已有重复分支/重复变更校验仍然生效。
* [ ] 单元测试覆盖成功创建、远端失败不落库、不支持代码源。

## Out Of Scope

* 不实现前端变更。
* 不支持未在后端代码源服务中建模的 Git 提供方能力。
* 不做分支删除补偿。

## Technical Notes

* 主要影响 `yudao-module-devops/yudao-module-devops-server`。
* 需查看 `RepositoryProviderServiceImpl` 和现有 GitLab API 封装。
