# Journal - 司徒俊杰 (Part 1)

> AI development session journal
> Started: 2026-06-03

---



## Session 1: Jenkins Compose Deployment

**Date**: 2026-06-05
**Task**: Jenkins Compose Deployment
**Branch**: `master-gigi`

### Summary

Configured Jenkins checkbox-based microservice builds, Maven tool fallback, SSH-based Docker Compose deployment to /data/situ/gone, external .env handling, selected-service compose deployment, and removed yudao-server monolith from the compose flow while fixing JAVA_OPTS quoting.

### Main Changes

- Added `POST /devops/change/create-from-application` for application-detail lightweight change creation.
- Backend now derives `branchName`, `changeKey`, `sourceBaseBranchName`, `ownerUserId`, and active status for this flow.
- Added Git branch-name validation, the `CHANGE_BRANCH_NAME_INVALID` error code, focused service tests, and backend spec documentation.

### Git Commits

| Hash | Message |
|------|---------|
| `47e174731` | (see git log) |
| `3424ed3cf` | (see git log) |
| `825b7b4b3` | (see git log) |
| `7f6988695` | (see git log) |
| `8848cd1f6` | (see git log) |
| `30c5bc7e2` | (see git log) |
| `28a3cd76d` | (see git log) |
| `d28ce558e` | (see git log) |

### Testing

- [OK] `mvn -pl yudao-module-devops/yudao-module-devops-server -am -Dtest=ChangeServiceImplTest -Dsurefire.failIfNoSpecifiedTests=false test`
- [OK] `mvn -pl yudao-module-devops/yudao-module-devops-server -am -Dsurefire.failIfNoSpecifiedTests=false test`

### Status

[OK] **Completed**

### Next Steps

- None - task complete


## Session 2: Demo Mode Configuration

**Date**: 2026-06-06
**Task**: Demo Mode Configuration
**Branch**: `master-gigi`

### Summary

Investigated the demo-mode write-blocking response, traced it to yudao.demo and DemoFilter, and recorded the recent configuration commits that disable demo mode.

### Main Changes

- Added `POST /devops/change/create-from-application` for application-detail lightweight change creation.
- Backend derives `branchName`, `changeKey`, `sourceBaseBranchName`, `ownerUserId`, and active status for this flow.
- Added Git branch-name validation, the `CHANGE_BRANCH_NAME_INVALID` error code, focused service tests, and backend spec documentation.

### Git Commits

| Hash | Message |
|------|---------|
| `9134339ba` | (see git log) |
| `c235cbe4a` | (see git log) |

### Testing

- [OK] `mvn -pl yudao-module-devops/yudao-module-devops-server -am -Dtest=ChangeServiceImplTest -Dsurefire.failIfNoSpecifiedTests=false test`
- [OK] `mvn -pl yudao-module-devops/yudao-module-devops-server -am -Dsurefire.failIfNoSpecifiedTests=false test`

### Status

[OK] **Completed**

### Next Steps

- None - task complete


## Session 3: DevOps module CRUD and deployment setup

**Date**: 2026-06-06
**Task**: DevOps module CRUD and deployment setup
**Branch**: `master-gigi`

### Summary

Implemented the DevOps module CRUD APIs, added DevOps standalone service resources and Docker deployment wiring, disabled BPM standalone deployment, and aligned local environment YAML files with the provided infrastructure configuration.

### Main Changes

- Added repository-provider branch creation support through GitLab4J.
- Updated application-detail change creation to create the GitLab branch before inserting `dev_change`.
- Added `REPOSITORY_PROVIDER_GITLAB_BRANCH_CREATE_FAIL` for remote branch creation failures.
- Updated change and repository-provider service tests plus the DevOps change backend contract.

### Git Commits

| Hash | Message |
|------|---------|
| `c624b5378` | (see git log) |
| `19dd412ed` | (see git log) |
| `d2746aada` | (see git log) |
| `30ded6720` | (see git log) |

### Testing

