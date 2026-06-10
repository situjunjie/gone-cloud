# brainstorm: 容器部署节点与部署单设计

## Goal

为 DevOps 系统设计一种新的平台原生流水线节点“容器部署”，不再由 Jenkins 执行，而由当前平台直接执行。该节点在触发时创建“部署单”实体，负责承载并追踪 Kubernetes 容器部署全过程、目标配置、运行进度、结果和审计信息。

## What I already know

* 当前流水线 DSL 由平台持有，Jenkins 只是执行器。
* 当前已有内建平台节点 `CODE_MERGE`，其运行明细落在 `dev_pipeline_run` 与 `dev_pipeline_run_log`。
* 当前节点注册表里已经预留 `TYPE_DEPLOY_K8S`，分类为 `PLATFORM`，但尚未开放。
* 当前 K8S 环境已经有连接器边界，`dev_environment.infra_config` 可存 kubeconfig 和 namespace。
* 当前流水线发布运行最终在代码合并完成后触发 Jenkins Runner，Jenkins 回调更新节点状态。
* 当前系统没有专门的“部署单”领域实体，也没有稳定的镜像产物实体。
* 已确认：MVP 采用节点显式配置完整镜像或镜像表达式；前置流水线节点负责把对应镜像构建并推送上去。
* 已确认：本次先做 Phase A，只支持容器部署作为 Jenkins 之后的最后节点，但实现应为后续 Phase B 混合编排预留扩展点。
* 已确认：部署成功必须等待 Kubernetes Deployment rollout ready；仅 patch 提交成功不能算部署成功。
* 已确认：MVP 支持取消和重试，不支持回滚；但后续需要支持回滚，因此部署单必须记录回滚所需的前态信息。
* 已确认：容器部署节点不允许覆盖 namespace，统一使用环境配置里的 Kubernetes namespace。
* 已确认：目标 Kubernetes Deployment 不存在时 MVP 直接失败，不自动创建工作负载。
* 已确认：镜像表达式 MVP 支持平台可解析变量 `${APP_KEY}`、`${COMMIT_SHA}`、`${BRANCH_NAME}`、`${PIPELINE_RUN_ID}`、`${ENV_KEY}`。
* 已确认：内部新增节点类型 `CONTAINER_DEPLOY`，MVP 参数里限定 `infraType=K8S`。
* 已确认：前端部署详情展示部署单主信息、持久化 rollout 摘要，以及实时查询的 Kubernetes 当前状态。
* 已确认：`rolloutTimeoutSeconds` 默认 300 秒。
* 已确认：`replicas` 可选；为空时保留 Deployment 当前副本数。
* 已确认：重试在同一个部署单上增加 attempt，不新建流水线运行，也不增加步骤子表。
* 已确认：MVP 最多允许一个 `CONTAINER_DEPLOY` 节点。

## Assumptions (temporary)

* MVP 先只支持 `K8S` 环境，不扩展到 HOST / SSH / 主机组。
* “容器部署”节点是平台执行节点，但仍隶属于现有 `dev_pipeline_run` 生命周期。
* 本次容器部署节点必须位于 Jenkins 执行节点之后，且作为流水线最后一个执行节点。
* 一次容器部署节点执行，对应创建一张部署单主记录。
* 部署单需要保留足够细节，支持后续扩展为回滚、分批发布、审批、变更审计。
* MVP 优先支持 Kubernetes Deployment 工作负载。

## Open Questions

* 暂无阻塞性开放问题。

## Requirements (evolving)

