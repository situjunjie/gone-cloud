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


## Session 75: 实现 Project 评论模块

**Date**: 2026-07-23
**Task**: 实现 Project 评论模块
**Branch**: `master-gigi`

### Summary

完成 Project 评论模块 V1：工作项评论、回复、Markdown 校验、@ 站内信、删除占位、项目级 COMMENT_* 权限、SQL/前端文档和 35 个聚焦测试；记录 VSCode Eclipse 增量编译覆盖 MapStruct 产物的环境问题。

### Main Changes

- Detailed change bullets were not supplied; see the summary above.

### Git Commits

| Hash | Message |
|------|---------|
| `9837127f4` | (see git log) |

### Testing

- Validation was not recorded for this session.

### Status

[OK] **Completed**

### Next Steps

- None - task complete


## Session 76: 工作项动态与评论接口

**Date**: 2026-07-24
**Task**: 工作项动态与评论接口
**Branch**: `master-gigi`

### Summary

完成工作项评论与操作记录统一动态分页、工作项专用发表评论接口和时间线操作日志 RPC；补齐权限、分页、映射与排序测试并通过项目和 system 聚焦回归。

### Main Changes

- Detailed change bullets were not supplied; see the summary above.

### Git Commits

| Hash | Message |
|------|---------|
| `2d27e84e6` | (see git log) |

### Testing

- Validation was not recorded for this session.

### Status

[OK] **Completed**

### Next Steps

- None - task complete


## Session 77: 项目迭代创建接口

**Date**: 2026-07-27
**Task**: 项目迭代创建接口
**Branch**: `master-gigi`

### Summary

新增项目迭代创建 API，完成表单校验、负责人项目成员校验、双层 RBAC、容量与描述字段迁移、接口文档和自动化测试。

### Main Changes

- Detailed change bullets were not supplied; see the summary above.

### Git Commits

| Hash | Message |
|------|---------|
| `297a88743` | (see git log) |

### Testing

- Validation was not recorded for this session.

### Status

[OK] **Completed**

### Next Steps

- None - task complete


## Session 78: 项目迭代列表与进度统计

**Date**: 2026-07-27
**Task**: 项目迭代列表与进度统计
**Branch**: `master-gigi`

### Summary

新增按 projectId 查询迭代列表接口，返回负责人昵称和头像；批量统计工作项总数及已完成数，实际工时暂以 0.0 返回，并补充权限、租户边界、聚合查询和响应契约测试。

### Main Changes

- Detailed change bullets were not supplied; see the summary above.

### Git Commits

| Hash | Message |
|------|---------|
| `213ddd41c` | (see git log) |

### Testing

- Validation was not recorded for this session.

### Status

[OK] **Completed**

### Next Steps

- None - task complete


## Session 79: 完成项目工时管理

**Date**: 2026-07-28
**Task**: 完成项目工时管理
**Branch**: `master-gigi`

### Summary

实现独立工时实体、工作项关联、工时明细与汇总、双层权限、字典、工作项级联删除和迭代实际工时聚合；通过 DBX 确认 gone 数据库迁移完整。

### Git Commits

| Hash | Message |
|------|---------|
| `8947cc205` | (see git log) |

### Status

[OK] **Completed**


## Session 80: 项目工作项工时范围契约

**Date**: 2026-07-28
**Task**: 项目工作项工时范围契约
**Branch**: `master-gigi`

### Summary

完成工时范围 SELF/DESCENDANTS 契约、递归 CTE、物化工时计数、DTO/迁移/前端文档和回归测试。

### Git Commits

| Hash | Message |
|------|---------|
| `d72b68b6b` | (see git log) |
| `b07b7c343` | (see git log) |

### Status

[OK] **Completed**


## Session 81: DevOps BPM 审批结束回调适配

**Date**: 2026-07-28
**Task**: DevOps BPM 审批结束回调适配
**Branch**: `master-gigi`

### Summary

适配 BPM 统一流程后置通知；按流程实例 ID 查询权威状态，并仅恢复已通过审批的流水线。

### Main Changes

