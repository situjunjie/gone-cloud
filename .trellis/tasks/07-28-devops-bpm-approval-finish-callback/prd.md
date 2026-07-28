# DevOps BPM 审批结束回调适配

## Goal

使 DevOps 流水线审批节点能够接收 BPM 流程后置通知，并在 BPM 确认该流程实例已通过时恢复流水线执行。

## Confirmed Facts

- BPM 当前仅在流程通过时发送流程后置通知；通知由 `BpmHttpRequestUtils` 以 `POST` 表单参数发送，并默认包含 `processInstanceId`。拒绝和取消不会触发该请求。见 `BpmProcessInstanceServiceImpl.java:1022` 与 `BpmHttpRequestUtils.java:39`。
- 当前 DevOps 回调 `POST /devops/pipeline-run/bpm/approval-status` 直接接收 JSON `BpmProcessInstanceStatusEvent`，不能接收上述统一表单通知。见 `PipelineRunController.java:155`。
- 现有 DevOps 审批状态处理已根据 BPM 状态正确处理通过、拒绝、取消，并通过业务标识定位流水线及节点。见 `PipelineApprovalServiceImpl.java:139`。
- BPM 内部 `BpmProcessInstanceApi` 当前仅暴露创建和取消，未暴露按流程实例 ID 查询状态的能力。见 `BpmProcessInstanceApi.java:19`。

## Requirements

- R1. DevOps 回调入口接收 BPM 流程后置通知的 `processInstanceId` 表单参数，并校验其非空。
- R2. 回调入口通过 BPM 内部 RPC 查询该流程实例的最终状态、业务标识及流程定义 key，不信任由 HTTP 调用方传入的审批结果。
- R3. DevOps 仅在查询结果为审批通过时，将结果交给既有审批状态处理链并恢复流水线执行；其他状态不修改流水线。
- R4. BPM 内部 RPC 提供最小的流程状态查询响应，且遵循当前租户上下文。
- R4a. 无响应变量映射的 BPM 流程后置通知在流程事务提交后发送，保证 DevOps 回调查询到已提交的流程终态；通知参数和仅“通过”时触发的既有语义保持不变。配置响应变量映射的既有通知保留同步语义。
- R5. 保留聚合部署的本地 `BpmProcessInstanceStatusEvent` 监听路径；不改变其行为。
- R6. 为新 RPC 和 DevOps 回调适配添加针对性测试，覆盖审批通过、非通过状态不处理及非 DevOps 业务标识被忽略的情形。

## Out Of Scope

- 不改变 BPM 流程后置通知的配置模型、请求方法、参数组装规则或仅“通过”时触发的现有语义。
- 不改变流水线审批节点的 BPM 流程创建、取消或前端“去审批”动作。
- 不新增外部签名认证机制；沿用 BPM 后置通知的租户头传递和现有内部网络部署边界。

## Acceptance Criteria

- [ ] BPM 后置通知以表单 `processInstanceId` 调用 DevOps 回调时，DevOps 查询到审批通过并恢复流水线执行。
- [ ] 查询到非通过状态时，DevOps 不修改审批节点或流水线，也不恢复执行。
- [ ] 非 DevOps 的 BPM 流程实例通知不会修改 DevOps 流水线。
- [ ] 聚合部署的本地 BPM 状态事件监听行为保持不变。
