# Docker 环境大盘和容器运维入口

## Goal

为 DevOps 环境增加 `DOCKER` 基础设施类型，使用户可以通过远程 Docker daemon 连接指定主机，并在环境大盘中查看 Docker daemon 摘要、容器列表、容器滚动日志和容器交互终端。

## What I Already Know

* 用户明确要第一期实现 Docker 类型环境。
* 第一期间范围包括环境大盘、pods 列表、pod 滚动日志、pod 终端执行 `exec -it bash`。
* 产品文案可沿用 “pods”，但后端实现应使用 Docker container 命名，避免和 Kubernetes Pod 混淆。
* 现有环境模块已有 `EnvironmentConnector` / `EnvironmentConnectorFactory` 抽象。
* `dev_environment.infra_config` 已加密存储，适合保存 Docker daemon 连接配置。
* 现有 Kubernetes 环境已实现 dashboard、pods、logs SSE、terminal WebSocket，可作为接口风格和实现边界参考。
* docker-java 已接入，`framework/docker` 已有默认 `DockerClientFactory` 和配置类。

## Assumptions

* 第一期间不做容器创建、删除、镜像 pull/push/build/delete 或流水线 `runsOn` 调度。
* Docker Compose 总览与详情优先基于 Docker labels 聚合，不依赖远端主机安装 `docker compose` CLI。
* 第一期间支持远程 TCP Docker daemon；本机 Unix socket 也可由 docker-java 配置自然支持。
* TLS 证书内容直接保存在加密 `infra_config` JSON 中，响应不返回原文。
* 终端默认自动选择 `bash`，不存在时降级为 `ash` / `sh`。

## Requirements

* 新增环境基础设施类型 `DOCKER`，并补充字典初始化数据。
* `EnvironmentSaveReqVO` 支持 `dockerConfig`。
* Docker 环境创建时必须提供 `host`；更新时如仍为 Docker 环境且不传 `host`，可保留旧配置。
* TLS 开启时证书相关字段应可随配置保存；如果新请求未传证书，更新可保留旧证书。
* 连接检测通过 docker-java 操作级调用完成，不在 Spring bean 构造时 ping Docker。
* `EnvironmentRespVO` 不返回证书、私钥或完整 `infraConfig`，只返回 display-safe 字段。
* 新增 Docker 环境大盘接口，返回 Docker daemon 版本、API 版本、OS、架构、容器数量、镜像数量等摘要。
* 新增 Docker 容器列表接口，返回容器 ID、名称、镜像、状态、端口、labels、是否可打开终端等展示字段。
* 新增 Docker Compose 项目总览接口，按 `com.docker.compose.project` 聚合容器，返回项目、服务、容器、镜像、网络和状态摘要。
* 新增 Docker Compose 项目详情接口，返回项目下的容器、服务、网络、卷、镜像和 Compose label 元信息。
* 新增 Docker 镜像列表接口，返回镜像 ID、仓库标签、大小、创建时间、labels、关联容器/Compose 项目、是否未使用等展示字段。
* 新增 Docker 容器 `start`、`stop`、`restart` 操作。
* 新增 Docker Compose 项目 `start`、`stop` 操作，语义为对该项目已存在容器批量 start/stop，不执行 `docker compose up/down`。
* Docker 环境的变更类操作属于敏感操作，必须在 Service 层使用 `@LogRecord` 记录操作日志。
* 新增 Docker 容器日志 SSE 接口，支持 tail 行数和 follow stream。
* 新增 Docker 容器终端 WebSocket，复用 K8S 终端消息协议：`input`、`resize`、`close`、`output`、`error`、`closed`。
* 所有 Docker 操作必须校验环境存在且 `infraType=DOCKER`。
* Docker 连接失败、容器不存在、容器非 running、日志/终端打开失败应使用 DevOps 业务错误码。

## Acceptance Criteria

* [ ] 可以创建/更新 `infraType=DOCKER` 的环境并加密保存连接配置。
* [ ] 非 Docker 环境访问 Docker dashboard/container/log/terminal 操作会返回不支持的基础设施类型错误。
* [ ] Docker 环境连接检测返回成功摘要或 Docker 连接失败业务错误。
* [ ] Docker dashboard 返回 daemon 和资源计数摘要。
* [ ] Docker containers 接口返回容器展示列表，running 容器 `terminalEnabled=true`。
* [ ] Docker images 接口返回镜像展示列表，并标记被容器/Compose 项目使用情况。
* [ ] Docker Compose projects 接口返回按项目聚合的状态摘要。
* [ ] Docker Compose project detail 接口返回项目容器、网络、卷、镜像详情。
* [ ] Docker 容器 start/stop/restart 和 Compose 项目 start/stop 可以执行，并对非 Docker 环境返回不支持错误。
* [ ] Docker 容器和 Compose 项目变更操作使用 `@LogRecord` 记录环境编号、目标资源和动作。
* [ ] Docker logs SSE 可以按容器输出 stdout/stderr 日志行，客户端断开后释放 Docker 资源。
* [ ] Docker terminal WebSocket 可以打开 running 容器交互 shell，并能写入输入、返回输出、关闭会话。
* [ ] 响应、日志、异常不泄露证书和私钥。
* [ ] DevOps server 模块编译通过，关键单测通过。

## Definition of Done

* Tests added/updated for connector config preservation, response conversion, container conversion, and validation branches where feasible.
* `mvn -pl yudao-module-devops/yudao-module-devops-server -am -DskipTests compile` passes.
* Targeted tests pass or any blocker is documented.
* SQL dictionary bootstrap updated.
* Rollout/rollback considered: new endpoints are additive; rollback removes `DOCKER` dict data and code path.

## Out of Scope

* Docker 容器创建、删除。
* Docker 镜像 pull/push/build/delete 管理。
* `docker compose up/down` 或基于 Compose 文件重新创建资源。
* 将 Docker 环境接入流水线 `runsOn` 调度。
* 多 Docker host 资源池、权限细粒度隔离、审计落库。
* 前端页面实现。

## Technical Notes

* Relevant files inspected:
  * `EnvironmentInfraTypeEnum`
  * `EnvironmentDO`
  * `EnvironmentSaveReqVO`
  * `EnvironmentRespVO`
  * `EnvironmentServiceImpl`
  * `EnvironmentController`
  * `EnvironmentConnector`
  * `KubernetesEnvironmentConnector`
  * `KubernetesPodLogServiceImpl`
  * `KubernetesTerminalWebSocketHandler`
  * `DockerClientFactory`
  * `DockerClientConfiguration`
  * `DockerClientProperties`
  * `sql/mysql/devops-dict.sql`
* Required specs:
  * `.trellis/spec/backend/index.md`
  * `.trellis/spec/backend/directory-structure.md`
  * `.trellis/spec/backend/database-guidelines.md`
  * `.trellis/spec/backend/error-handling.md`
  * `.trellis/spec/backend/quality-guidelines.md`
  * `.trellis/spec/backend/devops-infra-guidelines.md`
