# brainstorm: K8S 环境大盘

## Goal

在 DevOps 环境列表为 Kubernetes 类型环境增加“环境大盘”入口，后端提供当前环境绑定 Namespace 下的 Kubernetes 基础资源只读查询能力，支撑前端展示 Service、Deployment、Pod 信息，并复用现有 Pod 终端能力。

## What I already know

* 用户希望第一版只查看环境绑定的默认 Namespace。
* 第一版展示 Services、Deployments、Pods。
* Pod 终端入口只对 Running Pod 显示，多容器 Pod 先选择容器。
* 日志第一版不做。
* 当前仓库包含 DevOps 后端模块，但未包含前端 `.vue/.ts/.js` 源码。
* 现有后端已有 `/devops/environment` CRUD、连接检测、Kubernetes Namespace 列表，以及 Kubernetes 终端 WebSocket 实现。

## Assumptions

* 前端管理端源码在另一个仓库或未拉入当前 workspace，本任务先落后端接口契约。
* 大盘接口复用 `devops:environment:query` 权限。
* 不新增数据库表，不返回 kubeconfig 或其它连接敏感信息。

## Requirements

* 提供环境级 Kubernetes dashboard 接口，返回环境信息、Namespace 和资源数量摘要。
* 提供 Pods、Deployments、Services 只读列表接口。
* 所有资源查询限定在环境 `infra_config` 中配置的 Namespace。
* Pod 列表需要返回容器名称列表，支撑前端多容器 Pod 终端选择。
* K8S SDK 调用继续放在 Kubernetes connector 边界内。

## Acceptance Criteria

* [ ] 非 K8S 环境访问 Kubernetes 资源接口返回现有不支持错误。
* [ ] K8S 环境 dashboard 返回 Service、Deployment、Pod、Running Pod、异常 Pod 数量。
* [ ] Pod 列表包含 phase、ready 容器数、total 容器数、restartCount、nodeName、podIp、containerNames、createTime。
* [ ] Deployment 列表包含 replicas、readyReplicas、availableReplicas、updatedReplicas、images、createTime。
* [ ] Service 列表包含 type、clusterIp、externalIps、ports、selector、createTime。
* [ ] 单元测试覆盖 connector 的资源转换和异常处理。

## Definition of Done

* Tests added/updated for backend behavior.
* Targeted Maven test or compile command attempted.
* Frontend contract documented because frontend source is absent from this checkout.

## Out of Scope

* 日志查看。
* Kubernetes 写操作：删除、重启、扩缩容、编辑 YAML。
* Prometheus 指标。
* 跨 Namespace 切换。

## Technical Notes

* Existing controller: `yudao-module-devops/.../controller/admin/environment/EnvironmentController.java`
* Existing connector: `yudao-module-devops/.../framework/kubernetes/KubernetesEnvironmentConnector.java`
* Existing terminal service: `yudao-module-devops/.../service/kubernetes/terminal/KubernetesTerminalServiceImpl.java`
* Relevant specs: backend directory structure, error handling, quality, DevOps infrastructure integration.

## Frontend Integration Contract

* Environment list action:
  * Show `环境大盘` only when `row.infraType === 'K8S' && row.infraConfigConfigured`.
  * Route to `/devops/environment/dashboard?id=${row.id}`.
* Hidden route/menu:
  * Path: `/devops/environment/dashboard`
  * Component: `devops/environment/dashboard`
  * Component name: `DevopsEnvironmentDashboard`
  * Permission: `devops:environment:query`
* API calls:
  * `GET /devops/environment/kubernetes/dashboard?id={id}`
  * `GET /devops/environment/kubernetes/pods?id={id}`
  * `GET /devops/environment/kubernetes/deployments?id={id}`
  * `GET /devops/environment/kubernetes/services?id={id}`
* Dashboard layout:
  * Header: environment name/key/stage/namespace and refresh action.
  * Summary cards: `serviceCount`, `deploymentCount`, `podCount`, `runningPodCount`, `abnormalPodCount`.
  * Tabs: `Pods`, `Deployments`, `Services`.
* Pod terminal:
  * Show terminal button only when `terminalEnabled === true`.
  * If `containerNames.length > 1`, show a container selection dialog before opening terminal.
  * If `containerNames.length === 1`, open terminal with that container directly.
