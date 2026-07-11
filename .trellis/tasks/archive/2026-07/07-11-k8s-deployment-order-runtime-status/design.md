# K8s 部署单真实执行状态技术设计

## 1. Problem Statement

现有部署单把两类数据混在一起：`result_json` 在执行阶段被反复覆盖，只能留下阶段或最终 Deployment 摘要；详情接口的 `liveStatus` 则按 Deployment selector 查询集群当前状态，不能证明这些 Pod 属于本次发布。目标是在不增加明细表、不保存完整时间线的前提下，让部署单展示本次 YAML 各资源的 apply 事实、运行中的实时 rollout 状态，以及结束时的最终 Pod/Container/Event 诊断快照。

## 2. Scope Decisions

- 复用 `dev_deployment_order`，不新增数据库表，不新增 attempt 历史模型。
- `result_json` 是当前执行的结构化持久化快照；运行期间只保存低基数事实（阶段和逐资源 apply 结果），结束时覆盖/补齐最终运行快照。
- `liveStatus` 是运行时查询结果，不保存中间变化；前端使用现有详情接口轮询。
- 保持现有 K8sDeploy、K8sImageUpgrade、部署单分页、取消和重试入口兼容，但本任务不增强重试审计语义。
- 当前 manifest 白名单仍是 Service + 单 Deployment，不扩大资源类型。

## 3. Result JSON Contract

新增内部持久化上下文 `DeploymentOrderResultContext`，避免业务代码散落 Map key。建议结构：

```json
{
  "schemaVersion": "1.0",
  "stage": "WAIT_ROLLOUT",
  "summary": "等待 Deployment rollout ready",
  "applyResults": [
    {
      "documentIndex": 0,
      "apiVersion": "v1",
      "kind": "Service",
      "namespace": "prod",
      "name": "gone-api",
      "status": "APPLIED",
      "uid": "...",
      "resourceVersion": "...",
      "generation": null,
      "message": null
    }
  ],
  "rollout": {},
  "replicaSet": {},
  "pods": [],
  "events": [],
  "failure": {
    "reason": "IMAGE_PULL_BACKOFF",
    "message": "Pod gone-api-xxx 容器 server 镜像拉取失败"
  },
  "capturedAt": "2026-07-11T10:00:00"
}
```

兼容规则：

- 旧部署单 `result_json` 可能只是 rollout Map。解析失败或缺少 schemaVersion 时创建新的空上下文，不影响详情返回。
- `updateStage` 只更新 `stage/summary`，不得清空 `applyResults` 或最终快照。
- 逐资源 apply 前写 `APPLYING`，成功写 `APPLIED`，失败写 `FAILED` 和清理后的错误信息。
- `resultJson` 仍通过 RespVO 的 `result` 返回；`liveStatus` 暂时保留字段名，内部语义调整为“与部署单关联的实时执行状态”。

## 4. Deployment Correlation

为避免 selector 混入旧 Pod，apply Deployment 时写入：

```text
metadata.annotations[gone.devops/deployment-order-id]
metadata.annotations[gone.devops/pipeline-run-id]
spec.template.metadata.labels[gone.devops/deployment-order-id]
spec.template.metadata.annotations[gone.devops/deployment-order-id]
```

部署单 ID 是合法且稳定的 label value。Deployment selector 不增加该 label，Pod Template 允许拥有 selector 以外的附加 label。

查询关系：

1. 按 namespace/name 读取 Deployment，并校验 UID 与部署单已保存的 `workloadUid`；UID 不一致时只返回资源已被替换提示，不把新资源当成本次结果。
2. ReplicaSet 必须通过 ownerReference UID 指向该 Deployment；优先选择 revision 等于 `targetRevision` 的 ReplicaSet，否则选择带本部署单 Pod Template label 的最新 ReplicaSet。
3. Pod 必须带 `gone.devops/deployment-order-id=<orderId>`，并且 ownerReference 指向目标 ReplicaSet；缺少目标 ReplicaSet 时仍可返回带部署单 label 的 Pod，但明确关联级别。
4. Kubernetes Event 只保留 involvedObject UID 属于本次 Deployment、目标 ReplicaSet 或目标 Pod 的事件。

