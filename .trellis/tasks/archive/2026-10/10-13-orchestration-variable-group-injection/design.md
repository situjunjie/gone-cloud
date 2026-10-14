# Technical Design

## Boundaries

- 只改 DevOps server 的部署链路：`service/deployment/DeploymentOrderServiceImpl`、`service/deployment/context/ContainerDeployConfigContext`、`framework/kubernetes/KubernetesDeploymentManifestSupport`（可选新增掩码工具）。
- 新增一个跨链路复用的敏感键判定工具（`framework` 层），让流水线 `PipelineExecutionEngine` 与部署展示层使用同一套规则。
- 不改 `VariableGroupService`、`ApplicationOrchestration*`、`KubernetesDeploymentStatusCollector`，不新增表/迁移。
- 不改前端与 DSL 参数结构（`with` 字段不变）。

## Data Model

- 无数据库变更。
- `ContainerDeployConfigContext` 新增一个**仅内存/快照**字段：
  - `private Map<String, String> variableGroupVariables;`
  - 用途：展示层按 key 判断是否需要掩码。值是创建部署单时从变量组读取的原始键值对。
  - 该字段会随 `config_json` 落库（不新增泄露面：`renderedManifestYaml` 本就含替换后的明文），但**必须**在 `displayConfig()` 的响应副本中置空。

## Contracts

- 变量来源：`VariableGroupService.getEffectiveVariables(Long appId, Long applicationEnvId)`；返回顺序已保证 `GLOBAL` → `APPLICATION` → 应用环境绑定，后者覆盖前者。
- 合并顺序（后者覆盖前者）：变量组 → 内置固定变量。即内置变量不可被变量组影子化。
- 生效范围：仅 `DeploymentModeEnum.ORCHESTRATION`。`RAW_MANIFEST` 只传内置变量。
- 解析语义：沿用 `KubernetesDeploymentManifestSupport.renderManifest` 的字面量 `String.replace("${KEY}", value)`，单遍替换，不递归。
- 组件渲染时机：`buildOrchestrationManifest()` 内对每个 enabled 组件渲染后再做依赖/kind/唯一性校验，`manifestYaml` 存原始模板，`renderedManifestYaml` 存真实值。
- 展示契约：
  - `displayConfig(config)` 先走既有 `redactSecrets`，再对 `renderedManifestYaml` 做敏感值掩码。掩码结果只用于展示，不再回送 YAML 解析器——`******` 以 `*` 开头会被 YAML 当作别名 token，先掩码后解析会导致 `redactSecrets` 解析失败并把内容降级为「已隐藏」。
  - `copy.setVariableGroupVariables(null)`。
  - `manifestYaml`（模板）不含值，保持现状。
  - 内部 `order.config_json` 不经过 `displayConfig`，继续保留明文。
- 敏感键判定：新增 `SensitiveKeyUtils.isSensitive(String key)`，规则与 `PipelineExecutionEngine.isSensitiveInputKey` 等价（小写后包含 `password`/`secret`/`token`/`credential`/`passwd`/`pwd`）；`PipelineExecutionEngine` 改为调用它，行为不变。
- 掩码规则：对敏感键且值非空，在渲染后的 YAML 文本中把该值字面量替换为 `******`；跳过空值。变量组解析器只允许单行值，替换不会破坏 YAML 结构。

## Runtime Flow

```
K8sDeployStepHandler
  -> DeploymentOrderServiceImpl.startContainerDeploy(run, step, userId)
     -> createDeploymentOrder
        -> buildConfig
           -> buildManifestConfig
              1. image = resolveImageExpression(...)                     // 不含变量组
              2. groupVariables = orchestration
                     ? variableGroupService.getEffectiveVariables(appId, applicationEnvId)
                     : Map.of()
              3. deployVariables = groupVariables + 内置变量（内置覆盖）
              4. orchestration
                     ? buildOrchestrationManifest(..., deployVariables)   // 每组件 renderManifest 真实值
                     : requiredParam(step, "manifestYaml")
              5. config.manifestYaml = 原始模板
                 config.renderedManifestYaml = renderManifest(template, deployVariables)
                 config.variableGroupVariables = groupVariables
        -> order.configJson = JSON(config)                                // 明文，供重试
        -> createOrGetNodeLog -> log.contextJson = JSON(displayConfig(config))  // 掩码
  -> executeDeployment(order) -> parseConfig(order) -> 提交集群（真实值）
```

展示层：`buildRespVO` → `displayConfig(parseConfig(order))` → 掩码后的 config 进入响应。

## Compatibility / Rollout

- 无变量组、或变量组无匹配键时：渲染结果与现状逐字节一致（`renderManifest` 对未命中键不做任何替换）。
- `RAW_MANIFEST`、`K8sImageUpgrade` 完全不受影响。
- `ContainerDeployConfigContext` 新增字段对旧快照兼容：反序列化缺失字段为 `null`，`displayConfig`/掩码逻辑需对 `null` 安全。
- 回滚：移除注入与掩码即可；无需数据回滚，历史快照仍可解析（未知字段被忽略）。
- 需要同步修订 `.trellis/spec/backend/devops-pipeline-guidelines.md` 的 `Scenario: K8sDeploy Application Orchestration` 契约（R4 由「不变」改为「内置 + 变量组，内置优先」），并补 10-12 归档 PRD 的变更说明（仅在实际编辑时；归档文件默认不改，改为在新任务 spec 中记录）。

## Tests

- `OrchestrationDeploymentTest`：
  - 变量组真实值进入集群资源；
  - 内置变量覆盖同名变量组键；
  - 敏感值不出现在 `getDeploymentOrder` JSON 与 `log.contextJson`，非敏感值可见；
  - `config_json` 保留真实值。
- `DeploymentOrderServiceImplTest`：RAW_MANIFEST 不注入变量组（占位符保留）；注入 `VariableGroupService` 后既有用例仍绿。
- `KubernetesDeploymentManifestSupportTest`：`renderManifest` 覆盖顺序与特殊字符。
- `SensitiveKeyUtilsTest`：敏感键判定边界。
- `OrchestrationBeanWiringTest`：把 `VariableGroupServiceImpl` 加入真实链，`allowCircularReferences=false` 下两种初始化顺序均可启动。

## Risks

| Risk | Mitigation |
|---|---|
| 非 Secret 资源中的变量值经 API/日志外泄 | 敏感键精确掩码 + `variableGroupVariables` 置空 |
| 掩码误伤普通值 | 仅按 key 语义掩码，不做值全量替换 |
| 变量组值破坏 YAML 结构 | 解析器保证单行；掩码替换为定长标量 |
| Bean 环 | 只依赖 `VariableGroupService`；Bean wiring 测试固化 |
| 旧快照反序列化 | 新字段可空，逻辑对 `null` 安全 |
