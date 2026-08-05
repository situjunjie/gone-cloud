# 修复代码合并冲突详情超时

## Goal

修复 `GET /admin-api/devops/pipeline-run/{runId}/code-merge/conflict-detail` 在读取较大 Git 冲突 blob 时请求超时的问题，使有效冲突能够稳定返回，并让已失效的冲突工作区返回明确的业务错误，而不是暴露底层 `GitCommandException`。

## Background

- `PipelineExecutionServiceImpl#getCodeMergeConflictDetail` 从持久化的 CodeMerge `context_json` 读取冲突描述，然后调用 `GitWorkspaceService#readConflictContent`。
- `GitWorkspaceServiceImpl#readConflictContent` 会依次执行三次 Git 命令读取 base、ours、theirs blob，并读取 working file。
- `GitCommandExecutor` 当前先 `process.waitFor(300s)`，再读取合并后的 stdout/stderr。当子进程输出填满操作系统管道时，Git 等待父进程消费输出，父进程等待 Git 退出，形成阻塞；前端或网关通常早于 300 秒超时。
- 通用执行器原先把输出统一截断为 4000 字符，冲突内容原先还存在 512 KiB 在线上限；两者都会阻止当前“完整返回冲突内容”的产品决策落地。
- 冲突详情接口目前只检查 CodeMerge 日志是否存在，没有检查日志是否仍处于 `WAITING_INPUT`，也没有检查 external workspace、`.git` 和当前冲突现场是否仍有效。
- 模块已经定义 `PIPELINE_CODE_MERGE_WORKSPACE_NOT_EXISTS` 和 `PIPELINE_CODE_MERGE_GIT_EXEC_FAIL`，但冲突详情读取路径尚未使用。

## Requirements

### R1. Git 子进程使用 RuntimeUtil 完整读取输出

- 使用项目既有 Hutool 5.8.46 `RuntimeUtil.exec(...)` 启动 Git 进程，并通过 `RuntimeUtil.getResult(..., UTF_8)` 在进程运行期间读取合并后的 stdout/stderr。
- 通用 Git 命令输出暂不设置应用级大小上限，成功结果和失败诊断均保留完整输出；已知的大输出内存风险由本次产品决策接受。
- 工作目录语义通过 Git 原生 `git -C <workDir>` 保持，避免使用不会合并 stderr 的 `RuntimeUtil.exec(envp, dir, ...)` 重载。
- 保留 300 秒超时、线程中断处理和进程树清理，避免遗留进程或读取线程。
- 保持 `GitCommandResult`、退出码检查和 `GitCommandException` 调用契约兼容。

### R2. 冲突内容完整读取

- 普通 Git 命令返回完整输出，不再使用 4000 字符保存上限。
- 取消在线冲突内容 512 KiB 上限，完整返回 UTF-8 base、ours、theirs、working 和 result 内容。
- `contentTooLarge` 字段为兼容现有响应结构继续保留，本阶段固定返回 `false`。
- 大 blob、工作文件、HTTP 响应和浏览器编辑器的内存压力是用户明确接受的阶段性风险。

### R3. 失效冲突现场返回标准业务错误

- 冲突详情只允许读取仍处于 `WAITING_INPUT` 的 CodeMerge 日志；其他状态返回现有的流水线日志状态业务错误。
- 读取前检查持久化 workspace 路径、`.git` 仓库以及目标文件的未合并索引/所需 blob 是否仍可用。
- 工作区缺失、被重置、被后续运行复用或不再保留当前冲突现场时，返回 `PIPELINE_CODE_MERGE_WORKSPACE_NOT_EXISTS`。
- 其他真实 Git 执行失败应转换为 `PIPELINE_CODE_MERGE_GIT_EXEC_FAIL`，错误文本必须有界且不得包含凭证。
- Controller 继续返回项目统一的 `CommonResult`，不新增特殊异常响应结构。

### R4. 回归覆盖

- 为 `GitCommandExecutor` 增加真实子进程大输出测试，证明输出明显超过 pipe 缓冲区时可以完成并完整返回。
- 覆盖超过原 512 KiB 上限的 blob 和 working file 仍能完整返回。
- 覆盖有效 `WAITING_INPUT` 冲突、终态日志、缺失工作区和 Git 执行失败的服务行为与错误码。
- 保持现有 CodeMerge 冲突保存、继续、重试和 external workspace 注册测试通过。

## Acceptance Criteria

- [x] 产生至少 1 MiB stdout 的子进程能够在聚焦测试的短时限内完成，不发生 wait/read 管道死锁，并完整返回输出。
- [x] 超过原 512 KiB 上限的文本冲突 blob 和 working file 仍完整返回，`contentTooLarge=false`。
- [x] 非 `WAITING_INPUT` 的历史 CodeMerge 日志不能继续读取冲突详情。
- [x] 工作区、`.git`、未合并索引或必需 blob 已失效时，接口返回错误码 `1_011_006_018` 对应的标准业务错误。
- [x] 其他 Git 命令失败返回错误码 `1_011_006_017`，响应中不暴露 access token。
- [x] DevOps server 模块聚焦单测及受影响模块编译通过，`git diff --check` 无格式问题。

## Out of Scope

- 不修改前端页面、网关超时时间或 HTTP 客户端超时配置。
- 不改变 Pipeline workspace 的稳定路径模型、清理策略或多节点调度架构。
- 不把历史冲突内容归档到数据库或对象存储；工作区失效后的历史内容仍不可恢复，只返回明确错误。
