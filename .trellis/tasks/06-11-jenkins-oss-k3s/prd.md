# 离线镜像包交付：Jenkins 构建 → OSS → 内网 k3s 零依赖导入

## Goal

为"无网络客户环境"提供一条容器镜像的离线交付链路：公有云侧 Jenkins 构建镜像后 `docker save` 成离线包并上传到平台配置的 OSS；运维人员从平台下载该包，物理搬运到客户内网；内网侧平台接收上传的离线包，导入到内置的单节点 k3s（containerd `ctr import`，零额外常驻组件），再完成部署。

核心约束：**内网侧 k3s 必须尽可能轻量，不引入任何新增常驻服务（不建内网 registry、不装 skopeo/helm 等额外工具），只用 k3s 自带的 containerd / ctr / kubectl。**

## What I already know

平台技术栈（已确认，来自代码库探查）：
* Java 17 + Spring Boot 3.5.9 + Maven，yudao 多模块架构。
* OSS：`yudao-module-infra` 的 `S3FileClient`（AWS SDK v2，S3 兼容，已支持阿里云 OSS/MinIO 等），`FileServiceImpl` 封装上传下载并用 `FileDO` 落库。
* Jenkins：根目录 `Jenkinsfile` 定义构建（Maven 编译 + Docker build），`PipelineJenkinsCallbackServiceImpl` 处理回调，`PipelineRunDO` 记录构建（jenkinsBuildNumber、commitSha 等）。
* k8s：`yudao-module-devops` 用 Fabric8 client，`KubernetesEnvironmentConnector` 连集群，`KubernetesDeploymentManifestSupport` 解析 YAML 并注入镜像（`container.setImage(...)`），`DeploymentOrderDO` 记录部署（image、namespace、workloadName、containerName 等）。
* 数据模型：暂无独立的"镜像制品/发布版本/离线包"实体，制品信息隐含在 PipelineRun + DeploymentOrder 中。

## Assumptions (temporary)

* 内网 k3s 为**单节点**，且是终态（已确认，不考虑多节点扩展）。
* 交付方案为 `docker save` tar 包 + `k3s ctr images import`（已确认，零依赖）。

## Decisions (confirmed)

* **D1 部署形态**：内网平台后端 MVP 按**宿主机进程**形态实现（与 k3s 同机，直接 shell 调 `k3s ctr images import`）。但导入动作抽象为轻量接口 `ImageImportExecutor`，MVP 只实现 `HostDirectImportExecutor`；将来平台容器化时新增 `ContainerdSocketImportExecutor`（挂 containerd socket），上游不改。配置项 `devops.k3s.import-mode`（默认 `host-direct`）。**（本任务 Out of Scope，内网侧后续做）**

* **D2 离线包粒度**：单镜像包——一个离线包对应一个镜像的 `docker save` tar。数据模型简单，一对一关系。

* **D3 MVP 范围**：**本任务仅做公网打包侧**（Jenkins 构建后 docker save → 上传 OSS → 平台提供下载入口），内网导入侧（上传包 → 校验 → ctr import）作为独立后续任务。

* **D4 多架构**：MVP 默认 amd64，不考虑 arm64。Jenkinsfile 构建时不指定 `--platform`（默认构建机架构，一般是 amd64）。

* **D5 数据模型**：新增实体 `OfflineImagePackageDO`，字段包含：
  - `pipelineRunId`（Long，关联构建）
  - `imageName` / `imageTag` / `imageDigest`（镜像标识）
  - `architecture`（String，默认 "amd64"，预留扩展）
  - `fileId`（Long，关联 `FileDO`，指向 OSS tar 文件）
  - `packageSize`（Long，字节）
  - `status`（Integer，0=打包中 1=就绪 2=失败）
  - `createdTime` / `updatedTime`（继承 `BaseDO`）

* **D6 节点集成方式**：作为新的**流水线节点类型** `EXPORT_OFFLINE_IMAGE`（JENKINS 节点），走完整 DSL 编排体系。
  - 在 `PipelineNodeRegistryServiceImpl` 注册节点，参数包含 `imageName`、`imageTag`、`ossUploadPath`、`ossCredentialsId` 等。
  - 在 `JenkinsfileGeneratorServiceImpl` 的 switch 加 case，生成 stage 调用 shared library 方法 `goneDevopsExportOfflineImage`。
  - 在 `gone-devops-shared` 仓库新增 `vars/goneDevopsExportOfflineImage.groovy`，实现 `docker save` + OSS 上传（调 `aws s3 cp` 或 `ossutil cp`，Jenkins agent 已有）+ 回调平台传 `packageMetadata`（tar 路径、大小、digest）。
  - **不实现** `PipelineNodeRuntimeHandler`（JENKINS 节点无需平台端 handler，回调由通用 `PipelineJenkinsCallbackServiceImpl` 处理）。

