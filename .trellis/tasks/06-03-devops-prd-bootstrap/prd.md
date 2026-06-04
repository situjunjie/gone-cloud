# 研发管理 / DevOps PRD Bootstrap

## Goal

为“软件企业一站式管理平台”的研发管理能力沉淀首版 PRD，当前聚焦 DevOps 领域设计。目标不是复刻 Jenkins 流水线，而是建立一个以“变更”为核心对象、覆盖代码分支到多环境交付全过程的研发交付模型，为后续项目管理、需求管理、研发管理打通统一主线。

## What I Already Know

- 平台整体未来会覆盖项目管理、需求管理、研发管理。
- 第一阶段优先做研发管理，方向是 DevOps。
- 用户明确希望摒弃 Jenkins 式“流水线中心”思路，转向“变更中心”。
- 参考对象包括阿里内部 Aone，以及阿里云云效高级版的“变更管理 / 变更持续交付”能力。
- 当前用户已给出的核心概念：
  - `应用`：一个可交付制品。
  - `应用` 关联唯一 `代码库`。
  - `应用` 关联多个 `环境`。
  - 每个 `环境` 有对应的 `部署流程`。
  - 每个 `环境` 可关联到对应的 `变更`。
  - `变更` 指向具体代码开发分支。
- 用户已确认 MVP 约束：
  - `变更` 严格限定为 `单应用 + 单分支`。
  - 一个变更面向 `开发环境 / 测试环境 / 预发环境 / 生产环境` 这些长期环境工作，但不要求严格线性晋级。
  - 同一个变更可以同时部署在多个环境中。
  - 变更可以跳过某些环境直接进入更高环境。
  - 环境当前版本的含义，更接近“该环境基准分支已合入该变更分支并完成部署”。
  - 当前示例环境为：`测试环境 / 预发环境 / 正式环境`。
  - 应用主干基准分支为 `master`。
  - 新建变更时，系统会从 `master` 自动签出变更分支，例如 `featA`。
  - 当环境发起部署时，系统会签出该环境的部署分支，例如 `test-release`，并合并挂在该环境下的所有变更分支。
  - 正式环境发布完成后，对应变更会合并回 `master`，从而更新全局基准分支。
  - 所有环境在下一次部署时，都会重新基于最新 `master` 拉起各自部署分支，再合并各自挂载的变更分支。
  - 每个环境拥有自己的流水线，当前默认主流程为：`代码合并 -> 构建 -> 单元测试 -> 部署`。
  - 后续需要支持环境流水线自定义编排，但 MVP 可以先提供默认编排。
  - 代码合并冲突发生在环境流水线的第一步“代码合并”。
  - 合并冲突由开发自行解决，系统不做自动冲突消解。
  - 冲突解决方式可以是：平台内提供代码合并页面，或让开发签出到本地手工解决。
  - 应用详情页按环境分 Tab 展示，例如：`测试环境 / 预发环境 / 正式环境`。
  - 在某个环境 Tab 中，页面中部展示该环境当前流水线，下面展示“当前已在该环境中的变更列表”，再下面展示“当前应用下有效但尚未进入该环境的变更列表”。
  - 用户在“未进入该环境的有效变更列表”中点击“提交部署”按钮后，系统应将该变更加入该环境，并立即触发该环境流水线。
  - `提交部署` 触发的不是单个变更的增量部署，而是该环境当前挂载变更全集的一次完整流水线重跑。
  - 当一个变更在正式环境发布成功并合回 `master` 后，系统应自动清理它在测试/预发等其他环境中的显式挂载记录。
  - 正式环境发布必须经过人工审批或准入卡点，不能直接无审批执行完整流水线。
  - 预发环境支持配置审批 / 准入卡点，但默认不是强制审批环境。
  - 回滚的单位是“历史环境运行快照”，而不是对单个变更做移除式回滚。

## Research References

- [`research/devops-patterns.md`](./research/devops-patterns.md) - 云效和 GitLab 在“应用/环境/部署/变更追踪”上的共性，以及对本项目的启发。
- [`research/domain-objects.md`](./research/domain-objects.md) - MVP 领域对象与字段草案，作为后续表结构和 API 设计基础。
- [`research/database-tables.md`](./research/database-tables.md) - 带租户模型约束的数据库表草案，作为后续 DO / Mapper / SQL 设计基础。
- [`research/api-draft.md`](./research/api-draft.md) - 面向环境 Tab、提交部署、审批、回滚的核心 API 草案。
- [`research/application-entity.md`](./research/application-entity.md) - 单独收敛后的【应用】实体完整设计草案。
- [`research/change-entity.md`](./research/change-entity.md) - 单独收敛后的【变更】实体完整设计草案。
- [`research/environment-entity.md`](./research/environment-entity.md) - 单独收敛后的【环境】实体与应用-环境关系设计草案。