- DevOps 回调改为接收 processInstanceId 表单参数，并保留租户上下文。
- 新增 BPM 流程实例状态 RPC，按 Flowable 租户查询流程终态。
- 无响应变量映射的后置通知在事务提交后发送，避免跨服务读取未提交状态。

### Git Commits

| Hash | Message |
|------|---------|
| `32daf8e00` | (see git log) |

### Testing

- [OK] BpmProcessInstanceApiImplTest：2 tests passed
- [OK] PipelineApprovalServiceImplTest：9 tests passed
- [OK] git diff --check

### Status

[OK] **Completed**


## Session 82: 完善项目工时接口契约

**Date**: 2026-07-29
**Task**: 完善项目工时接口契约
**Branch**: `master-gigi`

### Summary

新增 SELF_AND_DESCENDANTS 工时统计范围并贯通分页和汇总；补齐分组显示名、前端接口文档与 Long 无损序列化和权限契约测试。

### Git Commits

| Hash | Message |
|------|---------|
| `5e91be046` | (see git log) |

### Status

[OK] **Completed**


## Session 83: Controller LocalDate timestamp serialization

**Date**: 2026-07-29
**Task**: Controller LocalDate timestamp serialization
**Branch**: `master-gigi`

### Summary

Added shared LocalDate epoch-millisecond serialization for Spring MVC controller responses, retained ISO request deserialization, added focused tests, and documented the backend JSON contract.

### Git Commits

| Hash | Message |
|------|---------|
| `157efdc9b` | (see git log) |

### Status

[OK] **Completed**


## Session 84: 修复代码合并冲突详情超时

**Date**: 2026-08-05
**Task**: 修复代码合并冲突详情超时
**Branch**: `master-gigi`

### Summary

使用 Hutool RuntimeUtil 并发完整读取 Git 输出，取消在线冲突内容大小上限，校验冲突工作区有效性并映射标准业务错误，同时改用 PipelineConvert 统一组装冲突详情响应。

### Git Commits

| Hash | Message |
|------|---------|
| `81673109a` | (see git log) |

### Status

[OK] **Completed**


## Session 85: 应用编排 K8s 部署配置后端实现

**Date**: 2026-08-06
**Task**: 应用编排 K8s 部署配置后端实现
**Branch**: `master-gigi`

### Summary

实现 DevOps 应用编排 K8s 部署配置后端:新增 dev_application_orchestration 单草稿表、Service/Controller、七类组件模板、编排校验(顺序归一化/唯一性/依赖引用逆序环/单资源YAML/白名单/kind/metadata.name)、乐观并发保存、query/update 权限。修复校验单遍索引导致依赖逆序被误报为不存在的缺陷,改为两遍校验。27 个测试用例全部通过,RAW_MANIFEST 回归通过。前端按契约待前端仓库接入。

### Git Commits

| Hash | Message |
|------|---------|
| `40e1abe66` | (see git log) |

### Status

[OK] **Completed**


## Session 86: 修复 DockerImageExportObjectStorage 下载链接输出

**Date**: 2026-08-14
**Task**: 修复 DockerImageExportObjectStorage 下载链接输出
**Branch**: `master-gigi`

### Summary

完成 DockerImageExportObjectStorage 公开对象下载 URL 方案：新增 downloadUrl 输出、统一下载动作 URL、补充应用发布 read-model 白名单与测试，并同步 Pipeline 规范。定向 32 项测试和 reactor 编译通过。

### Git Commits

| Hash | Message |
|------|---------|
| `a07a053cd` | (see git log) |

### Status

[OK] **Completed**


## Session 87: Implement DevOps variable groups
<!-- trellis-session: v=2 fp=7286403e17fb7d96 -->

**Date**: 2026-08-28
**Task**: Implement DevOps variable groups
**Branch**: `master-gigi`

### Summary

Added tenant-scoped DevOps variable groups with GLOBAL/APPLICATION/APPLICATION_ENV CRUD, line-based KEY=VALUE validation, runtime precedence injection into pipeline variable resolution, SQL schema, tests, and pipeline spec guidance.

### Git Commits

| Hash | Message |
|------|---------|
| `81861c3d7` | feat(devops): add variable groups |