* **D7 执行位置**：`docker save` + OSS 上传在 **Jenkins agent** 上执行（shared library 方法），平台后端通过**回调**接收离线包元数据并记录 `OfflineImagePackageDO` + `FileDO`。

* **D8 OSS 上传工具**：Jenkins agent 已有 `aws-cli` 或 `ossutil`，shared library 直接调用，不需要临时容器。

* **D9 上传方式修订（PR4 实施确认，覆盖 D5/D7/D8）**：上传改用 **Jenkins 的 Aliyun OSS Uploader 插件**直接上传到 OSS，插件返回离线包的 **OSS 访问 URL**。回调时只回传 `ossUrl`，**平台后端不再联动 `infra` 模块、不创建 `FileDO`**。
  - 原因：`devops-server` 模块未依赖 `infra-api`，且 `FileApi.createFile` 要求文件字节（平台从不接触 tar），无法登记已存在的 OSS 对象。
  - 数据模型变更：`OfflineImagePackageDO` 删除 `fileId` 字段，新增 `ossUrl`（varchar 1024）。`dev_offline_image_package` 表同步删除 `file_id` 列与 `idx_tenant_file` 索引，新增 `oss_url` 列；`package_size` 改为 `NOT NULL DEFAULT 0`。
  - 回调元数据：`packageMetadata` 中 `ossFilePath` 改为 `ossUrl`（完整可访问 URL）。
  - 下载接口：`getDownloadUrl` 直接返回 `ossUrl`（OSS 公共读 URL），不再生成预签名。
  - Handler：`ExportOfflineImageNodeRuntimeHandler.onCompleted` 解析 `ossUrl` 落库；**保留** handler（与原 D6"不实现 handler"的描述不符，但实际回调分发依赖 `List<PipelineNodeRuntimeHandler>`，需专用 handler 落库离线包记录）。

## Open Questions

（无，所有设计决策已确认）

## Requirements (完整需求)

### 核心功能（公网打包侧，本任务范围）

1. **新增流水线节点类型 `EXPORT_OFFLINE_IMAGE`**
   - 节点分类：JENKINS（在 Jenkins agent 执行）
   - 节点参数（UI 可配置）：
     - `imageName`（String，必填）：要导出的镜像名（如 `${IMAGE_REPO_PREFIX}/yudao-gateway`）
     - `imageTag`（String，必填）：镜像标签（如 `${IMAGE_TAG}` 或 `${BUILD_NUMBER}`）
     - `ossEndpoint`（String，必填）：OSS endpoint（如 `https://oss-cn-hangzhou.aliyuncs.com`）
     - `ossBucket`（String，必填）：OSS bucket 名称
     - `ossPath`（String，必填）：OSS 路径前缀（如 `offline-images/${APP_KEY}/`）
     - `ossCredentialsId`（String，必填）：Jenkins 凭据 ID（存储 OSS AccessKey/SecretKey）
     - `ossUploadTool`（String，可选，默认 `aws-cli`）：上传工具（`aws-cli` 或 `ossutil`）

2. **Jenkinsfile 生成增强**
   - `JenkinsfileGeneratorServiceImpl` 的 `appendStage` switch 增加 `TYPE_EXPORT_OFFLINE_IMAGE` case。
   - 生成的 stage 调用 `goneDevopsExportOfflineImage(imageName: ..., imageTag: ..., ...)`。
   - 回调参数 `packageMetadata` 包含：`ossFilePath`（完整 OSS 路径）、`packageSize`（字节）、`imageDigest`（SHA256）。

