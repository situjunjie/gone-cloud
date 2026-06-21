# devops image package registry upload step

## Goal

为 DevOps 流水线补充“镜像包上传到镜像仓库”能力，使平台可以消费由 `DockerImageExportOss` 导出的离线镜像包，并支持不依赖 `submit-branch` 的发布触发路径。

## Confirmed Facts

- 当前流水线 YAML 已支持这些执行 step：`Command`、`CodeMerge`、`APPROVAL`、`K8sDeploy`、`K8sImageUpgrade`、`PrivateRegistryDockerBuild`、`DockerImageExportOss`。
- `DockerImageExportOss` 是容器型 step，会在 job runtime 中执行 `/usr/local/bin/export-image-to-oss`。
- `export-image-to-oss.sh` 当前行为是：
  - 从源镜像仓库拉取镜像；
  - 导出为 `docker-archive` 或 `oci-archive`；
  - 可选 `gzip` / `zstd` 压缩；
  - 上传到指定 `oss://...` 路径。
- 当前 `DockerImageExportOss` step 成功后只输出 `image`、`archiveFormat`、`compression`、`outputFileName`、`ossPath`，没有写入 `OfflineImagePackage` 记录。
- 构建机/流水线运行时镜像应统一使用 `yudao-module-devops/docker/pipeline-builder`，后续把 `image-export-oss` 需要的工具也纳入该镜像；不同 step 通过不同 shell 脚本完成具体操作。
- 当前 `OfflineImagePackage` 模块虽然存在查询接口和 DO，但你已明确它目前废弃，不作为本次设计基础。
- 仓库已有统一文件上传基础设施：
  - `POST /infra/file/upload` 可由后端接收文件并直接返回文件 URL；
  - `GET /infra/file/presigned-url` + `POST /infra/file/create` 支持前端直传后，再把文件 URL / path 落库。
- 因此前端“上传镜像包”后，DevOps 后端最终完全可以只消费文件链接（URL）及其必要元数据，而不必自己承接二进制文件流。
- 当前应用发布触发入口只有 `POST /devops/application/release/submit-branch`。
- 当前 `submit-branch` 流程会创建 `PipelineRun`，`triggerType` 固定写入 `APPLICATION_RELEASE_TAB`，并携带变更快照、部署分支、commit SHA 等源码发布上下文。
- 用户期望的新场景是“直接上传镜像包来触发构建/发布”，不走 `submit-branch`。
- 当前 `K8sDeploy` / `K8sImageUpgrade` 下游消费的是镜像地址，不直接消费 OSS 文件。
- 当前 step 参数变量解析来自两类来源：
  - `PipelineRun` 固定字段（如 `branchName`、`commitSha`、`triggerType`）；
  - 执行过程中的 `sharedState` / 上游 step outputs。
- 当前没有现成的“触发接口自定义参数（如 fileUrl）”持久化并注入变量解析器的专用模型，需要本次设计明确。
- 现有 `PrivateRegistryDockerBuild` step 成功后输出标准变量 `artifact` 和 `image`，但你已明确新“镜像包导入 registry” step 不需要承担类似输出契约。
- 仓库已存在 DevOps 制品仓库领域模型：
  - `ArtifactRegistryDO` 保存 registry 实例地址、用户名、加密密码；
  - `ArtifactRepositoryDO` 保存 docker/maven repository 元数据；
  - 当前 registry 搜索能力已能区分 Docker repository。
- 但现有可执行 step（如 `PrivateRegistryDockerBuild`、`DockerImageExportOss`）暂时仍主要使用 step 内联的凭证对象，而不是复用 `ArtifactRegistryDO` / `ArtifactRepositoryDO`。

## Requirements

### Functional

