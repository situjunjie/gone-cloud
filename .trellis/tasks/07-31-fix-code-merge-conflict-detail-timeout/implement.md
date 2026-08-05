# 实施计划：修复代码合并冲突详情超时

## 1. Git 命令执行器

- [x] 使用 Hutool `RuntimeUtil.exec/getResult` 在进程运行期间完整读取合并后的 stdout/stderr。
- [x] 用 `git -C <workDir>` 保持工作目录语义，删除通用 4000 字符限制和 `maxOutputBytes` 重载。
- [x] 保留正常退出、非零退出、300 秒超时、中断时的进程树与 reader 收敛处理。
- [x] 调整 `GitCommandExecutorTest`，以真实 Java 子进程输出至少 1 MiB，断言快速完成并完整返回。

## 2. 冲突内容读取

- [x] 使用 `git cat-file blob` 完整读取 blob，删除 `cat-file -s` 和 512 KiB 预检。
- [x] working file 完整读取，`GitConflictContent.contentTooLarge` 固定为 `false`。
- [x] 扩展 `GitWorkspaceServiceImplTest`：超过原 512 KiB 的 blob 与 working file 仍完整返回。

## 3. 状态、工作区与异常映射

- [x] 在 `GitWorkspaceService` 及实现中增加冲突现场有效性契约，验证目录、`.git`、未合并索引和 descriptor blob。
- [x] `getCodeMergeConflictDetail` 只接受 `WAITING_INPUT` 日志，并在读取前执行现场校验。
- [x] 将失效现场映射到 `PIPELINE_CODE_MERGE_WORKSPACE_NOT_EXISTS`，将其他有界清理后的 Git 异常映射到 `PIPELINE_CODE_MERGE_GIT_EXEC_FAIL`。
- [x] 扩展 `PipelineExecutionServiceImplTest`，覆盖有效、终态、工作区失效、Git 失败和 external workspace 注册顺序。

## 4. 验证与审查

- [x] 运行聚焦测试：
  `mvn -pl yudao-module-devops/yudao-module-devops-server -Dtest=GitCommandExecutorTest,GitWorkspaceServiceImplTest,PipelineExecutionServiceImplTest test`
- [x] 运行受影响模块编译：
  `mvn -pl yudao-module-devops/yudao-module-devops-server -am -DskipTests compile`
- [x] 运行 `git diff --check`。
- [x] 检查所有新增错误响应不包含 workspace 绝对路径、仓库 token 或未界定的大段 Git 输出。

## 风险文件与回滚点

- `framework/git/GitCommandExecutor.java`：共享执行基础类，先完成其测试再修改上层读取。
- `framework/git/GitWorkspaceService.java` / `GitWorkspaceServiceImpl.java`：接口和 blob 读取契约必须同步。
- `service/pipeline/execution/PipelineExecutionServiceImpl.java`：只修改冲突详情读路径，不扩大到保存、继续和重试语义。
- 每一层均可独立回滚；没有数据库迁移和前端兼容变更。