## Assumptions (Temporary)

- MVP 阶段先聚焦“单应用 DevOps 主链路”，暂不覆盖跨应用编排、跨项目组合发布。
- `变更` 将成为研发活动主对象，至少承载分支、提交、流程运行、环境部署、审批和审计信息。
- `环境` 会是长期对象（如 dev/test/staging/prod），`变更` 则是流经这些环境的短期对象。
- `变更.status` 本身应保持轻量，不承担环境推进全过程状态机。
- 后续需求、缺陷、任务等管理对象会与 `变更` 建立关联，但本轮 PRD 先不展开完整需求域模型。

## Open Questions

- 下一步进入领域对象字段、数据库草案、核心 API 与页面草图设计。

## Requirements (Evolving)

- 需要定义 DevOps 领域的核心对象模型，至少包括：应用、代码库、分支/变更、环境、部署流程、部署记录。
- 需要定义以“变更”为主线的研发生命周期。
- 需要区分“应用配置的长期结构”与“变更执行的临时实例”。
- 需要支持变更在多个环境中并存，而不是把变更强行限制为单一路径单一状态。
- 需要支持按需选择环境，不要求固定经过所有环境。
- 需要支持“主干基准分支 + 环境临时部署分支 + 变更功能分支”的分支模型。
- 需要支持环境级流水线，默认至少包括：`代码合并 -> 构建 -> 单元测试 -> 部署`。
- 需要为未来的流水线自定义编排保留模型扩展点。
- 需要支持环境级准入、人工卡点、审批、回滚和审计。
- 需要支持把“某环境当前部署了什么”表达清楚。
- 需要明确“变更、构建产物、部署记录、环境当前版本”之间的关系。
- 需要将“变更生命周期状态”与“变更在各环境中的落点/部署状态”分开建模。
- 需要明确生产发布后，`master` 更新如何影响其他环境下一次重建部署分支的行为。
- 需要定义环境流水线在“代码合并”阶段发生冲突时的失败与人工处理机制。
- 需要支持以环境为中心的操作界面，让“提交部署”成为用户可直接理解的动作。
- 需要把“环境运行快照 / 构建产物 / 部署记录 / 环境当前版本”串成一条可审计链路。

## Acceptance Criteria (Evolving)

- [ ] 形成一版清晰的 DevOps 领域对象定义及关系图草案
- [ ] 形成一版以“变更”为主线的生命周期描述
- [ ] 明确 MVP 范围内必须支持的环境推进与部署治理能力
- [ ] 明确首轮不做的能力边界，避免 PRD 膨胀

## Definition of Done (Team Quality Bar)

- PRD 可以指导后续领域建模和数据库设计
- 关键对象边界清晰，无明显概念冲突
- 关键流程可落为页面、API、状态机
- 需要调研的行业模式有出处和结论

## Out of Scope (Explicit, Current Round)

- 具体页面原型
- 具体数据库表结构
- 具体执行引擎选型
- 完整项目管理 / 需求管理模型
- 完整 CI 构建系统细节

## Technical Notes

- 当前仓库是后端脚手架，多模块 Spring Boot / Maven 工程。
- 当前任务处于 Trellis Phase 1（planning），目标是先沉淀 PRD。
- 外部模式调研已优先聚焦云效官方文档与 GitLab 官方文档，作为“变更中心”与“环境中心”两类设计参考。

## Research Notes

### 初步结论

1. 云效的高级版持续交付模型与用户思路高度接近：`应用` 是主容器，先配置代码源、部署编排、环境规划、研发流程，再通过“新建变更”推动一次完整交付。
2. GitLab 更偏“环境/部署中心”模型：环境是部署目标，部署记录追踪某个 ref/commit 到某环境的历史；它能很好解决“某环境当前跑的是什么”，但“变更”不是第一公民。
3. 对本项目更合适的方向，是以 `变更` 作为研发生命周期主对象，同时保留 GitLab 式环境部署历史、回滚、审批、环境保护能力。
4. `变更` 不适合承载线性“开发中/测试中/预发中”状态；这些事实应归属到“变更 x 环境”的关系层。

### 候选设计方向

