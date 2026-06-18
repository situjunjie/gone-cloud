# Gone Cloud 产研一体化平台

Gone Cloud 是面向研发交付流程的产研一体化平台。项目基于 Spring Boot 3、Spring Cloud Alibaba 和多模块工程底座演进，当前重点建设从项目/需求、代码源、应用、变更、流水线到环境部署和运行观测的一条闭环。

本仓库不再定位为通用业务脚手架。原脚手架中的大量通用业务模块不是当前研发主线，根 Maven reactor 已只保留系统底座、基础设施、流程引擎、DevOps 和 Project 模块。

## 项目目标

平台目标是把研发交付过程中的关键对象沉淀为可追踪、可自动化、可审计的数据链路：

```text
项目 / 需求
  -> 代码源 / 应用
  -> 变更 / 分支
  -> 流水线 YAML
  -> 构建 / 测试 / 审批
  -> Docker / Kubernetes 部署
  -> 日志 / 终端 / 环境观测
```

当前建设重点在 DevOps 域：应用和环境建模、变更发布、GitLab 代码源集成、流水线编排与执行、Docker/Kubernetes 环境接入、部署单与实时日志。

## 当前能力

### 代码源与应用

* 代码源提供方管理，当前以 GitLab Access Token 集成为主。
* 应用实体绑定代码源、代码库、默认分支和环境。
* 应用环境用于表达同一个应用在开发、测试、预发、生产等环境中的部署配置。
* 应用详情页可发起发布提交，并关联变更分支和当前流水线运行。

### 变更与代码分支

* 变更记录承载研发交付单元，支持有效、已发布、废弃等状态。
* 创建应用变更时可同步创建 Git 分支，使平台变更与真实代码分支保持一致。
* 变更可挂载到目标环境，记录环境维度的合并、构建、测试、部署和审批状态。
* 代码审核相关字段已进入变更模型，用于后续串联评审流程。

### 流水线 YAML

* 流水线定义由平台持有，使用 YAML 描述 `sources -> stages -> jobs -> steps`。
* 支持草稿、校验、发布、版本回退和版本化缓存配置。
* `jobs.needs` 表达任务 DAG 依赖，stage 只作为展示分组，不作为隐式执行屏障。
* 当前已支持或正在演进的步骤包括：
  * `Command`：在临时 Docker job runtime 中执行 shell。
  * `CodeMerge`：平台侧执行代码合并，冲突时进入等待输入。
  * `APPROVAL`：对接 BPM 流程，审批通过后恢复执行。
  * `K8sDeploy`：按 Deployment manifest 部署到 Kubernetes。
  * `K8sImageUpgrade`：更新已有 Kubernetes Deployment 的镜像。
  * `PrivateRegistryDockerBuild`：平台侧构建并推送私有仓库镜像。

详见 [PIPELINE_YAML_SPEC.md](yudao-module-devops/PIPELINE_YAML_SPEC.md)。

### 构建运行时与缓存

* 流水线运行创建 run 级 workspace，所有 job 共享源码和产物目录。
* 每个容器型 job 创建临时 Docker 容器，job 内多个 step 复用同一容器。
* job 结束后销毁容器，减少构建环境污染。
* Maven、Gradle、npm、pnpm、yarn、Go 等依赖缓存独立于 workspace，并按流水线定义版本配置。
* 支持清理当前流水线定义下的派生缓存目录。

详见 [PIPELINE_DOCKER_RUNTIME_DESIGN.md](yudao-module-devops/PIPELINE_DOCKER_RUNTIME_DESIGN.md)。

### 环境与部署

* 环境模型支持 Kubernetes、Docker、Host 等基础设施类型的持续扩展。
* Kubernetes 接入基于 Fabric8 client，支持集群大盘、Deployment、Pod、Service、Namespace 等资源查询。
* Docker 接入基于 docker-java，支持环境大盘、容器、镜像、网络、Compose 项目等资源查询。
* Host 接入基于 SSH，支持主机基础信息、CPU、内存、磁盘、负载、进程等观测。
* 部署单记录应用部署过程中的镜像、namespace、workload、container、revision、状态等信息。

