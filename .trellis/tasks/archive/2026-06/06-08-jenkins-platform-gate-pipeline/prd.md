# DevOps Jenkins Platform Gate Pipeline

## Goal

在现有 DevOps 流水线基础上，实现“平台固定代码合并 + Jenkins 执行可配置中间流水线 + 平台固定部署”的完整执行闭环。平台流水线发布后仍然生成一份完整 Jenkinsfile；Jenkins 执行到平台节点时进入通用暂停节点，通知平台处理，平台完成后再通知 Jenkins 继续或终止。

## Background

当前平台已经具备流水线 DSL、可视化定义、发布版本、Jenkinsfile 生成和运行日志基础能力。现有执行侧只完成固定 `CODE_MERGE`，代码合并成功后直接将 run 标记为成功。后续需要将代码合并后的执行权交给 Jenkins，并支持流水线中混排 Jenkins 节点和平台节点。

## Product Principles

- 平台持有流水线定义、运行状态、审批状态、部署决策和产物记录。
- Jenkins 持有一条中间流水线 build 的执行上下文，负责构建、测试、镜像、扫描等任务。
- 平台可视化编排里的每个具体块对应一个平台 node，每个 Jenkins stage 对应一个平台 node。
- node 生命周期回调由 Jenkinsfile generator 隐式包裹在 stage step 前后，用户不需要手动配置生命周期节点。
- 平台节点在 Jenkinsfile 中统一表现为 `Platform Gate` 暂停节点，Jenkins 不理解平台节点业务细节。
- 一条平台流水线发布版本对应一份完整 Jenkinsfile，不因为平台节点切分为多份 Jenkinsfile。
- 固定代码合并和固定部署不进入用户可视化编排，仍由平台控制。
- 前台对用户屏蔽 Jenkins 概念，用户只能触发/查看/取消整条平台流水线，不能单独开启 Jenkins 构建。

## User Stories

### US1 发布流水线

作为 DevOps 管理员，我希望在平台可视化配置 Jenkins 节点和平台节点，并发布为不可变版本，使后续运行按发布时的版本执行。

### US2 触发变更上线

作为研发人员，我希望提交变更上线后，平台先合并代码，合并成功后自动触发 Jenkins 按已发布流水线构建。

### US3 平台节点暂停

作为审批人，我希望流水线执行到审批节点时在平台看到待处理任务，处理通过后 Jenkins 自动继续，处理拒绝后 Jenkins 终止。

### US4 运行可视化

作为研发/运维人员，我希望在平台看到整条流水线的节点状态、Jenkins build 地址、当前是否卡在平台节点、失败原因和构建产物。

### US5 部署闭环

作为发布人员，我希望 Jenkins 成功构建并上报产物后，由平台执行固定部署节点，而不是 Jenkins 直接部署。

## Functional Requirements

### FR1 流水线定义

- 平台 DSL 继续使用 `nodes` 和 `edges` 表达中间流水线。
- 每个可视化编排块必须映射为一个 `PipelineSpec.Node`。
- 每个 `PipelineSpec.Node` 在 Jenkinsfile 中必须生成一个对应 stage。
- 节点按执行类别分为：
  - `JENKINS`：由 Jenkins stage 执行。
  - `PLATFORM`：由平台处理，Jenkinsfile 中生成通用暂停 stage。
- 平台节点第一期至少支持 `APPROVAL`。
- `DEPLOY_K8S` 不作为中间可配置节点进入 MVP；最终部署仍是平台固定节点。

### FR2 Jenkinsfile 生成

- 发布版本生成一份完整 Jenkinsfile。
- Jenkins 节点生成真实 stage，例如 checkout、unit test、build artifact、build image、report artifacts。
- 每个 node stage 的真实 step 前后必须隐式注入统一生命周期 callback：
  - step 前：`STARTED`
  - step 成功后：`COMPLETED`
  - step 异常时：`FAILED`
- 平台节点统一生成 `Platform Gate` stage：
  - 调平台接口进入 gate。
  - 使用 Jenkins `input` 暂停。
  - 平台节点业务完成后，Jenkins 对该节点回调 `COMPLETED` 或 `FAILED`。
- 每个 gate 的 `inputId` 必须稳定，建议格式：`gate-${PIPELINE_RUN_ID}-${nodeId}`。

### FR3 代码合并后触发 Jenkins

- `CODE_MERGE` 成功后不再直接将 run 标记为 `SUCCESS`。
- 平台推送 deploy branch 后，由后端服务根据流水线发布配置自动触发 Jenkins build。
- Jenkins build 参数至少包括：
  - `PIPELINE_RUN_ID`
  - `PIPELINE_VERSION_ID`
  - `REPO_URL`
  - `BRANCH_NAME`
  - `COMMIT_SHA`
  - `APP_KEY`
  - `CALLBACK_TOKEN`