对于历史旧部署单没有新 label 的情况，允许回退到当前 selector 查询，但响应标记 `correlationMode=LEGACY_SELECTOR`；新部署必须使用 `DEPLOYMENT_ORDER_LABEL`。

## 5. Runtime Snapshot

抽取 `KubernetesDeploymentStatusCollector`（或同等职责组件），负责：

- 构造 Deployment rollout 摘要和 conditions。
- 找出目标 ReplicaSet，返回 revision、replicas、ready/available replicas、conditions。
- 返回 Pod metadata、phase、conditions、nodeName、Pod IP、host IP、startTime。
- 返回 initContainerStatuses 和 containerStatuses：image、imageID、containerID、ready、restartCount、state、lastState、waiting/terminated/running 详细字段。
- 查询并压缩相关 Kubernetes Event，按最后时间排序，限制最大数量，优先保留 Warning。
- 从容器状态、Pod conditions 和 Event 生成归一化 failure：apply rejected、ErrImagePull、ImagePullBackOff、CrashLoopBackOff、FailedScheduling、FailedMount、Unhealthy、ProgressDeadlineExceeded、RolloutTimeout。

采集失败返回 `{available:false,message:...}`，不得抛出并替换原始部署错误。

## 6. Execution Flow

### RAW_MANIFEST

1. 创建/加载空的结果上下文。
2. 解析并准备 Service + Deployment。
3. 每个资源独立执行：写 APPLYING → createOrReplace → 写 APPLIED；异常时写 FAILED 后继续抛出。
4. Deployment apply 时保存 UID/generation，并写部署单/Pod Template 标识。
5. rollout 循环仍负责判定成功；详情轮询独立调用 collector 返回实时状态。
6. 成功时采集最终快照并写 SUCCESS。
7. apply 失败、rollout 超时或其他异常时，在 KubernetesClient 仍可用时 best-effort 采集最终快照，再由原异常决定部署单失败原因。

### K8S_IMAGE_UPGRADE

- 复用相同 Deployment 标识、apply result 和最终快照逻辑。
- update 操作在 apply result 中标记 `operation=UPDATE`，RAW_MANIFEST 标记 `CREATE_OR_REPLACE`。

## 7. API Contract

继续使用：

```text
GET /devops/deployment-order/{id}
```

关键返回：

```json
{
  "result": { "schemaVersion": "1.0", "applyResults": [], "pods": [], "events": [] },
  "liveStatus": {
    "available": true,
    "correlationMode": "DEPLOYMENT_ORDER_LABEL",
    "deployment": {},
    "replicaSet": {},
    "pods": [],
    "events": [],
    "failure": null,
    "observedAt": "..."
  }
}
```

列表接口不读取 Kubernetes。详情接口读取失败仍返回数据库字段和持久化 `result`。

## 8. Data Size and Security

- 不保存完整 Kubernetes resource YAML/JSON，只保存 metadata、status 摘要和必要诊断字段。
- Event 默认最多保存/返回有限条目，message 截断到合理长度，按 UID/reason/message/count 去重。
- 不保存 Pod 日志、Secret data/stringData、kubeconfig 或认证信息。
- 最终快照 Pod 数量和每个 Pod 的容器状态均设置上限，防止 `result_json` 无界增长。

## 9. Compatibility and Rollback

- 无数据库 DDL，回滚只需恢复 Java 代码。
- RespVO 保留 `result` 和 `liveStatus`，前端旧逻辑仍可读取 available/ready/pods；新字段为增量扩展。
- 旧 `result_json` 按 legacy Map 返回；新执行写 schemaVersion 1.0。
- 新加 Pod Template label 会触发新的 ReplicaSet，符合 K8sDeploy/K8sImageUpgrade 的发布语义。

## 10. Risks

- Kubernetes Event 有集群级 TTL，运行期间可实时看到，结束快照只能保存采集时仍存在的事件。
- 详情接口实时查询会增加 Kubernetes API 请求量；前端建议运行中约 2 秒轮询，结束后停止自动轮询。
- 如果 Deployment 被外部系统删除并同名重建，必须用 UID 不一致阻止误关联。
- final snapshot 是 best-effort；连接完全中断时只能保存已完成的 apply 事实和连接错误。
