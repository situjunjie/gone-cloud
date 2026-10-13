# Implement: CodeMerge 工作区跨运行缓存 + partial clone

## 变更清单

| 文件 | 变更 |
|---|---|
| `framework/pipeline/runtime/PipelineWorkspaceConstants.java` | 新增 `WORKSPACE_SUB_PATH_KEY = "workspaceSubPath"` |
| `framework/pipeline/runtime/LocalPipelineWorkspaceService.java` | `createWorkspace` 写入稳定 `workspaceSubPath`；`resetRunWorkspace` 改为 `resetRunWorkspaceContents`（保留 `.git`） |
| `framework/pipeline/runtime/K8sPipelineJobRuntimeManager.java` | `prepareWorkspaceVolume` 用 `resolveWorkspaceSubPath` 读取稳定 subPath，缺失回退 `runs/{runId}` |
| `framework/git/GitWorkspaceServiceImpl.java` | 新增 `FETCH_BLOB_NONE_FILTER`，三处 fetch（baseline / deploy / item）加 `--filter=blob:none` |
| `.trellis/spec/backend/devops-pipeline-guidelines.md` | 新增稳定挂载点、`.git` 保留、`--filter=blob:none`、**禁止 shallow clone** 四条契约与测试要求 |

## 未做（含原因）

- **未改 `--depth=1`**：实测三种变体全部 `refusing to merge unrelated histories`。
- **未改 `finishCodeMerge` 的 `cleanup()`**：对 external/remote 工作区只 `unregister`，不删目录，是 no-op。
  先前「cleanup 删掉工作区」的判断有误，已在 design.md 中更正。
- **未做 PVC 上历史 `runs/{runId}` 目录清理**：超出本任务范围，需另行处理（当前 38 个目录 / 7.5 GB）。

## 验证

1. `mvn -o -pl yudao-module-devops/yudao-module-devops-server -am -DskipTests compile` — 通过。
2. 定向测试 `GitWorkspaceServiceImplTest,LocalPipelineWorkspaceServiceTest,K8sPipelineJobRuntimeManagerTest` — 39/39 通过。
3. 全模块 `mvn -o -pl yudao-module-devops/yudao-module-devops-server test` — **498/498 通过**。
4. 集群实测（同一 2 核 Pod、同 PVC、逐条复现 23 条 git 命令）：

| 方案 | fetch | checkout | 合计 | `.git` |
|---|---|---|---|---|
| 改前 全量 fetch | 32.0 s | 1.0 s | 35.0 s | 194.6 MB |
| `--filter=blob:none` | 15.0 s | 4.0 s | 20.0 s | 47.6 MB |
| 跨运行复用（对象库已存在） | 1.0 s | 1.0 s | 2.0 s | — |

## 踩坑记录

- 首次跑定向测试有 4 个失败：1 个是我在 `when(...)` 桩里漏了 `"origin"` 参数（生产命令 5 个元素、桩写成 4 个）；
  2 个是我新加的 K8s 测试忘了调 `readyPodResource()`；
  1 个是我对 `resetRunWorkspace` 断言过严（要求重建 `artifacts/`，而旧实现也不重建，引擎会在 reset 后重新 `createWorkspace`）。
- 全模块测试曾出现大批 `NoSuchFieldError: ORCHESTRATION`：**本地 `~/.m2` 中 `yudao-module-devops-api` jar 陈旧**，
  HEAD 的 orchestration 提交未 `install`。已在干净 worktree 上复现同一编译失败以确证与本改动无关，
  随后 `mvn -o -pl yudao-module-devops/yudao-module-devops-api -am -DskipTests install` 后 498 全绿。
- `GitCommandExecutorTest` 在全量并发下偶发 `NumberFormatException`（进程中断时序），单独跑 3/3 通过，属既有 flaky。
