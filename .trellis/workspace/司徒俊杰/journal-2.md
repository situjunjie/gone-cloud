# Journal - 司徒俊杰 (Part 2)

> Continuation from `journal-1.md` (archived at ~2000 lines)
> Started: 2026-07-02

---



## Session 60: DevOps approval deploy detail HTML

**Date**: 2026-07-02
**Task**: DevOps approval deploy detail HTML
**Branch**: `master-gigi`

### Summary

Added deploy_html rich-text approval details for DevOps pipeline BPM approvals, including application, environment, run metadata, ordered changes, tester/reviewer display fallback, HTML escaping, focused tests, and pipeline spec documentation.

### Main Changes

(Add details)

### Git Commits

| Hash | Message |
|------|---------|
| `c0e4c7636` | (see git log) |

### Testing

- [OK] (Add test results)

### Status

[OK] **Completed**

### Next Steps

- None - task complete


## Session 61: Pipeline execution context workspace refactor

**Date**: 2026-07-03
**Task**: Pipeline execution context workspace refactor
**Branch**: `master-gigi`

### Summary

Unified pipeline handler context around PipelineExecutionContext, initialized run workspace at execution start, moved CodeMerge to run workspace, persisted namespaced step outputs, updated source preparation and focused tests.

### Main Changes

(Add details)

### Git Commits

| Hash | Message |
|------|---------|
| `2a05626e2` | (see git log) |

### Testing

- [OK] (Add test results)

### Status

[OK] **Completed**

### Next Steps

- None - task complete


## Session 62: Pipeline step actions and approval resume

**Date**: 2026-07-04
**Task**: Pipeline step actions and approval resume
**Branch**: `master-gigi`

### Summary

Implemented pipeline step action metadata and platform step logs for CodeMerge, Approval, and image export steps; fixed approval resume by unblocking the blocked job after BPM approval so the engine continues to downstream steps; verified focused DevOps pipeline tests.

### Main Changes

(Add details)

### Git Commits

| Hash | Message |
|------|---------|
| `befc2ee26` | (see git log) |

### Testing

- [OK] (Add test results)

### Status

[OK] **Completed**

### Next Steps

- None - task complete


## Session 63: Return project page user avatars

**Date**: 2026-07-06
**Task**: Return project page user avatars
**Branch**: `master-gigi`

### Summary

Added project page response fields for creator user id/name/avatar and owner avatar via Easy-Trans, and expanded AdminUserApi auto-trans fields to include avatar. Verified with project module clean compile.

### Main Changes

(Add details)

### Git Commits

| Hash | Message |
|------|---------|
| `d6437f85f` | (see git log) |

### Testing

- [OK] (Add test results)

### Status

[OK] **Completed**

### Next Steps

- None - task complete


## Session 64: Project announcement update support

**Date**: 2026-07-06
**Task**: Project announcement update support
**Branch**: `master-gigi`

### Summary

Recorded the follow-up project commit that adds project announcement persistence, save/response fields, SQL schema support, and an update-announcement endpoint/service path.

### Main Changes

(Add details)

### Git Commits

| Hash | Message |
|------|---------|
| `1994f7572` | (see git log) |

### Testing

- [OK] (Add test results)

### Status

[OK] **Completed**

### Next Steps

- None - task complete


## Session 65: Add project sprint persistence model

**Date**: 2026-07-06
**Task**: Add project sprint persistence model
**Branch**: `master-gigi`

### Summary

Added project sprint persistence primitives: proj_sprint schema, ProjectSprintDO, mapper, sprint status enum, and renamed work item iteration references to sprintId/sprint_id across DOs, VOs, mappers, query/update field enums, and service field handling. Validated with project server clean compile.

### Main Changes

(Add details)

### Git Commits

| Hash | Message |
|------|---------|
| `91972e400` | (see git log) |

### Testing

- [OK] (Add test results)

### Status

[OK] **Completed**

### Next Steps

- None - task complete


## Session 66: DevOps deploy action and SSE disconnect fixes

**Date**: 2026-07-07
**Task**: DevOps deploy action and SSE disconnect fixes
**Branch**: `master-gigi`

### Summary

Added pipeline deploy-order frontend action/status support, simplified GONE startup banner, and handled Docker/Kubernetes SSE log client disconnects without noisy global errors.

### Main Changes

(Add details)

### Git Commits

| Hash | Message |
|------|---------|
| `2daece178` | (see git log) |

### Testing

- [OK] (Add test results)

### Status

[OK] **Completed**

### Next Steps

- None - task complete


## Session 67: Update Trellis and DevOps repository search

**Date**: 2026-07-10
**Task**: Update Trellis and DevOps repository search
**Branch**: `master-gigi`

### Summary

Updated Trellis platform/task workflow support and added searchable GitLab repository provider project listing with tests.

### Main Changes

- Detailed change bullets were not supplied; see the summary above.

### Git Commits

| Hash | Message |
|------|---------|
| `fe2f6f00b` | (see git log) |

### Testing

- Validation was not recorded for this session.

### Status

[OK] **Completed**

### Next Steps

- None - task complete


## Session 68: K8s 部署单真实执行状态

**Date**: 2026-07-11
**Task**: K8s 部署单真实执行状态
**Branch**: `master-gigi`

