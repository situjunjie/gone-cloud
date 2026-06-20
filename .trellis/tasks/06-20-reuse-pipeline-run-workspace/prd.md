# reuse pipeline run workspace

## Goal

调整 DevOps 流水线运行工作目录策略，不再为每次 pipeline run 创建独立的 run workspace；改为在同一条应用环境流水线定义范围内复用固定的 run workspace，并在新 run 启动前清理该目录。

## Requirements

- `runWorkspace` 目录在同一条流水线定义范围内固定复用，不再包含 `runId` 维度。
- 启动新的 pipeline run 时，平台先递归清理已有 `runWorkspace`，再创建平台保留目录并准备源码工作区。
- `cacheWorkspace` 继续保留现有 definition 级别缓存语义，不能因清理 `runWorkspace` 而丢失依赖缓存。
- 改动需兼容现有 job runtime 挂载 `/workspace`、源码准备、job 级 `artifacts/reports/tmp` 目录约定。
- 不能放宽现有“同一应用环境流水线不能同时存在多个活跃 run”的约束；如已有保护逻辑，需保持兼容。
- 为工作目录服务补充聚焦测试，覆盖固定目录复用和启动前清理行为。

## Acceptance Criteria

- [ ] 同一条应用环境流水线连续两次运行时，`runWorkspace` 主路径保持不变。
- [ ] 第二次运行创建 workspace 前，会清空上一次运行留下的普通文件和 job 子目录，但不会清掉 definition 级缓存目录。
- [ ] 现有执行引擎与 runtime 创建逻辑无需感知新的路径策略，仍能拿到可用的 `/workspace` 挂载目录。
- [ ] 相关测试通过，证明复用目录与清理逻辑符合预期。

## Notes

- Keep `prd.md` focused on requirements, constraints, and acceptance criteria.
- Lightweight tasks can remain PRD-only.
- For complex tasks, add `design.md` for technical design and `implement.md` for execution planning before `task.py start`.
