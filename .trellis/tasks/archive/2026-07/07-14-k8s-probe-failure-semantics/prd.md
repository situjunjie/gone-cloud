# 修正 K8s 启动期探针失败语义

## Goal

修正部署单详情中 Kubernetes 启动期探针失败的语义：历史 `Unhealthy` Warning 继续作为排障事件保留，但不能让最终已经健康、部署状态为 `SUCCESS` 的部署单同时返回顶层 `failure`，避免前端和用户把正常启动过程误解为部署失败。

## Background

- 部署单 101 的最终状态为 `SUCCESS`，`result.health.ready=true`，实时状态也为 `READY`，但 `result.failure.reason=Unhealthy`。
- 该 Pod 在容器启动后约 32 秒、42 秒发生两次 Readiness probe connection refused，约 53 秒后应用启动完成并通过 Readiness，Pod 最终 Ready 且没有重启。
- `KubernetesDeploymentStatusCollector.collect()` 当前先从 Pod 状态和历史事件执行 `diagnoseFailure()`，命中任意历史 `Unhealthy` 后写入顶层 `failure`，随后才执行最终健康门禁，见 `KubernetesDeploymentStatusCollector.java:130-137`、`:744-770`。
- `DeploymentOrderServiceImpl.captureFinalSnapshotQuietly()` 在成功路径会把采集器的 `failure` 原样持久化到 `result_json`，见 `DeploymentOrderServiceImpl.java:553-579`。
- 既有 K8s 健康门禁约定要求：`ImagePullBackOff`、`CrashLoopBackOff`、`FailedScheduling`、`FailedMount`、探针失败等可能恢复的状态继续等待到统一超时；只有健康门禁通过才成功，超时或明确不可恢复条件才失败。
- Readiness probe 失败只表示 Pod 当前不能接收流量，不会直接重启容器，也不等价于部署执行失败。

## Requirements

- R1. `events` 继续返回关联资源的 Kubernetes 历史事件，包括启动期 `Warning/Unhealthy`，不得通过删除事件掩盖诊断信息。
- R2. `health` 表示采集时刻的当前健康状态；当 `health.ready=true` 时，采集结果和成功部署单最终快照不得存在顶层 `failure`。
- R3. 顶层 `failure` 只表示部署执行的最终失败或 Kubernetes 明确不可恢复终态，不能由单条历史 Warning 事件直接产生。
- R4. 等待中的可恢复异常仍需保留诊断能力。部署最终超时时，主失败原因为 `ROLLOUT_TIMEOUT`，具体 Pod/事件诊断作为嵌套诊断保留，不能丢失 `Unhealthy`、拉镜像失败或调度失败信息。
- R5. `ProgressDeadlineExceeded` 等明确终态继续能产生失败诊断，不得因本次修正被忽略。
- R6. 成功路径必须显式清理此前可能存在的失败字段，确保同一结果中 `SUCCESS`、`health.ready=true` 和 `failure!=null` 不再并存。
- R7. 详情接口兼容已经持久化的矛盾数据：对于 `deployStatus=SUCCESS` 且 `result.health.ready=true` 的旧部署单，响应不得继续暴露历史顶层 `failure`。不要求数据库表变更或批量修复历史 JSON。
- R8. 失败部署单的现有 `failure.reason/message/diagnosis` 结构保持兼容；不改变部署等待时机、统一超时、取消流程和健康门禁成功条件。
- R9. 应用侧可通过 `startupProbe` 或调整 `initialDelaySeconds` 减少启动期事件噪声，但平台语义修正不能依赖应用修改 YAML。
- R10. 接口新增可选顶层 `diagnosis` 字段，表示采集时刻仍存在但可能恢复的主要异常；成功健康快照不返回 `diagnosis`，等待中可返回，最终失败时同时嵌入 `failure.diagnosis` 保持失败结果自包含。

## Acceptance Criteria

- [x] 当前 Deployment、目标 ReplicaSet、Pod 和容器均 Ready，但事件列表包含历史 `Unhealthy` 时，`events` 仍包含该事件，采集结果不包含顶层 `failure`。
- [x] 部署等待期间存在 Readiness probe、拉镜像、调度或挂载异常时，可通过顶层 `diagnosis` 读取当前主要诊断，但部署单和 Step 不因此提前失败。
- [x] 部署成功时，持久化结果满足 `deployStatus=SUCCESS`、`health.ready=true`、`failure=null/不存在`。
- [x] 查询部署单 101 或同类旧数据时，接口响应不再返回与成功健康状态冲突的顶层 `result.failure`。
- [x] Readiness probe 持续失败直至 rollout 超时时，部署单为 `FAILED`，顶层失败原因为 `ROLLOUT_TIMEOUT`，并保留 `Unhealthy` 具体诊断。
- [x] 当前容器处于 `ImagePullBackOff`、`CrashLoopBackOff` 等状态时，等待流程不提前失败，超时结果仍保留具体诊断。
- [x] Deployment 出现 `ProgressDeadlineExceeded` 时仍按明确终态失败。
- [x] 现有 DeploymentOrder、K8sDeploy、K8sImageUpgrade 健康门禁相关测试通过，并新增成功后忽略历史探针失败、旧数据兼容和超时诊断测试。

## Out of Scope

- 修改用户应用的 Deployment YAML、探针路径或 Spring Boot 启动性能。
- 新增数据库字段、事件历史表或批量更新所有部署单 `result_json`。
- 改变 K8s Deployment/Pod/Service Endpoints 健康门禁规则。
- 将所有 Kubernetes Warning 重新分类为产品级告警。

## Decisions

- D1. 新增可选顶层 `diagnosis` 字段，明确区分当前可恢复诊断与最终 `failure`。
