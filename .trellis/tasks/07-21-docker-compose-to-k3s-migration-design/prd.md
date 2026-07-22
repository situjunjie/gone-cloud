# Docker Compose 到 K3s 迁移方案

## Goal

为当前通过 Docker Compose 部署的 Gone Cloud 微服务设计一套可分阶段实施、可验证、可回滚的 K3s 迁移方案。方案不仅覆盖常规微服务的 Kubernetes 工作负载转换，还必须解决 `devops-server` 部署到目标集群后访问所在集群、执行流水线任务、管理 workspace、拉取 Git 源码以及构建和推送镜像的问题，最终摆脱对宿主机 Docker Socket 和固定节点目录的强依赖。

本任务当前只交付迁移需求、技术方案和实施计划，不直接修改业务代码或部署生产集群。

## Confirmed Facts

- 当前生产形态的 Compose 文件为 `script/docker/standalone/docker-compose.yml`，其中包含 `gateway-server`、`system-server`、`infra-server`、`bpm-server`、`devops-server` 和 `project-server`。
- Java 服务通过环境变量连接 MySQL、Redis、Nacos、XXL-JOB、RabbitMQ、Kafka 和 RocketMQ；这些中间件没有定义在 standalone Compose 文件内，当前按外部依赖处理。
- `gateway-server` 暴露宿主机端口 `48080`，其余服务主要依赖 Nacos 服务发现，不直接暴露宿主机端口。
- 每个服务当前把 `/root/logs` 挂载到宿主机目录。
- `devops-server` 使用 docker-java，通过 `unix:///var/run/docker.sock` 控制宿主机 Docker daemon。
- `devops-server` 当前把宿主机的 pipeline workspace 和 Git workspace 以相同绝对路径挂入容器，Docker daemon 创建的构建容器依赖这些宿主机路径。
- 当前 `LocalPipelineWorkspaceService` 直接使用 Java NIO 管理本地目录，并用 Redisson 分布式锁保护共享路径；`GitWorkspaceServiceImpl` 在 devops-server 进程内调用本机 Git 命令完成 clone、merge、冲突处理和 push。这意味着 Git 操作不能在不改业务交互的前提下全部简单迁入临时 Job。
- K3s 默认使用 containerd；containerd Socket 与 Docker API 不兼容，不能把 docker-java 的地址直接替换为 containerd Socket。
- 部署在 K3s 中的 `devops-server` 可以使用 Pod 自动挂载的 ServiceAccount 凭证和集群内 API 地址访问所在集群，但必须按最小权限配置 RBAC。
- 项目已经使用 Fabric8 Kubernetes Client 7.7.0，但当前 `KubernetesClientFactory#create(String kubeconfig)` 强制要求显式 kubeconfig，只服务于数据库中配置的目标环境；尚无读取 Pod ServiceAccount 的 in-cluster 平台客户端。
- 平台集群客户端与用户配置的外部目标集群客户端用途不同：前者负责创建流水线 Job、读取执行日志和清理运行时资源，后者继续负责目标环境部署、日志和终端等功能，二者需要独立配置与权限边界。
- 当前 standalone 部署文档明确把 MySQL、Redis、Nacos、XXL-JOB 等基础设施作为服务器已有的外部依赖，而不是本 Compose 模板的一部分。
- 目标 K3s 当前没有额外安装 Longhorn、NFS、CephFS 等存储能力，现阶段只有 K3s 默认 `local-path`。`local-path` 是节点本地卷，适合单节点或可丢弃数据，不满足跨节点共享和节点故障后自动恢复的目标。
- 当前流水线规范只接受 `runsOn.group=local-docker/default`，`PipelineExecutionEngine` 只注入一个 `PipelineJobRuntimeManager` 实现；新增 Kubernetes 运行时需要按 executor group 路由，而不能直接用第二个 Spring Bean 替换现有 Docker 实现。
- 当前 job runtime 是“启动长驻 Docker 容器，再逐 step 通过 Docker exec 执行命令”的模型；迁移到 Kubernetes Job 时必须定义 runner Pod 的存活、逐 step exec、成功退出、失败退出、取消和 Job 状态回写协议。
- `PrivateRegistryDockerBuild` 当前是直接调用平台 DockerClient 的平台步骤，不经过 `runsOn` job runtime。移除 Docker Socket 前还必须将该步骤迁移到 BuildKit，不能只迁移普通 Command 类步骤。
- 当前 `PipelineExecutionEngine` 在 devops-server 进程内创建并准备 run 级 workspace，Git checkout、多个 Job、平台步骤、审批/冲突恢复、artifacts、reports 和 cache 都围绕同一目录工作。立即改成每个 Job 独立 `emptyDir` 会要求同步重构源码凭证传递、跨 Job 产物、缓存和恢复协议。
- `CodeMergeStepHandler` 明确声明为 `PLATFORM` 运行时，直接在 devops-server JVM 内调用 `CodeMergeService`；`GitCommandExecutor` 使用 `ProcessBuilder` 调用本机 `git`，且 devops-server Dockerfile 已安装 Git，因此迁入 K3s 后无需把 CodeMerge 改成 Kubernetes Job。
- CodeMerge 冲突上下文会把 workspace key/path 写入数据库日志；查看冲突、解决冲突和继续执行时会根据持久化路径重新注册 workspace。只要 PVC 挂载路径稳定，devops-server Pod 重启后可以恢复；`local-path` 所在节点故障仍会导致 workspace 不可用。
- 关联任务 `06-30-application-orchestration-k8s-design` 只设计应用级 Kubernetes 编排配置、变量和版本能力，不包含本次平台迁移与流水线运行时改造。