### Summary

部署单按资源记录 Kubernetes apply 结果，实时关联本次 ReplicaSet、Pod、容器与 Event，并在结束时保存结构化最终快照和失败诊断；无需新增数据库表。

### Main Changes

- Detailed change bullets were not supplied; see the summary above.

### Git Commits

| Hash | Message |
|------|---------|
| `eb17c2983` | (see git log) |

### Testing

- Validation was not recorded for this session.

### Status

[OK] **Completed**

### Next Steps

- None - task complete


## Session 69: K8s 部署步骤等待工作负载健康

**Date**: 2026-07-11
**Task**: K8s 部署步骤等待工作负载健康
**Branch**: `master-gigi`

### Summary

K8sDeploy/K8sImageUpgrade 同步等待本部署单 Deployment、ReplicaSet、Pod、容器健康；Deployment-only 跳过 Service，存在 Service 时校验 ready Endpoints 覆盖全部目标 Pod，并扩展最终健康快照与测试。

### Main Changes

- Detailed change bullets were not supplied; see the summary above.

### Git Commits

| Hash | Message |
|------|---------|
| `202e67917` | (see git log) |

### Testing

- Validation was not recorded for this session.

### Status

[OK] **Completed**

### Next Steps

- None - task complete


## Session 70: 修正 K8s 启动期探针失败语义

**Date**: 2026-07-14
**Task**: 修正 K8s 启动期探针失败语义
**Branch**: `master-gigi`

### Summary

区分 Kubernetes 历史 events、当前 health、可恢复 diagnosis 与最终 failure；成功快照清理历史 Unhealthy，兼容旧部署单响应，并补充超时诊断、回归测试和 DevOps 规范。

### Main Changes

- Detailed change bullets were not supplied; see the summary above.

### Git Commits

| Hash | Message |
|------|---------|
| `b419167e6` | (see git log) |

### Testing

- Validation was not recorded for this session.

### Status

[OK] **Completed**

### Next Steps

- None - task complete


## Session 71: Pipeline Job 插件与钉钉通知

**Date**: 2026-07-18
**Task**: Pipeline Job 插件与钉钉通知
**Branch**: `master-gigi`

### Summary

为 DevOps Pipeline 增加 Job 级插件 SPI、独立异步分发与钉钉机器人通知；插件异常、超时和线程池拒绝均不影响 Job/Pipeline 状态或 DAG 调度，并完成 DSL、签名、脱敏及 138 项 Pipeline/DingTalk 回归验证。

### Main Changes

- Detailed change bullets were not supplied; see the summary above.

### Git Commits

| Hash | Message |
|------|---------|
| `b5b2d72be` | (see git log) |

### Testing

- Validation was not recorded for this session.

### Status

[OK] **Completed**

### Next Steps

- None - task complete


## Session 72: 修复钉钉插件 Spring Bean 创建失败

**Date**: 2026-07-18
**Task**: 修复钉钉插件 Spring Bean 创建失败
**Branch**: `master-gigi`

### Summary

为 DingTalkPlugin 生产构造函数显式添加 Spring 注入标记，并通过 AnnotationConfigApplicationContext 覆盖 DingTalkPlugin 与 PipelinePluginRegistry 的真实装配链路；插件测试与 DevOps 模块编译通过，同时将多构造函数组件的装配测试规则沉淀到后端质量规范。

### Main Changes

- Detailed change bullets were not supplied; see the summary above.

### Git Commits

| Hash | Message |
|------|---------|
| `84654fe39` | (see git log) |

### Testing

- Validation was not recorded for this session.

### Status

[OK] **Completed**

### Next Steps

- None - task complete


## Session 73: 完成项目成员角色权限管理

**Date**: 2026-07-21
**Task**: 完成项目成员角色权限管理
**Branch**: `master-gigi`

### Summary

完成项目级 RBAC、全局角色模板快照、成员多角色与双层鉴权；提供增量 SQL 和前端接口文档；修复 ProjectRoleMapper 跨模块 @Resource Bean 名冲突并新增装配回归测试。

### Main Changes

- Detailed change bullets were not supplied; see the summary above.

### Git Commits

| Hash | Message |
|------|---------|
| `edfd4a2cd` | (see git log) |
| `e82ff80b3` | (see git log) |
| `7e4bb95b0` | (see git log) |
| `ff4a02c87` | (see git log) |
| `4b1a7731c` | (see git log) |

### Testing

- Validation was not recorded for this session.

### Status

[OK] **Completed**

### Next Steps

- None - task complete


## Session 74: 项目成员列表返回用户头像

**Date**: 2026-07-22
**Task**: 项目成员列表返回用户头像
**Branch**: `master-gigi`

### Summary

为项目成员列表响应新增 userAvatar 字段，通过 Easy-Trans 从系统用户资料翻译头像；同步更新项目 RBAC 前端接口文档，并完成模块编译、定向测试及 diff 检查。

### Main Changes

- Detailed change bullets were not supplied; see the summary above.

### Git Commits

| Hash | Message |
|------|---------|
| `4daa223ff` | (see git log) |

### Testing

- Validation was not recorded for this session.

### Status

[OK] **Completed**

### Next Steps

- None - task complete
