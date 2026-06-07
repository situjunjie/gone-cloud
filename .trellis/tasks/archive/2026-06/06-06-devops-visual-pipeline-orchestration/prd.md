# brainstorm: devops visual pipeline orchestration

## Goal

为 DevOps 模块设计“应用 + 环境”维度的可视化流水线编排能力，让每个应用在不同环境下可以配置不同流水线，并支持从代码合并、构建、单元测试、审批到部署的完整执行链路。

## What I already know

* 当前仓库主要是后端工程，README 指向独立管理端：Vue3 + element-plus 或 Vue3 + vben/ant-design-vue。
* DevOps 模块已有代码源、应用、环境、应用环境关系、变更、变更环境关系。
* `dev_application_env` 已有 `pipeline_definition_id`、`approval_required`、`approval_config_json`、`current_snapshot_id` 字段，天然适合作为“应用在某环境的流水线配置入口”。
* `dev_change_env` 已有 `last_pipeline_run_id`、合并/构建/测试/部署阶段状态、审批状态等字段，适合作为变更挂载到环境后的运行摘要。
* 当前 SQL 只保留了流水线定义编号和最近运行状态，尚未定义流水线定义、版本、运行实例、阶段实例、日志、产物等核心表。

## Assumptions

* 流水线编辑器主要服务研发/运维配置，不是普通审批人员使用。
* MVP 优先支持线性主链路：代码合并 -> 构建 -> 单元测试 -> 可选审批 -> 可选部署。
* 后续可以扩展为 DAG，但第一版不做复杂并行矩阵、条件分支和循环。
* 总体方向已进一步收敛为：Jenkins 定位为代码合并到部署之间的构建/测试/产物生成执行器；平台负责编排、权限、审批、部署决策、部署执行和状态展示。
* Jenkins 输入主要是 Jenkinsfile、代码库、分支和平台运行参数；输出主要是构建产物、镜像 tag、测试报告、日志和执行状态。
* 审批节点优先由平台承载，不强制放在 Jenkins 内部；只有需要 Jenkins 暂停等待时才使用 Jenkins `input` step。

## Requirements

* 支持按 `application_env_id` 绑定一个启用中的流水线定义。
* 支持流水线定义版本化：编辑草稿不影响已执行或正在执行的运行实例。
* 支持节点类型插件化：merge、build、test、approval、deploy 是第一批内置节点。
* 支持节点参数 schema 化：前端按节点类型渲染属性面板，后端按节点类型校验配置。
* 支持运行快照：每次执行使用定义版本快照，记录输入、输出、状态、错误、日志、产物。
* 支持平台前端拖拽编排后生成可给 Jenkins 执行的构建/测试 Jenkinsfile。
* 支持平台部署操作在合适阶段触发 Jenkins 参数化构建，并更新 `dev_change_env` 的最近构建/测试状态。
* 支持平台感知 Jenkins 每个 build/test stage 的状态、开始时间、耗时、错误、产物和日志。
* 支持 Jenkins 完成后将构建产物返回平台，平台再决定是否进入审批和部署。
* 支持平台侧审批通过后，基于 Jenkins 输出产物执行部署。
* 支持发布前聚合多个变更分支：从基准分支创建发布分支，按变更列表顺序循环合并变更分支。
* 支持合并冲突暂停发布流程，并在平台页面展示冲突文件、三方内容和可编辑结果。
* 支持冲突解决完成后继续合并剩余变更分支，最终得到可交给 Jenkins 构建的发布分支或发布 commit。
* 支持最小权限控制：谁能编辑定义、谁能触发运行、谁能审批、谁能部署。

## Recommended Architecture

### 0. Jenkins as build/test artifact executor

当前方向收敛为：Jenkins 不负责整条 DevOps 流水线，而是作为“代码合并之后、部署之前”的构建/测试/产物生成执行器。平台负责编排和部署闭环，Jenkins 负责执行 Jenkinsfile 并产出可部署制品。

推荐边界：

* 平台负责：代码源/应用/环境/变更、代码合并策略、可视化编排、Jenkinsfile 生成与版本化、Jenkins 触发、构建/测试状态感知、产物入库、审批、部署、运行历史。
* Jenkins 负责：checkout 指定代码库和分支、执行 Jenkinsfile 中的单元测试/构建阶段、生成 jar/image/chart/test report 等产物。
* Jenkins 输入：Jenkinsfile、代码库、分支、runId、changeEnvId、构建参数。
* Jenkins 输出：build number、stage 状态、日志、测试报告、artifact URL、image tag/digest、构建结果。

