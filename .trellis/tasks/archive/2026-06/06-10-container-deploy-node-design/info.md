# 容器部署节点与部署单技术设计

## 1. 总体结论

推荐把“容器部署”设计为平台原生执行节点，而不是 Jenkins stage。流水线仍由 `dev_pipeline_run` 作为一次发布运行主记录承载，节点状态仍同步到 `dev_pipeline_run_log`，但容器部署的领域细节独立落到新的“部署单”聚合中。

核心关系：

```text
dev_pipeline_run
  └── dev_pipeline_run_log(node_type=CONTAINER_DEPLOY / DEPLOY_K8S)
        └── dev_deployment_order
```

`dev_pipeline_run_log` 用于流水线卡片展示和统一日志接口；`dev_deployment_order` 用于容器部署业务审计、K8S 细节、后续重试/回滚/扩展。

## 2. 节点类型设计

### 推荐命名

产品名：容器部署。

内部已确认使用 `CONTAINER_DEPLOY`，参数中声明 `infraType=K8S`、`workloadKind=DEPLOYMENT`。现有注册表里预留的 `DEPLOY_K8S` 暂不作为 MVP 公开 DSL 类型。

原因：用户认知是“容器部署”，K8S 是当前执行后端；后续支持其他容器平台时无需迁移节点类型。

### DSL 参数

MVP 参数建议：

```json
{
  "infraType": "K8S",
  "workloadKind": "DEPLOYMENT",
  "deploymentName": "",
  "containerName": "",
  "image": "",
  "replicas": 2,
  "rolloutTimeoutSeconds": 300,
  "strategy": "ROLLING_UPDATE",
  "recordChangeCause": true
}
```

字段说明：

* 节点不允许覆盖 namespace，运行时统一使用 `EnvironmentDO.infraConfig.namespace`，并把解析出的 namespace 写入部署单。
* `image` 可以是显式完整镜像，也可以支持表达式，例如 `${APP_KEY}:${COMMIT_SHA}`。
* `deploymentName` / `containerName` 是 MVP 必填，避免猜测集群对象。
* `replicas` 记录本次期望副本数，并实际 patch 到 Kubernetes Deployment。
* `rolloutTimeoutSeconds` 控制等待 Deployment 可用的最长时间。

后续可扩展参数：

* `manifestYaml`
* `helmChart`
* `valuesYaml`
* `canary`
* `preCheck` / `postCheck`
* `envVars`
* `resources`
* `imagePullSecrets`

## 3. 部署单领域模型

### 主表：`dev_deployment_order`

建议字段：

* `id`
* `pipeline_run_id`
* `pipeline_run_log_id`
* `node_id`
* `node_type`
* `definition_id`
* `definition_version_id`
* `app_id`
* `application_env_id`
* `environment_id`
* `infra_type`
* `deploy_type`：`K8S_DEPLOYMENT`
* `deploy_status`：`CREATED / RUNNING / SUCCESS / FAILED / CANCELED`
* `attempt`
* `current_stage`
* `trigger_type`
* `trigger_user_id`
* `triggered_at`
* `started_at`
* `finished_at`
* `namespace`
* `workload_kind`
* `workload_name`
* `container_name`
* `image`
* `replicas`
* `revision`
* `previous_image`
* `previous_replicas`
* `cluster_context`
* `config_json`
* `result_json`
* `error_message`
* standard audit columns from `TenantBaseDO`

索引：

* `(tenant_id, pipeline_run_id, node_id)`
* `(tenant_id, application_env_id, deploy_status)`
* `(tenant_id, app_id, environment_id, triggered_at)`

`config_json` 保存运行时解析后的完整部署配置快照，避免后续 DSL 修改影响历史部署单。`result_json` 保存本次部署最终摘要，例如 observedGeneration、revision、availableReplicas、readyReplicas、updatedReplicas、conditions。

MVP 不建部署步骤子表。部署单与最后一个 `CONTAINER_DEPLOY` 节点一一对应，通过 `current_stage` 表示当前执行阶段。Pod、Deployment、rollout 的事实状态在详情接口中实时查询 Kubernetes。

## 4. 状态机

部署单状态：

```text
CREATED -> RUNNING -> SUCCESS
                 └-> FAILED
                 └-> CANCELED
```

流水线节点日志状态映射：

* `CREATED` -> `PENDING` 或创建即 `RUNNING`
* `RUNNING` -> `RUNNING`
* `SUCCESS` -> `SUCCESS`
* `FAILED` -> `FAILED`
* `CANCELED` -> `CANCELED`

流水线总状态：

* 部署节点成功后，如果后续没有未完成节点，`dev_pipeline_run` 置为 `SUCCESS`。
* 部署节点失败后，`dev_pipeline_run` 置为 `FAILED`，写入短错误摘要。
* 取消流水线时，需要取消当前部署单；Fabric8 对已提交 patch 没有通用撤销能力，因此 MVP 的取消语义是“不再等待 / 标记取消”，不是回滚。

## 5. 执行编排设计

现状是：

```text
submit release -> create run -> startCodeMerge -> trigger Jenkins -> Jenkins callback -> mark success
```