## Requirements

- R1. 盘点 Compose 中全部服务、端口、依赖、配置、日志目录、健康检查和持久化要求，并给出对应 Kubernetes 资源模型。
- R2. 设计 K3s 目标架构，包括 namespace、Deployment/StatefulSet、Service、Ingress、ConfigMap、Secret、ServiceAccount、RBAC、存储、镜像仓库、可观测性和资源治理边界。
- R3. 给出普通微服务的迁移模板和配置转换规则，处理 Nacos 注册发现、网关入口、滚动升级、探针、资源限制和优雅停机。
- R4. 为 `devops-server` 设计 in-cluster Kubernetes Client 访问方式，明确 ServiceAccount、最小 RBAC、namespace 隔离、凭证轮换和禁止使用集群管理员凭证的约束。
- R4.1 保留基于显式 kubeconfig 的外部目标环境客户端，平台 in-cluster client 不得隐式替换用户环境连接，也不得把平台 ServiceAccount 权限用于访问外部集群。
- R5. 为流水线执行设计 Kubernetes Job 运行时，覆盖创建、调度、日志、取消、超时、重试、资源限制、状态回写和任务清理。
- R6. 设计 Git 源码和 workspace 数据流，明确同 Pod `emptyDir`、跨 Pod RWX PVC、对象存储/制品库的职责边界，并隔离不同 pipeline run 的工作目录。
- R6.1 CodeMerge 保持平台步骤：devops-server 镜像继续包含 Git，Git workspace 与 pipeline workspace 使用 PVC 持久化；构建 Job 只能挂载本次 run 的 pipeline 子目录，不能访问独立 Git 工作区或其他 run。
- R7. 设计不依赖 Docker Socket 的镜像构建方案，评估并确定 BuildKit/buildkitd 的部署模式、缓存、Registry 凭证、镜像推送和多架构扩展边界。
- R7.1 `buildctl` 必须随专用构建 runner 镜像运行在短生命周期 Kubernetes Job 中；`buildkitd` 作为独立集群服务运行，宿主机和 devops-server 镜像都不以安装 `buildctl` 作为最终方案前提。
- R8. 明确现有 docker-java 流水线运行时与新 Kubernetes 运行时的兼容、切换和回滚策略，迁移期间不能阻断现有流水线。
- R8.1 迁移期通过不同 executor group 路由 Docker 和 Kubernetes 运行时；Docker Socket 只能作为受限的临时兼容能力，必须定义停用条件、审计方式和最终删除步骤。
- R9. 给出数据库、缓存、注册中心和消息中间件的迁移边界，以及服务切换顺序、数据备份、验证、故障回退和停机窗口要求。
- R10. 对 Docker Socket、Kubernetes API、Registry 凭证、Git 凭证、Secret、Pod 权限和网络访问实施最小权限与审计设计。
- R11. 将迁移拆分为可独立验证的阶段或子任务，提供每一阶段的前置条件、交付物、验证门禁和回滚点。
- R12. 目标资源和运行时必须具备多节点兼容性，不使用业务 Pod `hostPath`、宿主机 Docker Socket或固定节点目录作为正常运行前提；第一阶段允许服务保持单副本，不把多节点兼容等同于所有组件立即实现高可用。
- R13. 单节点阶段允许通过 PVC 使用 K3s 默认 `local-path`，但 StorageClass 必须可配置且不得直接写宿主机路径；关键 PVC 必须有备份/恢复办法，扩容多节点前必须迁移到支持节点故障恢复的存储实现。

## Acceptance Criteria

