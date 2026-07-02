# DevOps 审批携带发布详情

## Goal

在 DevOps pipeline 的审批 step 发起 BPM 审批流程时，把本次发布详情以富文本 HTML 形式传入 BPM 流程变量 `deploy_html`，供 BPM 表单中字段 id 为 `deploy_html` 的富文本字段直接展示。

用户价值：审批人在 BPM 审批页面不需要切回 DevOps，即可看到本次发布涉及的应用、环境和变更列表，降低误批和漏看风险。

## Confirmed Facts

- 审批 step 当前由 `PipelineApprovalServiceImpl.startApproval(...)` 发起，调用 `BpmProcessInstanceApi.createProcessInstance(...)` 创建 BPM 流程实例。
  Evidence: `yudao-module-devops/yudao-module-devops-server/src/main/java/cn/iocoder/yudao/module/devops/service/pipeline/approval/PipelineApprovalServiceImpl.java`
- BPM 创建流程实例 DTO 支持 `variables` 和 `businessKey`，BPM server 会把传入的 `variables` 设置为流程变量。
  Evidence: `yudao-module-bpm/yudao-module-bpm-api/src/main/java/cn/iocoder/yudao/module/bpm/api/task/dto/BpmProcessInstanceCreateReqDTO.java`
- 当前 DevOps 审批变量已经包含 pipeline run、应用环境编号、分支、提交、step 等基础字段，但尚未包含 `deploy_html`。
  Evidence: `PipelineApprovalServiceImpl#buildVariables`
- `PipelineRunDO` 已保存本次发布上下文：`appId`、`applicationEnvId`、`changeId` / `changeEnvId`、`branchName`、`commitSha`、`changeSnapshotJson` 等。
  Evidence: `yudao-module-devops/yudao-module-devops-server/src/main/java/cn/iocoder/yudao/module/devops/dal/dataobject/pipeline/PipelineRunDO.java`
- 多变更发布时，`ApplicationServiceImpl.buildPipelineRun(...)` 会把所有目标变更写入 `changeSnapshotJson`，快照项目前包含 `changeId` 和发布提交时的 `commitSha`。
  Evidence: `yudao-module-devops/yudao-module-devops-server/src/main/java/cn/iocoder/yudao/module/devops/service/application/ApplicationServiceImpl.java`
- 可查询发布详情所需基础数据：`ApplicationDO` 提供应用标识和名称；`ApplicationEnvDO` 关联环境；`EnvironmentDO` 提供环境标识、名称、阶段；`ChangeDO` 提供变更标识、标题、分支和提交信息。
- `ChangeDO` 已保存测试人 `testerUserId` 和代码审核人 `codeReviewerUserId`；现有发布分支 VO 会通过 `AdminUserApi` 的 Trans 能力翻译为测试人昵称和代码审核人昵称。
  Evidence: `yudao-module-devops/yudao-module-devops-server/src/main/java/cn/iocoder/yudao/module/devops/controller/admin/application/vo/ApplicationReleaseBranchRespVO.java`
- `AdminUserApi` 支持按用户 ID 批量获取用户信息，可用于审批 HTML 组装阶段把测试人、代码审核人展示为昵称。
  Evidence: `yudao-module-system/yudao-module-system-api/src/main/java/cn/iocoder/yudao/module/system/api/user/AdminUserApi.java`

## Requirements

- R1: 当 pipeline 执行到 `APPROVAL` step 并首次发起 BPM 流程实例时，DevOps 必须在 `BpmProcessInstanceCreateReqDTO.variables` 中传入 key 为 `deploy_html` 的字符串变量。
- R2: `deploy_html` 必须是适合富文本字段展示的 HTML，包含本次发布的应用信息、环境信息、流水线运行信息和变更列表。
- R3: 变更列表必须覆盖本次发布的全部变更。多变更发布优先以 `PipelineRunDO.changeSnapshotJson` 为准；兼容单变更历史数据可回退到 `changeId`。
- R4: 组装发布详情时不得改动 BPM API 契约，不新增 BPM 表结构或流程表单配置。
- R5: 审批幂等行为保持不变：已有等待审批、已通过或终态日志时，不应重复创建 BPM 流程实例。
- R6: HTML 内容必须对来自数据库的文本字段做 HTML 转义，避免应用名、环境名、变更标题、提交信息等内容破坏富文本结构或引入脚本。
- R7: 若部分详情数据缺失，审批流程仍应能发起，HTML 中使用空值占位或省略可选字段，不因展示信息缺失阻断发布审批。
- R8: 变更列表必须包含每个变更的测试人和代码审核人；展示优先使用用户昵称，用户不存在或昵称缺失时回退展示用户 ID，字段为空时展示空值占位。

## Acceptance Criteria

- [ ] 首次执行审批 step 时，BPM 创建流程实例请求的 `variables.deploy_html` 非空。
- [ ] `deploy_html` 中能看到发布应用、发布环境、部署分支或运行编号，以及本次发布的变更列表。
- [ ] `deploy_html` 的每条变更都展示测试人和代码审核人，优先显示昵称，无法解析昵称时不阻断审批发起。
- [ ] 多变更发布时，`deploy_html` 的变更列表包含 `changeSnapshotJson` 中的全部变更。
- [ ] 空变更发布（例如上传镜像触发）仍可发起审批，HTML 中明确展示无关联变更或不展示变更表。
- [ ] 现有审批 step 状态处理、取消审批、BPM 回调推进流水线的行为不回归。
- [ ] 单元测试覆盖 `deploy_html` 变量生成、测试人/代码审核人展示、HTML 转义、多变更和空变更场景。

## Out of Scope

- 不调整 BPM 流程定义、表单字段配置或审批页面前端。
- 不改变 pipeline YAML 中 `APPROVAL` step 的配置字段。
- 不新增发布审批的结构化 BPM 表单字段；本次只填充富文本字段 `deploy_html`。
- 不在 `deploy_html` 中增加 DevOps 页面跳转链接。

## Decisions

- D1: `deploy_html` 的内容粒度为：应用、环境、流水线运行、部署分支、变更表。变更表包含变更标识、标题、分支、本次发布 commit、最新 commit、提交信息、测试人、代码审核人。
- D2: 第一版 `deploy_html` 不包含可点击链接，只展示静态审批信息，避免引入前端路由、网关地址、权限和环境域名配置问题。

## Open Questions

- None.
