# 优化 CodeMerge 代码合并耗时（跨运行仓库缓存 + partial clone）

## Goal

消除 `合并变更分支`（CodeMerge）步骤的重复全量克隆开销。目标：同一 app-env-definition 的后续运行不再重新下载整个仓库历史。

## 背景（实测数据）

run 1078 的 `code_merge_job` 耗时 48,649 ms，拆解：

| 阶段 | 耗时 |
|---|---|
| 创建 K8s Pod → Ready | 13.1 s |
| Pod 内 git 操作 | ≈ 35.5 s |

Pod 内 23 条 git 命令逐条复现（同 namespace、同 PVC、同 2 核配额）合计 41.97 s，其中：

| 命令 | 耗时 |
|---|---|
| `git fetch origin master-jdk25`（全量，190.94 MiB / 320,709 objects） | **33.4 s** |
| 其余 22 条 | 8.6 s |

根因：

1. `GitWorkspaceServiceImpl.prepareWorkspaceInPlace` 的 `git fetch origin <baseBranch>` 无 `--depth` / `--filter`，每次拉全量历史。
2. K8s 的 PVC subPath 是 per-run 的 `runs/{runId}`，容器 `/workspace` 每次都是全新空目录 → 仓库无法跨运行复用（PVC 上已堆积 38 个 run 目录、7.5 GB）。
3. Pod 的 2 核 CPU limit 使 `index-pack` 多线程受限：同一 fetch 无限流 15.66 s、2 核 33.43 s（cgroup `nr_throttled 248 / nr_periods 407`）。

## Requirements

- R1：CodeMerge 的 Git 工作区必须按 `tenantId + appId + applicationEnvId + definitionId` 稳定复用，同一 app-env-definition 的后续运行复用已下载的对象库。
- R2：复用不得让上一轮的未跟踪文件（`target/`、`node_modules` 等）污染下一轮构建；工作树每轮必须干净。
- R3：CodeMerge 的 fetch 使用 `--filter=blob:none` 部分克隆，减少首次冷启动传输量。
- R4：不得使用 `--depth=1` / shallow clone（见 Constraints）。
- R5：`resetRunWorkspace` 必须保留 `.git` 目录，否则 Docker runtime 下稳定工作区的缓存会被清掉。
- R6：保留原有冲突流程语义（`ls-files -u`、`cat-file blob`、`continueMerge`）。

## Constraints

- **禁止 shallow clone**：实测 `--depth=1` 在 CodeMerge 的三种用法（仅 baseline / 仅 item / 两者）下全部失败：
  `fatal: refusing to merge unrelated histories`。原因是 `git merge --no-ff --no-commit <sha>` 需要共同祖先与完整侧分支，shallow 边界截断历史后 merge-base 不可用。该结论同时否定了 item 分支 shallow fetch，而 item 分支 merge 正是在线冲突流程的入口。
  （注意：源码 checkout 路径 `GitlabPipelineSourceWorkspacePreparer` / `GitCheckoutScript` 使用 `--depth 1` 是**安全**的，因为它只取单分支 tip、不做 merge。两者不可混为一谈。）
- 同一 app-env-definition 串行执行（`CodeMergeService.validateNoOtherActiveRun` 已保证），因此稳定工作区无需额外并发锁。
- 不得把凭证（token）写入日志、`contextJson`、`resultJson`。
- PVC 上的历史 `runs/{runId}` 目录不做自动迁移/清理（超出本任务范围）。

## Acceptance Criteria

- [x] AC1：K8s 下容器 `/workspace` 的 PVC subPath 对同一 app-env-definition 稳定（不再是 `runs/{runId}`）。
- [x] AC2：`resetRunWorkspace` 保留 `.git`，清空其余工作树内容。
- [x] AC3：CodeMerge 的 baseline / deploy / item fetch 带 `--filter=blob:none`。
- [x] AC4：`mvn -pl yudao-module-devops/yudao-module-devops-server test` 通过（498/498）。
- [x] AC5：spec 更新并记录「CodeMerge 禁用 shallow clone」这一禁止项及其原因。

## Notes

- 预期收益：冷启 35 s → 20 s；热启（对象库已存在）→ ~2 s。
- 首次运行仍需完整传输一次历史；收益随运行次数累积。
