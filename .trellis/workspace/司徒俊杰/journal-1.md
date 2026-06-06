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

(Add details)

### Git Commits

| Hash | Message |
|------|---------|
| `9134339ba` | (see git log) |
| `c235cbe4a` | (see git log) |

### Testing

- [OK] (Add test results)

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

(Add details)

### Git Commits

| Hash | Message |
|------|---------|
| `c624b5378` | (see git log) |
| `19dd412ed` | (see git log) |
| `d2746aada` | (see git log) |
| `30ded6720` | (see git log) |

### Testing

- [OK] (Add test results)

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

(Add details)

### Git Commits

| Hash | Message |
|------|---------|
| `33fcdf759` | (see git log) |

### Testing

- [OK] (Add test results)

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