### Status

[OK] **Completed**


## Session 88: Finish work check
<!-- trellis-session: v=2 fp=2970d2f5f50d321a -->

**Date**: 2026-08-29
**Task**: Finish work check
**Branch**: `master-gigi`

### Summary

Verified clean working tree; no current task required archival. Existing DevOps variable group task was already archived and journaled.

### Git Commits

(No commits - planning session)

### Status

[OK] **Completed**


## Session 89: 应用环境变量组关联与增量迁移
<!-- trellis-session: v=2 fp=7b3cc0cd6ea2c5c5 -->

**Date**: 2026-08-29
**Task**: 应用环境变量组关联与增量迁移
**Branch**: `master-gigi`

### Summary

为应用环境增加自定义名称和变量组关联，移除变量组对应用环境的反向字段，新增按应用查询变量组 simple-list 接口，并补充 MySQL 增量迁移脚本。DevOps 服务编译和 diff 检查通过。

### Git Commits

| Hash | Message |
|------|---------|
| `b2f07f8f5` | feat(devops): link variable groups to application environments |

### Status

[OK] **Completed**


## Session 90: 修复变量组服务循环依赖
<!-- trellis-session: v=2 fp=f8b3f020d03848ea -->

**Date**: 2026-08-29
**Task**: 修复变量组服务循环依赖
**Branch**: `master-gigi`

### Summary

变量组服务改用 ApplicationMapper 做应用存在性校验，移除对 ApplicationService 的依赖，解决 applicationServiceImpl 与 variableGroupServiceImpl 的 BeanCurrentlyInCreationException。DevOps 模块编译和 git diff --check 通过。

### Git Commits

| Hash | Message |
|------|---------|
| `9abad1d6c` | fix(devops): break variable group service bean cycle |

### Status

[OK] **Completed**


## Session 91: DevOps 全局配置类
<!-- trellis-session: v=2 fp=4c0c1d135e5fbf6f -->

**Date**: 2026-08-31
**Task**: DevOps 全局配置类
**Branch**: `master-gigi`

### Summary

新增可扩展的 DevopsConfig JSON 配置结构，构建集群仅保存全局 environmentId；补充默认嵌套、未知字段兼容和 JSON 往返测试。

### Git Commits

| Hash | Message |
|------|---------|
| `b1bc90576` | feat(devops): add global config structure |

### Status

[OK] **Completed**


## Session 92: DevOps 应用页与变量组收尾
<!-- trellis-session: v=2 fp=529e3611ba392f95 -->

**Date**: 2026-08-31
**Task**: DevOps 应用页与变量组收尾
**Branch**: `master-gigi`

### Summary

提交应用收藏查询、负责人头像、发布环境展示、变量组作用域收敛、System 收藏 RPC、SQL、测试及对应归档任务文档；33 个聚焦测试通过。

### Git Commits

| Hash | Message |
|------|---------|
| `2d76780cd` | feat(devops): refine application page and variable groups |

### Status

[OK] **Completed**


## Session 93: 新增应用环境单独更新接口
<!-- trellis-session: v=2 fp=40591a05ec080277 -->

**Date**: 2026-08-31
**Task**: 新增应用环境单独更新接口
**Branch**: `master-gigi`

### Summary

新增 PUT /devops/application/update-env，支持按应用环境关系单独更新配置并复用变量组校验；补充 Service 测试，聚合 Maven 定向测试通过。

### Git Commits

| Hash | Message |
|------|---------|
| `d7a6165b7` | feat(devops): add single application env update api |

### Status

[OK] **Completed**


## Session 94: K8s 构建集群实现与真实集群验收
<!-- trellis-session: v=2 fp=ac75e144784b03df -->

**Date**: 2026-10-12
**Task**: K8s 构建集群实现与真实集群验收
**Branch**: `master-gigi`

### Summary

