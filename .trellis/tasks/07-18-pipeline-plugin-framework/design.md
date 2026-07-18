# Pipeline 插件框架与钉钉通知插件技术设计

## 1. Design Summary

在 Job DSL 和可执行 Job 模型中增加插件配置，通过统一 `PipelinePlugin` SPI 注册插件。`PipelineExecutionEngine` 在 Job 的 `SUCCESS/FAILED` 状态成功持久化后调用 `PipelinePluginDispatcher`；Dispatcher 仅负责状态匹配、任务快照和异步投递，具体配置校验与执行由插件实现。

插件是严格旁路能力：任何校验后运行期异常、线程池拒绝、HTTP 超时或钉钉业务错误都不得反向修改 Job/Pipeline 状态，也不得在 Pipeline 调度线程执行网络请求。

## 2. DSL Contract

```yaml
stages:
  build_stage:
    name: 构建阶段
    jobs:
      build_job:
        name: 构建任务
        runsOn:
          group: local-docker/default
          container: eclipse-temurin:17
        steps:
          build_step:
            name: 构建
            step: Command
            with:
              run: mvn -B package
        plugins:
          - name: 钉钉机器人通知插件
            plugin: DingTalkPlugin
            triggerState: [success, fail]
            with:
              webhook: https://oapi.dingtalk.com/robot/send?access_token=xxx
              secret: SECxxx
              noticeContent:
                - pipelineName
                - operator
                - pipelineTask
                - pipelineStage
                - status
              customContent: 请及时关注流水线结果
              at:
                - "13800138000"
                - "13900139000"
```

`PipelineSpec` 增加：

- `Job.plugins: List<PluginConfig>`，默认空列表。
- `PluginConfig.name: String`。
- `PluginConfig.plugin: String`，首版稳定标识为 `DingTalkPlugin`。
- `PluginConfig.triggerState: List<String>`，只接受小写 `success/fail`。
- `PluginConfig.with: Map<String, Object>`，默认空有序 Map。
- `ExecutableJob.plugins` 深拷贝列表，确保 YAML 声明顺序稳定。

旧定义没有 `plugins` 时解析为空列表，不改变版本表或 API 结构，不需要数据库迁移。

## 3. SPI And Registry

核心接口位于 Pipeline plugin 包，不放入 step handler 层：

```java
public interface PipelinePlugin {
    String type();
    List<PipelinePluginValidationIssue> validate(Map<String, Object> config);
    void execute(PipelinePluginExecutionContext context, Map<String, Object> config);
}
```

- `PipelinePluginValidationIssue` 只返回插件 `with` 下的相对字段、错误码和安全错误消息，由 DSL 校验服务补齐 Stage/Job/plugin index 路径。
- `PipelinePluginExecutionContext` 是异步任务使用的不可变快照，包含 tenantId、runId、definitionId、pipelineName、triggerUserId/operator、stageId/stageName、jobId/jobName、status 和 Job 时间信息，不暴露完整 `PipelineExecutionContext`、sharedState、workspace 或步骤凭据。
- `PipelinePluginRegistry` 通过 Spring 注入 `List<PipelinePlugin>`，启动时建立 `type -> plugin` 索引并拒绝重复 type；新增插件只需实现 SPI 并注册 Bean。
- 核心公共校验由 `PipelineSpecValidationServiceImpl` 完成；插件特有 `with` 校验委托给已注册实现。

## 4. Validation Contract

公共配置校验：

- `name`、`plugin`、`triggerState` 必填。
- 未注册 `plugin` 返回 `PLUGIN_NOT_SUPPORTED`。
- `triggerState` 非空、不得重复，只允许 `success/fail`；错误字段定位到 `stages.{stageId}.jobs.{jobId}.plugins[{index}].triggerState`。
- `with` 缺省时按空 Map 处理，再由具体插件报告必填项。

`DingTalkPlugin` 校验：

- `webhook` 必填，必须是合法 HTTPS URI；实现限制为官方钉钉机器人 host/path，阻止任意 SSRF 目标。
- `secret` 可选；存在时必须是非空字符串。
- `noticeContent` 必填且非空，元素不得重复，只允许 `pipelineName/operator/pipelineTask/pipelineStage/status`。
- `customContent` 可选且必须是字符串，不解析任何模板。
- `at` 可选，接受手机号字符串或字符串数组，归一化、去重并保留顺序；空项、非字符串或非法手机号为校验错误。

