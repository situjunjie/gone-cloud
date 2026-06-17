# K8s Pod 实时日志接口

## Goal

为 DevOps Kubernetes 环境提供一个直接查看指定 Pod 日志的后端接口，前端可以在环境详情或 Pod 列表中打开日志面板，先拉取尾部历史日志，再持续接收实时滚动日志。

## What I already know

* 用户需要“查看 k8s 环境指定 pods 的日志”的直接接口，且日志需要实时滚动。
* 现有环境接口位于 `EnvironmentController`，已有 `/devops/environment/kubernetes/pods` 等资源查询接口。
* 现有流水线行级日志已经使用 `SseEmitter` + `MediaType.TEXT_EVENT_STREAM_VALUE`，适合复用为日志滚动通道。
* 现有 Kubernetes Pod 终端服务已经实现环境校验、Pod 校验、单/多容器解析和 fabric8 `KubernetesClient` 使用方式。

## Requirements

* 新增管理后台接口：按环境 id、namespace、podName、containerName 获取 Kubernetes Pod 日志流。
* 使用 SSE 返回实时日志，前端可以用 `EventSource` 持续消费。
* `namespace` 可选；不传时使用环境配置中的 Kubernetes namespace。
* `containerName` 可选；单容器 Pod 自动选择唯一容器，多容器 Pod 必须指定容器。
* 支持 `tailLines` 参数，首次连接时返回最近 N 行日志，然后继续 follow 新日志；默认值需保守，避免一次性返回过多日志。
* 复用现有 `devops:environment:query` 权限。
* Kubernetes 连接、Pod 不存在、容器不存在等错误走现有业务异常；流建立后的错误通过 SSE error 事件发送并关闭连接。

## Acceptance Criteria

* [x] 后端暴露 `GET /admin-api/devops/environment/kubernetes/pod-logs/stream` 可流式查看 Pod 日志。
* [x] 参数包含 `id`、`podName`，可选 `namespace`、`containerName`、`tailLines`。
* [x] 单容器 Pod 不传 `containerName` 可以成功打开日志流。
* [x] 多容器 Pod 不传 `containerName` 返回 `KUBERNETES_POD_CONTAINER_REQUIRED`。
* [x] 不存在 Pod 返回 `KUBERNETES_POD_NOT_EXISTS`。
* [x] 单元测试覆盖容器解析、Pod 不存在、多容器必选、LogWatch 关闭。
* [x] 给前端提供可直接执行的开发 prompt，说明接口、事件格式和 UI 集成点。

## Definition of Done

* 后端代码遵循现有 DevOps 分层和错误码风格。
* 新增或更新 focused unit tests。
* 至少运行 devops server 模块相关测试或可行的 Maven 校验。
* 如发现新的可复用约定，更新 `.trellis/spec/`。

## Technical Approach

新增 `KubernetesPodLogService`，负责校验环境与 Pod、解析容器、创建 `LogWatch` 并把日志按行发送到 `SseEmitter`。`EnvironmentController` 只暴露接口并委托服务。接口采用 SSE 而不是 WebSocket，因为日志查看是单向流，且仓库已有流水线实时日志 SSE 模式。

SSE 事件建议：

* `message` / `log`：日志行，data 为文本或结构化对象。
* `error`：流式读取过程中的错误消息。
* `complete`：watch 正常结束。

## Decision (ADR-lite)

**Context**: Pod 终端已有 WebSocket，但日志查看是单向持续输出；流水线日志已使用 SSE。

**Decision**: 本次使用 SSE 实现 Pod 日志实时滚动接口，并复用环境查询权限与 Kubernetes 校验逻辑。

**Consequences**: 前端实现简单，使用 `EventSource` 即可；如果后续需要双向交互，仍由现有 Pod Terminal WebSocket 承担。

## Out of Scope

* 不实现 Pod exec/terminal，本次只看日志。
* 不做日志持久化、检索、下载和审计。
* 不支持跨 namespace 任意扫描；默认仍以环境配置 namespace 为边界，显式 namespace 仅用于用户指定的 Pod 日志查询。
* 不新增菜单和权限码。

## Technical Notes

* Relevant files inspected:
  * `EnvironmentController`
  * `EnvironmentServiceImpl`
  * `KubernetesEnvironmentConnector`
  * `KubernetesTerminalServiceImpl`
  * `PipelineRunController`
  * `ErrorCodeConstants`
* fabric8 Kubernetes client version is managed as `7.7.0`.
