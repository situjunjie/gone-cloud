# K8s 部署步骤等待 Pod 全部健康技术设计

## 1. Problem Statement

`K8sDeployStepHandler` 已同步等待部署服务返回，但部署服务当前仅依据 Deployment status 的 generation、updatedReplicas 和 availableReplicas 判断成功。该聚合状态不能严格证明可用副本来自本部署单的新 ReplicaSet，也不能证明每个目标 Pod、init container、普通 container 或 Service Endpoint 已健康。需要把 Step 成功条件提升为本次发布资源的端到端 Kubernetes 健康门禁。

## 2. Boundary and Ownership

- `K8sDeployStepHandler` 保持现状：调用 `startContainerDeploy`，成功映射 `CONTINUE`，失败映射 `FAIL`。
- `DeploymentOrderServiceImpl` 继续负责编排 apply、等待、取消、超时和最终部署单状态。
- `KubernetesDeploymentStatusCollector` 扩展为单一 Kubernetes 观察/健康判定组件，复用 Deployment UID、部署单 label 和 ownerReference 关联逻辑，避免执行门禁与详情实时状态产生两套规则。
- `KubernetesDeploymentManifestSupport` 负责 Service/Deployment 静态关系校验：Service 类型、selector 和 Deployment Pod Template label 兼容性。

## 3. Manifest Validation

RAW_MANIFEST 仍允许：

```text
Deployment only
Service(s) + exactly one Deployment
```

对每个 Service：

1. `spec.type` 不能是 `ExternalName`。
2. `spec.selector` 必须非空。
3. selector 的每个 key/value 必须存在于目标 Deployment `spec.template.metadata.labels`。
4. 如果 Service 存在且最终 Deployment desired replicas 为 0，执行准备阶段失败，因为 Endpoint 硬门禁不可能满足。

错误继续使用现有 `DEPLOYMENT_NODE_PARAM_INVALID`，返回具体 Service 名和原因，不新增数据库或通用错误模型。

## 4. Apply Result

RAW_MANIFEST apply 返回内部上下文：

```java
record KubernetesManifestApplyResult(
    Deployment deployment,
    List<Service> services
) {}
```

- Service/Deployment 仍按 YAML 文档顺序 apply。
- 每个资源仍独立写入 `result_json.applyResults`。
- Deployment-only 的 services 为空列表。
- K8sImageUpgrade 构造 services 为空的相同等待输入。

## 5. Health Evaluation Contract

增加内部结果：

```java
class KubernetesDeploymentHealthResult {
    boolean ready;
    String reason;
    String message;
    Map<String, Object> snapshot;
}
```

每轮等待按顺序判断：

### 5.1 Deployment

- Deployment 存在且 UID 等于部署单 `workloadUid`。
- `observedGeneration >= generation`。
- `updatedReplicas == desired`。
- `availableReplicas == desired`。
- 没有 `ProgressDeadlineExceeded`。

### 5.2 Target ReplicaSet

- ownerReference 指向 Deployment UID。
- ReplicaSet/Pod Template 带 `gone.devops/deployment-order-id=<orderId>`。
- 优先 revision 匹配目标 revision；revision 尚未更新时选择本部署单 label 下最新 ReplicaSet。
- ReplicaSet `spec.replicas == desired`，`status.readyReplicas == desired`。

### 5.3 Target Pods

- 只查询部署单 label 对应 Pod，并校验 ownerReference 指向目标 ReplicaSet UID。
- 非 terminating Pod 数量等于 desired replicas。
- 每个 Pod `phase=Running`、`Ready=True`。
- Pod spec/status 中普通容器数量一致，所有普通容器 `ready=true` 且 state=Running。
- init container 数量完整；普通 init container 必须 terminated 且 exitCode=0。为兼容 Kubernetes sidecar init container，running 且 ready 也视为健康。
- 目标容器的 Pod spec image 必须等于部署单目标 image。

### 5.4 Services

如果 services 为空，直接通过 Service 门禁。

否则逐个读取同 namespace、同名 core/v1 Endpoints：

- Endpoints 必须存在。
- `subsets.addresses` 是 ready addresses，`notReadyAddresses` 不计入成功。
- 优先通过 address.targetRef.kind=Pod + UID 关联目标 Pod；targetRef 缺失时允许用 address.ip 对应 Pod IP 兜底。
- 每个目标 Pod UID/IP 必须出现在每个 Service 的 ready addresses 中。
- 返回每个 Service 的 readyPodUids、notReadyPodUids、missingPodUids 和 ready 状态。

## 6. Wait Loop

`waitRolloutReady` 调整为健康等待：

```text
apply resources
  → poll Deployment
  → evaluate Deployment + target RS + target Pods + Services
  → all ready: markSuccess
  → canceled: exit canceled
  → explicit ProgressDeadlineExceeded: fail
  → timeout: fail with last health reason and final snapshot
```

- 保持 `rolloutTimeoutSeconds` 作为统一超时。
- ImagePullBackOff、CrashLoopBackOff、FailedScheduling 等可能恢复的状态不立即失败，持续等待到超时并保留诊断。
- 每轮不写完整快照到数据库，避免写放大；详情接口继续实时查询。仅阶段变化和最终结果持久化。

## 7. Result and Live Status

`DeploymentOrderResultContext` 新增：

```java
private List<Map<String, Object>> services;
private Map<String, Object> health;
```

`liveStatus` 增加同名 `services` 和 `health`：

```json
{
  "health": {
    "ready": false,
    "reason": "SERVICE_ENDPOINTS_NOT_READY",
    "message": "Service gone-api 尚未覆盖全部目标 Pod"
  },
  "services": [
    {
      "name": "gone-api",
      "ready": false,
      "readyPodUids": ["pod-1"],
      "notReadyPodUids": ["pod-2"],
      "missingPodUids": ["pod-2"]
    }
  ]
}
```

最终成功快照必须记录 `health.ready=true`。超时快照记录最后一个未通过原因，原始超时错误仍是部署单主错误。

## 8. Compatibility

- 无数据库 DDL。
- `result_json` schemaVersion 保持 `1.0`，仅增加可选字段。
- 旧部署单没有 services/health 时详情兼容空值。
- K8sImageUpgrade 没有 Service，上线后会比当前多等待本次 Pod/Container 全健康。
- Deployment-only manifest 不受 Service 校验影响。

## 9. Risks and Mitigations

- Endpoints 控制器存在短暂延迟：纳入统一轮询，不在 apply 后立即判失败。
- 大副本 Deployment：Pod/Endpoint 集合比较使用 UID Set，避免 O(n²)。
- Service selector 配错：执行准备阶段校验 selector 与 Pod Template label，减少无意义超时。
- 同名 Deployment 被替换：UID 校验阻止错误资源满足门禁。
- Service targetRef 缺失：仅使用 Pod IP 兜底，仍不接受无关外部地址。
