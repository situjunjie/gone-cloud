# DevOps image export OSS pipeline step

## Goal

新增一个 DevOps 流水线 step，用于从镜像仓库导出指定 Docker 镜像为归档文件，并上传到阿里云 OSS。该 step 应运行在流水线 job 的 Docker runtime 中，但不依赖 Docker-in-Docker 或宿主机 Docker socket。

## Confirmed Facts

- 现有流水线 YAML 使用 `stages -> jobs -> steps`，`runsOn` 挂在 job 上。
- `runsOn.group` 第一版支持 `local-docker/default`，`runsOn.container` 是 job 级执行容器镜像。
- `Command` step 是 `JOB_RUNTIME`，在 job 容器 `/workspace` 中执行命令。
- `PrivateRegistryDockerBuild` 是 `PLATFORM` step，通过平台 DockerClient 构建并推送镜像，不进入 job 容器。
- 仓库已有通用流水线工具镜像目录：`yudao-module-devops/docker/pipeline-builder/`。
- 新 step 的目标方案是专用工具容器内使用 `skopeo` 导出镜像，使用 OSS 工具上传文件，不在容器内执行 `docker pull`、`docker save` 或 `dockerd`。

## Requirements

- 新增 step 类型建议命名为 `DockerImageExportOss`。
- 新增专用 job runtime 镜像定义，建议目录为 `yudao-module-devops/docker/image-export-oss/`。
- 专用镜像需预置：
  - `bash`
  - `ca-certificates`
  - `skopeo`
  - OSS 上传工具固定使用官方 `ossutil`
  - 基础排障工具，如 `curl`、`jq`
- step 必须支持从私有镜像仓库拉取镜像，并导出为 `docker-archive` 或标准 `oci-archive` 文件。
- step 必须支持上传导出的归档文件到指定 OSS 路径，路径格式按 `ossutil` 支持的 `oss://bucket/path/file` 风格传入。
- step 镜像应内置压缩工具，第一版支持可选压缩，不默认压缩。
- step 参数必须显式区分镜像仓库凭证和 OSS 凭证。
- step 执行日志不得输出镜像仓库密码、OSS access key secret 等敏感信息。
- step 成功后应输出非敏感结果，至少包括镜像地址、归档格式、OSS 路径、文件名。
- 第一版不支持 Docker-in-Docker、宿主机 Docker socket 挂载、平台节点 `docker save` 兜底执行。

## Acceptance Criteria

- [x] 任务规划文档明确 Dockerfile 目录、基础镜像、预置工具和入口脚本职责。
- [x] 任务规划文档明确 `DockerImageExportOss` 的 YAML 参数契约、必填项、默认值和敏感字段。
- [x] 后续实现时，流水线校验能识别 `DockerImageExportOss` 并校验必填参数。
- [x] 后续实现时，节点注册表能返回 `DockerImageExportOss` 的默认参数和参数 schema。
- [x] 后续实现时，step handler 以 `JOB_RUNTIME` 运行，调用 job 容器中的工具命令完成导出和上传。
- [x] 后续实现时，文档提供可复制的最小 YAML 示例。
- [x] 后续实现时，单元测试覆盖参数校验、命令生成、敏感信息不落 resultJson 的关键路径。

## Out of Scope

- 不实现通用制品仓库管理。
- 不实现跨云 OSS/S3/COS 抽象，第一版仅按阿里云 OSS 设计。
- 不实现镜像多目标仓库同步。
- 不实现 Docker daemon 方式导出镜像。

## Decisions

- 第一版固定使用官方 `ossutil` 上传 OSS，不自研 OSS API 上传脚本。
- `archiveFormat` 第一版支持 `docker-archive` 和 `oci-archive`。
- 镜像归档压缩作为可选能力加入，默认 `none`，避免影响下游直接消费标准 archive 文件。
- 第一版支持流水线 YAML `with` 参数中的简单 `${VAR}` 替换；`image`、`outputFileName`、`oss.path` 等字段会在执行前从 run/job/step 上下文和上游 step outputs 中解析。
