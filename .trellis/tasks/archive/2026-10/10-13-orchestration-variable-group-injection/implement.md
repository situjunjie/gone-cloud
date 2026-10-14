# Implementation Plan

## Preconditions

- 阅读 `.trellis/spec/backend/index.md` 与 `.trellis/spec/backend/devops-pipeline-guidelines.md` 的 `Scenario: K8sDeploy Application Orchestration`。
- 确认基线测试绿：`mvn -q -pl yudao-module-devops/yudao-module-devops-server -am test -Dtest='OrchestrationDeploymentTest,DeploymentOrderServiceImplTest,KubernetesDeploymentManifestSupportTest,OrchestrationBeanWiringTest'`。

## Ordered Checklist

- [ ] 1. 新增 `SensitiveKeyUtils`（`framework/common/util` 或 devops framework 内），实现 `isSensitive(String key)`。
- [ ] 2. 让 `PipelineExecutionEngine.isSensitiveInputKey` 委托 `SensitiveKeyUtils.isSensitive`，保持行为不变。
- [ ] 3. `ContainerDeployConfigContext` 增加 `Map<String, String> variableGroupVariables`。
- [ ] 4. `DeploymentOrderServiceImpl`：
  - [ ] 4.1 注入 `VariableGroupService`。
  - [ ] 4.2 抽出 `buildDeployVariables(run, application, environment, image, namespace, groupVariables)`。
  - [ ] 4.3 `buildManifestConfig` 只在 ORCHESTRATION 读取变量组，算出 `deployVariables`，并 `config.setVariableGroupVariables(groupVariables)`。
  - [ ] 4.4 `buildOrchestrationManifest` 接受 `deployVariables`，组件渲染与合并 manifest 都走同一份变量，替换 `renderManifestYaml` 旧签名。
  - [ ] 4.5 `displayConfig`：敏感值掩码 + `copy.setVariableGroupVariables(null)`。
  - [ ] 4.6 删除/改造旧 `renderManifestYaml`，避免两套变量逻辑。
- [ ] 5. 测试：
  - [ ] 5.1 `SensitiveKeyUtilsTest`。
  - [ ] 5.2 `KubernetesDeploymentManifestSupportTest` renderManifest 优先级/特殊字符。
  - [ ] 5.3 `OrchestrationDeploymentTest` 新增 `@Mock VariableGroupService`，补 4 个用例（AC1/AC2/AC4/AC5）。
  - [ ] 5.4 `DeploymentOrderServiceImplTest` 补 RAW_MANIFEST 不注入用例；确认既有用例不因新依赖报错。
  - [ ] 5.5 `OrchestrationBeanWiringTest` 把 `VariableGroupServiceImpl` 加入 `SERVICES`。
- [ ] 6. 更新 `.trellis/spec/backend/devops-pipeline-guidelines.md` 编排场景契约与测试清单。

## Validation Commands

```bash
# 聚焦单测
mvn -q -pl yudao-module-devops/yudao-module-devops-server -am test \
  -Dtest='SensitiveKeyUtilsTest,KubernetesDeploymentManifestSupportTest,OrchestrationDeploymentTest,DeploymentOrderServiceImplTest,OrchestrationBeanWiringTest'

# 模块全量
mvn -q -pl yudao-module-devops/yudao-module-devops-server -am test

# 空白检查
git diff --check
```

## Review Gates

- Gate A（步骤 4 后）：确认 `renderManifestYaml` 只剩一处变量合并实现，RAW_MANIFEST 路径未读取变量组。
- Gate B（步骤 5 后）：AC1–AC6 全部有用例覆盖；旧用例全绿。
- Gate C（提交前）：`displayConfig` 响应副本中 `variableGroupVariables == null`，且敏感值不出现在响应文本。

## Rollback Points

- 步骤 4 引入行为变化；如需回滚，撤销 4.2–4.6 即可恢复内置变量渲染，`variableGroupVariables` 字段可保留（可空）。
- 步骤 2 的委托重构可独立回滚，不影响变量注入。
