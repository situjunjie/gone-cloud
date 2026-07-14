# 修正 K8s 启动期探针失败语义实施计划

## 1. Preparation

- [x] 加载 `trellis-before-dev` 及 backend 开发规范。
- [x] 检查工作区，确认仅包含本任务规划文件。
- [x] 运行 `KubernetesDeploymentStatusCollectorTest` 与 `DeploymentOrderServiceImplTest` 基线测试。

## 2. Result Model

- [x] 在 `DeploymentOrderResultContext` 增加可选 `diagnosis` 字段和字段说明。
- [x] 保持 `schemaVersion=1.0`，不新增数据库迁移。

## 3. Collector Semantics

- [x] 调整 `KubernetesDeploymentStatusCollector.collect()`：先完成健康门禁，再根据健康结果决定是否生成诊断。
- [x] 健康完成时只保留历史 `events`，不返回 `diagnosis` 或 `failure`。
- [x] 非终态未健康时将主要可恢复问题写入 `diagnosis`，不写入 `failure`。
- [x] 明确终态继续返回 terminal failure，保持 `ProgressDeadlineExceeded` 行为。
- [x] 保持容器状态和事件诊断优先级及消息截断规则。

## 4. Snapshot and API Compatibility

- [x] `captureFinalSnapshotQuietly()` 持久化可选 diagnosis。
- [x] 超时/执行失败时将 diagnosis 嵌入 `failure.diagnosis`，主失败原因保持外层异常原因。
- [x] `markSuccess()` 显式清理 failure 和 diagnosis。
- [x] `buildRespVO()` 对 `SUCCESS + result.health.ready=true` 的旧结果移除冲突的 failure/diagnosis。
- [x] 失败状态和缺少 health 的旧数据不做清理。

## 5. Tests

- [x] Collector：健康后历史 Unhealthy 不再产生 failure。
- [x] Collector：等待期 Unhealthy 返回 diagnosis，不提前失败。
- [x] Collector：ProgressDeadlineExceeded 仍为终态失败。
- [x] Service：成功最终快照清理旧式 failure/diagnosis。
- [x] Service：rollout 超时保留 `ROLLOUT_TIMEOUT + failure.diagnosis`。
- [x] Service：部署单 101 类型旧成功数据读取归一化。
- [x] Service：失败数据读取保持 failure。

## 6. Validation

- [x] 运行聚焦测试：

```bash
mvn -pl yudao-module-devops/yudao-module-devops-server -am \
  -Dtest='KubernetesDeploymentStatusCollectorTest,DeploymentOrderServiceImplTest,K8sDeployStepHandlerTest,K8sImageUpgradeStepHandlerTest' \
  -Dsurefire.failIfNoSpecifiedTests=false test
```

- [x] 编译 DevOps server 及依赖：

```bash
mvn -pl yudao-module-devops/yudao-module-devops-server -am -DskipTests compile
```

- [x] 使用 `trellis-check` 完成规范、数据流、回归和工作区检查。

## 7. Spec and Rollback

- [x] 使用 `trellis-update-spec` 将 `events/health/diagnosis/failure` 语义补充到 DevOps Pipeline 健康门禁规范。
- [x] 若出现兼容问题，整体回退 collector 诊断字段、快照写入和读取归一化；无数据库回滚步骤。
