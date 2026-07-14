# Bug Analysis: K8s 历史探针事件污染成功部署结果

## 1. Root Cause Category

- **Category**: B - Cross-Layer Contract，兼有 E - Implicit Assumption
- **Specific Cause**: 状态采集器默认“关联资源存在 Warning/Unhealthy 事件”等价于“当前部署失败”，没有区分 Kubernetes 历史事件、当前健康门禁、可恢复诊断和最终执行结果。成功路径又原样持久化 collector 的 `failure`，最终形成 `SUCCESS + health.ready=true + failure=Unhealthy` 的跨层矛盾数据。

## 2. Why Fixes Failed

1. 前一轮健康门禁实现保留了旧的 `diagnoseFailure()` 调用顺序，只在后面增加 `evaluateHealthGate()`；健康判定变严格了，但没有重新定义诊断字段语义。
2. 原测试只覆盖当前 `ImagePullBackOff` 能生成诊断，没有覆盖“Warning 发生后恢复成功”的状态转换，因此历史事件污染成功快照未被发现。
3. 只修未来写入会遗漏部署单 101 这类已持久化结果，必须同时处理读取兼容。

## 3. Prevention Mechanisms

| Priority | Mechanism | Specific Action | Status |
|---|---|---|---|
| P0 | Architecture | 固定 `events/health/diagnosis/failure` 四层语义，成功时显式清理诊断和失败 | DONE |
| P0 | Test Coverage | 增加 recovered Warning、waiting diagnosis、terminal failure、timeout nesting 测试 | DONE |
| P1 | Compatibility | 读取 `SUCCESS + health.ready=true` 旧结果时移除矛盾字段 | DONE |
| P1 | Documentation | 更新 DevOps Pipeline 健康门禁 code-spec | DONE |
| P1 | Review Guide | 在跨层思考指南加入历史证据与当前结果分离检查 | DONE |

## 4. Systematic Expansion

- **Similar Issues**: 所有同时返回事件历史、实时状态和最终结果的接口都可能出现同类问题，尤其是任务执行、外部系统检查和异步工作流详情。
- **Design Improvement**: 当前 Map 快照保持兼容，但后续字段继续扩张时应考虑类型化内部模型，先在内部保证非法状态组合不可构造，再转换为 Map API。
- **Process Improvement**: 状态机类缺陷的回归测试必须覆盖状态转换，而不只是单点快照；至少包含“异常后恢复”和“异常持续到终态”两条路径。

## 5. Knowledge Capture

- [x] 更新 `.trellis/spec/backend/devops-pipeline-guidelines.md`
- [x] 更新 `.trellis/spec/guides/cross-layer-thinking-guide.md`
- [x] 增加任务级回归测试和本复盘记录
- [x] 确认项目不存在 `src/templates/markdown/spec/`，无需模板同步