* 新增平台原生节点类型：容器部署。
* 节点内部类型使用 `CONTAINER_DEPLOY`；现有预留 `DEPLOY_K8S` 暂不作为 MVP 的公开 DSL 类型。
* 该节点被触发时创建部署单，并记录完整部署配置与执行过程。
* 部署单至少承载：目标集群/命名空间、工作负载、镜像、实例数、副本策略、发布状态、当前阶段、错误信息。
* 容器部署节点参数不暴露 namespace 覆盖项；运行时从 `EnvironmentDO.infraConfig.namespace` 获取部署 namespace，并写入部署单快照。
* 目标 Deployment 或目标 container 不存在时，部署单失败；MVP 不自动创建 Deployment / Service / Ingress。
* MVP 的镜像来源采用节点显式配置完整镜像或镜像表达式；前置流水线节点负责把同一个镜像构建并推送到镜像仓库。
* 镜像表达式只支持平台可解析变量：`${APP_KEY}`、`${COMMIT_SHA}`、`${BRANCH_NAME}`、`${PIPELINE_RUN_ID}`、`${ENV_KEY}`；不直接支持 Jenkins-only 变量，除非前置节点显式上报为平台上下文。
* 容器部署节点不在 MVP 阶段依赖完整产物模型，但需要记录最终解析出的完整镜像地址。
* MVP 只支持 `CODE_MERGE -> Jenkins 节点段 -> 容器部署` 的 Phase A 编排；若容器部署后还有节点，发布/校验应阻止。
* 后端实现应抽出平台节点执行入口或执行推进服务，避免把 K8S 部署逻辑直接写死在 Jenkins callback 里。
* 容器部署执行必须等待 Deployment rollout ready 后才将部署单、节点日志和流水线运行标记为成功；超时或 rollout 失败应标记部署单和流水线失败。
* MVP 支持取消部署等待和失败后重试；取消不自动撤销已提交到 K8S 的 patch。
* 重试使用同一个部署单的配置快照，新增 attempt，不新建流水线运行。
* MVP 不实现回滚接口，但部署单需要记录 `previousImage`、`previousReplicas`、`previousRevision` 等回滚前态，为后续回滚能力预留。
* 流水线运行态需要能展示该节点是否已创建部署单、当前部署状态、当前阶段、摘要和详情入口。
* 部署详情页需要展示部署单主信息、持久化 rollout 摘要，以及实时查询的 Kubernetes 当前状态。
* 容器部署目前只面向 K8S 集群。
* 设计应兼容现有平台 DSL、`dev_pipeline_run`、`dev_pipeline_run_log`、K8S connector 边界。

## Acceptance Criteria (evolving)

* [ ] 明确容器部署节点在 DSL、校验、运行编排中的位置与执行责任边界。
* [ ] 明确部署单领域模型、状态机、与流水线运行日志之间的关系。
* [ ] 明确 K8S MVP 支持范围、配置字段、执行流程、失败与幂等策略。
* [ ] 明确前后端最小接口面与展示方式。
* [ ] 明确后续可扩展方向与当前阶段不做内容。

## Definition of Done (team quality bar)

* 设计能直接指导后续后端实体、接口、执行器与测试拆解。
* 设计中包含数据库、服务边界、状态流转、错误处理、幂等/并发考虑。
* 明确 MVP 范围与 out of scope。

## Out of Scope (explicit)

* 非 K8S 的容器部署后端（如 ECS、主机 Docker、Nomad）。
* 完整灰度/蓝绿/金丝雀发布编排。
* 多工作负载编排、Helm Release 全量托管、复杂 GitOps 流程。
* 自动回滚或手工回滚执行接口；但需要保留回滚所需历史数据。
* 本轮直接进入编码实现。

## Technical Notes

* 已检查现有规则：`.trellis/spec/backend/devops-pipeline-guidelines.md`
* 已检查现有规则：`.trellis/spec/backend/devops-infra-guidelines.md`
* 已检查数据库约定：`.trellis/spec/backend/database-guidelines.md`
* 关键现状代码：
  * `PipelineNodeRegistryServiceImpl` 已预留 `TYPE_DEPLOY_K8S`
  * `PipelineSpecValidationServiceImpl` 当前只对 Jenkins 节点做参数校验
  * `PipelineExecutionServiceImpl` 当前编排是 `CODE_MERGE -> Jenkins`
  * `PipelineJenkinsCallbackServiceImpl` 当前以 Jenkins 回调驱动节点完成
  * `KubernetesEnvironmentConnector` 已具备 K8S 环境连接能力
* 设计重点不是新增一个孤立表，而是把“平台执行节点”纳入现有流水线运行编排。
