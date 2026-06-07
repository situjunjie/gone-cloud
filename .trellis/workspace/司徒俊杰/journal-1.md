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