3. **Jenkins Shared Library 新增方法**（`gone-devops-shared` 仓库）
   - 文件：`vars/goneDevopsExportOfflineImage.groovy`
   - 功能：
     - 校验 `imageName:imageTag` 镜像存在（`docker images -q`）。
     - `docker save ${imageName}:${imageTag} -o /tmp/${sanitized-filename}.tar`。
     - 计算 tar SHA256（`sha256sum`）。
     - 根据 `ossUploadTool` 调用 `aws s3 cp` 或 `ossutil cp` 上传到 OSS（使用 Jenkins 凭据绑定 `AWS_ACCESS_KEY_ID`/`AWS_SECRET_ACCESS_KEY` 或 `OSS_ACCESS_KEY_ID`/`OSS_ACCESS_KEY_SECRET`）。
     - 清理本地 tar 文件。
     - 返回 metadata map（`ossFilePath`、`packageSize`、`imageDigest`）供回调使用。

4. **数据模型新增**
   - 新建表 `dev_offline_image_package`，对应实体 `OfflineImagePackageDO`（字段见 D5）。
   - Mapper：`OfflineImagePackageMapper` extends `BaseMapperX<OfflineImagePackageDO>`。

5. **回调处理增强**
   - `PipelineJenkinsCallbackServiceImpl` 的 `handleCallback` 里，当 `nodeType == TYPE_EXPORT_OFFLINE_IMAGE` 且 `action == COMPLETED` 时：
     - 从 `reqVO.getPackageMetadata()` 提取 `ossFilePath`、`packageSize`、`imageDigest`。
     - 创建 `FileDO` 记录（`configId` 指向当前 OSS 配置，`path` = `ossFilePath`，`size` = `packageSize`）。
     - 创建 `OfflineImagePackageDO` 记录（关联 `pipelineRunId`、`fileId`、镜像信息、status=1 就绪）。

6. **API 新增**
   - `GET /admin-api/devops/offline-image-package/list`：查询离线包列表（可按 `pipelineRunId`、`imageName`、`status` 过滤）。
   - `GET /admin-api/devops/offline-image-package/{id}`：获取单个离线包详情。
   - `GET /admin-api/devops/offline-image-package/{id}/download-url`：生成 OSS 预签名下载链接（复用 `FileServiceImpl.getFilePresignedUrl`）。

7. **前端 UI（简化）**
   - 流水线编辑器节点面板新增"导出离线镜像"节点（图标 `download`，类型 `EXPORT_OFFLINE_IMAGE`）。
   - 节点配置表单根据节点参数 schema 自动渲染。
   - 流水线运行详情页：如果某个节点是 `EXPORT_OFFLINE_IMAGE` 且 completed，展示"下载离线包"按钮（调 `/download-url` API 获取链接）。

### 内网导入侧（Out of Scope，后续任务）
* 平台接收上传的离线包 → 校验 → `ImageImportExecutor` 执行 `k3s ctr images import` → 可部署。

## Acceptance Criteria (可测试标准)

* [ ] `PipelineNodeRegistryServiceImpl` 注册了 `TYPE_EXPORT_OFFLINE_IMAGE` 节点，参数 schema 完整。
* [ ] `JenkinsfileGeneratorServiceImpl` 能为 `EXPORT_OFFLINE_IMAGE` 节点生成正确的 stage（调 `goneDevopsExportOfflineImage`）。
* [ ] `gone-devops-shared/vars/goneDevopsExportOfflineImage.groovy` 实现完整，能 docker save + 上传 OSS + 返回 metadata。
* [ ] 数据库表 `dev_offline_image_package` 创建，字段与 `OfflineImagePackageDO` 一致。
* [ ] `PipelineJenkinsCallbackServiceImpl` 处理 `EXPORT_OFFLINE_IMAGE` 节点的 COMPLETED 回调，能正确创建 `FileDO` 和 `OfflineImagePackageDO`。
* [ ] API `/admin-api/devops/offline-image-package/list` 返回离线包列表。
* [ ] API `/admin-api/devops/offline-image-package/{id}/download-url` 返回有效的 OSS 预签名 URL。
* [ ] 前端流水线编辑器能拖拽"导出离线镜像"节点，配置表单可填。
* [ ] 前端流水线运行详情页，completed 的导出节点显示"下载离线包"按钮，点击能下载 tar。
* [ ] 端到端测试：配置一条流水线（checkout → maven build → docker build → **export offline image**），触发运行，构建成功后能从平台下载到镜像 tar 包，`docker load` 可导入。

## Definition of Done (team quality bar)

* Tests added/updated (unit/integration where appropriate)
* Lint / typecheck / CI green
* Docs/notes updated if behavior changes
* Rollout/rollback considered if risky

## Out of Scope (explicit)

