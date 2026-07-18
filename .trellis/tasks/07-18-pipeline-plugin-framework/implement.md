# Pipeline 插件框架与钉钉通知插件实施计划

## 1. DSL And Validation

- [x] 在 `PipelineSpec` 增加 `PluginConfig`、`Job.plugins` 和 `ExecutableJob.plugins`，保持有序复制及旧 YAML 默认空列表。
- [x] 增加插件触发状态常量/枚举，集中维护 `success -> SUCCESS`、`fail -> FAILED` 映射。
- [x] 实现 `PipelinePlugin`、`PipelinePluginValidationIssue` 和 `PipelinePluginRegistry`。
- [x] 扩展 `PipelineSpecValidationServiceImpl`：公共字段、插件注册、triggerState 和相对路径错误定位；委托插件校验 `with`。
- [x] 扩展 `PipelineSpecValidationServiceImplTest`，覆盖旧 YAML、有效 DingTalk YAML、未注册插件、空/重复/非法 triggerState、无效 webhook/noticeContent/at/secret。

## 2. Async Plugin Runtime

- [x] 增加不可变 `PipelinePluginExecutionContext` 和 Job 终态事件快照，避免向异步线程泄露可变执行上下文。
- [x] 实现 `PipelinePluginDispatcher`：状态匹配、配置快照、单 Job 事件投递、显式租户上下文、Pipeline/操作人信息回退、插件顺序执行和逐插件异常隔离。
- [x] 在 `DevOpsAsyncConfiguration` 增加使用 `AbortPolicy` 的有界 `pipelinePluginExecutor`，确保拒绝时不回落调用线程。
- [x] 新增 Dispatcher/Registry 单测，使用直接执行器或可控执行器验证状态映射、配置顺序、租户/上下文、异常隔离和拒绝 no-throw。

## 3. DingTalk Plugin

- [x] 实现 DingTalk 配置解析和归一化：webhook、可选 secret、noticeContent、原文 customContent、标量/数组 at。
- [x] 实现字段白名单与 text 消息构造，保持 noticeContent 顺序和 fallback 值。
- [x] 实现 HMAC-SHA256 签名 URL，使用 URI/query builder，禁止敏感字符串进入异常。
- [x] 实现 `DingTalkRobotClient`：JSON POST、固定短超时、2xx/`errcode == 0` 成功判断和安全错误摘要。
- [x] 新增 `DingTalkPluginTest`/客户端测试，覆盖无签名、有签名、字段选择、customContent 字面量、单/多手机号、业务错误和敏感信息不泄露。

## 4. Engine Integration

- [x] 在 `PipelineExecutionEngine` 的 Job `SUCCESS/FAILED` 成功落库后调用 Dispatcher；不接入 `SKIPPED/CANCELED/BLOCKED`。
- [x] 保证 Dispatcher 调用不位于改变 Job 状态的事务前置路径，且任何插件异常不能进入 Job errorMessage。
- [x] 扩展 `PipelineExecutionEngineTest`：成功/失败触发、跳过/取消不触发、插件异常/拒绝不改变 Job/Pipeline 状态、下游 DAG 正常推进。

## 5. Contract Documentation

- [x] 更新 `yudao-module-devops/PIPELINE_YAML_SPEC.md`，记录 Job plugins DSL、字段表、状态映射、异步非可靠语义、DingTalk 示例和错误矩阵。
- [x] 在完成质量检查后按 `trellis-update-spec` 流程更新 `.trellis/spec/backend/devops-pipeline-guidelines.md`，沉淀插件扩展和旁路约束。

## 6. Validation Commands

```bash
mvn -pl yudao-module-devops/yudao-module-devops-server -am -DskipTests compile
```

```bash
mvn -pl yudao-module-devops/yudao-module-devops-server -am \
  -Dtest='PipelineSpecValidationServiceImplTest,PipelinePluginRegistryTest,PipelinePluginDispatcherTest,DingTalkPluginTest,PipelineExecutionEngineTest' \
  -Dsurefire.failIfNoSpecifiedTests=false test
```

最后运行 DevOps Pipeline 全量相关测试：

```bash
mvn -pl yudao-module-devops/yudao-module-devops-server -am \
  -Dtest='*Pipeline*Test,*DingTalk*Test' \
  -Dsurefire.failIfNoSpecifiedTests=false test
```

## 7. Review Gates

- [x] 检查 `git diff` 中不存在 webhook、access_token、secret 或真实手机号。
- [x] 检查所有异步拒绝策略不是 `CallerRunsPolicy`，Pipeline 主线程没有 HTTP 调用或等待 Future。
- [x] 检查 Job 状态先持久化再 dispatch，且 only `SUCCESS/FAILED` 映射生效。
- [x] 检查新增 SPI 的第二个测试插件可注册执行，无需修改 Dispatcher/Engine 分支。
- [x] 检查旧 Pipeline YAML round-trip 和现有审批恢复、并行 Job、failFast 测试未回归。

## 8. Rollback Points

- DSL/SPI 与 Engine 接入分两步提交思考：若运行接入有问题，可先保留解析和校验代码并撤回 Dispatcher 调用。
- 不新增数据库迁移；代码回滚前先确保当前生效 Pipeline 版本不含 `plugins`，避免旧服务严格解析失败。
- 线程池或外部 HTTP 异常可通过删除插件配置并发布新版本停用，无需停止 Pipeline 服务。