- [OK] `mvn -pl yudao-module-devops/yudao-module-devops-server -am -Dtest=ChangeServiceImplTest,RepositoryProviderServiceImplTest -Dsurefire.failIfNoSpecifiedTests=false test`
- [OK] `mvn -pl yudao-module-devops/yudao-module-devops-server -am -Dsurefire.failIfNoSpecifiedTests=false test`

### Status

[OK] **Completed**

### Next Steps

- None - task complete


## Session 4: DevOps 代码源 GitLab 接入

**Date**: 2026-06-06
**Task**: DevOps 代码源 GitLab 接入
**Branch**: `master-gigi`

### Summary

实现 devops_repository_provider 代码源实体 CRUD，接入 GitLab4J Access Token 连接检测和项目列表，并补充密钥字段加密持久化规范。

### Main Changes

- Produced frontend-facing API contract and implementation prompt for Vue Flow based visual pipeline design.
- Added DevOps pipeline definition and version persistence with draft save, validation, publish, version list, and Jenkinsfile preview APIs.
- Added backend DSL validation, backend-owned command templates, disabled future approval/deploy nodes, and Jenkinsfile generation from approved templates.
- Added MySQL schema, dict, menu permissions, focused unit tests, and backend code-spec for future pipeline execution work.

### Git Commits

| Hash | Message |
|------|---------|
| `33fcdf759` | (see git log) |

### Testing

- [OK] `mvn -pl yudao-module-devops/yudao-module-devops-server -am -DskipTests compile`
- [OK] `mvn -pl yudao-module-devops/yudao-module-devops-server -am -Dtest='*Pipeline*Test,JenkinsfileGeneratorServiceImplTest' -Dsurefire.failIfNoSpecifiedTests=false test`
- [OK] `mvn -pl yudao-module-devops/yudao-module-devops-server -am -Dsurefire.failIfNoSpecifiedTests=false test`

### Status

[OK] **Completed**

### Next Steps

- None - task complete


## Session 5: DevOps 菜单权限 SQL

**Date**: 2026-06-06
**Task**: DevOps 菜单权限 SQL
**Branch**: `master-gigi`

### Summary

新增 DevOps 应用、环境、变更页面菜单与按钮权限导入 SQL，使用 SQL 变量解析父级菜单 ID，避免固定 ID 冲突。

### Main Changes

(Add details)

### Git Commits

| Hash | Message |
|------|---------|
| `6d6b0ebce` | (see git log) |

### Testing

- [OK] (Add test results)

### Status

[OK] **Completed**

### Next Steps

- None - task complete


## Session 6: 接入 Fabric8 Kubernetes 环境连接

**Date**: 2026-06-06
**Task**: 接入 Fabric8 Kubernetes 环境连接
**Branch**: `master-gigi`

### Summary

为 DevOps 环境接入 Fabric8 Kubernetes Client，新增 K8S 连接配置加密存储、连接检测、Namespace 查询接口，并记录环境连接器扩展规范。

### Main Changes

(Add details)

### Git Commits

| Hash | Message |
|------|---------|
| `aec53b305` | (see git log) |
| `192ccf919` | (see git log) |

### Testing

- [OK] (Add test results)

### Status

[OK] **Completed**

### Next Steps

- None - task complete


## Session 7: DevOps K8S 环境 Namespace

**Date**: 2026-06-06
**Task**: DevOps K8S 环境 Namespace
**Branch**: `master-gigi`

### Summary

支持 K8S 类型环境指定部署 Namespace，将 namespace 存入加密 infra_config 并在响应中返回摘要；补充连接器与转换器测试，更新 DevOps infra 规范。

### Main Changes

(Add details)

### Git Commits

| Hash | Message |
|------|---------|
| `7650363d4` | (see git log) |

### Testing

- [OK] (Add test results)

### Status

[OK] **Completed**

### Next Steps

- None - task complete


## Session 8: DevOps application repository source linkage