#### 方向 A（推荐）
单应用 DevOps，`变更 = 应用内的一条交付主线 = 一个分支`

- 优点：模型最清晰，适合 MVP，易于做状态机、审批流和审计。
- 风险：后续做跨服务联动发布时需要再引入“发布批次 / 发布单 / 变更集”。
- 当前状态：已确认采用。

#### 方向 B
`变更` 允许跨多个应用，直接作为发布编排单元

- 优点：更贴近复杂企业级发布场景。
- 风险：MVP 复杂度明显上升，应用、仓库、环境、制品、审批链都会变复杂。

#### 方向 C
以 `部署` 或 `流水线运行` 为中心，再把“变更”作为附属字段

- 优点：实现更接近常见 CI/CD 工具。
- 风险：会回到 Jenkins 思路，和本产品差异化目标冲突。

## Current Modeling Direction

### 1. 变更状态保持轻量

当前更合理的 `变更.status` 候选是：

- `有效`
- `已发布`
- `废弃`

这里的 `变更` 更像业务对象本身的生命周期，而不是环境推进状态机。

其中 `已发布` 的判定口径已确认：

- `变更` 进入生产环境后，才算 `已发布`
- 测试环境、预发环境中的存在不构成“已发布”

### 2. 环境事实独立建模

真正复杂的状态应落在“变更 x 环境”的关系对象上，而不是 `change.status`：

- 该变更是否已部署到某环境
- 该环境当前是否包含该变更
- 该变更在该环境最后一次部署是否成功
- 该变更在该环境是否已通过审批/卡点

因此后续领域模型里，至少需要一个类似“变更环境记录 / 变更环境落点 / 变更环境实例”的对象。

### 3. 分支与部署模型

当前已经比较明确的分支策略如下：

- 应用只有一个全局主干基准分支：`master`
- 新建变更时，从 `master` 自动签出变更分支，例如 `featA`
- 环境并不长期维护自己的业务基准分支
- 每次环境部署时，系统临时创建或重建该环境的部署分支，例如：
  - `test-release`
  - `pre-release`
  - `prod-release`
- 该环境部署分支会基于最新 `master`，再合并当前挂载到该环境的全部变更分支
- 正式环境发布完成后，变更分支会合并回 `master`
- 后续所有环境再次部署时，重新从更新后的 `master` 生成部署分支，并重新合并各自当前挂载的变更分支

这意味着环境“当前包含哪些变更”不是靠长期环境分支本身保存，而是靠：

- 环境当前挂载的变更集合
- 最近一次成功部署生成的环境部署结果

### 4. 环境流水线模型

每个环境拥有自己的流水线。当前默认流程已确认是：

1. `代码合并`
2. `构建`
3. `单元测试`
4. `部署`

其中：

- `代码合并`：从最新 `master` 拉起环境部署分支，并合并该环境下挂载的全部变更分支
- `构建`：对合并后的环境部署分支生成可部署产物
- `单元测试`：对本次构建结果执行测试
- `部署`：将通过测试的构建结果部署到该环境

MVP 可先内置这条默认流程，但模型上需要支持后续自定义编排。

### 5. 合并冲突处理

当环境流水线在 `代码合并` 阶段发生冲突时：

- 本次环境流水线执行失败
- 系统负责识别并暴露冲突
- 冲突不由系统自动解决
- 由开发人员自行解决冲突

当前确认的两种解决路径：

1. 平台内提供代码合并操作页面
2. 签出到本地，由开发手工合并后再重新执行

### 6. 环境中心交互模型

当前已经明确的 UI 交互方向是“以环境为中心”：

- 应用详情页按环境切换 Tab
- 每个环境 Tab 展示该环境当前流水线
- 流水线下方展示该环境当前已纳入部署范围的变更列表
- 再下方展示“有效但尚未进入该环境的变更”

对用户来说，核心操作不是先理解“挂载”，而是直接点击某个变更的 `提交部署`。

在系统内部，这个动作等价于：

1. 将变更加入该环境
2. 触发该环境流水线执行

也就是说，领域上仍然存在“变更进入环境”的关系对象，但交互上可以把它与“触发部署”合并成一个动作。

需要特别明确的是：

- `提交部署` 并不是只对一个变更做增量部署
- 它会触发该环境当前挂载变更全集的一次完整流水线重跑
- 因此环境的真实部署对象始终是“当前环境变更集合”

### 7. 构建产物归属

`构建` 阶段产物的归属已确认采用：

