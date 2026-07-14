# 修正 K8s 启动期探针失败语义技术设计

## 1. Problem Statement

部署状态采集器当前把关联资源的历史 Warning 事件直接提升为顶层 `failure`。Kubernetes Event 是有时间跨度的历史记录，Readiness probe 在应用启动阶段发生 connection refused 后，即使 Pod 最终 Ready，`Unhealthy` 事件仍会保留一段时间。因此“历史事件存在”不能推出“当前部署失败”。本次需要把事件、当前健康、当前诊断和最终失败拆成四个稳定语义。

## 2. Result Contract

部署结果和实时状态使用以下字段：

```text
events      历史 Kubernetes 事件，包含已恢复的 Warning
health      当前健康门禁结果
diagnosis   当前仍未通过健康门禁时的主要可恢复诊断，可选
failure     最终执行失败或明确不可恢复终态，可选
```

状态组合约束：

| 场景 | health | diagnosis | failure |
|---|---|---|---|
| 正常启动中，无明确异常 | `ready=false, terminal=false` | 可为空 | 空 |
| 启动中 Readiness probe 失败 | `ready=false, terminal=false` | `Unhealthy` | 空 |
| 启动中 ImagePullBackOff | `ready=false, terminal=false` | `ImagePullBackOff` | 空 |
| 最终健康，但 events 有历史 Unhealthy | `ready=true` | 空 | 空 |
| rollout 超时 | `ready=false` | 具体 K8s 诊断 | `ROLLOUT_TIMEOUT`，嵌套同一诊断 |
| ProgressDeadlineExceeded | `ready=false, terminal=true` | Kubernetes 诊断 | 明确终态失败 |

`diagnosis` 是新增可选字段，不修改现有字段类型；`result_json.schemaVersion` 保持 `1.0`，无需数据库迁移。

## 3. Collector Data Flow

调整 `KubernetesDeploymentStatusCollector.collect()` 的执行顺序：

```text
collect Deployment / ReplicaSet / Pods / Services / Events
  -> evaluateHealthGate(...)
  -> read health.ready / health.terminal
  -> health.ready=true: 不生成 diagnosis/failure
  -> health.ready=false: diagnose current issue
       -> diagnosis = container state / relevant event / Deployment condition
       -> health.terminal=true 时才允许生成 collector-level failure
```

历史事件始终保留在 `events`。`diagnoseFailure()` 更名或调整为“诊断当前问题”的语义；只有健康门禁未通过时才调用，避免已恢复的事件污染成功快照。

事件优先级和容器状态优先级保持现状，防止本次修正改变现有排障顺序。`ProgressDeadlineExceeded` 继续作为明确终态优先处理。

## 4. Persisted Snapshot Flow

`DeploymentOrderResultContext` 增加：

```java
private Map<String, Object> diagnosis;
```

`DeploymentOrderServiceImpl.captureFinalSnapshotQuietly()`：

1. 将 collector 的 `diagnosis` 写入最终快照。
2. 失败路径存在 `primaryReason/primaryMessage` 时，主失败继续来自执行异常；collector diagnosis 作为 `failure.diagnosis`。
3. 明确 Kubernetes 终态且没有外层主异常时，可保留 collector 的 terminal failure。
4. 成功路径显式将 `failure` 和 `diagnosis` 清空，保证成功结果内部一致。

该设计保持现有超时错误为主错误，不把 `Unhealthy` 替换成最终失败原因：

```json
{
  "failure": {
    "reason": "ROLLOUT_TIMEOUT",
    "message": "...",
    "diagnosis": {
      "reason": "Unhealthy",
      "message": "Readiness probe failed: ..."
    }
  }
}
```

## 5. Legacy Read Compatibility

部署单 101 等旧数据已经把历史 `Unhealthy` 写入 `result_json.failure`。仅修写入逻辑不会修正旧接口响应，因此 `buildRespVO()` 在读取结果后做兼容归一化：

```text
deployStatus == SUCCESS
  and result.health.ready == true
    -> remove result.failure
    -> remove stale result.diagnosis（若存在）
```

归一化只影响接口响应，不批量修改数据库。失败部署单、健康字段缺失的旧部署单保持原样，降低误清理风险。

## 6. Compatibility and Consumers

- 新增 `diagnosis` 为可选字段，旧调用方可以忽略。
- `failure.reason/message/diagnosis` 的失败结果结构保持兼容。
- `events`、`health`、Pod/Container/Service 摘要不改变。
- K8sDeploy 与 K8sImageUpgrade 共用 collector，因此自动获得相同语义。
- 不改变 rollout 等待、取消、超时秒数和成功健康门禁。

## 7. Tests

### Collector

- 健康 Pod + 历史 `Unhealthy`：保留 events，不产生 diagnosis/failure。
- 未 Ready Pod + `Unhealthy`：产生 diagnosis，不产生 failure。
- `ProgressDeadlineExceeded`：health terminal 且保留终态 failure。
- 当前 ImagePullBackOff：保持现有优先诊断。

### Deployment Service

- 成功采集器即使返回旧式 failure，也要在最终持久化结果中清理，形成防御性保障。
- 超时失败使用 `ROLLOUT_TIMEOUT` 作为主失败并嵌套 diagnosis。
- 读取旧 `SUCCESS + health.ready=true + failure` 数据时响应移除 failure。
- 失败部署单读取时不移除 failure。

## 8. Rollout and Rollback

- 无 DDL、无批量数据修复，可直接随 DevOps 服务发布。
- 回滚时只需回退 collector、结果上下文和响应归一化代码。
- 若前端暂未消费 `diagnosis`，不影响原有 `events` 和 `health` 展示。