### 日志、终端与观测

* 流水线命令输出落入行级日志表，并通过 SSE 支持实时滚动和断点重连。
* Kubernetes Pod 日志支持尾部历史日志和实时流。
* Docker 容器日志支持实时查看。
* Kubernetes、Docker、Host 均提供 WebSocket 终端入口，用于受控运维操作。

### 离线交付

* 已建模离线镜像包，用于后续支持无公网客户环境下的镜像包导出、下载和内网导入。
* 当前侧重公网构建侧和平台记录，内网导入链路作为后续任务演进。

## 技术架构

### 后端底座

* Java 17
* Spring Boot 3.5.x
* Spring Cloud Alibaba / Nacos / Gateway
* MyBatis Plus / MyBatis Plus Join
* Flowable BPM
* Redis / Redisson
* Docker Java
* Fabric8 Kubernetes Client
* Maven 多模块工程

### 运行形态

仓库同时保留两种运行方式：

* 单体聚合：`yudao-server` 聚合系统、基础设施、BPM、DevOps、Project 等模块，适合本地开发和快速联调。
* 分布式服务：`gateway-server`、`system-server`、`infra-server`、`devops-server`、`project-server` 可独立构建镜像，并通过 Jenkins + Docker Compose 部署。

`Jenkinsfile` 中当前启用服务：

| 服务 | Maven 模块 | 说明 |
|---|---|---|
| `gateway-server` | `yudao-gateway` | API 网关 |
| `system-server` | `yudao-module-system/yudao-module-system-server` | 用户、权限、租户、字典等系统能力 |
| `infra-server` | `yudao-module-infra/yudao-module-infra-server` | 文件、配置、定时任务、API 日志等基础设施能力 |
| `devops-server` | `yudao-module-devops/yudao-module-devops-server` | 产研交付和 DevOps 核心能力 |
| `project-server` | `yudao-module-project/yudao-module-project-server` | 项目管理能力预留模块 |

## 模块结构

```text
.
├── yudao-dependencies/          # 统一依赖 BOM
├── yudao-framework/             # 通用框架 starter 和基础能力
├── yudao-gateway/               # Spring Cloud Gateway
├── yudao-server/                # 单体聚合启动入口
├── yudao-module-system/         # 系统管理、权限、租户、字典
├── yudao-module-infra/          # 基础设施能力
├── yudao-module-bpm/            # Flowable 流程能力
├── yudao-module-devops/         # 产研交付 / DevOps 核心模块
├── yudao-module-project/        # 项目管理模块骨架
├── sql/                         # 数据库初始化脚本
├── script/docker/standalone/    # 分布式 Docker Compose 部署模板
└── .trellis/                    # Trellis 任务、PRD、项目规范和研发记录
```

`yudao-module-devops` 的核心后端对象包括：

| 领域对象 | 主要表 | 说明 |
|---|---|---|
| 代码源 | `devops_repository_provider` | GitLab 等代码源连接配置 |
| 应用 | `dev_application`、`dev_application_env` | 应用及应用环境绑定 |
| 变更 | `dev_change`、`dev_change_env` | 研发变更及环境挂载状态 |
| 环境 | `dev_environment`、`dev_environment_host` | Kubernetes、Docker、Host 等基础设施配置 |
| 流水线 | `dev_pipeline_definition`、`dev_pipeline_definition_version` | 流水线定义、草稿、发布版本、回退信息 |
| 运行记录 | `dev_pipeline_run`、`dev_pipeline_run_job`、`dev_pipeline_run_log`、`dev_pipeline_run_log_line` | 流水线运行、job 状态、结构化日志和实时输出 |
| 部署 | `dev_deployment_order` | Kubernetes/Docker 部署单 |
| 离线镜像 | `dev_offline_image_package` | 离线镜像包元数据 |