### FR4 Jenkins 回调平台

- Jenkins 必须通过统一回调入口回调平台，平台根据 `action`、`nodeType`、`nodeId` 分发处理。
- Jenkins 回调动作统一收敛为：
  - `STARTED`：step 开始。
  - `COMPLETED`：step 完成。
  - `FAILED`：step 失败。
- 平台根据 `nodeType` 决定该 step 对应的内部节点类型和处理逻辑。
- 产物上报通过统一 callback 的 `artifacts` 字段承载，不单独新增产物回调 controller。
- 回调必须携带 callback token，平台需要做鉴权和幂等。
- 这些回调接口是 Jenkins 与平台的系统内部集成接口，不暴露为前台用户操作入口。
- 回调接口路径可暂时放在 `/admin-api` 下，但接口文档必须明确调用方是 Jenkins，前端不能用这些接口触发 Jenkins 构建或更新节点状态。

### FR4.1 节点处理器抽象

- 后端需要抽象统一节点处理器接口，避免每新增一种节点就改核心执行流程。
- 节点处理器按 `nodeType` 或节点执行类别注册。
- Jenkins 节点处理器负责处理 `STARTED`、`COMPLETED`、`FAILED` 以及 artifact 字段。
- 平台节点处理器负责处理平台节点的 `STARTED`、`COMPLETED`、`FAILED`、审批/拒绝、超时/取消等事件。
- 节点处理器统一提供开始、完成、失败三个方法，Jenkins callback controller 不直接写具体节点业务分支。
- 第一版至少实现：
  - `JenkinsBuildNodeHandler`
  - `ApprovalPlatformNodeHandler`
  - `ArtifactReportNodeHandler`

### FR5 平台 gate 处理

- Jenkins 到达平台节点时，平台创建或更新对应节点日志为 `WAITING_INPUT`。
- 平台根据节点类型处理业务：
  - `APPROVAL`：生成审批待办，等待审批人处理。
- 审批节点不单独定义 platform gate approve/reject 接口；审批如何完成由审批类型 node handler 的业务实现负责。
- 审批类型 node handler 只需要遵守统一节点处理接口：开始、完成、失败。

### FR6 产物上报

- Jenkins 构建制品后必须上报平台。
- MVP 产物字段：
  - `artifactType`
  - `artifactName`
  - `artifactUrl`
  - `imageRepository`
  - `imageTag`
  - `commitSha`
  - `jenkinsBuildUrl`
- MVP 不单独建产物表，产物信息先记录在对应构建 node 的运行结果字段中。
- 平台固定部署节点读取平台记录的产物，不直接从 Jenkins 推断。

### FR7 取消与超时

- 平台取消 run 时，如果 Jenkins build 仍在运行，需要调用 Jenkins stop build。
- 平台节点支持 `timeoutSeconds`。
- Jenkinsfile 中平台 gate 应生成 Jenkins `timeout` 包裹 `input`。
- 超时后平台 run 标记失败或取消，MVP 建议标记失败并记录超时原因。

## Non-Functional Requirements

- Jenkins 回调接口必须具备鉴权，不能只依赖内网。
- 所有外部回调必须幂等，Jenkins 重试不能生成重复节点日志或重复审批单。
- Jenkins 回调接口必须适配 Jenkins Pipeline 常用 HTTP 调用方式：`POST`、`application/json`、header token、快速返回、响应体简单稳定。
- Jenkins 回调接口不能要求浏览器 session、cookie、复杂表单、multipart 或前端 CSRF 语义。
- 平台不能保存大体积 Jenkins console log，只保存 URL、摘要和关键事件。
- Jenkins 凭证最小权限化，平台只应具备触发 build、继续/中止指定 input、停止指定 build 的能力。
- 平台 current-run polling 响应不能暴露 callback token、Jenkins 凭证或工作区路径。

## State Model

### Run Status

- `QUEUED`
- `RUNNING`
- `SUCCESS`
- `FAILED`
- `CANCELED`

### Node Log Status

- `PENDING`
- `RUNNING`
- `WAITING_INPUT`
- `SUCCESS`
- `FAILED`
- `CANCELED`

## Happy Path

1. 用户提交变更上线。
2. 平台创建 `dev_pipeline_run`，状态 `RUNNING`。
3. 平台执行固定 `CODE_MERGE`。
4. 代码合并成功，推送 deploy branch。
5. 平台后端内部触发 Jenkins build。
6. Jenkins 按 Jenkinsfile 执行 Jenkins 节点。
7. Jenkins 执行到 `APPROVAL`，调用平台进入 gate。
8. 平台创建审批待办，节点状态 `WAITING_INPUT`。
9. 审批人通过。
10. 审批节点业务完成后，Jenkins 对该节点发送 `COMPLETED` callback。
11. Jenkins 继续执行后续节点。
12. Jenkins 上报产物并完成。
13. 平台执行固定部署。
14. 部署成功，run 状态 `SUCCESS`。