**Date**: 2026-06-06
**Task**: DevOps application repository source linkage
**Branch**: `master-gigi`

### Summary

Bound DevOps applications to repository providers, scoped repository uniqueness by code source, protected provider deletion, added tests and backend spec.

### Main Changes

(Add details)

### Git Commits

| Hash | Message |
|------|---------|
| `66469b31d` | (see git log) |

### Testing

- [OK] (Add test results)

### Status

[OK] **Completed**

### Next Steps

- None - task complete


## Session 9: 应用详情页轻量新建变更接口

**Date**: 2026-06-06
**Task**: 应用详情页轻量新建变更接口
**Branch**: `master-gigi`

### Summary

新增应用详情页轻量创建变更接口，后端生成分支名、变更标识、基线分支和负责人；补充分支名校验、服务测试和 DevOps 变更接口规范。

### Main Changes

(Add details)

### Git Commits

| Hash | Message |
|------|---------|
| `aaf390095` | (see git log) |

### Testing

- [OK] (Add test results)

### Status

[OK] **Completed**

### Next Steps

- None - task complete


## Session 10: 创建变更同步创建 Git 分支

**Date**: 2026-06-06
**Task**: 创建变更同步创建 Git 分支
**Branch**: `master-gigi`

### Summary

创建应用变更时同步调用应用代码源创建 GitLab 分支，远端分支创建成功后才插入变更记录；补充分支创建错误码、代码源服务封装、单元测试和后端契约。

### Main Changes

(Add details)

### Git Commits

| Hash | Message |
|------|---------|
| `74a1703cd` | (see git log) |

### Testing

- [OK] (Add test results)

### Status

[OK] **Completed**

### Next Steps

- None - task complete


## Session 11: DevOps visual pipeline orchestration MVP

**Date**: 2026-06-07
**Task**: DevOps visual pipeline orchestration MVP
**Branch**: `master-gigi`

### Summary

Implemented DevOps visual pipeline definition MVP: persisted draft/published pipeline versions, backend DSL validation, Jenkinsfile preview generation, frontend API/prompt docs, SQL/menu/dict bootstrap, focused tests, and backend pipeline code-spec.

### Main Changes

(Add details)

### Git Commits

| Hash | Message |
|------|---------|
| `e739c324f` | (see git log) |
| `5297e1a9c` | (see git log) |
| `78d754dfd` | (see git log) |

### Testing

- [OK] (Add test results)

### Status

[OK] **Completed**

### Next Steps

- None - task complete


## Session 12: Fix DevOps pipeline designer menu permissions

**Date**: 2026-06-07
**Task**: Fix DevOps pipeline designer menu permissions
**Branch**: `master-gigi`

### Summary

Adjusted DevOps pipeline designer menu bootstrap SQL to match the frontend hidden designer route, documented the menu contract, and recorded the task artifacts.

### Main Changes

(Add details)

### Git Commits

| Hash | Message |
|------|---------|
| `aa266f1ac` | (see git log) |
| `db70e3448` | (see git log) |
| `66700f363` | (see git log) |

### Testing

- [OK] (Add test results)

### Status

[OK] **Completed**

### Next Steps

- None - task complete


## Session 13: DevOps application release tab APIs

**Date**: 2026-06-07
**Task**: DevOps application release tab APIs
**Branch**: `master-gigi`

### Summary

Added application release tab read APIs, release submit pipeline run creation, and target-set sync semantics for deployed changes; updated frontend prompt, PRD, backend spec, and tests.

### Main Changes

(Add details)

### Git Commits

| Hash | Message |
|------|---------|
| `3faed79e1` | (see git log) |
| `2d129c627` | (see git log) |
| `5de7d86b0` | (see git log) |

### Testing

- [OK] (Add test results)

### Status

[OK] **Completed**

### Next Steps

- None - task complete


## Session 14: Implement devops code merge execution

**Date**: 2026-06-07
**Task**: Implement devops code merge execution
**Branch**: `master-gigi`

### Summary

