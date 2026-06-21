# Design

## Problem Restatement

当前镜像导出上传能力拆在独立 `image-export-oss` 执行镜像里，并且流水线契约强绑定阿里云
OSS。目标是把这项能力并入通用 `pipeline-builder` 镜像，并把流水线导出上传契约升级为面向
通用 S3 协议对象存储。

## Design Decision

### New step contract

- 废弃旧 step 名称 `DockerImageExportOss`。
- 新 step 名称：`DockerImageExportObjectStorage`。
- 新 `with` 参数模型：

```yaml
with:
  image: registry.example.com/ns/demo:1.0
  archiveFormat: oci-archive
  compression: zstd
  outputFileName: demo-1.0.oci.tar
  registryTlsVerify: true
  registryCertificate:
    type: usernamePassword
    username: robot
    password: secret
  storage:
    type: s3
    endpoint: https://s3.example.com
    path: s3://release-bucket/images/demo-1.0.oci.tar
    region: us-east-1
    forcePathStyle: true
    certificate:
      type: accessKey
      accessKeyId: ak
      accessKeySecret: sk
  overwrite: false
```

### Why this shape

- `storage` 把对象存储与镜像仓库凭证边界分开，避免继续沿用 `oss` 的产品命名。
- `type` 先只支持 `s3`，覆盖 MinIO、Ceph、阿里云 OSS S3 兼容端点等常见场景，同时避免为未验证协议扩展过早建模。
- `endpoint + path` 组合保留现有“显式指定上传目的地”的使用方式。
- `region` 和 `forcePathStyle` 作为 S3 兼容参数显式暴露，降低对具体厂商默认值的耦合。

## Runtime / Image Design

### pipeline-builder image

- 在 `yudao-module-devops/docker/pipeline-builder` 中新增导出上传脚本：
  `/usr/local/bin/export-image-to-object-storage`。
- 保留现有导入脚本 `/usr/local/bin/import-image-archive-to-registry`。
- 删除独立 `image-export-oss` 镜像目录，避免同一能力双份维护。

### Upload tool choice

- 在 `pipeline-builder` 中引入通用 S3 CLI 工具，用于：
  - 检查对象是否已存在
  - 上传文件到 `s3://bucket/key`
- 脚本仍使用 `skopeo` 做镜像导出，`gzip` / `zstd` 做压缩。
- 上传工具的配置必须完全通过环境变量或临时配置文件注入，凭证只能写入临时目录并在退出时清理。

## Backend Changes

### Pipeline node registry

- 新增/替换节点类型常量为 `TYPE_DOCKER_IMAGE_EXPORT_OBJECT_STORAGE`。
- 节点标题改为“导出镜像并上传对象存储”。
- 节点 schema 改为 `storage.*` 结构，不再出现 `oss.*` 字段。

### Validation

- `PipelineSpecValidationServiceImpl` 改为校验：
  - `with.image`
  - `with.registryCertificate`
  - `with.storage.type=s3`
  - `with.storage.endpoint`
  - `with.storage.path` must start with `s3://`
  - `with.storage.certificate.type=accessKey`
  - `with.storage.certificate.accessKeyId/accessKeySecret`
  - `with.storage.region` optional string
  - `with.storage.forcePathStyle` optional boolean
- 支持 `${VAR}` 占位符的静态校验放宽逻辑，与现有 step 保持一致。

### Step handler

- `DockerImageExportOssStepHandler` 重命名为
  `DockerImageExportObjectStorageStepHandler`。
- 执行命令改为 `/usr/local/bin/export-image-to-object-storage`。
- 输出字段改为：
  - `image`
  - `archiveFormat`
  - `compression`
  - `outputFileName`
  - `storagePath`
- `resultJson` 不再出现 `ossPath`。
- 日志文案改为“对象存储”，避免继续暴露过时产品名。

## Compatibility Impact

- 这是显式非兼容升级：
  - 旧 step 名称 `DockerImageExportOss` 不再保留。
  - 旧 `with.oss.*` 契约不再保留。
- 文档、测试、示例 YAML、校验错误说明会同步切换到新契约。
- `DockerImageArchiveImport` 继续兼容导出的归档格式，不依赖旧 step 名称。

## Risks and Mitigations

- 风险：S3 兼容存储对 endpoint / path-style 的要求不同。
  - 缓解：把 `endpoint`、`region`、`forcePathStyle` 显式入参化，不隐藏默认行为。
- 风险：执行镜像新增上传工具后，镜像构建体积和下载时间上升。
  - 缓解：只引入单一 CLI 工具，删除独立镜像，整体维护成本下降。
- 风险：已有 YAML 会失效。
  - 缓解：这是用户确认的非兼容升级，文档和节点 schema 全量同步，避免“看起来还能用但运行失败”的半兼容状态。

## Rollback Shape

- 若实现后发现 S3 CLI 工具不满足要求，可保留新的 step/参数契约不变，仅替换 `pipeline-builder`
  内的上传实现。
- 若必须临时回退发布，可回退到改动前版本分支；本次不设计运行时双契约兼容。