将流水线构建迁移到 Kubernetes 构建集群并完成真实集群验收：源码准备、Checkout、CodeMerge、Command、KanikoBuild 全部在构建 Pod 内执行。k3s 上跑通基础命令、跨 Job 共享工作区、并发、失败与取消、Kaniko 推镜像、RETAIN 到期回收；过程中修复 6 个真实 bug（同 PVC 双 Volume 声明致卷协调失败、容器路径误作平台工作目录、workspaceSubPath/cacheMappings 未被消费致共享 PVC 与缓存失效、Kaniko debug 镜像无 /tmp、Kaniko 缺 insecure/registryMirror、清理任务缺租户上下文）。KanikoBuild 新增 insecure 与 registryMirror 参数；补充界面自测注意事项文档。测试 471 个全部通过。

### Git Commits

| Hash | Message |
|------|---------|
| `fe0d9ea6f` | feat(devops): support Kubernetes build cluster for pipeline execution |
| `807119d8e` | fix(devops): point kaniko-image at a reachable registry mirror |

### Testing

- [OK] mvn -pl yudao-module-devops/yudao-module-devops-server clean test：471 tests，0 failures/errors/skipped

### Status

[OK] **Completed**

### Next Steps

- 界面端到端测试中 Kaniko 步骤仍需将 context 由 . 改为 yudao-server（Dockerfile 的 COPY ./target/yudao-server.jar 相对 context 解析）；冲突挂起恢复、DYNAMIC_PVC/EMPTY_DIR 模式、跨节点 RWX 尚未验证


## Session 95: CodeMerge 集群执行优化：工作区跨运行复用 + partial clone + PVC 孤儿目录清理
<!-- trellis-session: v=2 fp=65ffaa57af6fc904 -->

**Date**: 2026-10-13
**Task**: CodeMerge 集群执行优化：工作区跨运行复用 + partial clone + PVC 孤儿目录清理
**Branch**: `master-gigi`

### Summary

排查「合并变更分支」48.6s 的耗时构成，定位到每次运行全量 fetch 190MB 历史 + 2 核 Pod 限流，以及 PVC 上 36 万个孤儿文件拖慢 fsGroup chown。落地三项优化并实测验证。

### Git Commits

| Hash | Message |
|------|---------|
| `d9bfd9945` | feat(devops): 复用 CodeMerge 工作区以加速代码合并 |
| `68621c95a` | test(devops): 覆盖稳定工作区挂载、.git 保留与 partial clone 契约 |
| `a6ba8789e` | docs(spec): 补充工作区复用与禁用 shallow clone 契约 |

### Testing

- [OK] mvn -o -pl yudao-module-devops/yudao-module-devops-server test → 498/498 通过；集群实测：冷启 44.4s→28.4s、热启 21.6s（git 段 28.4s→9.8s）；删除孤儿 runs/ 后 Pod 就绪 12.4s→3.4s

### Status

[OK] **Completed**

### Next Steps

- 触发一次流水线验证删除 runs/ 的生产效果（预期 code_merge_job 24.9s→~16s）；若需再压缩，可加 fsGroupChangePolicy=OnRootMismatch 跳过 chown（预期 Pod 就绪 ~3.4s→~1s）


## Session 96: 应用编排支持变量组变量注入
<!-- trellis-session: v=2 fp=b4a21d4446ca3ed6 -->

**Date**: 2026-10-14
**Task**: 应用编排支持变量组变量注入
**Branch**: `master-gigi`

### Summary

K8sDeploy ORCHESTRATION 模式渲染编排 YAML 时合并变量组 ${VAR}，内置变量优先，RAW_MANIFEST 不变；部署单 page/detail 与流水线 log context 对敏感键值掩码为 ******，variableGroupVariables 响应前置空，内部 config_json 保留明文支持重试；新增 SensitiveKeyUtils 并被流水线输入过滤复用。504 单测全绿。坑：掩码必须在 redactSecrets 之后（****** 以 * 开头会被 YAML 当别名 token 导致解析失败）。

### Git Commits

| Hash | Message |
|------|---------|
| `7b28d5ca7` | feat(devops): 应用编排支持变量组变量注入 |
| `a0a4aaac8` | docs(spec): 补充编排变量组注入与敏感值掩码契约 |
| `424990f0d` | chore(task): 记录 10-13 应用编排变量组注入任务 |

### Status

[OK] **Completed**