Implemented code-merge pipeline execution MVP, added release-page current-run polling API, fixed no-op merge success handling, and recorded backend execution contracts.

### Main Changes

(Add details)

### Git Commits

| Hash | Message |
|------|---------|
| `8328990a5` | (see git log) |
| `9a2db8b41` | (see git log) |

### Testing

- [OK] (Add test results)

### Status

[OK] **Completed**

### Next Steps

- None - task complete


## Session 15: Cache release current-run API

**Date**: 2026-06-07
**Task**: Cache release current-run API
**Branch**: `master-gigi`

### Summary

Added declarative Spring Cache for the DevOps application release current-run polling API, cache eviction on release submit/pipeline publish/execution mutations, focused tests, and DevOps pipeline spec notes.

### Main Changes

(Add details)

### Git Commits

| Hash | Message |
|------|---------|
| `1a106b203` | (see git log) |

### Testing

- [OK] (Add test results)

### Status

[OK] **Completed**

### Next Steps

- None - task complete


## Session 16: GitLab webhook 更新变更分支 commit

**Date**: 2026-06-08
**Task**: GitLab webhook 更新变更分支 commit
**Branch**: `master-gigi`

### Summary

实现 GitLab Push Hook 同步变更分支最新 commit，并在流水线运行中记录发布变更快照供前端判断是否需要重新部署。

### Main Changes

(Add details)

### Git Commits

| Hash | Message |
|------|---------|
| `a8a134547` | (see git log) |

### Testing

- [OK] (Add test results)

### Status

[OK] **Completed**

### Next Steps

- None - task complete


## Session 17: 完成变更代码审核功能

**Date**: 2026-06-08
**Task**: 完成变更代码审核功能
**Branch**: `master-gigi`

### Summary

为 dev_change 增加代码审核人、审核状态、GitLab compare diff 接口与审核完成流转，补充 SQL、规范文档和定向测试。

### Main Changes

(Add details)

### Git Commits

| Hash | Message |
|------|---------|
| `63bfeee75` | (see git log) |

### Testing

- [OK] (Add test results)

### Status

[OK] **Completed**

### Next Steps

- None - task complete


## Session 18: 修复 DevOps 服务部署启动问题

**Date**: 2026-06-08
**Task**: 修复 DevOps 服务部署启动问题
**Branch**: `master-gigi`

### Summary

为 devops-server 补齐模块级安全配置，修复独立启动 List Bean 缺失；同时在 DevOps 运行时镜像安装 git，解决容器内代码合并命令找不到 git。

### Main Changes

(Add details)

### Git Commits

| Hash | Message |
|------|---------|
| `f39e7085e` | (see git log) |
| `9d89411ea` | (see git log) |

### Testing

- [OK] (Add test results)

### Status

[OK] **Completed**

### Next Steps

- None - task complete


## Session 19: Jenkins pipeline callback runtime

**Date**: 2026-06-08
**Task**: Jenkins pipeline callback runtime
**Branch**: `master-gigi`

### Summary

Implemented and debugged Jenkins pipeline callback execution: fixed admin-api anonymous callback security, added generic Jenkins node lifecycle handler for CHECKOUT and build stages, preserved tenant id on callback-created run logs, strengthened callback tests, and removed local Jenkins secrets from dev config.

### Main Changes

(Add details)

### Git Commits

| Hash | Message |
|------|---------|
| `3f6092421` | (see git log) |

### Testing

- [OK] (Add test results)

### Status

[OK] **Completed**

### Next Steps

- None - task complete


## Session 20: 复用 DevOps 部署分支

**Date**: 2026-06-08
**Task**: 复用 DevOps 部署分支
**Branch**: `master-gigi`

### Summary

实现 release submit 在无剔除变更时复用最近成功部署分支，有剔除时创建时间戳部署分支；同步 Git checkout 起点、测试和后端流水线规范。

### Main Changes

(Add details)

### Git Commits