## 本地开发

### 环境要求

* JDK 17+
* Maven 3.9+
* MySQL 8+
* Redis
* Docker Engine，调试 Docker runtime 或 Docker 环境能力时需要
* 可访问的 Kubernetes 集群，调试 K8s 环境和部署能力时需要

### 初始化数据库

按需导入 `sql/mysql/` 下的初始化脚本。DevOps 相关脚本主要包括：

```text
sql/mysql/devops.sql
sql/mysql/devops-dict.sql
sql/mysql/devops-menu.sql
```

本地配置在 `yudao-server/src/main/resources/application-local.yaml`。提交前不要把个人数据库、Redis、Token、证书等敏感配置固化到公共文档或代码中。

### 启动单体服务

```bash
mvn -pl yudao-server -am spring-boot:run
```

默认使用 `local` profile，端口见 `yudao-server/src/main/resources/application-local.yaml`，当前为 `48080`。

### 常用构建命令

```bash
# 编译聚合服务和依赖模块
mvn -pl yudao-server -am clean package -DskipTests

# 编译 DevOps 独立服务
mvn -pl yudao-module-devops/yudao-module-devops-server -am clean package -DskipTests

# 编译 Project 独立服务
mvn -pl yudao-module-project/yudao-module-project-server -am clean package -DskipTests

# 运行 DevOps 模块相关测试
mvn -pl yudao-module-devops/yudao-module-devops-server -am test
```

### 分布式部署

分布式部署入口：

```text
Jenkinsfile
script/docker/standalone/docker-compose.yml
script/docker/standalone/.env.example
script/docker/standalone/README.md
```

部署约定：

* Jenkins 负责选择服务、Maven 打包、Docker 镜像构建和远端 Compose 部署。
* MySQL、Redis、Nacos、XXL-Job 等基础设施由部署目标环境提供，不由 Compose 自动创建。
* 目标机器上的真实 `.env` 由运维维护，Jenkins 不覆盖。

## 关键文档

* [DevOps Pipeline YAML Spec](yudao-module-devops/PIPELINE_YAML_SPEC.md)
* [DevOps 流水线 Docker 临时构建环境设计方案](yudao-module-devops/PIPELINE_DOCKER_RUNTIME_DESIGN.md)
* [Jenkins Runner 配置说明](yudao-module-devops/JENKINS_RUNNER_CONFIGURATION.md)
* [独立 Docker Compose 部署说明](script/docker/standalone/README.md)
* Trellis 历史 PRD：`.trellis/tasks/` 和 `.trellis/tasks/archive/`
* 项目规范：`.trellis/spec/backend/`

## 开发约定

* 新业务能力优先放入对应 `yudao-module-<domain>`，不要直接堆到 `yudao-server`。
* 跨模块公开契约放在 `*-api`，本地执行逻辑放在 `*-server`。
* DevOps 流水线定义由平台持有，不把 DSL 所有权下放到 Jenkins Job。
* 流水线新增或修改 YAML 字段时，同步更新 `PIPELINE_YAML_SPEC.md`。
* 新增数据库对象时同步维护 `sql/mysql/devops.sql` 及必要的 dict/menu 脚本。
* 涉及外部凭据、Token、镜像仓库密码、KubeConfig、SSH 私钥的功能，日志、`contextJson` 和 `resultJson` 中必须脱敏。

## 当前演进方向

* Project 模块从骨架扩展为项目、需求、迭代和交付计划管理。
* DevOps 流水线从单机调度继续演进到资源池、并发 worker、远程 Docker/Host 执行。
* 完善应用发布视图，将变更、分支、流水线、部署单和运行环境串成一张可追踪交付链路。
* 增强 Kubernetes、Docker、Host 环境的观测、日志和终端能力。
* 补齐离线交付链路，支持公网构建、离线包下载、内网导入和部署。