目标是：

```text
submit release
  -> create run
  -> start platform orchestrator
      -> CODE_MERGE
      -> Jenkins segment, if there are Jenkins nodes before deploy
      -> CONTAINER_DEPLOY platform node
      -> next segments
```

MVP 可以分两步落地：

### Phase A：只支持容器部署作为 Jenkins 之后的最后节点

代码合并成功后仍触发 Jenkins。Jenkins 所有节点完成后，平台检查流水线中是否存在容器部署节点：

* 没有：按现有逻辑标记 run success。
* 有：创建 deployment order，执行 K8S 部署，成功后再标记 run success。

优点：对现有 Jenkinsfile 生成和回调改动小。

限制：暂不支持“平台节点之后再接 Jenkins 节点”。

### Phase B：引入通用混合编排器

把节点按拓扑排序拆成连续执行段：

* Jenkins 节点段：生成/执行 Jenkinsfile 子集。
* Platform 节点：平台本地执行。

编排器按顺序推进，统一判断下一个节点/段。这样以后审批、部署、通知、手工确认都能自然接入。

已确认：本次实现范围采用 Phase A，只支持容器部署作为 Jenkins 之后的最后节点；但代码结构需要为 Phase B 的混合编排器预留扩展点。

Phase A 的约束：

* 流水线可以没有容器部署节点，保持现有 Jenkins-only 行为。
* 流水线最多有一个容器部署节点。
* 容器部署节点必须是拓扑排序后的最后一个执行节点。
* 容器部署节点之前可以有 Jenkins 节点。
* 容器部署节点之后如果还有节点，DSL 校验应失败。
* Jenkins 所有节点完成后，不立即把 `dev_pipeline_run` 标记为 `SUCCESS`；如果存在容器部署节点，应转交平台部署执行器。

为 Phase B 预留的结构：

* 增加独立的流水线推进服务，例如 `PipelineRunOrchestrator` 或 `PipelineRunAdvanceService`。
* Jenkins callback 只负责更新 Jenkins 节点日志，然后调用推进服务判断下一步。
* 容器部署执行器通过统一的平台节点执行接口接入，例如 `PlatformPipelineNodeExecutor`。
* Phase A 中推进服务只识别“Jenkins 完成后执行最后一个容器部署节点”；Phase B 再扩展为按拓扑分段执行 Jenkins segment 和 Platform node。

## 6. K8S 部署执行细节

执行器建议放在类似：

* `service/deployment/DeploymentOrderService`
* `service/deployment/KubernetesDeploymentExecutor`
* `framework/kubernetes` 继续只放 client / connector / SDK 适配代码

运行步骤：

1. 校验 `ApplicationEnvDO`、`EnvironmentDO`、`ApplicationDO` 存在。
2. 校验环境 `infraType=K8S`。
3. 解析环境 kubeconfig 和 namespace。
4. 解析节点参数，生成不可变 deployment config snapshot。
5. 创建 `dev_deployment_order` 和 `dev_pipeline_run_log`。
6. 创建 Fabric8 client。
7. 获取目标 Deployment，不存在则失败。
8. 定位目标 container，不存在则失败。
9. 记录 previous image / previous replicas。
10. patch Deployment：
    * spec.replicas
    * target container image
    * annotation change-cause / pipelineRunId / deploymentOrderId
11. 等待 rollout：
    * observedGeneration >= metadata.generation
    * updatedReplicas == replicas
    * availableReplicas == replicas
    * Progressing condition 未超时
12. 成功写 result；失败写 sanitized error。

成功判定已确认：必须等待 Deployment rollout ready 才能将部署单、流水线节点和流水线运行标记为成功。Kubernetes patch 成功只代表部署变更已提交，不能作为最终成功条件。

rollout ready MVP 判断：

* Deployment `status.observedGeneration >= metadata.generation`
* `status.updatedReplicas == spec.replicas`
* `status.availableReplicas == spec.replicas`
* `Progressing` condition 未出现超时/失败状态
* 在 `rolloutTimeoutSeconds` 内达成以上条件

如果超时或发现失败 condition：

* `dev_deployment_order.deploy_status = FAILED`
* `dev_deployment_order.current_stage = WAIT_ROLLOUT`
* 对应 `dev_pipeline_run_log` 标记 `FAILED`
* `dev_pipeline_run` 标记 `FAILED`
* 错误信息写入截断且脱敏后的 rollout 摘要

## 7. 幂等与并发

创建部署单时使用 `(pipeline_run_id, node_id)` 作为业务幂等键。重复触发时：

* 已 `SUCCESS`：直接返回，不重复 patch。
* `RUNNING`：返回当前部署单。
* `FAILED`：允许在同一个部署单上增加 attempt 后重试。

并发控制：

* 仍沿用 `validateNoActivePipelineRun(applicationEnvId)`，同一应用环境不允许多个 active run。
* K8S patch 前记录 Deployment resourceVersion / generation。
* 如果目标 Deployment 不存在或容器名不匹配，快速失败。
* MVP 不自动创建 Deployment / Service / Ingress；这些能力后续应归入 manifest / Helm / 应用部署模板设计。