| Hash | Message |
|------|---------|
| `d87a46d94` | (see git log) |

### Testing

- [OK] (Add test results)

### Status

[OK] **Completed**

### Next Steps

- None - task complete


## Session 21: DevOps Jenkins 节点与发布分支规则

**Date**: 2026-06-09
**Task**: DevOps Jenkins 节点与发布分支规则
**Branch**: `master-gigi`

### Summary

实现 Jenkins-compatible pipeline nodes、shared library/Jenkinsfile 配套，以及发布分支规则调整：release/{envKey}/{timestamp} 命名、无下掉变更时复用最近 release 分支、有下掉变更新建分支、空变更从基线直接发布。

### Main Changes

(Add details)

### Git Commits

| Hash | Message |
|------|---------|
| `8e432d2d0` | (see git log) |

### Testing

- [OK] (Add test results)

### Status

[OK] **Completed**

### Next Steps

- None - task complete


## Session 22: Jenkins console stream API

**Date**: 2026-06-09
**Task**: Jenkins console stream API
**Branch**: `master-gigi`

### Summary

Added realtime Jenkins console SSE streaming API for pipeline runs, Jenkins progressiveText client support, focused tests, and frontend integration guidance.

### Main Changes

(Add details)

### Git Commits

| Hash | Message |
|------|---------|
| `e4f9d08b0` | (see git log) |

### Testing

- [OK] (Add test results)

### Status

[OK] **Completed**

### Next Steps

- None - task complete


## Session 23: Jenkins tool dropdown backend

**Date**: 2026-06-09
**Task**: Jenkins tool dropdown backend
**Branch**: `master-gigi`

### Summary

Added Jenkins tool list API for JDK/Maven dropdowns, wired pipeline node schema remote select metadata, documented the contract, and verified focused DevOps tests.

### Main Changes

(Add details)

### Git Commits

| Hash | Message |
|------|---------|
| `1d8289b2e` | (see git log) |

### Testing

- [OK] (Add test results)

### Status

[OK] **Completed**

### Next Steps

- None - task complete


## Session 24: Jenkins tool descriptor fallback

**Date**: 2026-06-09
**Task**: Jenkins tool descriptor fallback
**Branch**: `master-gigi`

### Summary

Fixed Jenkins tool lookup to retry JDK and Maven descriptor implementation ids when short descriptor paths return 404, updated the DevOps pipeline spec, and verified focused Jenkins client tests.

### Main Changes

(Add details)

### Git Commits

| Hash | Message |
|------|---------|
| `9d6edd245` | (see git log) |

### Testing

- [OK] (Add test results)

### Status

[OK] **Completed**

### Next Steps

- None - task complete


## Session 25: Broaden Jenkins tool lookup fallback

**Date**: 2026-06-09
**Task**: Broaden Jenkins tool lookup fallback
**Branch**: `master-gigi`

### Summary

Expanded Jenkins tool lookup to try root and manage descriptor routes, correct Maven installation descriptor ids, and scriptText fallback; updated spec and verified focused Jenkins client tests.

### Main Changes

(Add details)

### Git Commits

| Hash | Message |
|------|---------|
| `81477d332` | (see git log) |

### Testing

- [OK] (Add test results)

### Status

[OK] **Completed**

### Next Steps

- None - task complete


## Session 26: DevOps container deployment node

**Date**: 2026-06-10
**Task**: DevOps container deployment node
**Branch**: `master-gigi`

### Summary

Implemented platform container deployment node with deployment order tracking, Kubernetes rollout/pod status details, async release branch merge dispatch, focused tests, and frontend API guidance.

### Main Changes

(Add details)

### Git Commits

| Hash | Message |
|------|---------|
| `0e4f00d53` | (see git log) |

### Testing

- [OK] (Add test results)

### Status

[OK] **Completed**

### Next Steps

- None - task complete


## Session 27: Kubernetes Pod terminal

**Date**: 2026-06-10
**Task**: Kubernetes Pod terminal
**Branch**: `master-gigi`

