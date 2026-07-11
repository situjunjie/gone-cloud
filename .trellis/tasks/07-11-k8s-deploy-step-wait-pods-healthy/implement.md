# K8s 部署步骤等待 Pod 全部健康实施计划

## 1. Preparation

- [ ] 加载 `trellis-before-dev` 和 backend/DevOps Pipeline 规范。
- [ ] 检查工作区和当前分支，确认无并行修改冲突。
- [ ] 运行现有 DeploymentOrderServiceImplTest、K8s handler 测试基线。

## 2. Manifest Contract

- [ ] 在 `KubernetesDeploymentManifestSupport` 增加 Service 列表提取和 selector 校验。
- [ ] 拒绝 ExternalName、无 selector、selector 不匹配 Deployment Pod Template label 的 Service。
- [ ] Service 存在且最终 desired replicas=0 时明确失败。
- [ ] 增加 Deployment-only、合法 Service、无 selector、ExternalName、selector 不匹配测试。

## 3. Health Evaluator

- [ ] 新增健康判定结果模型。
- [ ] 在状态采集器中实现本部署单目标 ReplicaSet 精确选择。
- [ ] 实现 desired Pod 数量、Pod Ready、terminating、普通容器和 init container 健康判定。
- [ ] 校验目标容器 Pod spec image。
- [ ] 实现每个 Service Endpoints ready address 覆盖全部目标 Pod UID/IP。
- [ ] 输出 services 和 health 实时摘要。

## 4. Deployment Execution

- [ ] RAW_MANIFEST apply 返回 Deployment + Service 列表。
- [ ] 调整 `waitRolloutReady` 使用完整健康门禁，而非只看 Deployment status。
- [ ] Deployment-only 跳过 Service 门禁。
- [ ] K8sImageUpgrade 复用 Deployment/Pod/Container 门禁。
- [ ] 保持取消、超时和可恢复异常行为。

## 5. Persisted Snapshot

- [ ] `DeploymentOrderResultContext` 增加 services/health 可选字段。
- [ ] 详情实时状态返回 services/health。
- [ ] 成功、失败、超时最终快照保存最后健康状态。
- [ ] 快照采集失败不覆盖原始部署错误。

## 6. Tests

- [ ] Deployment status ready 但目标 ReplicaSet 不存在时不成功。
- [ ] 旧 ReplicaSet Pod ready 不能满足门禁。
- [ ] Pod 数量不足、Pod Ready=False、container not ready、init container 未完成时不成功。
- [ ] Deployment-only 全健康时成功。
- [ ] Service 无 Endpoints、部分 Pod missing、全部 Pod covered 场景。
- [ ] K8sImageUpgrade 使用相同 Pod 门禁。
- [ ] 取消和超时保留最后健康诊断。

## 7. Validation

- [ ] 运行 DeploymentOrderServiceImplTest。
- [ ] 运行 KubernetesDeploymentStatusCollectorTest 和 KubernetesDeploymentManifestSupportTest。
- [ ] 运行 K8sDeployStepHandlerTest、K8sImageUpgradeStepHandlerTest、ApplicationServiceImplTest。
- [ ] 编译 DevOps server 及依赖。
- [ ] 使用 `trellis-check` 完成全范围质量检查。

建议命令：

```bash
mvn -pl yudao-module-devops/yudao-module-devops-server -am \
  -Dtest='DeploymentOrderServiceImplTest,KubernetesDeploymentStatusCollectorTest,KubernetesDeploymentManifestSupportTest,K8sDeployStepHandlerTest,K8sImageUpgradeStepHandlerTest,ApplicationServiceImplTest' \
  -Dsurefire.failIfNoSpecifiedTests=false test

mvn -pl yudao-module-devops/yudao-module-devops-server -am -DskipTests compile
```

## 8. Rollback

- 健康门禁实现集中在 collector/evaluator；出现兼容问题可回退完整门禁并恢复原 Deployment status 判定。
- Service Endpoint 门禁可独立回退，不影响 Deployment/Pod 精确健康判定。
- 无数据库迁移，回滚不需要数据修复。