## 8. API 设计

MVP 后端接口：

* `GET /devops/deployment-order/page`
* `GET /devops/deployment-order/{id}`
* `POST /devops/deployment-order/{id}/cancel`
* `POST /devops/deployment-order/{id}/retry`

流水线现有接口增强：

* `GET /devops/application/release/current-run`
  * 节点返回 `detailType=DEPLOYMENT_ORDER`
  * 节点 result 返回 `deploymentOrderId`、`deployStatus`、`currentStage`、`namespace`、`workloadName`、`image`、`replicas`
* `GET /devops/pipeline-run/{runId}/logs`
  * 继续返回节点日志，不返回 kubeconfig / raw infraConfig。

前端部署详情 MVP：

* 展示部署单主信息：状态、应用、环境、namespace、Deployment、container、image、replicas、开始/结束时间、当前阶段。
* 展示持久化 rollout 摘要：observedGeneration、updatedReplicas、availableReplicas、readyReplicas、conditions、revision。
* 展示 Kubernetes 当前状态摘要：详情接口实时查询 K8S。
* 不展示 kubeconfig、raw infraConfig、完整 Pod 事件流。

## 9. 错误码建议

新增部署单错误码段，例如 `1_011_007_xxx`：

* `DEPLOYMENT_ORDER_NOT_EXISTS`
* `DEPLOYMENT_NODE_PARAM_INVALID`
* `DEPLOYMENT_ENVIRONMENT_NOT_K8S`
* `DEPLOYMENT_KUBERNETES_WORKLOAD_NOT_EXISTS`
* `DEPLOYMENT_KUBERNETES_CONTAINER_NOT_EXISTS`
* `DEPLOYMENT_KUBERNETES_APPLY_FAIL`
* `DEPLOYMENT_KUBERNETES_ROLLOUT_TIMEOUT`
* `DEPLOYMENT_ORDER_STATE_INVALID`

错误信息需要截断并脱敏，不暴露 kubeconfig、token、集群内部敏感连接串。

## 10. MVP 范围

包含：

* 启用容器部署节点。
* 支持 K8S Deployment 更新 image 和 replicas。
* 创建部署单并记录持久化结果摘要。
* current-run 展示部署节点进度。
* 部署失败能让 pipeline run 失败。
* 支持查询部署单详情。

不包含：

* 自动创建 Deployment / Service / Ingress。
* Helm。
* 蓝绿 / 金丝雀 / 分批灰度。
* 自动回滚或手工回滚接口；但部署单必须记录回滚所需前态。
* 多集群批量部署。
* 部署后健康检查 DSL。

## 11. 已确认的镜像来源

MVP 采用节点显式配置完整镜像或镜像表达式。前置流水线节点负责构建并推送对应镜像；容器部署节点只负责解析表达式、记录最终镜像地址，并将该镜像部署到 K8S。

设计约束：

* 前置节点必须把镜像推送到目标集群可拉取的镜像仓库。
* 容器部署节点不从 Jenkins workspace 拉取本地镜像或制品。
* 容器部署节点 MVP 不依赖完整产物表。
* 部署单必须记录最终解析出的完整 `image`。
* 如果镜像 tag 由前置节点动态生成，前置节点必须通过稳定的流水线上下文变量上报给平台。
* 镜像表达式 MVP 支持平台可解析变量：`${APP_KEY}`、`${COMMIT_SHA}`、`${BRANCH_NAME}`、`${PIPELINE_RUN_ID}`、`${ENV_KEY}`。
* 不直接支持 Jenkins-only 变量，例如 `${BUILD_NUMBER}`，除非前置节点显式上报为平台上下文。
* 推荐优先使用确定性表达式，例如 `${APP_KEY}:${COMMIT_SHA}`。

## 12. 取消、重试与后续回滚

已确认：MVP 支持取消和重试，不支持回滚；后续需要支持回滚。

### 取消

取消语义：

* 如果部署单还未 patch K8S，标记部署单和节点为 `CANCELED`。
* 如果已经 patch K8S 并处于 rollout 等待中，停止平台侧等待并标记 `CANCELED`。
* 取消不自动把 Deployment 恢复到旧镜像或旧副本，因为 Kubernetes patch 已经提交，撤销属于回滚能力。

### 重试

重试语义：

* 仅允许 `FAILED` 或 `CANCELED` 的部署单重试。
* 重试使用原部署单的配置快照，不重新读取当前 DSL。
* 同一部署单通过 `attempt` 记录重试次数，避免重试时覆盖第一次失败原因。
* 重试成功后，部署单最终状态置为 `SUCCESS`，并更新流水线节点/运行状态。

### 后续回滚预留

MVP 不实现回滚接口，但首次部署时必须记录：

* `previous_image`
* `previous_replicas`
* `previous_revision`
* `target_image`
* `target_replicas`
* `target_revision`
* Deployment UID / resourceVersion / generation 摘要

后续回滚可以基于这些字段实现：

* 回滚到 previous image / replicas。
* 或使用 Kubernetes Deployment revision 做 rollout undo。
* 回滚也应创建新的部署单或回滚单，不能直接覆盖原部署单历史。
