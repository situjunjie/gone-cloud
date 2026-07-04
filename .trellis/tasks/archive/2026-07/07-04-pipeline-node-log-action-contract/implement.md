# Pipeline 节点日志与动作契约优化实现计划

## Checklist

1. Contract models
   - 新增 `PipelineStepAction`、`PipelineStepActionTarget`、动作类型枚举和动作 key 常量。
   - 扩展 `StepResult` 支持 `actions`。
   - 新增 `PipelineStepActionHelper`，负责读写 `PipelineRunLogDO.resultJson.actions`。

2. Engine integration
   - 修改 `PipelineExecutionEngine.publishStepOutputs`，同时合并 `outputs` 和 `actions`。
   - 保证已有 `resultJson` 字段不被覆盖。
   - 增加敏感字段检查或白名单 target schema。

3. Platform log helper
   - 扩展 `PipelineStepLogHelper`，支持平台步骤准备日志和追加低频日志行。
   - 复用现有 step log 查询和 SSE/行级日志能力。

4. Built-in step actions
   - `CodeMergeStepHandler` / `CodeMergeService`: 冲突挂起时写入 `CODE_MERGE_RESOLVE_CONFLICT`，继续成功后清理。
   - `PipelineApprovalServiceImpl`: BPM 实例创建后写入 `APPROVAL_OPEN_PROCESS`，target 使用 `processInstanceId` 跳 BPM 流程实例/审批详情页；审批终态后清理或替换为查看动作。
   - `DockerImageExportObjectStorageStepHandler`: 成功后写入 `IMAGE_ARCHIVE_DOWNLOAD`。

5. Optional download endpoint
   - 若现有 `storagePath` 不能直接作为安全下载 URL，新增受权限保护的 step artifact 下载接口。
   - 下载接口从 `resultJson` 校验 action key 和 storage metadata，不接受前端传入任意对象存储路径。

6. Tests
   - `StepResult` actions 合并到 `resultJson`。
   - `PipelineStepActionHelper` 保留现有 result 字段、替换同 key action、清理 action。
   - `CodeMerge` 冲突/解决状态下 actions 正确变化。
   - `Approval` 等待/终态 actions 正确变化。
   - `DockerImageExportObjectStorage` 成功动作不包含密钥。
   - 平台日志追加后可通过 step log line service 查询。

## Validation Commands

Focused backend tests:

```bash
mvn -pl yudao-module-devops/yudao-module-devops-server -am -Dtest='PipelineExecutionEngineTest,CodeMergeStepHandlerTest,ApprovalStepHandlerTest,PipelineApprovalServiceImplTest,DockerImageExportObjectStorageStepHandlerTest,PipelineRunLogLineServiceImplTest' -Dsurefire.failIfNoSpecifiedTests=false test
```

Broader devops module check:

```bash
mvn -pl yudao-module-devops/yudao-module-devops-server -am test
```

## Review Points

- `resultJson.actions` 中不得出现 password、secret、token、accessKeySecret。
- 后端 action target 只能使用受控 schema，不能下发任意 JS。
- 前端未知 action type/key 必须降级。
- 旧运行无 actions 时页面展示不变。

## Rollback

- 后端动作字段只写在 `resultJson.actions`，回滚时可停止生成该字段，旧数据不影响执行引擎。
- 平台日志追加是增量可观测能力，若出现问题可保留 `summary` 更新路径并关闭平台日志行写入。
