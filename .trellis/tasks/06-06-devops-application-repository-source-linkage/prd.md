# DevOps Application Repository Source Linkage

## Goal

DevOps 应用新增/修改时，后端接口要支持并约束“先选择代码源，再选择该代码源下代码仓库”的产品模型，避免应用只保存代码库提供方类型导致无法区分多个 GitLab/GitHub/Gitee 实例。

## What I Already Know

- 用户确认前端源码不在当前仓库，本任务只管理后端接口；需要前端修改时输出可执行 prompt。
- 当前后端已有代码源实体 `devops_repository_provider` 和接口 `/devops/repository-provider/projects?id=...`。
- 当前应用实体 `dev_application` 只保存 `repo_provider_type`、`repo_identifier`、`repo_url`，没有保存代码源编号。
- 当前应用仓库唯一性按 `repo_identifier` 全局租户唯一，无法允许不同代码源下同名仓库。

## Assumptions

- 应用保存时新增必填字段 `repositoryProviderId`，指向代码源实体。
- `repoProviderType` 保留在应用表中用于兼容展示和查询，但创建/更新时以后端读取代码源实体后的 `providerType` 为准。
- 不在应用创建/更新时主动远程请求 GitLab 校验仓库存在性；前端通过 `/devops/repository-provider/projects` 获取候选仓库，后端负责保存关联和本地一致性校验。

## Requirements

- `ApplicationSaveReqVO` 新增必填 `repositoryProviderId`。
- `ApplicationRespVO`、`ApplicationPageReqVO`、`ApplicationDO` 支持 `repositoryProviderId`。
- 创建/更新应用时校验代码源存在。
- 创建/更新应用时将 `repoProviderType` 设置为代码源实体的 `providerType`。
- 应用仓库重复校验改为同一代码源下 `repoIdentifier` 唯一，不同代码源允许相同 `repoIdentifier`。
- MySQL schema 草案同步 `dev_application.repository_provider_id` 字段、索引和唯一键。
- 输出前端改造 prompt。

## Acceptance Criteria

- [x] 创建应用必须传 `repositoryProviderId`。
- [x] 保存后的应用响应包含 `repositoryProviderId`。
- [x] 同一代码源下重复 `repoIdentifier` 仍报“代码库唯一标识已存在”。
- [x] 不同代码源下相同 `repoIdentifier` 可以分别创建。
- [x] `repoProviderType` 不信任前端传值，由后端从代码源实体推导。
- [x] DevOps 后端模块编译/测试通过。

## Out of Scope

- 不实现前端页面。
- 不新增代码源远程仓库缓存表。
- 不在应用保存时分页搜索全部远程仓库做强校验。
- 不补齐非 MySQL 数据库脚本，当前 DevOps schema 只存在于 `sql/mysql/devops.sql`。

## Technical Notes

- Backend specs read: `.trellis/spec/backend/index.md`, directory, database, error handling, cross-layer guide.
- Relevant backend files:
  - `ApplicationSaveReqVO`, `ApplicationRespVO`, `ApplicationPageReqVO`
  - `ApplicationDO`, `ApplicationMapper`, `ApplicationServiceImpl`
  - `RepositoryProviderService`, `RepositoryProviderServiceImpl`
  - `sql/mysql/devops.sql`
