# 技术设计:Jenkins → SSH 构建重构

> 配合 `prd.md` 阅读。本文把重构拆成可执行子任务,并标注现有代码锚点。

## 关键现有锚点(实现前必读)

| 现有资产 | 路径 | 重构中的作用 |
|---|---|---|
| `GitCommandExecutor` | `framework/git/GitCommandExecutor.java` | 本机 ProcessBuilder 执行器范本。`LocalBuildExecutor` 对齐其超时/`redirectErrorStream`/输出截断写法 |
| `EnvironmentConnector` + `Factory` | `framework/infra/` | **重要**:spec 已写明 "Kubernetes now and HOST/host-group later"。`BuildHost` 优先核对能否复用 Environment 的 HOST 模型,而非另造平行抽象 |
| `GitWorkspaceService` | `framework/git/GitWorkspaceServiceImpl.java` | 本机合并+推送部署分支,**不动**。构建机靠 CHECKOUT 节点 clone 部署分支 |
| `PipelineNodeRegistryServiceImpl` | `service/pipeline/` | 节点类型注册表。category 当前为 `JENKINS`/`PLATFORM`,需重新定义;参数 schema 里的 `agentLabel/toolJdk/toolMaven/registryCredentialsId/configName` 等 Jenkins 专有项要清理 |
| `JenkinsfileGeneratorServiceImpl` | `service/pipeline/` | 节点→脚本映射逻辑可平移:docker build/push、maven、npm 的命令拼装搬进 `StepScriptGenerator`,去掉 Jenkins DSL 外壳 |
| `PipelineNodeRuntimeHandler` 体系 | `service/pipeline/runtime/` | onStarted/onCompleted/onFailed + `PipelineNodeRuntimeSupport.mark*` 写 RunLog 的机制保留;触发源从「Jenkins 回调」改为「引擎直接调用」 |
| `PipelinePlatformNodeAdvanceServiceImpl` | `service/pipeline/execution/` | DAG 遍历 + 平台节点分发逻辑,作为新引擎的骨架基础 |

## 节点类型重新归类(责任链上的环)

整条流水线是一条责任链,每个节点是链上一环;`category` 决定该环由哪类 Handler 处理。
现有 `category` 二分是 `JENKINS` / `PLATFORM`,重构后:

- **PLATFORM 类**(进程内调 Service,在平台执行):
  - `CODE_MERGE`(**新增节点**)→ `CodeMergeNodeHandler` 调 `GitWorkspaceService`
    合并 + 推送部署分支;遇冲突 → SUSPEND 等人工解决。**取代原引擎硬编码的合并前置步骤。**
    它通常是链的第一环(由 DSL 拓扑序决定,不硬编码)。
  - `APPROVAL` → `ApprovalNodeHandler`,触发 BPM,SUSPEND 等审批回调
  - `CONTAINER_DEPLOY` → `ContainerDeployNodeHandler`,调 `DeploymentOrderService`
- **BUILD 类**(`BuildNodeHandler` 统一处理,在构建机 SSH 执行 shell):
  CHECKOUT / UNIT_TEST / BUILD_ARTIFACT / BUILD_IMAGE / MAVEN_BUILD_JAR / NPM_BUILD /
  DOCKER_BUILD_PUSH / ARTIFACT_UPLOAD / REPORT_ARTIFACTS / EXPORT_OFFLINE_IMAGE /
  EXECUTE_SHELL / MOCK —— 一个 `BuildNodeHandler` 覆盖全部(它们只是脚本不同),
  脚本由 `StepScriptGenerator` 按 nodeType 生成。避免造十个近乎雷同的 Handler。
- **SSH_PUBLISH**:语义变化最大。原本依赖 Jenkins SSH Publisher 插件 + `configName`。
  新模型下,「发布到远端」本就是 SSH 执行,可考虑合并进通用 BUILD 执行(传目标 host),
  或保留为「构建产物 scp 到另一台 host」的专用节点。**需在实现时定夺,先标记。**

> CODE_MERGE 节点的参数 schema:沿用现有合并流程所需(部署分支名、合并源 commit 等);
> 它能 SUSPEND(冲突)这点与 APPROVAL 同构,正好印证责任链「可挂起重入」是通用机制,
> 不是审批专属。

要清理的 Jenkins 专有 schema 字段:`agentLabel` / `toolJdk` / `toolMaven`(L275-279)、
`registryCredentialsId`(L123)、`ossCredentialsId`(L139)、`configName`(L149)。
凭据类字段改为引用平台凭据存储的 key。category 取值从 `JENKINS`/`PLATFORM`
改为 `BUILD`/`PLATFORM`(或等价划分)。

## 子任务拆解

### ST-1:`BuildExecutor` 抽象 + `LocalBuildExecutor`
- 定义 `BuildExecutor` 接口(`exec(ExecContext, script, LogSink)` / `cancel(runId)`)
- `ExecContext`:workingDir / env(注入凭据)/ timeout
- `LogSink`:行级回调,收进现有 `PipelineRunLogDO` 摘要/`result_json`(不新建表)
- `LocalBuildExecutor`:对齐 `GitCommandExecutor`,reader 线程流式回填
- 单测:成功/失败退出码、超时、cancel 终止进程

### ST-2:`SshBuildExecutor`(主路径,Apache MINA SSHD)
- 引入 `org.apache.sshd:sshd-core` 依赖(pom,锁定版本)
- 同一 run 的节点脚本在同一远程工作目录顺序执行(下游见上游产物)
- 密钥/密码认证;连接超时、命令超时、channel 关闭即 cancel
- exec stdout/stderr 流式回 LogSink
- 单测:可对 localhost sshd 或用 testcontainers/mock;失败优雅降级

