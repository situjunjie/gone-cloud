# K8sDeploy 应用编排支持变量组变量注入

## Goal

让 `K8sDeploy` 的 `ORCHESTRATION` 模式在渲染应用编排 YAML 时，除现有 8 个内置固定变量外，还能解析变量组定义的 `${KEY}`，且不破坏既有的 Secret 脱敏与重试快照语义。

## Background

- 变量组能力（`dev_variable_group`，作用域 GLOBAL / APPLICATION，应用环境经 `dev_application_env.variable_group_id` 绑定）已在 `.trellis/tasks/archive/2026-08/08-28-devops-variable-groups` 与 `08-29-application-env-variable-groups` 落地。
- 流水线构建已在 `PipelineExecutionEngine.buildSharedState()` 注入变量组，`PipelineVariableResolver` 支持 `${VAR}`。
- 应用编排（`.trellis/tasks/archive/2026-10/10-12-k8s-deploy-application-orchestration`）的 R4 当时明确「沿用固定部署变量替换，不增加变量组语法」，并把「变量组新语法」列为 Out of Scope。
- 现状：`DeploymentOrderServiceImpl.renderManifestYaml()` 只写入 8 个内置变量；全仓库仅 `PipelineExecutionEngine` 调用过 `VariableGroupService.getEffectiveVariables()`。
- 本任务是一次有意的契约变更，需要同步修订编排 PRD/规范中的 R4 表述。

## Requirements

- R1. `ORCHESTRATION` 模式下，编排组件 YAML 与最终合并 manifest 支持 `${KEY}` 引用变量组；变量来源为 `VariableGroupService.getEffectiveVariables(run.appId, run.applicationEnvId)`。
- R2. 优先级：内置固定变量（`IMAGE`、`IMAGE_URI`、`APP_KEY`、`COMMIT_SHA`、`BRANCH_NAME`、`PIPELINE_RUN_ID`、`ENV_KEY`、`NAMESPACE`）覆盖同名变量组键；变量组内部仍按 `GLOBAL` → `APPLICATION` → 应用环境绑定合并（由既有服务保证）。
- R3. `RAW_MANIFEST` 行为不变，不注入变量组。
- R4. 组件在依赖校验与 YAML 结构解析前完成渲染，使用真实值而非占位符。
- R5. 变量组在创建部署单时读取一次；重试复用 `config_json` 快照，不回读变量组或编排草稿。
- R6. 展示层（部署单 page/detail 的 `config`、流水线节点 `context_json`）对变量组中「敏感键」的值掩码；非敏感值保持可见。内部 `config_json` 快照保留渲染后真实值以支持重试。
- R7. `variableGroupVariables` 本身不得出现在任何 API 响应或日志 context 中。
- R8. 不引入 Bean 环：`DeploymentOrderServiceImpl` 只允许依赖 `VariableGroupService`（其仅依赖 Mapper），不得依赖 `ApplicationService`。
- R9. 不新增数据库表或字段迁移。

## Acceptance Criteria

- [ ] AC1 → R1：编排组件含 `${DB_HOST}` 且变量组提供 `DB_HOST` 时，提交到集群的资源为真实值。
- [ ] AC2 → R2：变量组存在与内置变量同名的 `IMAGE` 键时，目标镜像仍为步骤 `image`。
- [ ] AC3 → R3：`RAW_MANIFEST` 的 `${DB_HOST}` 保持占位符原样。
- [ ] AC4 → R6/R7：敏感键（key 含 `password`/`secret`/`token`/`credential`/`passwd`/`pwd`）的值不出现在 `getDeploymentOrder` JSON 与 `log.contextJson`；非敏感值仍可见。
- [ ] AC5 → R5：`order.config_json` 保留真实值；重试不重新读取变量组与编排草稿。
- [ ] AC6：`OrchestrationDeploymentTest`、`DeploymentOrderServiceImplTest`、`KubernetesDeploymentManifestSupportTest`、`OrchestrationBeanWiringTest` 全绿。
- [ ] AC7：`git diff --check` 通过。

## Out of Scope

- 变量值的递归解析（`${A}` 的 A 值再含 `${B}`）。
- `with.image` 表达式支持变量组。
- 变量加密、secret 类型、版本发布、审批、导入导出。
- 前端页面改动。
- `RAW_MANIFEST` 的变量组支持。

## Key Decisions

- 变量组只在 ORCHESTRATION 生效，先收敛风险面；RAW_MANIFEST 留待后续独立评估展示层脱敏后再放开。
- 内置变量优先，保持与流水线「系统保留变量不可被覆盖」一致。
- 脱敏按 key 语义精确掩码（而非按值全量替换），避免误伤 `prod`、`8080` 这类普通值。
- 内部快照继续保留明文以支持重试，与现有 Secret 处理方式一致（10-12 PRD R5/R7）。