- 新增一个可被流水线使用的 step，用于把离线镜像包上传/导入到目标镜像仓库，供后续部署类 step 使用。
- 该 step 的输入镜像包来源需要兼容 `DockerImageExportOss` 产物。
- 平台需要支持一条不依赖 `submit-branch` 的触发路径，用于“以镜像包为输入”创建并启动流水线运行。
- 该方案不能依赖 `OfflineImagePackage` 领域对象或其现有接口。
- 触发入口主形态是“前端上传文件”，但 DevOps 侧实际接收的是上传完成后的文件链接。
- 触发接口输入以 `fileUrl` 为主，且需要允许任意外部链接，不强制要求是平台文件体系内的受控文件引用。
- 目标镜像仓库凭证配置在流水线 YAML 中，不由前端触发接口传入。
- 与 `DockerImageExportOss` 的兼容性定义在“镜像包文件格式”层面，而不是复用其 `ossPath` 输出。
- 用户已明确：前端上传镜像包后会得到一个新的 `fileUrl`，因此触发接口消费新的文件链接即可，不要求直接使用原始导出步骤产出的 `ossPath`。
- 发布页需要新增“上传镜像部署”交互入口，用于上传镜像包并触发该环境的已发布流水线。
- 新 step 必须显式出现在流水线 YAML 中，是否执行由该次镜像包触发路径决定。
- 新能力必须是流水线里的显式 step，不能由触发接口隐式插入隐藏前置步骤。
- 镜像包触发运行时，仍然必须绑定 `applicationEnvId`，并继续执行该环境已发布的流水线定义。
- 触发接口传入的运行参数（至少包括 `fileUrl`）需要持久化到 `PipelineRun` 扩展上下文，执行时再注入 `sharedState`。
- 新 step 只负责下载镜像包并推送到目标 registry，不要求向下游 step 暴露专门 outputs；下游部署应直接从 registry 拉取镜像。
- 你已明确：下游部署镜像命名规则原则上不改，版本号使用 `${runId}`；因此部署 step 不需要新增专门的镜像输出变量。
- 最终镜像名由流水线配置决定，tag 使用 `${runId}`；因此触发接口不需要传完整 `targetImage`。
- 新 step 通过显式 YAML 参数接收文件链接，例如 `with.fileUrl: ${FILE_URL}`，而不是隐式从运行上下文偷偷读取。
- 目标交互流程已经明确：
  - 发布页提供“上传镜像部署”按钮；
  - 用户在界面上传镜像包；
  - 上传完成后前端获得 `fileUrl`；
  - 用户点击确定后触发当前 `applicationEnvId` 的已发布流水线；
  - 触发接口把 `fileUrl` 等参数传给流水线中的显式 step 处理。
- 新触发入口接口可以挂在现有应用发布域，例如 `ApplicationController` 的 `/devops/application/release/...` 路径下。

### Constraints

- 需要与现有 Pipeline YAML、step 校验、step registry、execution engine 模型保持一致。
- 容器型 step 运行环境优先复用 `yudao-module-devops/docker/pipeline-builder`，避免为导出/导入镜像包长期维护多个构建机镜像。
- 敏感信息（镜像仓库凭证、OSS 凭证）不能写入日志或 `resultJson`。
- 下游部署 step 最终仍应拿到标准镜像地址，而不是 OSS 文件路径。
- 由于允许任意外链，后端需要自行承担远程文件下载、超时控制、文件格式校验、大小限制与来源风险控制。
- 由于目标 registry 凭证位于流水线 YAML，后端仍需确保执行日志、`resultJson`、运行上下文不泄露这些敏感字段。

## Acceptance Criteria

- [ ] 流水线新增一个显式 step，用于从 `with.fileUrl` 下载镜像包，并按 YAML 中配置的镜像仓库/镜像名模板导入 registry。
- [ ] 发布页新增“上传镜像部署”入口；前端上传文件后获得 `fileUrl`，再调用新的应用发布接口触发当前 `applicationEnvId` 的已发布流水线。
- [ ] 新触发接口仅需绑定 `applicationEnvId` 和 `fileUrl`；不传流水线版本 id、不传完整目标镜像名、不传 registry 凭证。
- [ ] 触发接口入参会持久化到 `PipelineRun` 扩展上下文，执行时注入 `sharedState`，供 `${FILE_URL}` 等变量解析。
- [ ] 新 step v1 只支持 `DockerImageExportOss` 产物格式：`docker-archive` / `oci-archive`，以及 `none` / `gzip` / `zstd`。
- [ ] 下游部署 step 继续按原有 YAML 里的镜像模板工作，tag 使用 `${runId}`，不依赖新 step outputs。
- [ ] 形成可实施的 PRD、`design.md`、`implement.md`，可进入实现评审。

## Open Questions

- 当前无阻塞性开放问题；如进入实现前发现数据库字段、前端交互或安全约束仍有歧义，再回到规划阶段补充。

## Notes

- Keep `prd.md` focused on requirements, constraints, and acceptance criteria.
- Lightweight tasks can remain PRD-only.
- For complex tasks, add `design.md` for technical design and `implement.md` for execution planning before `task.py start`.