校验消息不得包含 webhook、secret 或完整配置 Map。

## 5. Execution Flow

```text
Job step execution
  -> persist SUCCESS / FAILED
  -> dispatcher filters matching plugin configs
  -> submit one immutable job-plugin event to bounded executor
  -> Pipeline scheduler continues immediately
  -> async task enters explicit tenant context
  -> resolve pipeline name and operator display name
  -> execute matching plugins in YAML order, isolating each failure
```

触发规则：

- `SUCCESS -> success`，`FAILED -> fail`。
- `PENDING/RUNNING/BLOCKED/SKIPPED/CANCELED` 不触发。
- 一次 Job 终态转换只提交一次；引擎重入时已经是终态的 Job 不会再次提交。
- 未来若实现 Job retry，每个 attempt 的终态可独立触发；首版不新增跨 attempt 幂等记录。

Dispatcher 的公开 `dispatch(...)` 必须保持 no-throw：状态不匹配、注册器缺失、线程池拒绝和运行时异常只生成带 `runId/stageId/jobId/plugin` 的安全日志。

## 6. Async Isolation

在 `DevOpsAsyncConfiguration` 增加专用 `pipelinePluginExecutor`：

- 有界核心/最大线程数和队列容量。
- 线程名前缀 `pipeline-plugin-`。
- 使用 `AbortPolicy`，Dispatcher 捕获拒绝；禁止 `CallerRunsPolicy`，避免降级到 Pipeline 调度线程执行 HTTP。
- 异步任务使用事件快照中的 tenantId，通过 `TenantUtils.execute(tenantId, ...)` 显式建立租户上下文，再查询 Pipeline 定义和操作人。
- 优雅停机只允许短时间等待；未发送任务允许按已确认的 fire-and-forget 语义丢弃。

Pipeline 名称由 `PipelineDefinitionMapper` 查询，缺失时回退 definitionId；操作人由 `AdminUserApi` 查询昵称，RPC 失败时回退 triggerUserId，均不影响插件继续发送。

## 7. DingTalk Implementation

`DingTalkPlugin` 负责配置转换、字段白名单、消息文本和签名 URL；`DingTalkRobotClient` 只负责有超时的 JSON POST 和响应判断，便于无网络单测。

请求体：

```json
{
  "msgtype": "text",
  "text": {
    "content": "流水线通知\n流水线：demo\n操作人：张三\n任务：构建任务\n阶段：构建阶段\n状态：SUCCESS\n请及时关注流水线结果"
  },
  "at": {
    "atMobiles": ["13800138000"],
    "isAtAll": false
  }
}
```

- 标准字段按 `noticeContent` 配置顺序输出，只输出被选择字段。
- 名称为空时回退对应 ID；操作人为空时输出 `-`。
- `customContent` 非空时作为最后一段原文追加，`${VAR}` 保持字面值。
- 配置 `secret` 时使用 `timestamp + "\n" + secret` 进行 HMAC-SHA256、Base64 和 URL 编码，并安全地替换/追加 webhook 的 `timestamp/sign` 查询参数。
- HTTP 使用固定短超时；只有 2xx 且响应 `errcode == 0` 视为成功。
- 异常只保留 HTTP status、钉钉 errcode 和截断后的安全错误摘要，禁止记录请求 URL、body、webhook、access_token 或 secret。

## 8. Compatibility And Security

- YAML/JSON parser 继续保持 unknown-field 严格校验；新增字段只扩展 Job 模型。
- `specJson` 仍按当前权限原样保存/返回。本任务不解决配置凭据加密，但 webhook/secret 不得复制到运行表、日志、context、result 或插件执行记录。
- DingTalk webhook 目标限制和 URI 结构化解析用于降低 SSRF 风险；不使用字符串拼接处理 query 参数。
- 不新增数据库表、不改变 Pipeline 状态枚举、不改变发布页 read model。

## 9. Rollout And Rollback

- 未配置 `plugins` 时行为与当前版本一致，可先部署代码再逐步发布包含插件的 Pipeline 版本。
- 配置错误在草稿校验/发布前暴露，不进入运行态。
- 运行问题可通过删除 Job 的 `plugins` 并发布新版本立即停用。
- 代码回滚不涉及 schema 回滚；但回滚到不认识 `plugins` 的版本后，已含插件的 YAML 会被严格解析器拒绝，回滚前应先发布不含插件的定义版本。
- 服务重启、队列拒绝或进程退出可能丢通知，这是首版明确接受的限制。
