# 流水线重构:从 Jenkins 切换到 SSH 本机/远程构建

## 背景与目标

现有流水线是「平台 + Jenkins」两段式架构。真正外包给 Jenkins 的只有构建类节点
(CHECKOUT / BUILD / DOCKER_BUILD_PUSH / EXECUTE_SHELL / SSH_PUBLISH);代码合并
(`GitWorkspaceServiceImpl`)、审批、容器部署本来就在平台侧。为这一段构建,引入了
Jenkinsfile 生成、REST 客户端、队列轮询、shared library、HTTP 回调 + token 校验、
Jenkins 侧凭据/工具管理 —— 全是为「跨进程异步」付的税,链路过长、不好用。

**目标**:把两段式塌缩成平台内的**单编排引擎**,构建节点通过 `BuildExecutor` 直接在
专用构建机(SSH)上跑 shell,平台节点继续进程内调现有 Service。砍掉整条 Jenkins 链路。

## 锁定的决策

1. **构建位置**:默认 SSH 到专用构建机,平台进程只做编排。`BuildHost` 统一抽象,
   `type=SSH` 为主路径,`type=LOCAL`(SSH-localhost / ProcessBuilder)作为退化特例。
   这样隔离了任意 shell 执行的安全风险和资源占用,远程委派天然成立。
2. **日志**:不新建日志表、不归档 OSS、暂不做实时滚动。构建 stdout/stderr 收进现有
   `dev_pipeline_run_log` 的节点级摘要 / `result_json`,沿用现有语义。
3. **迁移**:Jenkins 仍在开发期、未上生产、无业务依赖 → **直接替换**,Jenkins 代码
   同步删除,不背 feature-flag 双轨,不需要回退能力。

## 范围

### 新增
- `BuildHost` 实体 + 表 `dev_build_host`
- `BuildExecutor` 接口 + `SshBuildExecutor`(主) + `LocalBuildExecutor`(退化)
- `BuildHostSelector` —— 按 label/容量挑主机
- `StepScriptGenerator` —— 节点类型 → shell 片段(替代 `JenkinsfileGenerator`)
- `PipelineExecutionEngine` —— 进程内 DAG 编排器(替代 Jenkins REST + 回调流)
- 凭据存储 —— git token / registry / SSH key(扩展平台现有 access token 雏形)

### 删除(直接替换)
- `JenkinsfileGeneratorService(Impl)` + 对应测试
- `JenkinsPipelineClient(Impl)`、`JenkinsPipelineStartRequest`、`JenkinsProperties`
- `PipelineJenkinsCallbackService(Impl)` + Controller + VO + token 校验
- `PipelinePlatformNodeAdvanceService(Impl)` —— 职责并入新引擎
- shared library(`gone-devops-shared`)及 `goneDevopsCallback` 等引用
- DO 中的 Jenkins 字段(`jenkins_queue_id` / `jenkins_build_number`)→ 改为
  `build_host_id` 等;按需迁移

### 保持不动(复用)
- `GitWorkspaceServiceImpl` —— 本机合并、冲突解决、推送部署分支的**底层能力**,完全不动
  (但不再由引擎硬编码前置调用,而是由 `CodeMergeNodeHandler` 节点处理器调用)
- `DeploymentOrderService` —— 容器部署,不动
- `PipelineApprovalService` —— 审批触发 BPM,不动
- `dev_pipeline_run_log` —— 节点级日志语义不变
- `PipelineSpec` DSL —— 结构保持,仅执行后端从 Jenkins 换成 handler 链

## 目标架构:流水线即责任链

**核心设计**:整条流水线是一条**责任链**。代码合并、推送分支不再是引擎硬编码的前置步骤,
而是和构建、审批、部署一样,降格成链上的普通**节点(链上一环)**。引擎不再有「特殊前戏 +
通用节点」两套逻辑,只剩一种抽象:一串有序节点,逐环处理。

