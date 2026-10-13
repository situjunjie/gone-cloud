# Design: CodeMerge 工作区跨运行缓存 + partial clone

## 1. 现状与问题定位

### 1.1 两条工作区路径

| Runtime | 平台侧路径 | 容器内 `/workspace` |
|---|---|---|
| DOCKER | `runRoot/workspace`（稳定） | bind `runWorkspace`（稳定） |
| KUBERNETES | `runRoot/workspace`（稳定） | PVC subPath **`runs/{runId}`（per-run）** |

`runRoot = {workspaceRoot}/tenants/tenant-{t}/apps/app-{a}/envs/app-env-{e}/pipelines/definition-{d}`

### 1.2 为什么没有复用

- **Docker**：路径本身稳定，但 `LocalPipelineWorkspaceService.resetRunWorkspace` 在首次执行时 `clearPath(runWorkspace)` 会连 `.git` 一起删掉。
- **K8s**：`K8sPipelineJobRuntimeManager.prepareWorkspaceVolume` 用 `"runs/" + run.getId()` 作为 subPath，每次运行都是全新空目录；`prepareWorkspaceInPlace` 检测不到 `.git` 就 `git init` + 全量 fetch。

> 更正：`CodeMergeService.finishCodeMerge` 末尾的 `gitWorkspaceService.cleanup(...)` **不会删除**稳定工作区。
> `cleanup` 对 `externalWorkspaces` 中的 key 只做 `remove` +（remote 时）`unregister` 后 `return`；
> 只有 `workspace == null` 的 legacy managed 路径（`run-{id}-{uuid}`）才会真正删目录。故本任务不改这里。

## 2. 设计

### 2.1 稳定 PVC subPath（R1 / AC1）

由拥有路径方案的 `LocalPipelineWorkspaceService` 计算并写入 `PipelineWorkspace.metadata`，
`K8sPipelineJobRuntimeManager` 只负责挂载：

```
workspaces/tenants/tenant-{t}/apps/app-{a}/envs/app-env-{e}/pipelines/definition-{d}
```

- key 常量 `PipelineWorkspaceConstants.WORKSPACE_SUB_PATH_KEY = "workspaceSubPath"`。
- K8s manager 读取该 key；缺失时回退 `runs/{runId}`（保持旧行为，避免影响未走 `createWorkspace` 的调用方）。
- subPath 含 `definitionId`，因此同一 app-env 下不同 definition 仍互相隔离。
- 同 app-env-definition 由 `validateNoOtherActiveRun` 保证串行，无需新增锁。

### 2.2 reset 保留 `.git`（R2 / R5 / AC2）

`resetRunWorkspace` 从「清空整个目录」改为「清空除 `.git` 外的所有顶层条目」：

```
list(runWorkspace) -> for each entry != ".git": clearPath(entry)
Files.createDirectories(runWorkspace)
```

- 保留 `.git` → 对象库跨运行复用（Docker 路径生效）。
- 清空其余内容 → 工作树干净，避免 `target/`、`node_modules` 等未跟踪产物污染下一轮（满足 R2）。
- 不使用 `git clean -ffdx`：它会删掉 `artifacts/ reports/ tmp/ jobs/ .devops/` 这些平台目录。
- 工作树由 `prepareWorkspaceInPlace` 的 `merge --abort` + `reset --hard` + `checkout -B` 重建。

### 2.3 partial clone（R3 / AC3）

在 CodeMerge 工作区准备的三处 fetch 加 `--filter=blob:none`：

- `prepareWorkspaceInPlace`：baseline fetch、deploy branch fetch
- `resolveRemoteBranchCommit`：item branch fetch（只影响传输内容，不影响返回值；commit/tree 对象仍会获取，满足 spec 第 70 行「本地需有该 commit 对象」的约束）

blob 在 checkout / merge 需要时由 promisor remote 惰性获取；`RuntimeGitWorkspaceSupport` 已为每条远端命令注入 `GIT_ASKPASS` 与 `credential.helper=''`，因此惰性获取有凭证可用。

服务端不支持 filter 时 git 仅告警并退化为全量 fetch，不报错。

### 2.4 明确不做

- 不改 `--depth=1`（R4）：实测三种变体全部 `refusing to merge unrelated histories`。
- 不改 `finishCodeMerge` 的 `cleanup()`：对稳定工作区是 no-op。
- 不迁移/清理 PVC 上历史 `runs/{runId}` 目录。

## 3. 实测对照（同一 2 核 Pod、同 PVC）

| 方案 | fetch | checkout | 合计 | `.git` |
|---|---|---|---|---|
| 现状 全量 fetch | 32.0 s | 1.0 s | 35.0 s | 194.6 MB |
| `--filter=blob:none` | 15.0 s | 4.0 s | 20.0 s | 47.6 MB |
| 跨运行复用（仓库已存在） | 1.0 s | 1.0 s | 2.0 s | — |

## 4. 影响面

| 文件 | 变更 |
|---|---|
| `PipelineWorkspaceConstants` | 新增 `WORKSPACE_SUB_PATH_KEY` |
| `LocalPipelineWorkspaceService` | 写入 `workspaceSubPath`；`resetRunWorkspace` 保留 `.git` |
| `K8sPipelineJobRuntimeManager` | `prepareWorkspaceVolume` 读取稳定 subPath |
| `GitWorkspaceServiceImpl` | 三处 fetch 加 `--filter=blob:none` |

## 5. 风险

- **磁盘**：稳定工作区按 app-env-definition 累积（部分克隆约 47.6 MB/个），不再随 runId 无限增长；PVC 上旧的 `runs/{runId}` 目录需另行清理。
- **惰性获取**：partial clone 在 checkout/merge 时会访问远端，要求该步骤执行时凭证仍有效（与原全量 fetch 一致）。
- **首次运行**：仍需完整传输一次，收益随运行次数累积。
