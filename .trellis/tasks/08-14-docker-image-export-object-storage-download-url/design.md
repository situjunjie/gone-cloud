# Technical Design

## Boundary

修改 `DockerImageExportObjectStorageStepHandler` 的成功结果组装和下载动作构建逻辑；不修改上传脚本、数据库结构或通用下载 API。

## Data Flow / Contract

1. `parseConfig` 解析并校验 `storage.endpoint`、`storage.path` 与 `forcePathStyle`。
2. handler 在上传成功后由 endpoint + `s3://bucket/key` 计算公开 `downloadUrl`。
3. `resultJson` 顶层写入 `downloadUrl`；`StepResult.outputs` 写入 `downloadUrl`，同时保留 `storagePath`。
4. `PipelineStepAction.target.url` 使用同一个 `downloadUrl`；payload 保留 `storagePath` 等诊断字段，但不包含凭证。

## URL Rules

- 仅接受现有 S3 path 格式 `s3://bucket/key`，bucket 与 key 均不能为空。
- path-style：`endpoint + "/" + bucket + "/" + encodedKey`。
- virtual-host style：将 endpoint 的 scheme/host 保留，在 host 前加 bucket，再拼 `encodedKey`；若 endpoint 含非根 path，则按 path-style 处理，避免错误改变服务端路由。
- 对 key 的每个 path segment 做 UTF-8 URL 编码，保留 `/` 分隔符；fragment 不生成。
- 计算失败时 step 配置无效并失败，不输出下载动作。

## Compatibility / Risk

- 现有 `storagePath` 和下游变量不变；新增字段向后兼容。
- 公开 URL 不提供鉴权能力，依赖用户明确的公开桶前提；不记录 AccessKey/SecretKey。
- 使用 `java.net.URI`/编码器而非手工拼接，覆盖 endpoint 尾斜杠、路径和特殊 key 字符。

## Tests

- 成功 handler 测试验证 `downloadUrl` 出现在 outputs/resultJson，且 action target.url 相同。
- 分别覆盖 path-style、virtual-host style、endpoint 尾斜杠和 key 编码；保留敏感信息断言。
