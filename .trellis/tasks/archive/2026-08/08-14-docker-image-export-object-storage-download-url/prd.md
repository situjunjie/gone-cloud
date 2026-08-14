# 修复 DockerImageExportObjectStorage 镜像下载链接输出

## Goal

修复 `DockerImageExportObjectStorage` step 成功后输出不完整下载链接的问题，使流水线步骤结果和下载动作向调用方提供可实际下载导出镜像归档的链接。

## Background / Confirmed Facts

- 实现位于 `yudao-module-devops/yudao-module-devops-server/.../DockerImageExportObjectStorageStepHandler.java`。
- 当前 `resultJson` 与 `StepResult.outputs` 只输出 `storagePath`，值为 `s3://bucket/key`。
- 当前下载动作 `target.url` 直接使用 `storagePath`，浏览器无法将其作为完整 HTTP(S) 下载地址。
- 导出脚本通过 `mc cp` 将归档上传到 S3 兼容对象存储；对象存储凭证只用于执行环境，不应写入 `resultJson`、日志或 step outputs。
- 现有流水线规范允许输出 artifacts/reports 的 `url` 为空，但本 step 已经声明下载动作，因此需要明确可下载链接契约。

## Requirements

1. 成功完成导出上传后，步骤结果必须输出一个明确的下载链接字段，并与下载动作使用同一链接。
2. 保留 `storagePath` 作为对象定位信息，避免破坏已有下游变量和历史结果读取。
3. 下载链接生成/访问必须遵循权限与敏感信息约束，不得暴露 AccessKey/SecretKey。
4. 失败结果不得输出误导性的可下载链接。
5. 增加针对链接输出、动作目标和敏感字段脱敏的聚焦测试，并同步相关 Pipeline 文档契约。

## Acceptance Criteria

- 成功 step 的 `resultJson.outputs` 包含可下载链接字段；该字段为完整 HTTP(S) URL 或受保护的后端下载 API URL，不能是 `s3://` URI。
- 成功 step 的 `actions[].target.url` 与上述下载链接一致，点击动作可按既定权限下载归档。
- `storagePath` 仍保留且值不变；现有使用 `storagePath` 的下游逻辑兼容。
- 任意凭证（源仓库密码、对象存储密钥）不出现在结果 JSON、动作 payload、日志中。
- 现有成功、失败、占位符解析测试继续通过，新增测试覆盖链接契约。

## Out of Scope

- 不改变镜像归档格式、压缩格式、S3 上传脚本及对象存储供应商支持范围。
- 不建设独立制品表、过期清理机制或通用对象存储管理能力。

## Resolved Product Decision

- 对象存储对象是公开的，采用直接公开对象 URL，优先保持现有 step 和配置改动最小。
- `storagePath` 继续保留为 `s3://bucket/key`；新增 `downloadUrl` 输出，并让下载动作的 `target.url` 使用该字段。
- 当 `forcePathStyle=true` 时生成 `{endpoint}/{bucket}/{key}`；否则生成 `{endpoint}/{bucket}.<host-path-compatible>/...` 的虚拟主机 URL。endpoint 的 path/query/尾部斜杠必须被规范化，key 保留对象路径并进行 URL 编码。

## Goal

TBD.

## Requirements

- TBD

## Acceptance Criteria

- [ ] TBD

## Notes

- Keep `prd.md` focused on requirements, constraints, and acceptance criteria.
- Lightweight tasks can remain PRD-only.
- For complex tasks, add `design.md` for technical design and `implement.md` for execution planning before `task.py start`.
