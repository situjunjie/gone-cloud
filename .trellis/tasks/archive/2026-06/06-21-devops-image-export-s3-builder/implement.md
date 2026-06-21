# Implementation Plan

## Ordered Checklist

- [ ] 更新 planning 文档并确认非兼容升级范围。
- [ ] 读取 `trellis-before-dev` 和 backend 相关思考指南，补足编码前约束。
- [ ] 修改 `pipeline-builder` Dockerfile，加入对象存储上传工具与导出脚本。
- [ ] 删除 `image-export-oss` 独立镜像目录及其 README / Dockerfile / 脚本。
- [ ] 将后端 step 常量、节点 schema、校验逻辑从 `DockerImageExportOss` 切换到新契约。
- [ ] 重命名 step handler 与测试，切换输出字段、日志文案、环境变量和脚本路径。
- [ ] 更新 `PIPELINE_YAML_SPEC.md`、`pipeline-builder/README.md` 等文档示例。
- [ ] 运行聚焦测试并修复回归。

## Validation Commands

- `mvn -pl yudao-module-devops/yudao-module-devops-server -am -Dtest='PipelineSpecValidationServiceImplTest,DockerImageExportObjectStorageStepHandlerTest,DockerImageArchiveImportStepHandlerTest' -Dsurefire.failIfNoSpecifiedTests=false test`

如新增或重命名测试类后需要调整测试名，命令同步更新。

## Risky Files

- `yudao-module-devops/PIPELINE_YAML_SPEC.md`
- `yudao-module-devops/yudao-module-devops-server/src/main/java/.../PipelineNodeRegistryServiceImpl.java`
- `yudao-module-devops/yudao-module-devops-server/src/main/java/.../PipelineSpecValidationServiceImpl.java`
- `yudao-module-devops/yudao-module-devops-server/src/main/java/.../execution/handler/*`
- `yudao-module-devops/docker/pipeline-builder/*`

## Review Gates Before Start

- 新 step 名称、参数结构、输出字段已经在文档中统一。
- 不再残留 `ossPath`、`with.oss`、`DockerImageExportOss` 这种旧用户契约。
- 敏感字段未写入 `resultJson`、日志或 step outputs。
- `pipeline-builder` 文档已经说明它同时承担导出与导入能力。
