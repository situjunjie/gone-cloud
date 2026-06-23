# Refactor DevOps pipeline run log storage

## Goal

将 DevOps 流水线运行的行级构建日志从数据库正文存储迁移为文件存储，降低数据库写入压力和大日志膨胀风险，同时尽量保持现有前端接口不变。

## Confirmed Facts

- 当前行级日志实体是 `PipelineRunLogLineDO`，映射表 `dev_pipeline_run_log_line`，字段包含 `pipelineRunId/runLogId/stageId/jobId/stepId/lineNo/streamType/content`。
- 当前写入入口是 `PipelineRunLogLineService.appendLine(PipelineRunLogDO, streamType, content)`，会为每行插入一条数据库记录，并在 2000 行后标记父 `PipelineRunLogDO.logTruncated=true`。
- 当前查询接口是 `GET /devops/pipeline-run/{runId}/log-lines`，SSE 接口是 `GET /devops/pipeline-run/{runId}/log-lines/stream`，返回 `PipelineRunLogLineRespVO`，前端游标参数为 `afterId`。
- 现有规范要求 `Command` 直播日志写入 `dev_pipeline_run_log_line`，但更早的 Docker runtime 设计文档已经指出“完整日志后续可落文件或对象存储，DB 只存摘要和索引”。
- 现有流水线 workspace 使用 `yudao.devops.pipeline.workspace-root`，默认 `${java.io.tmpdir}/gone-devops/pipeline-workspaces`。
- devops 模块已经启用 `FileApi` Feign 客户端，可通过 `yudao-module-infra-api` 的 `FileApi.createFile(byte[], name, directory, type)` 上传文件并获得访问 URL。
- 当前 Docker `Command` 通过 `DockerPipelineCommandExecutor` 执行 `docker exec` 并 attach stdout/stderr；容器当前只挂载 run workspace 和 cache 目录。

## Requirements

- 新产生的流水线行级日志正文必须由 Docker 构建容器写入挂载目录中的日志文件，不再由后端为每一行插入 `dev_pipeline_run_log_line`。
- 创建 Docker 构建容器或执行 Command 前，后端必须准备好宿主机日志目录，并通过已挂载 workspace 暴露给容器。
- Docker 容器销毁前后，后端必须能从宿主机挂载目录读取日志文件；步骤完成后应通过 `FileApi` 上传完整日志文件并写入 `PipelineRunLogDO.logFileUrl`。
- 现有前端查询和 SSE API 优先保持路径、参数和 `PipelineRunLogLineRespVO` 字段不变。
- `afterId` 继续作为单调递增游标使用；迁移后它可以是文件内行级 cursor，而不是数据库主键。
- 查询接口必须继续支持按 `pipelineRunId` 和可选 `stepId` 过滤，并按 cursor 顺序返回。
- SSE 必须继续先补发历史行，再轮询新文件内容，并在 run 或 step 终态后发送 `complete` 事件。
- 历史数据库日志应保留只读兼容能力，避免已有运行记录因为迁移变成空日志。
- `PipelineRunLogDO.logTruncated` 继续用于标记存储上限或写入被截断的情况。
- 日志文件内容不得扩大敏感信息暴露面；现有调用链传入的内容仍按当前脱敏约束处理。
- 本次不要求前端修改；只有当保持兼容会造成明显后端缺陷时才调整接口契约。

## Acceptance Criteria

- [ ] Command step 执行时，Docker 容器将 stdout/stderr 写入挂载日志文件。
- [ ] 步骤完成后，后端通过 `FileApi` 上传完整日志文件，并把 URL 写入 `PipelineRunLogDO.logFileUrl`。
- [ ] 新运行不再依赖 `PipelineRunLogLineMapper.insert` 保存日志正文。
- [ ] `getLogLines(runId, stepId, afterId, limit)` 可从挂载日志文件读取日志并保留 limit、step filter、cursor 行为。
- [ ] SSE 日志流可读取文件历史行和新增行，终态关闭逻辑不回退。
- [ ] 如果文件日志不存在但数据库旧表有日志，查询/SSE 仍能走旧表只读 fallback。
- [ ] 单元测试覆盖文件追加、截断、查询过滤、历史 DB fallback、run 不存在、SSE 终态。
- [ ] 更新 DevOps pipeline 规范，记录新日志存储契约和兼容边界。

## Out Of Scope

- 不做前端页面改版。
- 不做对象存储上传或日志下载 API。
- 不做历史数据库日志批量迁移脚本。
- 不删除旧 `dev_pipeline_run_log_line` 表，避免破坏历史兼容和部署升级。