推荐 MVP 形态：

* DevOps 平台保存可视化 DSL 和 Jenkinsfile 版本。
* Jenkins 只执行一个“构建/测试参数化 Pipeline Job”或每应用一个构建 Job。
* 平台触发 Jenkins 时传入 `appId`、`changeEnvId`、`repoUrl`、`branchName`、`pipelineVersionId`、`pipelineRunId` 等参数。
* Jenkins 执行结果回写平台，平台维护 `PipelineRun` / `PipelineStageRun`，并回写 `dev_change_env.last_build_status`、`last_test_status`。
* Jenkins 成功后，平台读取产物信息并进入平台侧审批/部署。

关键可行性结论：

* 触发 Jenkins：可通过 Remote Access API 的 `buildWithParameters`。
* 感知阶段：可通过 Pipeline REST API 插件的 `wfapi/describe` 获取 run/stage 状态。
* 查看日志：可通过 Pipeline REST API 的节点 log 链接获取 stage 内日志，也可用 Jenkins progressive console log 兜底。
* 产物获取：Jenkins 可通过 archiveArtifacts、测试报告发布、镜像构建输出、平台回调等方式提供产物元数据。
* 审批暂停：推荐平台侧审批；如确需 Jenkins 内暂停，可通过 Pipeline Input Step 的 `input id: ...` 暂停。

### 0.1 Release branch aggregation and conflict resolution

发布第一步建议建模为“发布合并会话”：

1. 平台锁定本次发布的变更列表和每个变更分支的 commit SHA。
2. 从基准分支或基准 commit 创建发布分支，例如 `release/{appKey}/{envKey}/{timestamp}`。
3. 后端创建隔离的临时 Git worktree。
4. 按变更顺序执行 `git merge --no-ff --no-commit <changeBranchOrSha>`。
5. 无冲突则提交一个合并 commit，并继续下一个变更。
6. 有冲突则暂停会话，记录当前正在合并的变更、冲突文件和三方内容。
7. 前端用 Monaco/类似三方 merge editor 展示 base/current/incoming/result。
8. 用户解决冲突后，后端写回工作区、`git add`、`git merge --continue` 或创建合并 commit。
9. 继续合并后续变更，直到发布分支聚合完成。
10. Jenkins 以发布分支或最终 release commit 作为输入执行构建/测试。

推荐使用真实 Git 命令或 JGit 管理工作区，不建议只靠 GitLab 文件 API 在数据库里拼接冲突。

关键原因：

* Git 冲突包含 index stage 1/2/3、文件模式、删除/修改、重命名、二进制文件、子模块等状态。
* 真实 Git 工作区可以标准化使用 `git ls-files -u`、`git show :1:path`、`:2:path`、`:3:path`、`git add`、`git merge --continue`。
* Git 官方冲突模型天然就是三方：base、current/ours、incoming/theirs。

### 1. Domain model

建议把流水线拆成三层：

* `PipelineDefinition`：逻辑定义，属于某租户，可被模板或应用环境引用。
* `PipelineDefinitionVersion`：不可变版本，保存完整画布 JSON、节点 JSON、边 JSON、节点参数、校验结果。
* `PipelineRun` / `PipelineStageRun`：运行实例和节点运行实例，记录这次执行的快照、状态、日志、产物和审批等待点。

`dev_application_env.pipeline_definition_id` 可以继续作为绑定字段，但建议绑定到 definition；执行时解析 latest published version，并把 version id 固化到 run。

### 2. Visual model

前端画布 JSON 不建议直接成为执行 DSL。建议分两份：

* `diagram_json`：只负责画布展示，包括节点坐标、边路径、折线、颜色、分组。
* `pipeline_spec_json`：后端可执行规范，包括 nodes、edges、node type、params、dependsOn、retry、timeout、manualGate。

保存时后端根据 `pipeline_spec_json` 校验拓扑和参数，必要时重新生成执行顺序。

### 3. Execution model

MVP 采用状态机 + 顺序/DAG 调度：

1. 创建 `PipelineRun`，绑定 `change_env_id`、`application_env_id`、definition version。
2. 按拓扑找到可运行节点，创建/更新 `PipelineStageRun`。
3. 每个节点由对应 `PipelineNodeExecutor` 执行。
4. approval 节点进入 `WAITING_APPROVAL`，审批通过后继续，拒绝后 run 失败或终止。
5. deploy 节点调用环境连接器，例如 K8S connector；后续 HOST connector 扩展。
6. 每个阶段状态回写 run，同时摘要回写 `dev_change_env.last_*_status`。