## Error Cases

| 场景 | 期望行为 |
|---|---|
| 代码合并冲突 | `CODE_MERGE` 进入 `WAITING_INPUT`，不触发 Jenkins |
| Jenkins 触发失败 | run 标记 `FAILED`，记录失败原因 |
| Jenkins 节点失败 | 对应节点日志 `FAILED`，run 标记 `FAILED` |
| Jenkins 到达 gate 后平台创建审批失败 | gate 节点 `FAILED`，Jenkins build abort |
| 审批拒绝 | 审批类型 node handler 触发节点 `FAILED`，run `FAILED` |
| 用户取消 run | 平台 stop Jenkins build，run `CANCELED` |
| Jenkins 回调重复 | 平台幂等处理，不重复创建日志/审批 |
| Jenkins input 状态与平台节点状态不一致 | 平台提示当前节点状态不可继续，并刷新 Jenkins build 状态 |
| Jenkins 长时间无回调 | 平台超时兜底，run `FAILED` |

## MVP Scope

- 支持一份完整 Jenkinsfile。
- 第一阶段先支持 `MOCK` 节点：Jenkins stage 不执行真实业务，只用于验证 Jenkins 构建触发和 stage 生命周期回调。
- 支持 Jenkins build 触发、统一状态回调、节点 `STARTED/COMPLETED/FAILED` 状态落库。
- 平台对 Jenkins 的后端内部能力收敛为开启 Jenkins 流水线和停止 Jenkins 流水线。
- 支持固定代码合并成功后触发 Jenkins，Jenkins 成功后进入固定部署。
- 用户侧不增加任何“单独触发 Jenkins 构建”的前台入口。

## Out of Scope

- Jenkins 多分支流水线自动发现。
- 平台节点复杂条件表达式。
- 并行 DAG 执行。
- Jenkins 节点动态分段生成多份 Jenkinsfile。
- Jenkins 直接部署生产环境。
- 大体积控制台日志入库。

## Acceptance Criteria

- [ ] 发布包含 Jenkins 节点和 `APPROVAL` 节点的流水线时，生成一份包含 Platform Gate stage 的 Jenkinsfile。
- [ ] Jenkins 只需要调用一个统一 callback endpoint，并通过 `action=STARTED/COMPLETED/FAILED` 区分 step 状态。
- [ ] 平台后端内部只需要开启 Jenkins 流水线、停止 Jenkins 流水线两个核心 Jenkins 操作能力。
- [ ] 代码合并成功后，run 保持 `RUNNING` 并触发 Jenkins build。
- [ ] `MOCK` 节点生成 Jenkins stage，并隐式注入 `STARTED/COMPLETED/FAILED` callback。
- [ ] Jenkins 回调 `MOCK` 节点 `STARTED` 后，平台节点日志变为 `RUNNING`。
- [ ] Jenkins 回调 `MOCK` 节点 `COMPLETED` 后，平台节点日志变为 `SUCCESS`。
- [ ] Jenkins 回调 `MOCK` 节点 `FAILED` 后，平台节点日志变为 `FAILED`，run 失败。
- [ ] Jenkins 回调重复不会产生重复审批单或重复节点日志。
- [ ] Jenkins 回调接口能被 Jenkins Pipeline `httpRequest` 以简单 JSON + header token 方式调用。
- [ ] 后端节点处理逻辑通过节点处理器分发，新增节点类型时不需要改 Jenkins callback controller 主流程。
- [ ] 取消 run 会同步停止 Jenkins build。
- [ ] current-run 能展示代码合并、Jenkins 节点、平台 gate、部署的统一状态。

## Technical Notes

- 当前 `PipelineSpec` 已具备 `nodes`、`edges`、`timeoutSeconds`、`retryTimes`、`failStrategy` 字段。
- 当前 `PipelineNodeRegistryServiceImpl` 已区分 `JENKINS` 和 `PLATFORM` 类别，但 `APPROVAL` 当前为 disabled。
- 当前 `JenkinsfileGeneratorServiceImpl` 已生成一份完整 Jenkinsfile，需要扩展平台节点 stage。
- 当前 `PipelineExecutionServiceImpl#finishCodeMerge` 会直接将 run 标记 `SUCCESS`，后续需改为触发 Jenkins。
- 当前 `dev_pipeline_run_log` 的通用日志模型可以复用为 DSL 节点日志。
- Jenkins build 的触发属于后端内部基础设施调用，不新增面向前台用户的独立 trigger controller。