### Summary

Implemented generic Kubernetes Pod terminal WebSocket backend with authenticated token access, tenant context restoration, stdin/TTY/resize handling, shell auto-detection preferring bash with ash/sh fallback, and focused service tests.

### Main Changes

(Add details)

### Git Commits

| Hash | Message |
|------|---------|
| `ef522829b` | (see git log) |

### Testing

- [OK] (Add test results)

### Status

[OK] **Completed**

### Next Steps

- None - task complete


## Session 28: K8S 环境大盘接口

**Date**: 2026-06-10
**Task**: K8S 环境大盘接口
**Branch**: `master-gigi`

### Summary

新增 DevOps K8S 环境大盘后端接口：dashboard、pods、deployments、services；补齐响应 VO、connector 资源转换、隐藏路由 SQL、单元测试和 DevOps infra 规范。

### Main Changes

(Add details)

### Git Commits

| Hash | Message |
|------|---------|
| `e71534d6b` | (see git log) |

### Testing

- [OK] (Add test results)

### Status

[OK] **Completed**

### Next Steps

- None - task complete


## Session 29: DevOps deploy and env update fixes

**Date**: 2026-06-11
**Task**: DevOps deploy and env update fixes
**Branch**: `master-gigi`

### Summary

Implemented raw manifest container deploy support, fixed application environment differential updates, and started container-only deployment pipelines directly after code merge.

### Main Changes

(Add details)

### Git Commits

| Hash | Message |
|------|---------|
| `6b081f664` | (see git log) |
| `c1fb06280` | (see git log) |
| `2a492fe83` | (see git log) |

### Testing

- [OK] (Add test results)

### Status

[OK] **Completed**

### Next Steps

- None - task complete


## Session 30: Remove unused DevOps application env fields

**Date**: 2026-06-11
**Task**: Remove unused DevOps application env fields
**Branch**: `master-gigi`

### Summary

Removed unused application environment deploy branch pattern and approval configuration fields from backend API, persistence model, MySQL schema, and tests; added database cleanup guidance.

### Main Changes

(Add details)

### Git Commits

| Hash | Message |
|------|---------|
| `22aeac831` | (see git log) |

### Testing

- [OK] (Add test results)

### Status

[OK] **Completed**

### Next Steps

- None - task complete


## Session 31: Pipeline Approval Node

**Date**: 2026-06-11
**Task**: Pipeline Approval Node
**Branch**: `master-gigi`

### Summary

新增流水线审批节点(APPROVAL)功能:实现 PipelineApprovalService 处理审批发起与状态回调,集成 BPM 流程实例 API,记录 processInstanceId 到节点日志 context 供前端拼接审批详情页链接;引入 PipelinePlatformNodeAdvanceService 处理平台侧节点推进;新增审批上下文、状态事件监听器及错误码;重构执行服务与 Jenkins 回调服务并补充测试覆盖。

### Main Changes

(Add details)

### Git Commits

| Hash | Message |
|------|---------|
| `680802121` | (see git log) |

### Testing

- [OK] (Add test results)

### Status

[OK] **Completed**

### Next Steps

- None - task complete


## Session 32: Implement offline image package download API (PR4)

**Date**: 2026-06-11
**Task**: Implement offline image package download API (PR4)
**Branch**: `master-gigi`

### Summary

Completed PR4 of offline image delivery pipeline: implemented OfflineImagePackageService with page/detail/download-url endpoints, OfflineImagePackageController, replaced fileId with ossUrl in data model (Jenkins uploads directly via OSS plugin). Schema updated, handler revised to parse ossUrl from callback. All code compiles successfully.

### Main Changes

(Add details)

### Git Commits

| Hash | Message |
|------|---------|
| `687ff4068` | (see git log) |
| `94475e616` | (see git log) |
| `362c8b3ea` | (see git log) |

### Testing

- [OK] (Add test results)

### Status

[OK] **Completed**

### Next Steps

- None - task complete
