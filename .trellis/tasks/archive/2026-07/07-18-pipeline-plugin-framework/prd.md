# Pipeline 插件框架与钉钉通知插件

## Goal

为 DevOps Pipeline 增加可扩展的插件机制，使流水线任务可以在指定运行状态下调用声明式插件，并以钉钉机器人通知作为首个插件实现。

## Background

用户期望 Pipeline DSL 在 job 层支持如下配置形态：

```yaml
stages:
  build_stage:
    jobs:
      build_job:
        plugins:
          - name: 钉钉机器人通知插件
            plugin: DingTalkPlugin
            triggerState:
              - success
              - fail
            with:
              webhook: <ding-talk-webhook-url>
              noticeContent:
                - pipelineName
                - operator
                - pipelineTask
                - pipelineStage
                - status
              customContent: <DIY-message>
              at: <mobile-phone>
```

当前仓库已有 Pipeline DSL、版本化定义和 Pipeline/Stage/Job 运行状态模型；插件接口的归属、触发点和错误语义需依据现有执行链设计。

## Confirmed Facts

- `PipelineSpec.Job` 当前持有 `runsOn/needs/steps/timeoutSeconds/retryTimes/failStrategy`，并由 `toExecutableGraph()` 转换为 `ExecutableJob`；插件配置需同时进入声明模型和执行模型（`PipelineSpec.java:113`、`PipelineSpec.java:238`）。
- DSL 解析启用了 `FAIL_ON_UNKNOWN_PROPERTIES`，不增加 `plugins` 字段时示例 YAML 会直接得到 `SPEC_INVALID`；保存草稿和发布均调用统一校验服务（`PipelineSpecValidationServiceImpl.java:35`、`PipelineDefinitionServiceImpl.java:109`、`PipelineDefinitionServiceImpl.java:147`）。
- Job 是调度与终态的基本单位；无依赖 Job 可并行执行，`SUCCESS/FAILED/SKIPPED/CANCELED` 是终态，`BLOCKED` 会在审批或冲突处理后重入（`.trellis/spec/backend/devops-pipeline-guidelines.md`）。
- 正常 Job 在 `executeJob()` 内落为 `SUCCESS` 或 `FAILED`；禁用、依赖失败/failFast 和用户取消则通过独立路径落为 `SKIPPED/CANCELED`，插件触发应只接入前两种已确认终态（`PipelineExecutionEngine.java:234`、`PipelineExecutionEngine.java:439`、`PipelineExecutionEngine.java:474`）。
- Pipeline 运行记录保存 `definitionId/definitionVersionId/triggerUserId`，Job 运行记录保存 Stage/Job 名称和终态；Pipeline 名称可由 definition 查询，操作人昵称可复用 `AdminUserApi`，远程用户查询失败时应降级为用户 ID（`PipelineRunDO.java`、`PipelineRunJobDO.java`、`PipelineApprovalDeployDetailBuilder.java:164`）。
- `specJson` 作为版本内容原样持久化并通过版本接口返回。现有构建/镜像步骤同样允许凭据字段直接位于 YAML，并要求不得进入日志、上下文、结果或输出；仓库目前没有通用 Pipeline 凭据引用模型（`PipelineDefinitionServiceImpl.java:243`、`PipelineDefinitionVersionRespVO.java:31`、`PIPELINE_YAML_SPEC.md:296`）。
- DevOps 模块已有 Hutool HTTP 客户端使用模式；system 模块存在钉钉机器人签名参考，但 DevOps 不应依赖其具体短信客户端实现（`NexusArtifactRegistryClient.java`、`DebugDingTalkSmsClient.java`）。

## Requirements

