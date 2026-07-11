# K8s 部署步骤等待 Pod 全部健康

## Goal

收紧 `K8sDeploy` 平台 Step 的成功条件：Service、Deployment YAML apply 成功后，Step 必须继续同步等待本次 Deployment 产生的目标 ReplicaSet 和全部期望 Pod 健康启动；如果 manifest 包含 Service，还需按最终产品决策验证 Service 已指向健康后端。只有健康门禁全部通过，`K8sDeployStepHandler` 才返回 `CONTINUE`，否则在超时、取消或明确失败后返回 `FAIL`。

## Background

- `K8sDeployStepHandler.handle()` 已同步调用 `DeploymentOrderService.startContainerDeploy()`，Handler 不会在 Service 方法返回前结束；真正决定 Step 完成时机的是 `DeploymentOrderServiceImpl.waitRolloutReady()`。
- 当前 rollout 成功条件只有 Deployment `observedGeneration >= generation`、`updatedReplicas == desired`、`availableReplicas == desired` 且没有 `ProgressDeadlineExceeded`。
- 当前条件没有逐个验证本部署单标签对应的目标 ReplicaSet Pod，也没有检查 init container、普通 container ready、Pod Ready condition 或 Service Endpoint。
- Deployment 汇总的 available/ready 数字可能在新旧 ReplicaSet 并存阶段包含旧 Pod，因此仅依赖 Deployment status 存在提前完成风险。
- 上一任务已经给 Deployment Pod Template 写入 `gone.devops/deployment-order-id=<orderId>`，并能通过 Deployment UID、ReplicaSet ownerReference 和 Pod ownerReference 精确关联本次 Pod，可直接复用该能力。
- Service 本身不拥有 Pod；所谓“Service 的 Pod”是 Service selector 选中的后端 Pod，Service 可用性应通过 Endpoints/EndpointSlice 或 selector 与健康 Pod 的匹配关系判断。

## Requirements

- R1. `K8sDeployStepHandler` 继续保持同步平台 Step 语义，不引入异步回调、后台任务、WebSocket 或额外 Step 状态机。
- R2. 所有 YAML 资源必须先成功 apply；任一 Service 或 Deployment apply 失败时立即失败，不进入健康成功状态。
- R3. 健康等待必须只观察本部署单创建的目标 ReplicaSet 和 Pod，关联依据为部署单 Pod Template label、Deployment UID 及 ownerReference，不能让旧 ReplicaSet Pod 满足成功条件。
- R4. 目标 ReplicaSet 的期望 Pod 数量必须达到 Deployment desired replicas，且每个目标 Pod 必须满足 Pod Ready=True。
- R5. 每个目标 Pod 的 init container 必须成功完成，所有普通 container 必须 ready；目标容器镜像应与部署单目标镜像一致或解析为该镜像对应的 imageID。
- R6. Deployment 自身仍必须满足 observedGeneration、updated replicas、available replicas 和 ProgressDeadline 条件；Pod 门禁是在其上增加，不替代 Deployment rollout 判定。
- R7. 等待过程中遇到 ErrImagePull、ImagePullBackOff、CrashLoopBackOff、FailedScheduling、FailedMount、探针失败等状态时继续保留实时诊断；最终按 rolloutTimeoutSeconds 超时失败，除 Kubernetes 已给出明确不可恢复的 Deployment 条件。
- R8. 取消部署时健康等待循环必须及时退出，不能在取消后把 Step 标记成功。
- R9. manifest 包含 Service 时，每个 Service 都必须产生 ready Endpoint，且 Endpoint 的 targetRef Pod UID 必须覆盖本部署单全部目标健康 Pod；任一 Service 未覆盖时 Step 继续等待。manifest 没有 Service 时跳过 Endpoint 门禁，不影响 Deployment-only 部署。
- R10. manifest 中的 Service 必须是带非空 selector 的 Pod Service，且 selector 必须匹配目标 Deployment Pod Template labels；`ExternalName`、无 selector 或 selector 不匹配的 Service 在执行准备阶段明确失败。
- R11. 成功时部署单最终快照必须反映通过健康门禁的 Deployment、ReplicaSet、Pod、Container 和每个 Service 的 Endpoint 健康状态。
- R12. `K8sImageUpgrade` 与 `K8sDeploy` 共用相同 Deployment/Pod 健康门禁；K8sImageUpgrade 没有 manifest Service 时不执行 Service Endpoint 门禁。
- R13. 不新增数据库表；继续复用部署单 `result_json` 和实时状态采集器。

## Acceptance Criteria

- [ ] Service 和 Deployment apply 成功，但目标 ReplicaSet 尚未创建时，Step 保持运行中。
- [ ] Deployment status 表面满足 available replicas，但可用 Pod 属于旧 ReplicaSet时，Step 不得成功。
- [ ] 目标 ReplicaSet Pod 数量少于 desired replicas 时，Step 保持运行中。
- [ ] 任一目标 Pod Ready=False、任一普通 container ready=false、init container 未成功结束时，Step 保持运行中。
- [ ] 所有目标 Pod 和容器健康、Deployment rollout 条件满足，并且 manifest 中每个 Service 的 ready Endpoint 覆盖全部目标 Pod 后，Step 返回 CONTINUE，部署单状态为 SUCCESS。
- [ ] manifest 只有 Deployment、没有 Service 时，只要 Deployment 和本次目标 Pod/Container 健康门禁通过，Step 即可返回 CONTINUE。
- [ ] ImagePullBackOff、CrashLoopBackOff、FailedScheduling 或探针失败持续到超时时，Step 返回 FAIL，部署单最终快照包含具体诊断。
- [ ] 用户取消时等待循环退出，Step 不会被标记为成功。
- [ ] K8sDeploy 和 K8sImageUpgrade 的健康成功语义一致。
- [ ] 新增单元测试覆盖旧 Pod 不能满足门禁、Pod/容器未就绪、全部健康、Service 无健康后端及超时/取消场景。

## Decisions

- D1. 当 manifest 包含 Service 时，Service ready Endpoint 覆盖本部署单健康 Pod 是 Step 成功的硬门禁；Service apply 成功但没有可用后端不能视为部署成功。
- D2. Service 是可选资源；Deployment-only manifest 不执行 Service Endpoint 门禁。
- D3. `ExternalName`、无 selector 或 selector 无法匹配目标 Deployment Pod Template labels 的 Service 不进入 rollout 等待，直接以明确配置错误失败。

## Out of Scope

- Deployment 以外的 StatefulSet、DaemonSet、Job 健康门禁。
- 多 Deployment manifest。
- 应用层 HTTP/TCP 探活；本期只使用 Kubernetes Pod readiness 和 Service Endpoint。
- 重试历史、完整过程时间线和新增持久化表。