第一版可以先同步执行或用 XXL-Job/消息队列异步化；如果日志和部署耗时较长，建议从一开始按异步 run 设计。

### 4. Node types

内置节点建议：

* `MERGE_CODE`：把变更分支合并到环境部署分支或临时集成分支，记录 merge commit / conflict。
* `BUILD_IMAGE`：执行构建命令或触发外部构建器，生成 image/tag/artifact。
* `UNIT_TEST`：执行测试命令，记录报告地址和摘要。
* `APPROVAL`：等待人工审批，支持 approver user ids / role ids / owner。
* `DEPLOY_K8S`：基于环境配置部署到 namespace，记录 workload、image、revision。

每个节点都应有统一字段：`id`、`type`、`name`、`enabled`、`params`、`timeoutSeconds`、`retryTimes`、`failStrategy`。

### 5. Frontend recommendation

推荐第一优先级：Vue Flow。

原因：当前管理端主线是 Vue3，Vue Flow 原生 Vue3/TypeScript，内置拖拽、缩放、选择、MiniMap、Controls、节点/边自定义，适合做“业务流水线 DSL 画布”。相比 BPMN，学习成本低；相比 X6，工程复杂度更轻。

备选：

* AntV X6：更强的图编辑引擎，适合后续要做复杂 DAG、分组、自动布局、对齐线、剪贴板、导出等重编辑体验。
* LogicFlow：偏业务流程图，中文生态好，支持插件和 BPMN/Turbo 数据转换，适合“业务流程可视化”而不是代码流水线。
* bpmn-js：只有当我们决定 DevOps 流水线严格采用 BPMN 2.0，或要直接复用 BPMN/Flowable 标准流程模型时才建议。
* Rete.js：更适合数据流/可视化编程，有执行引擎概念，但对 DevOps 线性流水线来说偏重。

## Research References

* [research/frontend-flow-editor-libraries.md](research/frontend-flow-editor-libraries.md) - Vue3 流程/图编排库调研与推荐。
* [research/jenkins-api-pipeline-integration.md](research/jenkins-api-pipeline-integration.md) - Jenkinsfile 可视化编排与 Jenkins API 集成方案。
* [research/jenkins-feasibility-and-technical-plan.md](research/jenkins-feasibility-and-technical-plan.md) - 外接 Jenkins API 的完整可行性分析和技术方案。
* [research/release-branch-merge-conflict-resolution.md](research/release-branch-merge-conflict-resolution.md) - 发布分支聚合合并和在线冲突解决方案。

## Technical Design

* [technical-design-visual-pipeline-mvp.md](technical-design-visual-pipeline-mvp.md) - 流水线可视化编排 MVP 详细技术方案。

## Acceptance Criteria

* [ ] 明确流水线定义、版本、运行实例、阶段实例的数据模型。
* [ ] 明确应用环境与流水线定义的绑定关系。
* [ ] 明确前端画布 JSON 与后端执行 DSL 的边界。
* [ ] 明确 MVP 节点类型和执行顺序。
* [ ] 明确前端编排库选型。

## Out of Scope

* 第一版不实现完整可视化代码生成。
* 第一版不支持复杂循环、动态矩阵、多分支回滚编排。
* 第一版不把所有审批能力都搬进 DevOps；复杂审批可后续接 BPM。
* 第一版不要求与 Jenkins/GitLab CI/YAML 全量互通，只预留外部执行器扩展点。

## Open Question

* Jenkinsfile 存储策略选择：提交到应用仓库、提交到独立流水线仓库，还是由 Jenkins Job 配置直接保存脚本？当前推荐独立流水线仓库。
* Jenkins 状态同步策略选择：第一版轮询 Jenkins API，后续是否增加 Jenkins 回调平台 webhook？

## Confirmed Decisions

* 第一期 Jenkinsfile 只保存在平台数据库并提供预览，暂不接 Jenkins 执行交付。
* 构建/测试命令必须从后端模板选择，第一期不允许用户输入任意命令。
* `APPROVAL` 和 `DEPLOY_K8S` 节点第一期在前端展示为禁用状态，作为后续审批/部署能力占位。

## Technical Notes

* Inspected `ApplicationEnvDO`, `ChangeDO`, `ChangeEnvDO`, `ChangeServiceImpl`, `sql/mysql/devops.sql`, `sql/mysql/devops-dict.sql`, README frontend notes.
* Backend spec index: `.trellis/spec/backend/index.md`.
