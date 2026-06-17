# 实现 PrivateRegistryDockerBuild 步骤

## Goal

为 DevOps 流水线 YAML 增加 `PrivateRegistryDockerBuild` step，作为平台 step 使用后端 docker-java 能力构建 Docker 镜像并推送到自定义私有镜像仓库。当前任务只实现用户名密码凭证方式，不实现 `serviceConnection`。

## What I Already Know

* 用户给定目标 step：`PrivateRegistryDockerBuild`。
* YAML 参数包含 `artifact`、`image`、`certificate`、`dockerfilePath`、可选 `contextPath`、`noCache`、`variables`、`buildkitVersion`。
* `certificate.type=serviceConnection` 需要明确不实现。
* `certificate.type=usernamePassword` 时必须校验并使用 `certificate.username`、`certificate.password`。
* 现有 DevOps pipeline 使用 `PipelineStepHandler` 执行 step，`Command` 已通过 `PipelineCommandExecutor` 在 Docker job runtime 内执行 shell。
* `PipelineNodeRegistryServiceImpl` 负责注册前端可配置节点类型与参数 schema。
* `PipelineSpecValidationServiceImpl` 负责 YAML/JSON 校验与必填参数校验。

## Requirements

* 注册 `PrivateRegistryDockerBuild` 节点类型，作为 PLATFORM 类别节点暴露给前端。
* 校验必填参数：
  * `artifact`
  * `image`
  * `dockerfilePath`
  * `certificate.type`
  * 当 `certificate.type=usernamePassword` 时，校验 `certificate.username` 与 `certificate.password`
* 明确拒绝或不支持 `certificate.type=serviceConnection`，返回清晰校验错误。
* 执行时作为平台 step：
  * 不要求 job 配置 `runsOn`，不创建 Docker job runtime。
  * 准备源码 workspace。
  * 使用平台 `DockerClientFactory` / docker-java 构建镜像，支持 `dockerfilePath`、`contextPath`、`noCache`、`variables`。
  * 使用 docker-java `AuthConfig` 推送镜像。
  * 尽量避免在日志、`resultJson`、`contextJson` 中泄露密码。
* 成功后在 `resultJson` 中写入非敏感输出，至少包括 `artifact` 与 `image`，供后续部署任务读取。
* 保持现有 Command step 日志写入路径，构建/推送输出应进入 `dev_pipeline_run_log_line`。
* 更新流水线 YAML 说明文档，包含用户给定示例，并注明当前仅支持 `usernamePassword`。

## Acceptance Criteria

* [ ] `PrivateRegistryDockerBuild` 出现在节点注册表和可配置节点类型中。
* [ ] YAML 校验接受合法的 `usernamePassword` 示例。
* [ ] YAML 校验拒绝缺少 `artifact/image/dockerfilePath/certificate` 的配置。
* [ ] YAML 校验拒绝 `serviceConnection`，并提示当前不支持。
* [ ] 执行 handler 使用平台 docker-java 完成 build/push。
* [ ] 密码不写入 resultJson，也不主动输出到日志。
* [ ] 相关文档更新，前端可据此接入。

## Out of Scope

* 不实现 `certificate.type=serviceConnection`。
* 不新增镜像仓库服务连接表或 API。
* 不实现复杂制品仓库管理，只输出当前 step 的 artifact/image 元数据。
* 不扩展自定义 Docker socket 挂载、privileged、网络模式。

## Technical Notes

* Relevant files:
  * `PipelineNodeRegistryServiceImpl`
  * `PipelineSpecValidationServiceImpl`
  * `CommandStepHandler`
  * `DockerPipelineCommandExecutor`
  * `PIPELINE_YAML_SPEC.md`
* Relevant spec:
  * `.trellis/spec/backend/devops-pipeline-guidelines.md`
* Implementation should use `PipelineStepHandler`, not old `PipelineNodeHandler`.