- 产物归属 `环境本次流水线运行快照`
- 不归属单个变更

也就是说，一个产物表达的是：

- 某应用
- 某环境
- 某次运行
- 基于当时的 `master`
- 合并当时该环境挂载的变更集合

后续回滚、审计、环境差异对比，都应围绕“环境运行快照/产物”展开，而不是围绕单个变更产物展开。

### 8. 回滚模型

回滚单位已确认采用：

- 回滚到某次历史 `环境运行快照`
- 不对单个变更做“移除后重跑”式回滚作为核心模型

这意味着：

- 回滚对象与部署对象保持一致，都是“环境快照”
- 回滚记录、部署记录、产物记录可以共用同一条追踪链
- 环境历史可以按“成功运行快照”维度直接回退

## MVP Draft Summary

### 核心对象

- `应用`
- `代码库`
- `环境`
- `变更`
- `变更环境记录`
- `环境流水线`
- `环境流水线运行记录`
- `环境运行快照`
- `构建产物`
- `部署记录`

### 核心规则

1. 一个应用绑定一个代码库和一个主干基准分支 `master`
2. 一个变更只属于一个应用，并绑定一个从 `master` 签出的变更分支
3. 一个变更可以同时存在于多个环境中
4. 一个变更进入环境后，不是单独部署，而是参与该环境当前变更全集的完整重跑
5. 每个环境部署时，都会基于最新 `master` 重新创建环境部署分支，并合并该环境当前全部变更分支
6. 正式环境发布成功后，相关变更合回 `master`
7. 变更状态保持轻量：`有效 / 已发布 / 废弃`
8. 进入生产环境是 `已发布` 的唯一判定条件
9. 构建产物归属环境运行快照，不归属单个变更
10. 合并冲突发生在环境流水线第一步，由开发人工解决
11. 生产发布并合回 `master` 后，该变更在其他环境中的显式挂载记录自动清理
12. 正式环境发布必须经过人工审批或准入卡点
13. 预发环境支持配置审批或准入卡点，但默认不强制
14. 回滚单位是历史环境运行快照，而不是单个变更

### 核心用户动作

1. 新建变更
2. 开发在变更分支提交代码
3. 在某环境 Tab 中点击某个变更的 `提交部署`
4. 系统将该变更加入该环境
5. 系统触发该环境流水线：`代码合并 -> 构建 -> 单元测试 -> 部署`
6. 若是正式环境发布成功，则变更合回 `master`

## Domain Objects Draft

The first-pass domain object draft is recorded in:

- [`research/domain-objects.md`](./research/domain-objects.md)

This draft defines the MVP objects and fields for:

- `Application`
- `Environment`
- `Change`
- `ChangeEnvironmentRecord`
- `PipelineDefinition`
- `PipelineRun`
- `EnvironmentSnapshot`
- `Artifact`
- `DeploymentRecord`
- `ApprovalRecord`

### Immediate Modeling Takeaways

1. `Change` remains the business object, but environment-specific facts move into `ChangeEnvironmentRecord`.
2. `EnvironmentSnapshot` becomes the canonical unit for deployment, artifact ownership, and rollback.
3. `PipelineRun` is the execution record; `DeploymentRecord` is the deploy result; `ApprovalRecord` is the governance trace.
4. These DevOps entities are tenant business data and should default to `TenantBaseDO` when translated into DO classes.
5. The minimum MVP persistence set is now explicit enough to move into table and API design.

## Database Table Draft

The first-pass relational table draft is recorded in:

- [`research/database-tables.md`](./research/database-tables.md)

This draft makes the persistence layer more concrete:

- every MVP business table assumes `tenant_id`
- table prefix proposal is `dev_`
- each entity now has draft columns, status fields, and index suggestions
- snapshot/artifact/deploy/approval traceability has been translated into explicit tables

## API Draft

The first-pass API draft is recorded in:

- [`research/api-draft.md`](./research/api-draft.md)

This draft covers the agreed MVP workflow:

- application and environment management
- change creation and query
- environment console tab loading
- submit deployment
- pipeline run observation
- approval
- snapshot rollback

## Focused Entity Design

To reduce context complexity, the design is now being refined one domain entity at a time.

The first focused entity draft is:

- [`research/application-entity.md`](./research/application-entity.md)

This draft makes the `Application` entity concrete across:

- business meaning
- boundary
- field design
- table design
- validation
- API surface

Current naming decision for this entity:

- table name: `dev_application`
- DO class name: `ApplicationDO`
