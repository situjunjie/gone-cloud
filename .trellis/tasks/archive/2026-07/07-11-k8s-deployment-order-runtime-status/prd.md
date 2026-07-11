# K8s 部署单真实执行状态

## Goal

让部署单详情准确展示 `K8sDeploy` 本次 YAML apply 的真实结果，而不是把关联 Deployment 在集群中的当前状态当成本次部署结果。运行期间实时展示本次 Deployment、ReplicaSet、Pod、容器及 Kubernetes Event；执行结束后固化最终快照和明确失败原因，便于用户判断 Service、Deployment 是否 apply 成功，以及镜像拉取、容器启动或探针等异常。

## Background

- `K8sDeployStepHandler` 只负责把 `DeploymentOrderService.startContainerDeploy` 的结果转换为流水线节点结果，实际 apply、rollout 等待和部署单详情构造集中在 `DeploymentOrderServiceImpl`。
- RAW_MANIFEST 当前支持按 YAML 文档顺序提交 Service 和单个 Deployment，但 apply 循环没有逐资源持久化成功或失败结果。
- 当前 `updateStage` 会用阶段摘要覆盖 `result_json`，最终结果无法稳定保留部分 apply 成功、rollout 和 Pod 信息。
- 部署单详情的 `liveStatus` 通过 Deployment selector 查询当前 Pod，可能混入旧 ReplicaSet 的 Pod，也可能在后续发布后展示与本部署单无关的状态。
- 当前工作区不包含前端源码，本任务交付后端 API 契约和所需数据；前端按契约轮询展示由前端仓库另行接入。

## Requirements

- R1. 不新增部署 attempt、资源结果或事件明细表，复用 `dev_deployment_order` 与 `DeploymentOrderDO.resultJson` 保存当前执行的结构化汇总和最终快照。
- R2. 不建设部署重试历史、完整过程持久化、历史事件检索或事件分页能力；现有重试入口不在本任务扩展其审计语义。
- R3. YAML 中每个受支持资源必须独立记录 apply 结果。Service apply 成功而后续 Deployment apply 失败时，部署单仍能返回 Service 的成功结果及 Deployment 的失败原因。
- R4. `result_json` 使用稳定、可演进的结构，至少包含 schemaVersion、资源 apply 结果、rollout 摘要、最终 Pod/Container 快照、最终相关 Event 和归一化失败诊断；阶段更新不得整体覆盖已有结果。
- R5. Deployment 和 Pod Template 必须携带部署单标识；实时查询必须结合部署单标识、Deployment UID 和 ownerReference 关联本次 ReplicaSet/Pod，不能仅使用应用 Deployment selector。
- R6. 部署单运行期间返回实时执行状态，包括 Deployment rollout、副本数、目标 ReplicaSet、Pod conditions、init container/普通 container 状态、镜像与 imageID、containerID、restartCount、waiting/terminated 详细原因及相关 Kubernetes Event。
- R7. 实时状态通过详情接口轮询获取，第一版不引入 WebSocket、SSE 或 Kubernetes Watch；后端实时读取 Kubernetes，不持久化每次状态变化。
- R8. 部署成功、失败、超时或取消结束前，尽力采集并保存一次最终 Deployment、ReplicaSet、Pod、Container 和相关 Event 快照。集群状态读取失败不得覆盖原始 apply/rollout 失败原因。
- R9. 部署失败时优先返回可操作诊断，例如资源 apply 被拒绝、ErrImagePull、ImagePullBackOff、CrashLoopBackOff、FailedScheduling、FailedMount、探针失败或 rollout 超时。
- R10. Kubernetes 资源和 Event 快照必须控制体积并清理敏感内容，不持久化 Secret data/stringData、kubeconfig、镜像仓库凭证或无界日志正文。
- R11. 保持现有 `/devops/deployment-order/{id}`、流水线部署 action 和分页接口兼容；允许在响应中新增结构化执行状态字段，并暂时保留 `liveStatus` 字段以降低前端迁移风险。

## Acceptance Criteria

- [ ] Service 和 Deployment 都 apply 成功时，部署单结果分别包含两个资源的 APPLIED 状态、资源身份和 Deployment generation/UID。
- [ ] Service apply 成功、Deployment apply 失败时，部署单保留 Service 成功结果，并明确标记 Deployment FAILED 及 Kubernetes API 错误。
- [ ] rollout 运行期间查询详情，能够返回只属于本部署单的目标 ReplicaSet、Pod、容器状态和相关 Event，不混入旧版本 Pod。
- [ ] Pod 镜像拉取失败时，响应包含 waiting reason/message、目标镜像以及相关 Failed/BackOff Event；最终部署结果包含归一化失败诊断。
- [ ] 容器启动后异常退出时，响应包含 terminated reason/message、exitCode、startedAt/finishedAt、restartCount 和 lastState。
- [ ] rollout 成功时，最终结果固化目标 revision、副本摘要、最终 Pod/Container 快照，部署单状态为 SUCCESS。
- [ ] rollout 超时时，最终结果固化最后可见的 Deployment、Pod、Container、Warning Event，并保留超时为主错误。
- [ ] `updateStage` 不再清空已写入的 apply 结果或最终结果。
- [ ] 集群不可访问时，部署单详情仍能返回已持久化执行结果，并在实时状态中单独报告查询失败。
- [ ] 现有部署单、K8sDeploy 和 K8sImageUpgrade 相关单元测试通过，并新增逐资源结果、Pod 精确关联、容器异常和最终快照测试。

## Out of Scope

- 部署重试历史和 attempt 详情。
- 完整执行时间线持久化、历史 Kubernetes Event 检索和分页。
- WebSocket、SSE、Kubernetes Watch。
- Pod 日志正文持久化或流式查看。
- Service EndpointSlice/后端端点就绪判定。
- 多 Deployment、任意 CRD、Helm 或 Kustomize 部署。
- 前端仓库页面实现。