```
触发 → PipelineExecutionEngine(责任链驱动器):
        拓扑排序节点 → 有序节点链,从首个未完成节点起逐环解析 Handler 并执行
        ├─ CodeMergeNodeHandler   → GitWorkspaceService 合并+推送;冲突 → SUSPEND 等人工解决
        ├─ BuildNodeHandler       → StepScriptGenerator + BuildExecutor 在构建机执行,
        │                            同步阻塞、流式写 RunLog → CONTINUE
        ├─ ApprovalNodeHandler    → PipelineApprovalService 触发 BPM → SUSPEND 等审批回调
        └─ ContainerDeployHandler → DeploymentOrderService → CONTINUE
        每环 handle() 返回 CONTINUE / SUSPEND / FAIL:
          CONTINUE → 下一环
          SUSPEND  → 持久化位置并停止;外部事件(冲突解决 / 审批完成)重入引擎,
                     幂等跳过已完成节点,从挂起处续跑(沿用现有 advance 的幂等语义)
          FAIL     → run 置 FAILED
        全链 CONTINUE → run 置 SUCCESS
```

**责任链不可用同步嵌套实现**(经典 FilterChain 那样 handler 套 handler 跑在一个线程上):
审批可挂起数天、合并冲突要等人工,线程/事务不可能阻塞这么久,服务器重启链就断。
因此责任链落地为:**链 = 数据(拓扑排序后的有序节点),引擎 = 可重入驱动器**。
多数节点(构建)同步跑完即 CONTINUE;只有人工闸口(审批、合并冲突)才 SUSPEND,
靠持久化位置 + 幂等重入续跑 —— 而非阻塞线程。

两层模式划分清楚:
- **责任链(流水线本身)**:有序节点逐环流转,每环可 续跑 / 挂起 / 中止。代码合并只是第一环。
- **Handler 解析(策略/注册表)**:每环的具体行为由 `supports(nodeType)` 解析到对应 Handler。

对比现状:删掉了「生成 Jenkinsfile → REST 调 Jenkins → 进队列 → agent 执行 →
shared library 逐节点 HTTP 回调 → token 校验 → 找节点去重」整段;同时把「合并/推送」前置步骤
也统一进责任链,消除引擎内的特殊分支。

> 已知约束:责任链当前按拓扑排序线性执行(与现有 `advance` 一致,不支持并行分支)。
> 若未来需要并行分支,需扩展驱动器(fork/join),不在本次范围。

## 核心组件

### 1. `BuildExecutor`(方案支点)
```java
interface BuildExecutor {
    ExecResult exec(ExecContext ctx, String script, LogSink sink);
    void cancel(String runId);
}
// ExecContext: workingDir / env(注入凭据) / timeout
// LogSink:    行级回调,收进 RunLog 摘要
// ExecResult: exitCode / 错误信息
```
- `SshBuildExecutor`(主):Apache MINA SSHD client(比 JSch 活跃,推荐)。
  同一 run 的所有节点脚本在同一远程工作目录、同一会话内顺序执行,下游节点可见上游产物。
- `LocalBuildExecutor`(退化):`ProcessBuilder`,独立 reader 线程流式回填日志。
- `cancel`:销毁进程 / 关 SSH channel,支撑流水线取消。

### 2. `BuildHost` 实体 + 表 `dev_build_host`
```
type(LOCAL/SSH), host, port, username, credential_ref,
labels, max_concurrency, enabled
```
本机也建模成一条 `type=LOCAL` 记录。后期加远程机器 = 插一条 `type=SSH` + 凭据,引擎零改动。

### 3. `BuildHostSelector`
按 label / 容量挑主机。v1 可固定返回默认 SSH 构建机;远程多机调度逻辑后置。

### 4. `StepScriptGenerator`
节点类型 → shell 片段。现有 `JenkinsfileGeneratorServiceImpl` 里的 docker build/push、
checkout、maven/npm 构建逻辑大部分能平移成纯 shell(去掉 Jenkins DSL 包装)。

