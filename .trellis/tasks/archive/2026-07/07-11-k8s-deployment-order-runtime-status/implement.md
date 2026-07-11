# K8s 部署单真实执行状态实施计划

## 1. Preparation

- [x] 加载 `trellis-before-dev` 和 backend 必读规范。
- [x] 检查工作区状态，确认无用户未提交修改与目标文件冲突。
- [x] 固化现有 DeploymentOrderServiceImplTest 基线。

## 2. Persisted Result Contract

- [x] 新增部署结果上下文及资源 apply 结果模型，定义 schemaVersion 1.0。
- [x] 增加 resultJson 安全解析、创建、更新和持久化方法。
- [x] 修改 `updateStage`，仅合并阶段/摘要，不覆盖已有结果。
- [x] retry/重新执行开始时重置当前结果上下文；不建设历史 attempt。

## 3. Resource Apply Evidence

- [x] RAW_MANIFEST 每个 YAML 文档写 APPLYING/APPLIED/FAILED、operation、资源身份和错误。
- [x] K8sImageUpgrade 写 Deployment UPDATE 结果。
- [x] 给 Deployment metadata 和 Pod Template 写部署单关联标识。
- [x] 保存 applied Deployment UID、generation 和 revision 信息。

## 4. Runtime Status Collector

- [x] 实现 Deployment UID 校验和 rollout 摘要。
- [x] 通过 ownerReference 解析目标 ReplicaSet。
- [x] 通过部署单 label + ReplicaSet ownerReference 查询本次 Pod，并保留旧部署单兼容回退。
- [x] 完整映射 Pod conditions、init container、container state/lastState 和退出信息。
- [x] 查询相关 Kubernetes Event，完成排序、去重、截断和数量限制。
- [x] 汇总归一化失败诊断。

## 5. Detail and Final Snapshot

- [x] 调整详情 `liveStatus` 返回结构，同时保留旧 available/ready/pods 兼容字段。
- [x] 成功、apply 失败、rollout 超时、取消路径 best-effort 固化最终快照。
- [x] 确保采集异常不会覆盖原始 apply/rollout 错误。

## 6. Tests

- [x] 测试 Service 成功、Deployment 失败时的部分 apply 结果。
- [x] 测试阶段更新不覆盖 applyResults。
- [x] 测试 Pod Template 标识写入。
- [x] 测试只返回本部署单 ReplicaSet/Pod，排除旧 Pod。
- [x] 测试 ImagePullBackOff、terminated/lastState 字段和 Event 诊断。
- [x] 测试集群查询异常仍返回持久化结果。
- [x] 测试成功、超时和 apply 失败的最终快照。

## 7. Validation

- [x] 运行 DeploymentOrderServiceImplTest。
- [x] 运行 K8sDeploy/K8sImageUpgrade 和 manifest 支持相关测试。
- [x] 编译 `yudao-module-devops-server` 及依赖模块。
- [x] 运行 `trellis-check` 完成规范、数据流和回归检查。

建议命令：

```bash
mvn -pl yudao-module-devops/yudao-module-devops-server -am \
  -Dtest=DeploymentOrderServiceImplTest,KubernetesDeploymentManifestSupportTest \
  -Dsurefire.failIfNoSpecifiedTests=false test

mvn -pl yudao-module-devops/yudao-module-devops-server -am -DskipTests compile
```

## 8. Rollback Points

- 结果 JSON 引入 schemaVersion，旧数据解析必须容错；若兼容异常，可先回退 typed context，仅保留 Map merge。
- 实时 Event 查询若 Kubernetes Client API 兼容性不足，可保留 Pod/Container 实时状态，单独回退 Event 聚合。
- Pod 精确关联失败时不能静默返回全 selector Pod；仅旧部署单允许显式 LEGACY_SELECTOR 回退。