- [ ] 服务与外部依赖清单完整，并能从当前 Compose/配置追溯到目标 Kubernetes 资源和配置来源。
- [ ] 目标架构明确说明 devops-server 如何通过 in-cluster Kubernetes Client 访问所在 K3s 集群，以及对应的最小 RBAC 权限。
- [ ] 流水线从 Git checkout、workspace 准备、Job 执行、日志采集到 BuildKit 构建推送的完整数据流有清晰边界。
- [ ] 方案明确单节点与多节点 K3s 下存储、调度和故障恢复的差异，不能把 K3s 默认 local-path 误当作跨节点 RWX 存储。
- [ ] 单节点首期明确哪些数据使用 `local-path` PVC、哪些使用 `emptyDir` 或外部存储，并提供未来更换 StorageClass 时的数据迁移门禁。
- [ ] PVC 迁移方案包含停写、复制、校验、切换和回滚步骤，且不要求修改业务代码中的 workspace 路径。
- [ ] 迁移期间存在可操作的双运行或兼容策略，现有 Docker Compose 和 docker-java 流水线有明确退出条件与回滚路径。
- [ ] 新旧 executor group 可以并存、独立限制并发并逐条迁移流水线；BuildKit 和 Kubernetes Job 验证完成后能关闭 Docker executor 且不再挂载 Docker Socket。
- [ ] 每个迁移阶段都有验证方法，至少覆盖服务可用性、注册发现、网关流量、流水线执行、镜像构建、日志和数据安全。
- [ ] 最终形成经过用户确认的 `prd.md`、`design.md` 和 `implement.md`，但在明确批准前不进入代码实现。

## Out Of Scope

- 本规划阶段直接实施 K3s 安装、生产数据迁移或流量切换。
- 第一阶段把 MySQL、Redis、Nacos、XXL-JOB、RabbitMQ、Kafka 和 RocketMQ 等既有外部基础设施迁入 K3s；Java 服务继续通过受控网络访问现有实例。
- 在需求和技术方案确认前修改现有流水线执行代码。
- 在本任务内实现 `06-30-application-orchestration-k8s-design` 的应用编排页面和版本管理功能。

## Decisions

- D1. 第一阶段只迁移 `gateway-server`、`system-server`、`infra-server`、`bpm-server`、`devops-server` 和 `project-server` 以及支撑 devops 流水线所需的集群运行时；现有外部基础设施保持不动。这样优先验证无状态服务迁移和流水线运行时改造，不把 StatefulSet、数据搬迁、备份恢复与中间件高可用风险混入第一阶段。
- D2. 目标架构按多节点 K3s 兼容设计，即使首个环境只有一个节点。第一阶段不强制所有服务多副本高可用，但禁止新增依赖特定节点的 hostPath、Docker Socket 和本地镜像假设，保证后续增加节点时不需要重做应用运行时设计。
- D3. 当前 K3s 只有一个节点，第一阶段使用默认 `local-path` 实现 PVC，不安装 Longhorn。`local-path` 只作为单节点过渡能力：devops-server 的 Git/运行状态和流水线 run workspace 使用 RWO PVC，Job 通过每个 run 的 `subPath` 挂载相同目录；服务日志输出到 stdout，镜像与制品进入外部 Registry/制品存储。扩容多节点前必须安装分布式存储或接入外部共享存储并迁移关键 PVC。
- D4. 流水线采用双运行时过渡：保留 `local-docker/default` 承载未迁移流水线，新增 Kubernetes executor group 承载 Kubernetes Job；`PipelineJobRuntimeManager` 改为按 group 路由。迁移期 devops-server 可临时挂载 Docker Socket，但完成 Kubernetes Job、BuildKit、镜像归档/导入等 Docker 相关步骤迁移并通过回归后，必须禁用 Docker executor、移除 Socket 挂载并停止依赖宿主机 Docker。
- D5. BuildKit 采用“集群内独立 `buildkitd` Service + 每次构建创建包含 `buildctl` 的 Kubernetes Job”模式。devops-server 只通过平台 Kubernetes Client 创建、监控和取消构建 Job；`buildctl` runner 挂载本次 run workspace并把构建上下文传给 `buildkitd`，`buildkitd` 直接推送镜像到 Registry。宿主机不安装 BuildKit，devops-server 镜像也不内置 `buildctl` 作为最终执行路径。
- D6. PVC 的业务名称、挂载路径和 run 目录结构保持稳定，StorageClass 通过部署配置注入。`local-path` 到 RWX 后端不是单纯修改字段：需要创建新 PVC、停止或冻结 workspace 写入、复制并校验数据、切换工作负载挂载，并保留旧 PVC 作为回滚点；可重建的 Git workspace 和临时构建目录可以直接清理后重建。

## Open Questions

- 目标集群使用哪个私有镜像仓库和入口控制器。
