# Design: DevOps 审批携带发布详情

## Architecture

本任务只修改 DevOps server 的审批发起链路。BPM API、BPM server、流程定义和表单配置保持不变。

核心变化：

- `PipelineApprovalServiceImpl.startApproval(...)` 首次创建 BPM 流程实例前，继续构造原有审批变量。
- 在原有变量中新增 `deploy_html`。
- `deploy_html` 由 DevOps 本地服务根据 `PipelineRunDO` 查询应用、环境、变更和用户昵称后组装。

建议新增一个局部服务类：

- `PipelineApprovalDeployDetailBuilder`
- 包路径：`cn.iocoder.yudao.module.devops.service.pipeline.approval`
- 职责：读取发布上下文，生成可放入 BPM 富文本字段的安全 HTML。

`PipelineApprovalServiceImpl` 只负责审批流程生命周期，不承载 HTML 拼装细节。

## Data Flow

1. Pipeline 执行到 `APPROVAL` step。
2. `ApprovalStepHandler` 调用 `PipelineApprovalService.startApproval(run, step, userId)`。
3. `PipelineApprovalServiceImpl` 检查已有 run log，保持当前幂等语义：
   - 已成功：返回 `SUCCESS`
   - 等待审批：返回 `SUSPEND`
   - 终态失败/取消：返回 `FAIL`
   - 无现有审批实例：继续发起 BPM
4. 生成审批变量：
   - 保留现有变量：`pipelineRunId`、`pipelineDefinitionId`、`pipelineVersionId`、`appId`、`applicationEnvId`、`branchName`、`commitSha`、`stepId`、`stepName`、`stepType`
   - 新增变量：`deploy_html`
5. BPM 创建流程实例，`deploy_html` 随 `BpmProcessInstanceCreateReqDTO.variables` 传入。

## Data Sources

发布基础信息：

- `PipelineRunDO.appId`
- `PipelineRunDO.applicationEnvId`
- `PipelineRunDO.id`
- `PipelineRunDO.branchName`
- `PipelineRunDO.commitSha`
- `PipelineRunDO.triggerUserId`
- `PipelineRunDO.triggeredAt`

应用和环境：

- `ApplicationMapper.selectById(run.getAppId())`
- `ApplicationEnvMapper.selectById(run.getApplicationEnvId())`
- `EnvironmentMapper.selectById(applicationEnv.getEnvId())`

变更：

- 优先解析 `PipelineRunDO.changeSnapshotJson` 为 `PipelineRunChangeSnapshotContext` 列表。
- 若快照为空且 `run.getChangeId()` 非空，则回退为单变更。
- 使用 `ChangeMapper.selectListByIds(...)` 批量查询 `ChangeDO`，再按快照顺序输出。

人员：

- 从 `ChangeDO.testerUserId` 和 `ChangeDO.codeReviewerUserId` 收集用户 ID。
- 使用 `AdminUserApi.getUserMap(...)` 一次性批量查询昵称。
- 展示优先级：昵称 > 用户 ID > `-`。
- 用户不存在、昵称为空、用户 RPC 查询失败时，审批发起不失败，HTML 回退展示用户 ID 或 `-`。

## HTML Contract

`deploy_html` 是字符串流程变量，字段名固定为 `deploy_html`。

HTML 内容包含：

- 标题：发布审批详情
- 应用信息：应用名称、应用标识、应用编号
- 环境信息：环境名称、环境标识、环境阶段、应用环境编号
- 流水线信息：运行编号、部署分支、提交 SHA、触发人、触发时间
- 变更列表：
  - 变更标识
  - 标题
  - 分支
  - 本次发布 commit
  - 最新 commit
  - 提交信息
  - 测试人
  - 代码审核人

空变更发布展示“本次发布无关联变更”，不输出空表或阻断审批。

第一版不包含链接，不依赖前端路由或站点域名配置。

## Safety

- 所有来自数据库、用户输入、Git 提交信息、分支名和远程 API 的文本都必须 HTML 转义。
- 使用 Spring 已有的 `HtmlUtils.htmlEscape(...)` 或等价标准库方式，不新增第三方依赖。
- 空值统一展示为 `-`。
- HTML 使用基础富文本标签：`h3`、`h4`、`p`、`table`、`thead`、`tbody`、`tr`、`th`、`td`。
- 不输出 `script`、事件属性、iframe 或外链。
- 不把凭据、环境连接配置、镜像仓库密码等敏感信息放入 HTML。

## Compatibility

- BPM API DTO 不变。
- BPM 表单字段不变，只要求已有富文本字段 id 为 `deploy_html`。
- Pipeline YAML 的 `APPROVAL` step 配置不变，仍只要求 `with.processDefinitionKey`。
- 已存在等待审批的 step 不会重新创建 BPM 实例，也不会补写 `deploy_html`；新发起的审批实例才带新变量。
- `PipelineApprovalContext.variables` 会随当前逻辑保存 `deploy_html`，便于审计；HTML 不应包含敏感信息。

## Error Handling

- 关键审批创建失败仍按现有行为向外抛出，让 pipeline step 失败。
- 发布详情缺失不阻断审批：
  - 应用、环境、变更记录缺失时用编号或 `-` 回退。
  - 用户昵称查询失败时记录警告日志并回退用户 ID。
- 不新增业务错误码。

## Tests

新增或调整 focused unit tests：

- `PipelineApprovalServiceImplTest`
  - 创建 BPM 流程实例时变量包含 `deploy_html`
  - 现有等待/成功/终态审批不重复创建 BPM，保持原行为
- `PipelineApprovalDeployDetailBuilderTest`
  - 多变更快照按 `changeSnapshotJson` 顺序输出
  - 变更展示测试人和代码审核人昵称
  - 用户昵称缺失或用户 RPC 失败时回退 ID
  - 空变更发布输出无关联变更说明
  - HTML 转义覆盖应用名、环境名、变更标题、提交信息

验证命令：

```bash
mvn -pl yudao-module-devops/yudao-module-devops-server -Dtest='PipelineApprovalServiceImplTest,PipelineApprovalDeployDetailBuilderTest' -Dsurefire.failIfNoSpecifiedTests=false test
```
