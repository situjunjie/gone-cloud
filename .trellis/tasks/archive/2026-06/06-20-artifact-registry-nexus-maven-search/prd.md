# 制品仓库 Nexus Maven 搜索

## Goal

在 DevOps 模块新增“制品仓库”后端能力，支持配置 Nexus3 制品仓库实例，接入 Maven 类型仓库和 Docker 镜像仓库，并通过系统 API 搜索 Nexus 中的 Maven 制品和 Docker 镜像。

## Background

- 当前仓库已有 `yudao-module-devops` 模块，包含代码源、应用、变更、环境、流水线等 DevOps 后端能力。
- 现有 `devops_repository_provider` 是代码源模型，语义面向 GitLab 等代码托管平台，不适合直接复用为制品仓库。
- 当前代码仓库未包含前端源码，本任务范围先覆盖后端 API、数据库脚本、权限菜单 SQL 和测试。
- Nexus Repository Manager 3 提供 REST API，实例会暴露 OpenAPI 文档，可通过 `/service/rest/v1/repositories` 查询仓库，通过 `/service/rest/v1/search` 搜索组件。

## Requirements

- 新增制品仓库实例管理：
  - 支持创建、更新、删除、详情、分页查询。
  - 一期仅支持 `NEXUS3` 提供方。
  - 一期仅支持用户名密码认证，密码必须加密存储，并在响应中只返回掩码。
  - 支持连接检测，记录最近检测时间、状态和消息。
- 新增制品仓库内的仓库管理：
  - 支持从 Nexus 拉取仓库列表。
  - 保存或展示 Maven 类型仓库，即 Nexus `format=maven2`。
  - 支持 Docker 镜像仓库，即 Nexus `format=docker`。
  - 保存仓库名、格式、仓库类型、URL、online 状态等元数据。
  - 同一租户、同一 Nexus 实例下仓库名唯一。
- 新增 Maven 制品搜索：
  - 支持按 Nexus 实例、仓库、关键词、groupId、artifactId、version 搜索。
  - 后端实时调用 Nexus 搜索 API，不做本地制品索引。
  - 返回 Maven 坐标、仓库名、版本、baseVersion、classifier、extension、path、downloadUrl、lastModified 等前端展示需要的字段。
  - 翻页沿用 Nexus `continuationToken` 语义。
- 新增 Docker 镜像搜索：
  - 支持按 Nexus 实例、Docker 仓库、关键词、镜像名、Tag 搜索。
  - 后端实时调用 Nexus 搜索 API，不做本地镜像索引。
  - 返回仓库名、镜像名、Tag、manifest path、downloadUrl、lastModified 等前端展示需要的字段。
- 权限与菜单：
  - 在 DevOps 菜单下新增“制品仓库”菜单 SQL。
  - 新增查询、新增、修改、删除、搜索权限点。
- 质量：
  - 关键 Service 逻辑需要单元测试或可替代的 focused test。
  - Nexus HTTP 调用应通过可替换 client/factory 隔离，便于测试和后续扩展。

## Acceptance Criteria

- [ ] 导入 SQL 后存在制品仓库相关表，表字段包含租户、审计、逻辑删除字段，敏感密码字段符合加密存储模式。
- [ ] 管理后台 API 可创建、更新、删除、查询 Nexus3 制品仓库实例。
- [ ] 创建实例时密码必填；更新实例时密码为空表示沿用旧密码。
- [ ] 同一租户下制品仓库实例名称不能重复。
- [ ] `check` 接口会调用 Nexus API，成功或失败都更新最近检测字段，失败时返回业务错误。
- [ ] 仓库同步或列表接口接入 Maven 与 Docker 仓库，npm 等其他格式不进入一期结果。
- [ ] Maven 搜索接口可按仓库和 Maven 坐标条件调用 Nexus 搜索 API，并返回标准化结果。
- [ ] Docker 搜索接口可按仓库、关键词、镜像名、Tag 条件调用 Nexus 搜索 API，并返回标准化结果。
- [ ] 搜索接口支持 `continuationToken`，不强行转换成传统页码。
- [ ] 删除制品仓库实例时，如存在已保存的仓库记录，应阻止删除。
- [ ] 编译或 focused test 通过。

## Out of Scope

- 不实现 Maven 制品上传、删除、晋级发布。
- 不实现 Docker 镜像推送、删除、复制、清理策略。
- 不实现 Nexus 仓库创建、修改、删除。
- 不实现 npm 搜索，只预留模型扩展空间。
- 不实现本地制品索引、后台同步任务、缓存刷新策略。
- 不实现前端页面，本仓库当前没有前端源码。
- 不实现 Nexus 角色、用户、权限同步。

## Open Questions

- Nexus 搜索结果中的 Maven 维度字段是否需要和前端展示字段完全一致，需要实现时通过真实或 mock Nexus 响应再确认。
