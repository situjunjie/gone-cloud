# Merge image export oss into pipeline builder

## Goal

将 `yudao-module-devops/docker/image-export-oss` 的镜像导出能力整合进
`yudao-module-devops/docker/pipeline-builder`，并让流水线镜像导出上传能力从仅支持阿里云
OSS 扩展为支持通用 S3 协议对象存储。

## Confirmed Facts

- 当前 `pipeline-builder` 镜像已经内置 `skopeo`、`zstd`、`jq` 和
  `/usr/local/bin/import-image-archive-to-registry`，但没有导出上传脚本。
- 当前 `image-export-oss` 是独立执行镜像，内置 `ossutil` 和
  `/usr/local/bin/export-image-to-oss`。
- 后端已经存在用户可见的流水线 step：
  `DockerImageExportOss`，对应的执行处理器、YAML 校验、节点注册和测试已落地。
- 当前 `DockerImageExportOss` 的 `with` 参数强绑定 `oss.endpoint`、
  `oss.path`、`oss.certificate.type=accessKey`，脚本也只接受 `oss://` 路径。
- `PIPELINE_YAML_SPEC.md` 已经声明该 step 建议未来统一迁移到
  `gone-cloud/pipeline-builder:java17-node24-maven3.9`。
- 现有实现要求敏感信息不能写入日志或 `resultJson`。

## Requirements

- 将镜像导出上传脚本并入 `pipeline-builder` 镜像，避免继续依赖单独的
  `image-export-oss` 运行镜像。
- 保持镜像导出 step 在流水线中可继续使用，并支持上传到通用 S3 协议对象存储。
- 新方案需要继续支持当前 OSS 使用场景，除非产品决策明确放弃旧参数兼容。
- 继续支持现有归档格式与压缩格式：`docker-archive`、`oci-archive`、
  `none`、`gzip`、`zstd`。
- Registry 和对象存储凭证不得写入日志、step outputs、`resultJson`。
- 文档、节点 schema、YAML 校验、执行处理器和测试需要与最终契约保持一致。

## Acceptance Criteria

- [ ] `pipeline-builder` 镜像包含镜像导出上传所需工具和脚本，可替代当前
      `image-export-oss` 镜像。
- [ ] 流水线镜像导出 step 可上传到至少一种通用 S3 协议对象存储。
- [ ] 现有 OSS 场景仍可工作，或有明确的兼容迁移策略并已同步到文档与校验。
- [ ] `PIPELINE_YAML_SPEC.md`、节点注册 schema、后端校验、step handler 和测试全部同步更新。
- [ ] 相关 DevOps 服务端测试通过。

## Out of Scope

- 新增非 S3/OSS 的其他对象存储协议。
- 调整镜像归档导入 step 的产品能力范围，除非为复用脚本或镜像构建必须顺带修改。

## Open Questions

- 是否保留 `DockerImageExportOss` 这个 step 名称与 `with.oss` 参数结构做向后兼容，
  还是升级为更通用的对象存储命名并同步前后端契约。