* 内网自建 registry（Harbor/registry:2）——与轻量化约束冲突，明确排除。
* 多节点 k3s 的镜像分发。
* 内网导入侧功能（上传包 → 校验 → ctr import → 部署）——独立后续任务。
* 多 CPU 架构支持（arm64）——MVP 只做 amd64。
* 应用版本聚合包（多镜像 + manifest）——MVP 只做单镜像粒度。
* 离线包的过期策略、下载统计、权限控制——后续迭代。
* `ImageImportExecutor` 接口及其实现（`HostDirectImportExecutor`）——属于内网导入侧，本任务不涉及。

## Technical Notes

* OSS 抽象层入口：`infra` 模块 `FileServiceImpl` / `S3FileClient`。
* Jenkins 集成入口：`Jenkinsfile` + `PipelineJenkinsCallbackServiceImpl` + `PipelineRunDO`。
* 节点系统入口：`PipelineNodeRegistryServiceImpl`（注册）+ `JenkinsfileGeneratorServiceImpl`（生成）+ `PipelineJenkinsCallbackServiceImpl`（回调处理）。
* Shared library 仓库：`gone-devops-shared`（独立仓库，Jenkins 全局配置，本任务需修改）。
* 关键风险：Jenkins agent 必须能访问 Docker daemon（docker save）和 OSS endpoint（上传），且已安装 `aws-cli` 或 `ossutil`。
* 现有节点模板参考：
  - `TYPE_DOCKER_BUILD_PUSH`（同为 JENKINS 节点，涉及 Docker 操作 + 凭据）
  - `TYPE_ARTIFACT_UPLOAD`（涉及文件上传 + Jenkins 凭据）
  - `TYPE_APPROVAL`（平台节点，回调处理模板，虽然本节点是 JENKINS 节点但回调处理逻辑可参考）

## Implementation Plan（分 PR 实施）

**PR1: 数据模型 + 节点注册（后端基础）**
- 新建 `dev_offline_image_package` 表（migration SQL）。
- 新增 `OfflineImagePackageDO` / `OfflineImagePackageMapper`。
- 在 `PipelineNodeRegistryServiceImpl` 注册 `TYPE_EXPORT_OFFLINE_IMAGE` 节点（参数 schema）。
- 单元测试：节点注册、Mapper CRUD。

**PR2: Jenkinsfile 生成 + 回调处理（后端核心）**
- `JenkinsfileGeneratorServiceImpl` 增加 `appendExportOfflineImage` 方法，switch 加 case。
- `PipelineJenkinsCallbackServiceImpl` 的 `handleCallback` 增加 `TYPE_EXPORT_OFFLINE_IMAGE` COMPLETED 处理逻辑（创建 `FileDO` + `OfflineImagePackageDO`）。
- `PipelineJenkinsCallbackReqVO` 增加 `packageMetadata` 字段（Map，可选）。
- 单元测试：Jenkinsfile 生成输出、回调处理（mock reqVO）。

**PR3: Shared Library 方法（Jenkins 侧，跨仓库）**
- 在 `gone-devops-shared` 仓库新增 `vars/goneDevopsExportOfflineImage.groovy`。
- 实现 docker save + OSS 上传 + metadata 返回。
- 调用 `goneDevopsCallback` 时传 `packageMetadata`。
- 测试：在测试 Jenkins 环境手动触发验证。

**PR4: API + Service（后端查询下载）**
- `OfflineImagePackageService` / `OfflineImagePackageServiceImpl`（查询列表、详情）。
- `OfflineImagePackageController`（list、get、download-url API）。
- 集成测试：API 返回格式、预签名 URL 有效性。

**PR5: 前端 UI（流水线编辑 + 运行详情）**
- 流水线编辑器节点面板增加"导出离线镜像"节点（`EXPORT_OFFLINE_IMAGE`）。
- 节点配置表单（基于参数 schema 自动渲染）。
- 流水线运行详情页增加"下载离线包"按钮（条件渲染：nodeType == EXPORT_OFFLINE_IMAGE && status == completed）。
- E2E 测试：拖拽节点 → 配置 → 保存 → 触发运行 → 下载。

**PR6: 端到端集成测试 + 文档**
- 配置测试流水线（checkout → maven → docker build → export offline image）。
- 运行验证：能下载 tar、`docker load` 可导入、镜像完整。
- 更新 `JENKINS_RUNNER_CONFIGURATION.md`（新增节点说明）。
- 更新平台用户文档（如何配置离线镜像导出节点）。