### ST-3:`BuildHost` 模型 + 凭据存储
- **先核对** `EnvironmentConnector` 的 HOST 规划能否承载,再决定建 `dev_build_host` 表还是复用
- 字段:type(LOCAL/SSH)/ host / port / username / credential_ref / labels /
  max_concurrency / enabled
- 凭据加密存储(git token / registry / ssh key);日志与异常不得回显凭据值
- `BuildHostSelector`:v1 固定返回默认 SSH 构建机,预留 label/容量调度

### ST-4:`StepScriptGenerator`
- 替代 `JenkinsfileGeneratorServiceImpl`,节点参数 → 纯 shell 片段
- 平移现有 docker build/push、maven、npm、checkout 命令拼装
- 变量注入:REPO_URL / BRANCH_NAME / COMMIT_SHA / APP_KEY / IMAGE_TAG 等以 env 传入
- 单测:各节点类型生成的脚本快照(参考现有 `JenkinsfileGeneratorServiceImplTest` 改写)

### ST-5:`PipelineNodeHandler` 责任链 + `PipelineExecutionEngine` 驱动器
- **责任链接口**:`PipelineNodeHandler { boolean supports(nodeType); NodeOutcome handle(ctx); }`
  - `NodeOutcome`:`CONTINUE` / `SUSPEND` / `FAIL`
  - `PipelineNodeContext`:run / 当前 node / 工作区句柄 / 共享状态 / RunLog 写入口
- **链上 Handler**(每个一个 Spring bean,引擎按 `supports` 解析):
  - `CodeMergeNodeHandler` → 调 `GitWorkspaceService` 合并+推送;冲突 → SUSPEND
  - `BuildNodeHandler` → 覆盖所有 BUILD 类;`StepScriptGenerator` 生成脚本 →
    `BuildHostSelector` 选 host → `BuildExecutor.exec` 流式写 RunLog → CONTINUE/FAIL
  - `ApprovalNodeHandler` → `PipelineApprovalService` 触发 BPM → SUSPEND(现有 listener 唤醒)
  - `ContainerDeployNodeHandler` → `DeploymentOrderService` → CONTINUE/FAIL
  - `MockNodeHandler`
- **驱动器 `PipelineExecutionEngine`**:
  - 以 `PipelinePlatformNodeAdvanceServiceImpl` 的拓扑遍历为骨架改写成责任链驱动
  - 异步 worker(复用 `PipelineCodeMergeAsyncService` 调度模式 / 独立线程池)
  - 拓扑排序成链,从首个未完成节点起逐环 `handle`,按 `NodeOutcome` 流转
  - 幂等跳过已成功节点 —— SUSPEND 后由外部事件重入,据此续跑(**不阻塞线程等人工**)
  - run 取消 → 对运行中节点调 `BuildExecutor.cancel`
- 复用 `PipelineNodeRuntimeSupport.mark*` 写 RunLog
- 单测:链流转(CONTINUE/SUSPEND/FAIL 各路径)、幂等重入续跑、取消

### ST-6:删除 Jenkins 全链路
- 删 `framework/jenkins/*`、`service/pipeline/jenkins/*`、
  `JenkinsfileGeneratorService(Impl)`、`PipelineJenkinsToolService(Impl)`、
  `JenkinsPipelineNodeRuntimeHandler`(被主动执行的 `PipelineNodeHandler` 链取代)、
  `PipelinePlatformNodeAdvanceService(Impl)`(职责已并入驱动器)
- 删 `JenkinsProperties` 配置、回调 Controller/VO/token 校验、shared library 引用
- DO 字段:`jenkins_queue_id`/`jenkins_build_number` → `build_host_id` 等;按需 SQL 迁移
- 删/改 `JenkinsfileGeneratorServiceImplTest`
- 清理 `JENKINS_RUNNER_CONFIGURATION.md` 等文档

## 依赖顺序

```
ST-1 ──┐
ST-3 ──┼─→ ST-5 ──→ ST-6
ST-2 ──┘     ↑
ST-4 ────────┘
```
ST-1/ST-2/ST-3/ST-4 可并行起步;ST-5 依赖前四者;ST-6 最后(端到端验证通过再删 Jenkins)。

## 待实现时定夺的开放点

1. **BuildHost vs Environment**:复用现有 HOST 环境模型还是独立表 —— 核对 `EnvironmentConnector` 后定。
2. **SSH_PUBLISH 去留**:合并进通用 SSH 执行,还是保留为产物分发专用节点。
3. **凭据存储载体**:新建独立凭据表,还是挂在已有配置/连接模型上。
4. **引擎并发模型**:复用项目现有异步调度(`PipelineCodeMergeAsyncService` 的模式),还是独立线程池。
5. **SUSPEND 状态持久化**:挂起位置存哪 —— 复用 `PipelineRunDO` 现有状态字段 +
   节点级 RunLog 的 `WAITING_INPUT` 状态(现有合并冲突/审批已有类似语义),还是新增字段。
   核对现有 `advance` 的暂停/恢复实现后定。

## 已定的设计决策(不再开放)

- 流水线 = 责任链;代码合并降格为 `CODE_MERGE` 节点(链第一环),不再硬编码前置。
- 责任链 = 数据(拓扑排序节点)+ 可重入驱动器;**不用同步嵌套调用**(审批/冲突挂起数天)。
- BUILD 类节点由**单个** `BuildNodeHandler` 覆盖(脚本由 `StepScriptGenerator` 按 type 生成),
  不为每个 build 子类型造独立 Handler。
- 人工闸口(审批、合并冲突)走 SUSPEND + 幂等重入;构建节点同步阻塞跑完即 CONTINUE。