### 5. `PipelineNodeHandler` 责任链(执行核心)
```java
interface PipelineNodeHandler {
    boolean supports(String nodeType);          // 策略解析:此环归我处理吗
    NodeOutcome handle(PipelineNodeContext ctx); // 处理本环,返回链流转信号
}
// NodeOutcome: CONTINUE(下一环) / SUSPEND(挂起待外部事件) / FAIL(中止)
// PipelineNodeContext: run / 当前 node / 工作区 / 共享状态 / RunLog 写入口
```
链上 Handler:
- `CodeMergeNodeHandler` → 调 `GitWorkspaceService` 合并+推送;冲突 → SUSPEND
- `BuildNodeHandler` → 覆盖所有构建类节点,`StepScriptGenerator` 生成脚本 +
  `BuildExecutor` 在构建机执行,同步阻塞流式写 RunLog → CONTINUE/FAIL
- `ApprovalNodeHandler` → `PipelineApprovalService` 触发 BPM → SUSPEND
- `ContainerDeployNodeHandler` → `DeploymentOrderService` → CONTINUE/FAIL
- `MockNodeHandler` 等

复用现有 `PipelineNodeRuntimeSupport.mark*` 写 RunLog;`PipelineNodeRuntimeHandler`
(原 onStarted/onCompleted/onFailed,由 Jenkins 回调驱动)被这套主动执行的 Handler 取代。

### 6. `PipelineExecutionEngine`(责任链驱动器)
进程内异步 worker(线程池 / 复用现有 `PipelineCodeMergeAsyncService` 调度模式)。
拓扑排序节点成链,从首个未完成节点起逐环解析 Handler 执行,按 NodeOutcome 流转。
幂等跳过已成功节点(SUSPEND 重入时据此续跑)。并入原
`PipelinePlatformNodeAdvanceService` 职责。run 取消 → 对运行中节点调 `BuildExecutor.cancel`。

## 工作区与凭据

- **工作区**:平台本机合并后推送部署分支 → 构建机 CHECKOUT 节点 clone 该分支
  (与 Jenkins 原模型一致)。每个 run 在构建机上独立目录,节点脚本顺序复用同目录。
- **凭据**:registry 账号、SSH key、git token 以前在 Jenkins 管,现在平台需要一个
  **加密凭据表**,`BuildHost.credential_ref` / 节点参数引用,exec 时注入脚本 env。
- **工具**:JDK/Maven 版本依赖构建机预装(PATH);容器化构建后置。

## 风险

- **任意脚本执行面**:`EXECUTE_SHELL` 在构建机执行。SSH 到专用机已隔离平台进程,
  但构建机本身仍需收敛权限(独立账号、非 root、目录限制)。
- **凭据落地安全**:必须加密存储,日志/异常不得回显凭据值。
- **并发与资源**:`max_concurrency` 限流,避免构建机过载。

## 分期

- **P1**:`BuildExecutor` 抽象 + `SshBuildExecutor` + `StepScriptGenerator` + 引擎 +
  `BuildHost` 表 + 凭据存储。打通最小构建链路(CHECKOUT → BUILD → DOCKER_BUILD_PUSH)。
- **P2**:接平台节点(审批 / 容器部署)进引擎,跑通端到端;删除 Jenkins 全链路代码。
- **P3**:`BuildHostSelector` 多机调度、`LocalBuildExecutor`、并行节点、取消/重试完善。

## 验收

- 一条含 CHECKOUT / 构建 / Docker 推送 / 审批 / 容器部署的流水线,全程不经 Jenkins 跑通。
- 节点日志在 `dev_pipeline_run_log` 正常落地;失败节点有错误信息。
- 流水线取消能终止远程构建进程。
- 仓库中无 Jenkins 相关类、配置、shared library 引用残留。