- R1. Pipeline DSL 支持在 job 的 `plugins` 数组中声明零到多个插件，未配置插件的历史定义继续正常解析和执行。
- R2. 每个插件配置至少包含展示名称 `name`、稳定插件标识 `plugin`、触发状态集合 `triggerState` 和插件参数 `with`。
- R3. 提供可扩展的 Plugin SPI、插件注册/查找机制、统一执行上下文和配置校验机制，新增插件不需要修改 Pipeline 核心调度分支。
- R4. 插件只在配置的 Job 到达匹配终态时执行一次；`success` 仅匹配 `SUCCESS`，`fail` 仅匹配 `FAILED`，`SKIPPED/CANCELED` 不触发插件。
- R5. 实现 `DingTalkPlugin`，使用钉钉 `text` 消息，支持 webhook、可选加签 secret、通知字段选择、普通文本自定义内容和手机号 @ 配置，并发送包含当前 Pipeline/Stage/Job 运行信息的机器人消息；配置 secret 时按钉钉 HMAC-SHA256 协议生成 `timestamp/sign`。`customContent` 按原文追加，不解析变量或模板表达式；`at` 同时接受单个手机号和手机号数组，并归一化为钉钉 `atMobiles`。
- R6. webhook 等敏感配置不得写入运行日志、执行上下文、插件结果或异常明文。首版沿用当前 Pipeline YAML 凭据模型，不新增独立凭据中心；版本接口仍按现有权限返回定义原文。
- R7. 插件配置错误应在可执行定义生效前被发现，并返回可定位到 Stage、Job 和插件项的校验错误。
- R8. 插件执行结果应具备可诊断日志，至少包含 pipeline run、stage、job、plugin 标识和成功/失败结果，但不泄露敏感参数。
- R9. 插件是旁路扩展：Job 原始终态必须先持久化；插件执行失败不得改变 Job/Pipeline 状态、阻断后续 Job 调度或覆盖任务错误信息。
- R10. 插件使用独立的有界线程池异步执行，Pipeline 不等待插件完成；线程池拒绝、请求超时和插件业务错误只记录脱敏告警。首版不持久化插件执行记录、不自动重试，也不承诺服务重启期间的可靠送达。

## Acceptance Criteria

- [x] AC1. 现有不含 `plugins` 的 Pipeline YAML 能通过原有保存、发布/生效和执行流程，行为不变。
- [x] AC2. 含一个或多个插件的 Job 能被正确解析、序列化和版本化保存，配置顺序保持稳定。
- [x] AC3. 未注册插件、重复或不支持的触发状态、缺少 DingTalk 必填参数等配置会被拒绝，并返回明确定位信息。
- [x] AC4. Job 成功时只执行包含 `success` 的插件；Job 失败时只执行包含 `fail` 的插件；`SKIPPED/CANCELED` 及其他不匹配状态不调用插件。
- [x] AC5. `DingTalkPlugin` 发送的消息只包含 `noticeContent` 选择的标准字段，并按原文附加非空 `customContent`；`customContent` 中类似 `${VAR}` 的文本保持不变；标量或数组 `at` 均转换为 `atMobiles`；未配置 secret 时直接请求 webhook，配置 secret 时请求 URL 包含有效 `timestamp/sign`。
- [x] AC6. 插件实现可通过统一 SPI 被注册和调用，增加第二种插件无需修改核心插件调度逻辑。
- [x] AC7. 单元测试覆盖 DSL 兼容、配置校验、状态匹配、插件分发、钉钉请求构造和异常路径。
- [x] AC8. 日志、异常和测试快照中不出现完整 webhook。
- [x] AC9. 插件抛出异常、请求超时或返回业务错误时，Job/Pipeline 保持原始终态，满足依赖条件的后续 Job 仍可正常调度。
- [x] AC10. 慢响应插件不会阻塞 Pipeline 调度线程；线程池拒绝任务时 Pipeline 仍按原状态完成，并产生可定位且不含敏感配置的告警。

## Out Of Scope

- Pipeline 可视化编辑器的插件配置 UI，除非现有后端接口契约要求同步调整 VO。
- 插件市场、动态上传 JAR、脚本插件和第三方插件在线安装。
- 钉钉以外的具体通知插件实现。
- 通用凭据中心、`serviceConnection`/`secretRef` 及 Pipeline 版本内容字段级加密。
- 插件执行记录表、可靠消息、失败重试和服务重启后的补偿发送。
