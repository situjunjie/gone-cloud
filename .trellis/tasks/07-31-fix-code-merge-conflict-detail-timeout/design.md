# 技术设计：修复代码合并冲突详情超时

## 1. 边界与根因

本任务只修改 DevOps server 内的 Git 命令执行、Git 冲突内容读取和冲突详情服务校验。

当前执行顺序为：

```text
ProcessBuilder.start
  -> waitFor(300s)
  -> readAllBytes(stdout+stderr)
```

当 Git 输出大于 pipe 容量时，子进程无法退出，导致 `waitFor` 与输出写入互相等待。正确顺序必须让输出消费与进程等待并行。

## 2. GitCommandExecutor

### 2.1 RuntimeUtil 输出读取

1. 将带 `workDir` 的 Git 命令规范化为 `git -C <workDir> ...`。
2. 使用 Hutool 5.8.46 `RuntimeUtil.exec(String...)` 启动进程；该入口内部使用 `ProcessBuilder.redirectErrorStream(true)` 合并 stderr。
3. 在专用 Future/reader 中调用 `RuntimeUtil.getResult(process, UTF_8)`，从进程运行期开始读取直到 EOF，避免“先 wait 后读”的 pipe 互锁。
4. 本次按用户明确取舍不限制通用命令输出大小，完整字符串进入 `GitCommandResult` 或 `GitCommandException.output`。
5. 调用线程对读取 Future 保留 300 秒等待上限；超时或中断时终止进程及 descendants，并取消/收敛 reader。

不使用 `RuntimeUtil.exec(envp, dir, ...)`：该重载走 `Runtime.exec`，不会合并 stderr，只读取 stdout 仍可能被 stderr pipe 阻塞。当前实现类只执行 Git 命令，使用 `git -C` 保持工作目录语义。

### 2.2 输出契约

- 保留现有三参数 `execute(workDir, command, checkExitCode)`，删除临时引入的 `maxOutputBytes` 重载。
- 普通 Git 命令完整返回 UTF-8 输出，不再保留历史 4000 字符截断。
- API 边界仍必须对返回前端的 Git 错误做凭证掩码与长度限制；通用执行器的完整输出不得直接透传前端。

## 3. Git blob 与 working file 读取

- `GitWorkspaceServiceImpl#readBlob` 使用 `git cat-file blob <blobSha>` 完整读取 Git 对象，不再做 `cat-file -s` 大小预检。
- working file 在路径规范化与越界检查后使用 UTF-8 完整读取。
- base、ours、theirs、working 和 result 均不做应用级大小截断；`GitConflictContent.contentTooLarge` 为响应兼容固定返回 `false`。
- 取消 512 KiB 上限可能放大服务端堆内存、HTTP 响应体和浏览器编辑器压力，这是用户明确接受的阶段性取舍。

## 4. 冲突工作区有效性

### 4.1 状态校验

`PipelineExecutionServiceImpl#getCodeMergeConflictDetail` 在触碰文件系统前要求 CodeMerge 日志状态为 `WAITING_INPUT`。终态或其他状态抛 `PIPELINE_RUN_LOG_STATE_INVALID`。

### 4.2 现场校验

在 `GitWorkspaceService` 增加面向冲突读取的有效性检查，检查：

- workspace 路径存在且为目录；
- `.git` 存在；
- `git ls-files -u -- <filePath>` 仍包含未合并条目；
- descriptor 中非空的 base/ours/theirs blob 可被 Git 解析。

检查返回 false 时，service 抛 `PIPELINE_CODE_MERGE_WORKSPACE_NOT_EXISTS`。路径解析继续复用 external workspace 注册逻辑，不能把绝对路径返回给前端。

### 4.3 异常映射

- 明确失效现场：`PIPELINE_CODE_MERGE_WORKSPACE_NOT_EXISTS`。
- 通过有效性检查后发生的其他 `GitCommandException`：清理并限制 message/output 后转成 `PIPELINE_CODE_MERGE_GIT_EXEC_FAIL`。
- 继续复用现有 token 掩码规则或等价的统一清理逻辑，响应不得包含认证 URL 中的 token。

## 5. 兼容性与风险

- 不修改 Controller 路由或响应 VO 字段，前端只会观察到超时消失以及失效场景变成标准业务错误。
- `GitCommandExecutor` 是共享基础类，所有 Git 调用都会经过 RuntimeUtil 完整读取逻辑，因此必须用真实子进程测试大输出、退出码、超时/中断清理和 UTF-8 完整性。
- 通用命令输出与在线冲突内容均无大小上限，可能放大服务端堆内存、HTTP 响应体和浏览器编辑器压力，这是用户明确接受的阶段性风险。
- daemon reader 是每次命令一个短生命周期线程；当前 Git 并发规模可接受。Java 版本为 17，不引入虚拟线程或新的线程池依赖。
- base/ours/theirs 三个 Git blob 保持逐个读取，不并行进一步放大瞬时内存。

## 6. 回滚

- Executor 并发读取变更可独立回滚，不涉及数据迁移。
- Blob 读取与服务校验均为 server 内部实现，无数据库和 API schema 迁移。
- 若新的现场校验出现兼容问题，可单独回滚未合并索引/blob 校验，而保留 RuntimeUtil 并发读取的根因修复。
