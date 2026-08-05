# Bug Analysis: CodeMerge 冲突详情大输出超时

## 1. Root Cause Category

- **Category**: E - Implicit Assumption；D - Test Coverage Gap
- **Specific Cause**: `GitCommandExecutor` 假设 Git 子进程可以先退出再读取 stdout/stderr，忽略了操作系统 pipe 容量有限。大 blob 填满 pipe 后，子进程等待父进程读取、父进程等待子进程退出。现有测试全部 mock `GitCommandExecutor`，没有真实大输出子进程覆盖。冲突元数据还隐含假设 persisted workspace 永远对应同一 Git index，未校验 stage blob 是否被后续运行替换。

## 2. Why Fixes Failed

1. **初始“工作区不存在”诊断**：可以解释 `GitCommandException`，但目录不存在会快速失败，无法解释 HTTP 长时间超时；用户补充超时现象后才形成区分性证据。
2. **只调整输出限制或 HTTP 超时**：不能解除 pipe 互锁；最终产品决策是完整返回输出，因此必须先解决并发消费，再明确接受无应用级大小上限的内存风险。
3. **在请求线程中直接同步调用 Hutool `RuntimeUtil.getResult`**：虽然能持续消费输出，但没有保留调用方可控的超时与中断收敛；带工作目录的重载也不合并 stderr，仍可能在 stderr pipe 上阻塞。最终方案使用 `RuntimeUtil.exec(String...)` 合并输出，并在 Future reader 中完整读取。

## 3. Prevention Mechanisms

| Priority | Mechanism | Specific Action | Status |
|---|---|---|---|
| P0 | Architecture | 子进程启动后通过 RuntimeUtil 并发排空合并输出并完整返回 | DONE |
| P0 | Runtime | 超时/中断时有界收敛 reader，并终止进程及 descendants | DONE |
| P0 | Test Coverage | 真实 Java 子进程输出 1 MiB，验证不死锁和完整保留 | DONE |
| P0 | Test Coverage | 中断调用线程，验证 interrupt flag 与子进程终止 | DONE |
| P1 | Domain Validation | persisted base/ours/theirs SHA 必须匹配当前 Git index stages | DONE |
| P1 | Documentation | 在 DevOps Pipeline spec 固化签名、错误矩阵、好坏案例和测试要求 | DONE |

## 4. Systematic Expansion

- **Similar Issues**: 仓库内另一处直接使用 `ProcessBuilder` 的 `LocalBuildExecutor` 已经用独立 reader 线程流式消费输出，没有发现同类“先 wait 后读”实现；其注释已同步为新的 Git 执行器契约。
- **Design Improvement**: 所有未来本机子进程封装都必须把“输出消费、明确的内存策略、超时、进程树清理、线程中断”视为一个不可拆分的生命周期契约。本场景按产品决策完整保留通用 Git 输出，只在 API 错误边界做掩码和长度限制。
- **Process Improvement**: 涉及外部进程时，mock 测试之外至少增加一个真实子进程的大输出或长运行测试；HTTP 超时要先区分快速启动失败、子进程阻塞和存储 I/O 变慢。

## 5. Knowledge Capture

- [x] 更新 `.trellis/spec/backend/devops-pipeline-guidelines.md`，加入完整的 Git 命令输出与 CodeMerge 冲突详情场景。
- [x] 更新 `LocalBuildExecutor` 注释，移除对 Git 执行器 `readAllBytes` 的过期描述。
- [x] 项目没有 `src/templates/markdown/spec/` 镜像目录，因此无需同步模板。
- [x] 本任务无需另建后续 issue；根因、现场失效和测试缺口均已在当前变更闭环。
