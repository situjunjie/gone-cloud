# Implementation Plan

1. 在 handler 的存储配置/导出配置中保留公开 URL 所需的 endpoint、path、forcePathStyle，并新增 URL 计算 helper。
2. 成功分支统一使用计算出的 `downloadUrl` 更新 resultJson、StepResult.outputs 和下载 action。
3. 对 URL 计算失败补充参数校验错误，确保失败时不创建下载动作。
4. 更新 Pipeline YAML/spec 文档，说明 `downloadUrl` 输出和公开对象 URL 约定。
5. 扩展 `DockerImageExportObjectStorageStepHandlerTest`，运行模块定向测试与质量检查。

## Validation

- `mvn -pl yudao-module-devops/yudao-module-devops-server -Dtest=DockerImageExportObjectStorageStepHandlerTest test`
- `git diff --check`
- Trellis backend quality/check workflow

## Rollback Point

修改集中在 step handler、对应测试和契约文档；如公开 URL 兼容性不满足，可回滚新增 `downloadUrl` 计算并继续保留 `storagePath`。
