# DevOps 流水线 Docker 临时构建环境设计方案

本文档定义流水线 YAML 中 `runsOn` 的执行语义，以及后端如何基于 docker-java 为每个流水线任务创建临时 Docker 构建环境。

## 1. 背景与目标

当前流水线配置已经调整为 YAML 结构：

```yaml
sources:
  main_repo:
    type: gitlab
    endpoint: https://git.example.com/team/demo-service.git
    branch: master
stages:
  build_stage:
    name: 构建
    jobs:
      maven_build_job:
        name: Maven 构建
        runsOn:
          group: local-docker/default
          container: maven:3.9.9-eclipse-temurin-17
        steps:
          build_step:
            name: 执行构建
            step: Command
            with:
              run: mvn -B clean package -DskipTests
```

其中 `runsOn` 不只是展示字段，而是流水线任务的执行环境声明：

- `runsOn.group`：执行资源池。第一期支持本机 Docker 执行池，例如 `local-docker/default`。
- `runsOn.container`：构建容器镜像。

设计目标：

1. 每个 `job` 创建一个临时 Docker 容器作为构建环境。
2. 同一个 `job` 下的多个 `steps` 复用同一个容器，保证源码、缓存、构建产物和临时文件上下文连续。
3. `job` 结束后自动销毁容器，避免构建环境污染宿主机。
4. Docker SDK 初始化集中在 `framework/docker`，业务执行逻辑不直接散落 docker-java 初始化代码。
5. 支持后续扩展远程 Docker、多资源池、多租户隔离和资源限制。

## 2. 核心设计结论

容器生命周期按 `job` 管理，不按 `step` 管理。

推荐执行模型：

```text
PipelineRun
  -> 解析所有 stage/job 为全局 job DAG
    -> 无 needs 的 job 默认并行
    -> 有 needs 的 job 等依赖成功后执行
      -> 创建 job 级 Docker 临时容器
      -> 执行 job.steps
      -> 收集日志、报告、构建物
      -> 停止并删除 job 容器
```

不建议每个 `step` 单独创建容器，因为这样会导致：

- Maven、npm 等缓存无法复用。
- `SetupMavenSettings` 写入的配置无法被后续构建步骤读取。
- `Command` 生成的构建产物无法被 `ArtifactUpload` 读取。
- 容器启动和销毁成本过高。

## 3. YAML 语义

`runsOn` 挂在 `job` 上，表示该任务的执行环境。

```yaml
stages:
  java_build_stage:
    name: Java 构建
    jobs:
      java_build_job:
        name: Maven 构建上传
        needs:
          - java_test_job
        runsOn:
          group: local-docker/default
          container: maven:3.9.9-eclipse-temurin-17
        timeoutSeconds: 1800
        retryTimes: 0
        failStrategy: failFast
        steps:
          setup_maven_settings_step:
            name: 配置 Maven Settings
            step: SetupMavenSettings
            with:
              mavenSettingXmlPath: /root/.m2/settings.xml
          build_command_step:
            name: Maven 打包
            step: Command
            with:
              shellType: bash
              run: mvn -B clean package -DskipTests
          upload_artifact_step:
            name: 构建物上传
            step: ArtifactUpload
            with:
              filePath:
                - target/
              version: "${BUILD_NUMBER}"
```

语义规则：

- `runsOn.container` 必填，作为 Docker 镜像名。
- `runsOn.group` 必填，第一期仅支持 `local-docker/default` 或配置中心中存在的本机 Docker 资源池。
- `job.needs` 非必填，表示当前 job 依赖的其他 job。未配置时，该 job 在依赖条件满足后可并行执行。
- `needs` 支持跨阶段依赖，填写被依赖任务的 `jobId`。
- 为避免跨阶段引用歧义，`jobId` 必须在整条流水线内全局唯一。
- `job.timeoutSeconds` 是该 job 的默认超时。
- `step.timeoutSeconds` 优先级高于 `job.timeoutSeconds`。
- `step.with` 仍由具体步骤处理器解释。

## 4. 执行引擎改造

当前执行模型已经能将 YAML 展开为 `PipelineSpec.ExecutableStep`。为了正确表达 `job` 级容器生命周期和 `needs` 依赖，执行引擎需要从纯扁平步骤执行升级为 job DAG 调度。

建议新增执行视图：

```java
class ExecutableStage {
    private String stageId;
    private String stageName;
    private List<ExecutableJob> jobs;
}

class ExecutableJob {
    private String jobId;
    private String jobName;
    private String stageId;
    private String stageName;
    private PipelineSpec.RunsOn runsOn;
    private List<String> needs;
    private Integer timeoutSeconds;
    private Integer retryTimes;
    private String failStrategy;
    private List<PipelineSpec.ExecutableStep> steps;
}
```

`PipelineSpec.Job` 增加字段：

```java
private Object needs;
```

或者直接定义为：

```java
private List<String> needs = new ArrayList<>();
```

如果为了兼容 YAML 里 `needs: test_job` 的写法，需要自定义反序列化或 setter，将字符串和数组统一规范化为 `List<String>`。

`PipelineSpec#toExecutableSteps()` 可以保留，用于内部 YAML flattening、日志写入和 focused tests；执行引擎内部优先使用 `toExecutableGraph()` 或等价的 job DAG 结构。前端 read model 直接返回 stage/job/step，不再返回旧图模型字段。

调度伪代码：

```java
JobGraph graph = pipelineSpec.toExecutableGraph();
validateAcyclic(graph);

while (!graph.isTerminal()) {
    List<ExecutableJob> readyJobs = graph.findReadyJobs();
    runJobsInParallel(readyJobs, job -> {
        BuildRuntime runtime = null;
        try {
            for (PipelineSpec.ExecutableStep step : job.getSteps()) {
                PipelineStepHandler handler = stepHandlerRegistry.resolve(step.getStep());
                if (handler.runtimeRequirement() == StepRuntimeRequirement.JOB_RUNTIME) {
                    runtime = buildRuntimeManager.getOrCreate(run, job);
                }
                PipelineStepContext ctx = PipelineStepContext.builder()
                        .run(run)
                        .stageId(job.getStageId())
                        .stageName(job.getStageName())
                        .job(job)
                        .step(step)
                        .runtime(runtime)
                        .sharedState(sharedState)
                        .userId(userId)
                        .build();
                StepResult result = handler.handle(ctx);
                if (result.getType() != StepResultType.CONTINUE) {
                    handleResult(result);
                    return;
                }
            }
            graph.markSuccess(job);
        } catch (Exception ex) {
            graph.markFailed(job);
            graph.markDependentsSkipped(job);
        } finally {
            buildRuntimeManager.destroyIfExists(runtime);
        }
    });
}
```

### 4.1 needs 依赖语义

`needs` 是 job 级依赖关系：

```yaml
stages:
  test_stage:
    name: 测试
    jobs:
      test_job:
        name: 测试任务
        runsOn:
          group: local-docker/default
          container: maven:3.9.9-eclipse-temurin-17
        steps:
          test_step:
            name: 单元测试
            step: Command
            with:
              run: mvn test
  build_stage:
    name: 构建
    jobs:
      build_job:
        name: 构建任务
        needs:
          - test_job
        runsOn:
          group: local-docker/default
          container: maven:3.9.9-eclipse-temurin-17
        steps:
          build_step:
            name: 打包
            step: Command
            with:
              run: mvn package -DskipTests
```

约束：

- `needs` 可写字符串或字符串数组，解析后统一为 `List<String>`。
- `needs` 中每个值必须指向已存在的 `jobId`。
- `jobId` 必须全局唯一，不能只在 stage 内唯一。
- 不能依赖自己。
- 不能出现循环依赖，例如 `A -> B -> C -> A`。
- 被依赖 job 失败或取消时，依赖它的 job 标记为 `SKIPPED` 或不调度执行。第一期推荐新增 `SKIPPED` 状态，便于前端明确展示“因依赖失败跳过”。
- 无 `needs` 的 job 在 pipeline 开始后即可进入 ready 队列，并受资源池并发限制执行。
- stage 只作为 UI 分组和默认展示顺序，不再隐式表达跨 stage 的串行屏障。需要先后关系时必须显式写 `needs`。

第一期并发策略：

- 使用全局 job DAG 调度。
- 同一批 ready jobs 默认并行。
- 实际并发数受 `runsOn.group` 资源池并发限制控制。
- 同一个 job 内的 steps 仍然串行执行，并复用同一个 job runtime。

示例 DAG：

```yaml
stages:
  check_stage:
    name: 检查
    jobs:
      unit_test_job:
        name: 单元测试
        runsOn:
          group: local-docker/default
          container: maven:3.9.9-eclipse-temurin-17
        steps:
          unit_test_step:
            name: 执行单元测试
            step: Command
            with:
              run: mvn test
      code_scan_job:
        name: 代码扫描
        runsOn:
          group: local-docker/default
          container: maven:3.9.9-eclipse-temurin-17
        steps:
          scan_step:
            name: 执行扫描
            step: JavaP3CScan
            with: {}
  build_stage:
    name: 构建
    jobs:
      package_job:
        name: 打包
        needs:
          - unit_test_job
          - code_scan_job
        runsOn:
          group: local-docker/default
          container: maven:3.9.9-eclipse-temurin-17
        steps:
          package_step:
            name: Maven 打包
            step: Command
            with:
              run: mvn package -DskipTests
```

上述示例中：

- `unit_test_job` 和 `code_scan_job` 没有 `needs`，可以并行执行。
- `package_job` 跨阶段依赖两个检查任务，必须等待它们都成功后才能执行。
- `build_stage` 不会因为声明在 `check_stage` 后面而天然等待；真正的等待关系来自 `needs`。

## 5. Job 状态表与状态机

支持 `needs` 和并行 job 后，需要引入 job 级运行状态表。`dev_pipeline_run_log` 继续保存 step/event 明细，`dev_pipeline_run_job` 负责表达 DAG 调度中的 job 状态、依赖关系和运行时实例。

### 5.1 Job 状态

job 状态建议固定为：

| 状态 | 含义 |
|---|---|
| `PENDING` | 未开始。依赖未满足、等待资源池调度，或尚未进入执行队列 |
| `RUNNING` | 进行中。job 正在执行，可能正在创建容器、执行 step、收集结果 |
| `BLOCKED` | 阻塞中。job 内 step 等待外部事件，例如审批、代码冲突处理 |
| `SUCCESS` | 已完成。job 内所有 step 成功 |
| `FAILED` | 失败。job 内 step 失败、容器创建失败、镜像拉取失败、超时等 |
| `SKIPPED` | 已跳过。上游依赖失败或取消，当前 job 不再执行 |
| `CANCELED` | 已取消。用户主动取消 pipeline 或当前 job |

`BLOCKED` 是 job 级状态；step log 可以继续使用现有 `WAITING_INPUT` 表达具体哪个 step 在等待输入。映射关系：

```text
step WAITING_INPUT -> job BLOCKED -> pipeline RUNNING 或 WAITING_INPUT
```

### 5.2 dev_pipeline_run_job 表

建议新增表：

```sql
CREATE TABLE dev_pipeline_run_job (
  id bigint NOT NULL AUTO_INCREMENT COMMENT '流水线运行任务编号',
  pipeline_run_id bigint NOT NULL COMMENT '流水线运行编号',
  stage_id varchar(128) DEFAULT NULL COMMENT '阶段编号',
  stage_name varchar(128) DEFAULT NULL COMMENT '阶段名称',
  job_id varchar(128) NOT NULL COMMENT '任务编号',
  job_name varchar(128) NOT NULL COMMENT '任务名称',
  status varchar(32) NOT NULL COMMENT '状态（PENDING RUNNING BLOCKED SUCCESS FAILED SKIPPED CANCELED）',
  needs_json text COMMENT '依赖任务编号 JSON 数组',
  attempt int NOT NULL DEFAULT 1 COMMENT '当前执行次数',
  sort int NOT NULL DEFAULT 0 COMMENT '展示排序',
  started_at datetime DEFAULT NULL COMMENT '开始时间',
  finished_at datetime DEFAULT NULL COMMENT '结束时间',
  duration_millis bigint DEFAULT NULL COMMENT '执行耗时，毫秒',
  runtime_type varchar(32) DEFAULT NULL COMMENT '运行时类型（PLATFORM LOCAL DOCKER）',
  executor_group varchar(128) DEFAULT NULL COMMENT '执行资源池',
  executor_image varchar(255) DEFAULT NULL COMMENT '执行容器镜像',
  runtime_id varchar(128) DEFAULT NULL COMMENT '运行时实例编号，例如容器编号',
  runtime_name varchar(128) DEFAULT NULL COMMENT '运行时实例名称，例如容器名称',
  workspace_path varchar(500) DEFAULT NULL COMMENT '工作目录路径',
  summary varchar(500) DEFAULT NULL COMMENT '摘要',
  error_message varchar(2000) DEFAULT NULL COMMENT '错误信息',
  creator varchar(64) DEFAULT '' COMMENT '创建者',
  create_time datetime NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
  updater varchar(64) DEFAULT '' COMMENT '更新者',
  update_time datetime NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
  deleted bit(1) NOT NULL DEFAULT b'0' COMMENT '是否删除',
  tenant_id bigint NOT NULL DEFAULT 0 COMMENT '租户编号',
  PRIMARY KEY (id),
  UNIQUE KEY uk_tenant_run_job (tenant_id, pipeline_run_id, job_id),
  KEY idx_tenant_run_status (tenant_id, pipeline_run_id, status),
  KEY idx_tenant_run_stage (tenant_id, pipeline_run_id, stage_id, sort)
) COMMENT='DevOps 流水线运行任务表';
```

说明：

- `needs_json` 保存规范化后的依赖 jobId 列表，用于运行详情展示和重建 DAG。
- `runtime_*` 字段保存 job 级容器或平台运行时信息。step log 可以记录更细的执行结果，但容器生命周期归 job 所有。
- `uk_tenant_run_job` 保证同一次 run 内 jobId 唯一。

### 5.3 状态流转

基础流转：

```text
PENDING -> RUNNING -> SUCCESS
PENDING -> RUNNING -> FAILED
PENDING -> SKIPPED
PENDING -> CANCELED
RUNNING -> BLOCKED
BLOCKED -> RUNNING
BLOCKED -> SUCCESS
BLOCKED -> FAILED
RUNNING -> CANCELED
BLOCKED -> CANCELED
```

调度规则：

- 创建 run 时，为 YAML 中每个 job 初始化一条 `PENDING` 记录。
- 所有 `needs` 都为 `SUCCESS` 的 `PENDING` job 进入 ready 队列。
- ready job 获取资源池执行许可后变为 `RUNNING`。
- job 内 step 返回 `SUSPEND` 时，job 变为 `BLOCKED`，释放 job runtime。
- 外部事件恢复后，job 从 `BLOCKED` 回到 `RUNNING`，从挂起 step 继续或按 handler 幂等逻辑跳过已完成 step。
- job 失败或取消后，所有直接或间接依赖它且尚未执行的 job 标记为 `SKIPPED`。
- pipeline 完成条件：所有 job 都进入终态 `SUCCESS/FAILED/SKIPPED/CANCELED`。

第一期服务重启策略：

- 应用启动时扫描 `RUNNING` job。
- 如果关联容器不存在或无法可靠接管，标记 job `FAILED`，写明“服务重启导致执行中断”，并清理可能遗留的容器。
- `BLOCKED` job 可以保留，等待外部事件恢复。

### 5.4 PipelineRun 聚合状态

`dev_pipeline_run` 是整次流水线运行的聚合状态，`dev_pipeline_run_job` 是 DAG 调度状态，`dev_pipeline_run_log` 是 step/event 明细。三者状态不要互相替代。

建议聚合规则：

| 条件 | PipelineRun 状态 |
|---|---|
| 存在 `RUNNING` job | `RUNNING` |
| 不存在 `RUNNING`，但存在 `BLOCKED` job | `WAITING_INPUT` |
| 所有 job 都为 `SUCCESS` | `SUCCESS` |
| 任一 job 为 `FAILED`，且已完成依赖跳过传播 | `FAILED` |
| 用户取消 run，所有运行中或阻塞 job 都完成取消处理 | `CANCELED` |
| 所有 job 都是 `SUCCESS` 或 `SKIPPED`，且至少一个 job 是 `SKIPPED` | `FAILED` |

说明：

- `SKIPPED` 表示依赖失败导致未执行，不等于成功；只要出现 `SKIPPED`，最终 run 不应显示为成功。
- `BLOCKED` 不代表失败，通常是等待审批、冲突处理或人工输入。
- `WAITING_INPUT` 可以继续作为 run/log 层对外兼容状态；job 层统一使用 `BLOCKED`。

### 5.5 重试、恢复和取消

重试语义第一期建议固定为 job 级重试，不做单 step 局部重试：

- 重试失败 job 时，`attempt + 1`，重新准备 workspace，重新创建 runtime，从 job 的第一个 step 开始执行。
- 重试不复用上一次失败容器，避免脏环境影响结果。
- 重试不新建 `pipeline_run`，但新的 step log 需要记录新的 `attempt`。
- 已经 `SUCCESS` 的上游依赖不重新执行。
- 依赖当前 job 且此前被标记为 `SKIPPED` 的下游 job，在当前 job 重试成功后可以重置为 `PENDING`，由 DAG 调度重新发现。

恢复语义只针对 `BLOCKED` job：

- 外部事件完成后，例如审批通过或冲突解决，事件服务只更新对应 step log 和 job 状态，然后唤醒执行引擎继续调度；事件服务不直接执行下游 job。
- 恢复时不默认重放整个 job；由挂起 step 的 handler 根据已有 log 判断是 `CONTINUE`、`SUSPEND` 还是 `FAIL`。
- `BLOCKED` 期间不得持有资源池许可，也不得长期保留 Docker runtime。

取消语义：

- 取消 run 时，所有 `PENDING` job 标记为 `CANCELED`。
- 所有 `BLOCKED` job 标记为 `CANCELED`，并调用对应平台步骤的取消钩子，例如取消审批流程。
- 所有 `RUNNING` job 先中断当前命令，再销毁 runtime，最后标记为 `CANCELED`。
- 已经 `SUCCESS/FAILED/SKIPPED/CANCELED` 的 job 不再改写状态，除非属于同一次取消命令的幂等重放。

### 5.6 DAG 调度边界

DAG 调度指的是把所有 job 作为图节点，把 `needs` 作为有向边，然后只调度依赖已经成功的 job。它解决的问题不是“按 YAML 顺序跑”，而是“按依赖关系发现哪些 job 可以并行，哪些必须等待”。

推荐调度边界：

1. 创建 run 时解析 YAML，生成 `ExecutableJob` 列表和依赖图。
2. 校验 jobId 全局唯一、依赖存在、无自依赖、无循环。
3. 初始化 `dev_pipeline_run_job`，全部为 `PENDING`。
4. 调度循环只扫描 `PENDING` job：
   - 所有依赖为 `SUCCESS`：尝试获取资源池许可。
   - 任一依赖为 `FAILED/CANCELED/SKIPPED`：标记为 `SKIPPED`。
   - 依赖仍为 `PENDING/RUNNING/BLOCKED`：保持 `PENDING`。
5. 获取许可成功后，job 变为 `RUNNING` 并开始执行 steps。
6. job 进入终态或 `BLOCKED` 后，释放资源池许可并再次触发调度。

调度实现要注意事务边界：

- job 状态更新需要使用带当前状态条件的更新，避免并发调度重复启动同一个 job。
- 从 `PENDING` 抢占到 `RUNNING` 应该是原子操作。
- 多条 `PipelineRun` 并发执行是第一版能力；后端服务单副本时可用 JVM 线程池和 `Semaphore` 控制本机资源。
- 后端服务多副本部署时，必须把 job 抢占和资源池许可迁移到 DB/Redis 等共享协调机制。

## 6. Step Handler 扩展点

YAML 重构后，流水线的最小可执行单元是 `step`，不再是旧图模型里的 node。因此执行扩展点应从 `PipelineNodeHandler` 迁移为 `PipelineStepHandler`。

`PipelineStepHandler` 不是简单改名，它要表达新的执行边界：

- handler 处理一个 YAML `step`。
- handler 可以读取所属 `stage/job/runsOn`。
- handler 可以使用 job 级 `BuildRuntime`。
- handler 返回执行流转信号，由执行引擎决定继续、挂起或失败。

建议接口：

```java
public interface PipelineStepHandler {

    /**
     * 是否支持当前 step 类型，例如 Command、APPROVAL、JavaP3CScan。
     */
    boolean supports(String stepType);

    /**
     * 当前 step 是否需要 job 运行时。
     *
     * PLATFORM：平台控制步骤，不进入 Docker 容器。
     * JOB_RUNTIME：使用 runsOn 创建出来的 job 运行时，通常是 Docker 容器。
     */
    StepRuntimeRequirement runtimeRequirement();

    /**
     * 执行当前 YAML step。
     */
    StepResult handle(PipelineStepContext ctx);

}
```

运行时需求枚举：

```java
public enum StepRuntimeRequirement {
    PLATFORM,
    JOB_RUNTIME
}
```

执行结果建议使用值对象，便于引擎准确维护 step log、job 状态和 run 聚合状态：

```java
public class StepResult {
    private StepResultType type;
    private String summary;
    private String errorCode;
    private String errorMessage;
    private Map<String, Object> outputs;
}

public enum StepResultType {
    CONTINUE,
    SUSPEND,
    FAIL
}
```

其中：

- `summary` 写入 step log 和 job summary。
- `errorCode/errorMessage` 用于失败诊断，错误信息必须脱敏。
- `outputs` 是 step 输出变量，只能写入非敏感值；敏感值必须通过凭据引用传递。

上下文对象：

```java
public class PipelineStepContext {
    private PipelineRunDO run;
    private PipelineDefinitionVersionDO version;
    private String stageId;
    private String stageName;
    private PipelineSpec.ExecutableJob job;
    private PipelineSpec.ExecutableStep step;
    private BuildRuntime runtime;
    private Path localWorkspace;
    private Map<String, Object> sharedState;
    private Long userId;
}
```

`PipelineStepHandlerRegistry` 负责从 Spring Bean 列表中解析 handler：

```java
public class PipelineStepHandlerRegistry {

    private final List<PipelineStepHandler> handlers;

    public PipelineStepHandler resolve(String stepType) {
        return handlers.stream()
                .filter(handler -> handler.supports(stepType))
                .findFirst()
                .orElseThrow(() -> exception(PIPELINE_NODE_TYPE_NOT_SUPPORTED, stepType));
    }

}
```

handler 命名建议：

| 当前类 | 目标类 |
|---|---|
| `PipelineNodeHandler` | `PipelineStepHandler` |
| `PipelineNodeContext` | `PipelineStepContext` |
| `NodeOutcome` | `StepResult` |
| `BuildNodeHandler` | `CommandStepHandler` |
| 审批旧处理器 | `ApprovalStepHandler` |
| `CodeMergeNodeHandler` | `CodeMergeStepHandler` |
| `PipelineBuiltinStepHandler` | `BuiltinStepHandler` |
| `PipelineNodeLogHelper` | `PipelineStepLogHelper` |

因为项目尚未上线，推荐在 Docker 运行时落地前直接完成命名迁移，不保留旧 handler 命名作为长期兼容层。

### 6.1 Handler 幂等要求

所有 `PipelineStepHandler` 必须具备幂等意识：

- 同一个 step 可能因为服务重启、恢复阻塞、用户重试或调度重入再次进入 handler。
- handler 执行前应先读取当前 step log。
- 已经 `SUCCESS` 的 step 应返回 `CONTINUE`。
- 已经 `WAITING_INPUT` 且外部事件未完成的 step 应返回 `SUSPEND`。
- 已经 `FAILED/CANCELED` 的 step 应返回 `FAIL`，除非当前是明确的 retry attempt。
- handler 不应自己推进下游 job；下游调度只由执行引擎根据 DAG 状态完成。

### 6.2 平台步骤与运行时步骤

步骤按运行位置分为两类：

| 类别 | 说明 | 示例 |
|---|---|---|
| `PLATFORM` | 在平台服务内执行，不创建 Docker runtime | 后续接入 `CODE_MERGE`、`APPROVAL`、`ArtifactUpload`、`UnitTestReport` |
| `JOB_RUNTIME` | 在 job runtime 中执行，第一期是 Docker 容器 | 第一版只实现 `Command` |

如果一个 job 混合两类步骤，引擎按 step 顺序执行，并在第一个 `JOB_RUNTIME` step 前懒创建 runtime。`PLATFORM` step 不应假设 runtime 一定存在。

## 7. 运行时抽象

新增构建运行时抽象，避免 pipeline handler 直接感知 Docker 容器生命周期细节。

建议包结构：

```text
cn.iocoder.yudao.module.devops.framework.build
  BuildRuntime
  BuildRuntimeManager
  CommandExecutor
  CommandExecutionRequest
  CommandExecutionResult

cn.iocoder.yudao.module.devops.framework.docker
  DockerClientFactory
  DockerJobContainerManager
  DockerCommandExecutor
  DockerJobRuntime
```

职责划分：

| 类型 | 职责 |
|---|---|
| `BuildRuntime` | 表示一次 job 的执行环境，包含 workspace、环境变量、运行时类型等信息 |
| `BuildRuntimeManager` | 根据 `runsOn` 创建和销毁运行时 |
| `CommandExecutor` | 执行命令的统一接口，屏蔽本地执行和容器执行差异 |
| `DockerJobContainerManager` | 使用 docker-java 创建、启动、停止、删除 job 容器 |
| `DockerCommandExecutor` | 使用 docker exec 在 job 容器内执行命令并流式收集日志 |
| `DockerJobRuntime` | 保存 `containerId`、`containerName`、`workspace`、`runsOn` 等 Docker 运行时信息 |

`PipelineStepContext` 增加运行时字段：

```java
private BuildRuntime runtime;
```

### 7.1 Runtime 创建时机

job runtime 采用懒创建策略：

- job 开始时不立即创建 Docker 容器。
- 执行到第一个 `runtimeRequirement() == JOB_RUNTIME` 的 step 时，才创建 job runtime。
- `PLATFORM` step 不需要 Docker runtime，执行时 `PipelineStepContext.runtime` 可以为空。
- 如果 job 内只有 `PLATFORM` step，则不创建 Docker 容器。
- 如果 job 在 `PLATFORM` step 上进入 `BLOCKED`，不得提前创建或长期占用 Docker 容器。
- 如果 job 已经创建 runtime，后续 `PLATFORM` step 仍可执行，但不应依赖容器存在。

推荐建模：

```java
for (PipelineSpec.ExecutableStep step : job.getSteps()) {
    PipelineStepHandler handler = stepHandlerRegistry.resolve(step.getStep());
    BuildRuntime runtime = null;
    if (handler.runtimeRequirement() == StepRuntimeRequirement.JOB_RUNTIME) {
        runtime = buildRuntimeManager.getOrCreate(run, job);
    }
    StepResult result = handler.handle(buildContext(run, job, step, runtime));
    if (result.getType() == StepResultType.SUSPEND) {
        buildRuntimeManager.destroyIfExists(run, job);
        markJobBlocked(job);
        return;
    }
}
```

推荐 YAML 组织方式：

- `CODE_MERGE`、`APPROVAL` 这类平台控制步骤单独放 job。
- 构建、测试、扫描等需要容器的步骤放到构建 job。
- 用 `needs` 连接平台控制 job 和构建 job。

## 8. Workspace 与源码准备

每个 job 使用独立 workspace，避免并行 job 互相污染。

宿主机源码目录：

```text
${workspaceRoot}/{runId}/{stageId}/{jobId}/source
```

容器内源码目录：

```text
/workspace/source
```

宿主机产物目录：

```text
${workspaceRoot}/{runId}/{stageId}/{jobId}/artifacts
```

容器内产物目录：

```text
/workspace/artifacts
```

源码准备策略：

1. YAML 配置 `sources` 时，第一版最多支持 1 个来源，且 `type` 仅支持 `gitlab`。
2. 通过 `submit-branch` 触发时，源码从当前应用绑定的 GitLab 代码库拉取，分支使用应用默认分支；不做前置代码合并。
3. 非应用上下文运行时，可以使用 YAML `sources.endpoint` 和 `sources.branch` 做无凭证 clone。
4. YAML 未配置 `sources` 时，只创建空临时 workspace。
5. 每个 job 开始前，平台把源码准备到独立 job workspace。
6. 容器只挂载 job workspace，不直接持有 Git 凭据。
7. 多个并行 job 各自拥有独立源码和产物目录。
8. Maven/npm 等依赖缓存可以按租户或资源池共享，但源码目录不能共享。

推荐目录结构：

```text
{workspaceRoot}/{runId}/{stageId}/{jobId}/
  source/
  artifacts/
  reports/
  tmp/
```

缓存目录：

```text
{cacheRoot}/maven/{tenantId}/ -> /root/.m2
{cacheRoot}/npm/{tenantId}/   -> /root/.npm
```

实现边界：

- `BuildRuntimeManager` 负责创建 workspace 目录。
- `SourceWorkspacePreparer` 负责 checkout 代码到 `source/`。
- `DockerJobContainerManager` 只负责挂载和容器生命周期，不负责 Git 逻辑。

## 9. Docker 容器生命周期

每个 `job` 的容器生命周期如下：

1. 准备宿主机工作目录。
2. 检查或拉取镜像。
3. 创建容器。
4. 启动容器并保持常驻。
5. 通过 `docker exec` 执行 job 内每个命令步骤。
6. job 完成后停止并删除容器。

### 9.1 工作目录

宿主机目录：

```text
${workspaceRoot}/{runId}/{stageId}/{jobId}
```

容器内目录：

```text
/workspace
```

推荐配置项：

```yaml
yudao:
  devops:
    build:
      workspace-root: /data/devops/workspaces
      maven-cache-root: /data/devops/cache/maven
      keep-failed-container: false
```

### 9.2 容器创建参数

容器建议配置：

```text
image: job.runsOn.container
name: devops-run-{runId}-{jobId}-{shortId}
workingDir: /workspace
command: sh -c "sleep infinity"
```

挂载：

```text
${workspaceRoot}/{runId}/{stageId}/{jobId} -> /workspace
${mavenCacheRoot} -> /root/.m2
```

标签：

```text
devops.managed=true
devops.runId={runId}
devops.stageId={stageId}
devops.jobId={jobId}
```

标签用于故障排查和过期容器清理。

### 9.3 镜像处理

第一期策略：

1. 先 `inspectImage`。
2. 镜像不存在时执行 `pullImage`。
3. 拉取失败则 job 失败。

后续可以支持：

- 私有镜像仓库认证。
- 镜像预热。
- 按资源池配置镜像白名单。

## 10. Step 执行位置

不同步骤的执行位置不同：

| Step 类型 | 执行位置 | 说明 |
|---|---|---|
| `CODE_MERGE` | 宿主机 / 平台服务 | 需要访问平台 Git workspace、变更上下文和冲突处理 |
| `APPROVAL` | 宿主机 / 平台服务 | 审批属于平台控制流，不进入构建容器 |
| `Command` | job 容器内 | 第一版唯一实现的 step，使用 `docker exec` 执行 `with.run` |
| 其他 step | 暂不实现 | 校验或执行时明确提示暂不支持，后续逐步开发 handler |

## 11. 命令执行设计

`Command` 不再直接调用 `LocalBuildExecutor`，而是走 `CommandExecutor`。第一版不实现 `EXECUTE_SHELL`，需要 shell 命令时统一写 `step: Command` 和 `with.run`。

接口示例：

```java
public interface CommandExecutor {

    CommandExecutionResult execute(CommandExecutionRequest request, LogSink logSink);

}
```

请求对象包含：

```java
class CommandExecutionRequest {
    private BuildRuntime runtime;
    private String script;
    private String shellType;
    private Map<String, String> env;
    private Integer timeoutSeconds;
}
```

Docker 实现通过 docker-java 的 exec API 执行：

```text
execCreateCmd(containerId)
  -> withCmd(shell, "-lc", script)
  -> withWorkingDir("/workspace/source")
  -> withEnv(...)
  -> withAttachStdout(true)
  -> withAttachStderr(true)

execStartCmd(execId)
  -> attach stdout/stderr
  -> stream to LogSink
```

退出码通过 `inspectExecCmd(execId)` 获取。

## 12. 日志与状态

日志仍写入 `dev_pipeline_run_log`。

建议日志结构：

- `stageId/stageName`：来自当前 YAML stage。
- `jobId/jobName`：来自当前 YAML job。
- `stepId`：当前 YAML step key。
- `stepType`：当前 YAML step 的 `step` 值。
- `stepName`：当前 YAML step 的 `name` 值。
- `contextJson`：记录 sanitized 的运行时信息，例如 `containerImage`、`containerName`、`workspaceRelativePath`。
- `resultJson`：记录 `exitCode`、日志截断信息、产物摘要等。

禁止写入：

- registry password
- Git token
- settings.xml 明文
- 宿主机绝对敏感路径

日志流式写入策略：

- 内存中只保留有限行数，例如 500 行摘要。
- 完整日志后续可落文件或对象存储，DB 只存摘要和索引。

## 13. 变量、凭据与表达式

流水线 YAML 允许在字符串参数中使用简单变量表达式：

```yaml
with:
  artifact: "Artifacts_${PIPELINE_ID}"
  version: "${BUILD_NUMBER}"
```

第一期只支持 `${VAR}` 简单替换，不支持函数、条件、脚本表达式。

内置变量：

| 变量 | 含义 |
|---|---|
| `PIPELINE_ID` | 流水线定义编号 |
| `PIPELINE_RUN_ID` | 流水线运行编号 |
| `BUILD_NUMBER` | 本次运行展示编号，可先等于 run id |
| `STAGE_ID` | 当前阶段编号 |
| `JOB_ID` | 当前任务编号 |
| `STEP_ID` | 当前步骤编号 |
| `BRANCH_NAME` | 当前构建分支 |
| `COMMIT_SHA` | 当前构建提交 |
| `APP_KEY` | 应用标识 |
| `IMAGE_TAG` | 默认取 commit sha，空时取 build number |

变量来源优先级：

```text
step 输出变量 > job 变量 > run 内置变量 > 系统默认变量
```

实现建议：

- 新增 `PipelineVariableResolver`，统一解析字符串、列表和 map 中的 `${VAR}`。
- step handler 不直接做字符串替换，统一调用 resolver。
- resolver 返回解析后的值和脱敏后的展示值。

step 输出变量建议落在 step log 的 `resultJson.outputs` 中，结构示例：

```json
{
  "outputs": {
    "IMAGE_TAG": "abc123",
    "PACKAGE_PATH": "target/app.jar"
  }
}
```

规则：

- 输出变量名只允许大写字母、数字和下划线。
- 输出变量只在同一个 pipeline run 内有效。
- 下游 job 可以读取上游成功 job 的输出变量。
- 输出变量不得保存密码、token、私钥或 settings.xml 内容。
- 如果 handler 需要传递敏感信息，只能传递凭据 id 或服务连接 id。

凭据规则：

- YAML 中只允许写凭据引用，不允许写密码、token、私钥明文。
- 字段命名优先使用 `credentialId`、`serviceConnection`、`registryCredentialId`。
- handler 通过凭据服务读取密钥，并注入环境变量、配置文件或 SDK 参数。
- `contextJson`、`resultJson`、日志、异常信息必须保存脱敏值。

示例：

```yaml
with:
  serviceConnection: artifact-repo-001
  registryCredentialId: docker-registry-001
```

脱敏策略：

- token/password/privateKey 输出为 `******`。
- tokenized URL 写入日志前必须移除凭据部分。
- settings.xml 等配置文件内容不写入 DB。

## 14. Artifact 与 Report 输出模型

构建物和报告都先落在 job workspace，再由平台上传或读取。

标准 `resultJson` 结构：

```json
{
  "artifacts": [
    {
      "name": "app.jar",
      "path": "target/app.jar",
      "size": 123456,
      "url": "https://example.com/artifacts/app.jar",
      "checksum": "sha256:..."
    }
  ],
  "reports": [
    {
      "type": "JUnit",
      "name": "单元测试报告",
      "path": "target/site/surefire-report.html",
      "url": "https://example.com/reports/surefire-report.html"
    }
  ]
}
```

规则：

- `ArtifactUpload` 负责根据 `with.filePath` 收集文件并上传。
- `UnitTestReport` 负责根据 `with.reportPath` 读取报告并生成报告记录。
- `path` 使用相对 workspace/source 的路径，不返回宿主机绝对路径。
- `url` 可以为空；为空时表示文件仍在平台本地或尚未接入对象存储。
- 大文件内容不写入 DB，DB 只保存元数据。

第一期可以继续把 artifacts/reports 存入 step log 的 `resultJson`；如果后续需要检索、下载权限、过期清理，再拆分为独立表。

## 15. 超时、取消和失败处理

超时优先级：

```text
step.timeoutSeconds > job.timeoutSeconds > 系统默认值
```

默认值建议：

```text
1800 秒
```

取消策略：

1. 用户取消 `PipelineRun`。
2. 标记 run 为取消中。
3. 当前 `docker exec` 中断。
4. 停止并删除当前 job 容器。
5. run/log 标记为取消。

失败策略：

- 命令退出码非 0：当前 step 失败。
- Docker daemon 连接失败：当前 job 失败。
- 镜像拉取失败：当前 job 失败。
- 容器启动失败：当前 job 失败。
- 清理容器失败：记录 warn，不覆盖原始失败原因。
- 变量解析失败：当前 step 失败，错误信息指向变量名，不输出变量值。
- 凭据读取失败：当前 step 失败，错误信息指向凭据引用 id，不输出密钥内容。
- artifact/report 路径不存在：当前 step 失败或按 step 参数决定是否允许空结果。

失败容器保留：

- 默认不保留。
- 可通过 `yudao.devops.build.keep-failed-container=true` 保留失败容器用于排查。
- 保留时必须打标签并由定时清理任务兜底。

错误分类建议：

| 分类 | 示例 | 处理 |
|---|---|---|
| `PIPELINE_SPEC_ERROR` | YAML 缺字段、needs 循环 | 创建 run 前失败 |
| `PIPELINE_SCHEDULER_ERROR` | job 抢占失败、状态非法流转 | run/job 失败并记录调度错误 |
| `PIPELINE_RESOURCE_ERROR` | executor group 不存在、并发许可异常 | job 失败或保持 PENDING |
| `PIPELINE_RUNTIME_ERROR` | Docker daemon 不可用、容器启动失败 | job 失败 |
| `PIPELINE_COMMAND_ERROR` | 命令退出码非 0、命令超时 | step/job 失败 |
| `PIPELINE_CREDENTIAL_ERROR` | 凭据不存在、凭据无权限 | step/job 失败 |
| `PIPELINE_EXTERNAL_ERROR` | 制品库上传失败、报告读取失败 | step/job 失败 |

错误码进入 `StepResult.errorCode` 和 job `summary/error_message`；异常堆栈只写服务端日志，不进入前端响应。

## 16. 安全边界

默认安全策略：

1. 不把 `/var/run/docker.sock` 挂载进构建容器。
2. 不使用 privileged 容器。
3. 不在日志和 API 响应里输出凭据。
4. 不允许 YAML 自由声明宿主机任意挂载路径。
5. 容器名称、label、workspace 目录由平台生成，不直接使用用户输入。
6. 第一阶段可不开放 `networkMode`、`privileged`、自定义 volume 等高风险能力。

后续增强：

- 资源限制：CPU、内存、磁盘。
- 镜像白名单。
- 网络隔离。
- 每个租户独立 workspace/cache。
- 构建容器以非 root 用户运行。

## 17. 资源池设计

第一期 `runsOn.group` 可以只支持本机 Docker：

```text
local-docker/default
```

后续可以扩展为资源池配置：

```text
devops_executor_group
  id
  group_key
  name
  type              LOCAL_DOCKER / REMOTE_DOCKER
  docker_host
  tls_config
  max_concurrency
  enabled
```

资源池职责：

- 决定使用哪个 Docker daemon。
- 控制并发。
- 管理镜像仓库认证。
- 管理默认缓存和 workspace 根目录。

当前阶段可以先用配置项，不急于落库。

### 17.1 第一版资源池配置

第一版可用配置项表达本机 Docker 资源池：

```yaml
yudao:
  devops:
    executor-groups:
      local-docker/default:
        type: LOCAL_DOCKER
        max-concurrency: 2
        workspace-root: /data/devops/workspaces
        cache-root: /data/devops/cache
        enabled: true
```

调度规则：

- ready job 必须先获取 `runsOn.group` 对应资源池的执行许可。
- 获取许可后 job 从 `PENDING` 变为 `RUNNING`。
- 没有许可时继续保持 `PENDING`，summary 可写为“等待执行资源”。
- job 进入终态或 `BLOCKED` 时释放许可。
- 多个 pipeline run 共享同一个资源池限流。
- 资源池不存在或被禁用时，job 直接 `FAILED`，错误信息写明 group 不可用。

实现建议：

- 第一版必须支持多条 `PipelineRun` 同时构建部署；多个 run 的 ready jobs 共享同一个资源池并发额度。
- 第一版按后端服务单副本调度实现，可以使用 JVM 内 `Semaphore` 管理资源池并发。
- 后续支持后端服务多副本部署时，不能使用 JVM 内 `Semaphore` 作为唯一并发控制，必须用 DB 抢占/租约或 Redis 分布式信号量。

### 17.2 多运行实例与多服务副本

这里需要区分两个概念：

| 概念 | 第一版建议 | 说明 |
|---|---|---|
| 多流水线运行实例 | 支持 | 多个 `PipelineRun` 可以同时存在，DAG 调度按资源池额度执行 ready jobs |
| 多后端服务副本 | 第一版不支持，后续增强 | 多个应用实例同时调度时，需要避免同一个 job 被重复抢占 |

“多后端服务副本”指部署本系统后端服务时，为了高可用或扩容启动多个相同服务进程或 Pod。它不是指同一个应用环境能否有多个生效流水线。同一个应用环境只允许一个生效流水线定义，这是业务约束；后端服务多副本是运行时部署形态。

多流水线运行实例并发不难，核心是 `dev_pipeline_run_job` 有独立 `pipeline_run_id`，工作目录也包含 `{runId}`：

```text
${workspaceRoot}/{runId}/{stageId}/{jobId}/
```

因此不同 run 的 job 天然隔离，只需要共享资源池并发许可。

后端服务多副本部署的难点在调度抢占和资源池许可：

1. 多个服务实例可能同时扫描到同一个 `PENDING` job。
2. 多个服务实例可能同时认为资源池还有空位。
3. 某个服务实例执行中宕机后，需要其他实例识别租约过期并接管或标记失败。

后续支持后端服务多副本时，推荐 DB 租约模型：

```sql
ALTER TABLE dev_pipeline_run_job
  ADD COLUMN worker_id varchar(128) DEFAULT NULL COMMENT '调度工作节点',
  ADD COLUMN lease_until datetime DEFAULT NULL COMMENT '执行租约到期时间';
```

抢占规则：

```sql
UPDATE dev_pipeline_run_job
SET status = 'RUNNING',
    worker_id = ?,
    lease_until = ?,
    started_at = IFNULL(started_at, NOW())
WHERE id = ?
  AND status = 'PENDING'
  AND deleted = b'0';
```

只有更新成功的服务实例才能执行该 job。执行期间定期续租；服务实例宕机后，其他实例扫描到 `RUNNING` 且 `lease_until` 过期的 job，再按服务重启策略处理。

资源池并发许可第一版有两种可选实现：

| 方案 | 推荐程度 | 说明 |
|---|---|---|
| DB 计数/租约 | 推荐 | 少引入中间件，和 job 抢占事务一致 |
| Redis 信号量 | 可选 | 性能更好，但依赖 Redis 可用性和锁释放兜底 |

DB 方案可以通过统计未过期 `RUNNING` job 数量控制资源池并发：

```sql
SELECT COUNT(*)
FROM dev_pipeline_run_job
WHERE executor_group = ?
  AND status = 'RUNNING'
  AND lease_until > NOW()
  AND deleted = b'0';
```

当计数小于 `max-concurrency` 时再尝试抢占 job。严格并发上限需要把“计数 + 抢占”放进同一事务，或引入资源池占用表；第一版如果只在单库事务内执行，可以接受短时间竞争后通过状态更新失败重试。

## 18. 清理机制

需要增加兜底清理任务，处理异常退出遗留容器。

扫描条件：

```text
label devops.managed=true
label devops.runId exists
createdAt 超过配置阈值
```

处理策略：

1. 查询平台 run 状态。
2. 如果 run 已终态且容器仍存在，停止并删除。
3. 如果 run 不存在或超过最大运行时长，停止并删除。
4. 清理失败只记录日志，不影响正常流水线执行。

建议配置：

```yaml
yudao:
  devops:
    build:
      orphan-container-ttl: 6h
      cleanup-interval: 10m
```

## 19. 分阶段落地计划

### 阶段一：DAG 调度与容器运行时骨架

目标：

- 新增 `dev_pipeline_run_job`，持久化 job DAG 状态。
- 引入 `BuildRuntime`、`BuildRuntimeManager`、`DockerJobContainerManager`、`DockerCommandExecutor`。
- 引入 `SourceWorkspacePreparer`，为每个 job 准备独立源码 workspace。
- 引入 `PipelineVariableResolver`，统一处理 `${VAR}` 简单变量替换。
- 按 `job.runsOn.container` 创建临时容器。
- `Command` 在容器内执行。
- job 结束后清理容器。

验收：

- `needs` 可以控制跨阶段 job 依赖，无依赖 job 可并行进入 ready 队列。
- 一个 job 内连续两个 `Command` 可以共享 `/workspace` 文件。
- Maven 构建产物能在宿主 workspace 中看到。
- `${PIPELINE_RUN_ID}`、`${COMMIT_SHA}` 等内置变量可以解析。
- 容器失败、命令失败、超时都能正确标记 run log。

### 阶段二：内置步骤逐步补齐

目标：

- `EXECUTE_SHELL` 映射到 `Command` 或独立 handler。
- `SetupMavenSettings` 写入 settings。
- `SetupJava` 校验 Java/Maven 版本。
- `UnitTestReport` 读取报告文件。
- `ArtifactUpload` 上传构建物。
- `JavaP3CScan` 转为真实扫描命令或专用执行逻辑。
- `ArtifactUpload` 和 `UnitTestReport` 按标准 `resultJson.artifacts/reports` 输出元数据。

验收：

- 示例 Java 流水线可以完成扫描、测试、打包和构建物上传。

### 阶段三：资源池和并发控制

目标：

- `runsOn.group` 映射到执行资源池。
- 支持最大并发。
- 支持远程 Docker daemon。
- 支持资源池级 registry credential。
- 支持资源池不存在、禁用、并发耗尽时的明确 job 状态和错误信息。

验收：

- 多个 pipeline run 可以按资源池并发限制排队执行。

### 阶段四：安全和治理增强

目标：

- 容器资源限制。
- 镜像白名单。
- 孤儿容器清理任务。
- 构建日志对象存储。
- 失败容器保留和自动过期。

## 20. 测试策略

单元测试：

- `PipelineSpec` 分层展开顺序。
- `PipelineSpec` 解析 `needs: job_id` 和 `needs: [job_a, job_b]` 后都规范化为列表。
- `JobGraph` 校验缺失依赖、自依赖和循环依赖。
- `PipelineExecutionEngine` 发现无依赖 ready jobs 并按资源池并发调度。
- `BuildRuntimeManager` 根据 `runsOn` 选择 Docker 运行时。
- `BuildRuntimeManager` 对 PLATFORM-only job 不创建 Docker runtime，对第一个 JOB_RUNTIME step 懒创建 runtime。
- `SourceWorkspacePreparer` 为并行 job 创建隔离 workspace。
- `PipelineVariableResolver` 正确替换内置变量并脱敏凭据。
- `DockerJobContainerManager` 使用 mock `DockerClient` 验证 create/start/stop/remove 调用。
- `DockerCommandExecutor` 使用 mock exec callback 验证 stdout/stderr、exitCode、timeout。
- `CommandStepHandler` 验证命令类 step 通过 `CommandExecutor` 执行。
- `ArtifactUpload` / `UnitTestReport` 输出标准 artifacts/reports resultJson。

集成测试：

- 在本机 Docker 可用时，跑一个最小容器：
  - image: `alpine:latest`
  - step1: `echo hello > marker.txt`
  - step2: `cat marker.txt`
  - 验证两个 step 共享 workspace。

注意：

- 默认 CI 不应强依赖 Docker daemon。
- Docker 集成测试需要通过 profile 或环境变量显式开启。

## 21. 关键风险

| 风险 | 处理 |
|---|---|
| 应用启动环境没有 Docker daemon | docker-java bean 构造不 ping Docker，只在操作时失败 |
| 构建容器遗留 | 容器 label + finally 清理 + 定时清理任务 |
| 构建脚本泄露凭据 | 凭据只注入环境变量，日志做脱敏 |
| 用户通过 YAML 挂载宿主敏感路径 | 第一阶段不开放自定义 volume |
| 每步独立容器导致上下文丢失 | 容器生命周期绑定 job |
| 构建日志过大 | DB 存摘要，完整日志后续落文件或对象存储 |
| 并行 job 共享源码目录导致互相污染 | 每个 job 独立 workspace，缓存目录和源码目录分离 |
| Platform step 阻塞时占用 Docker 容器 | runtime 懒创建；job BLOCKED 时释放 runtime |
| 变量替换泄露凭据 | 统一 resolver 和脱敏输出，handler 不自行拼接敏感值 |

## 22. 最小实现清单

第一版需要完成：

1. `PipelineSpec` 增加 `needs` 和 job DAG executable view，保留现有 `ExecutableStep`。
2. 新增 `dev_pipeline_run_job` 表、DO、Mapper 和 job 状态枚举。
3. `PipelineExecutionEngine` 改为 job DAG 调度，支持 `PENDING/RUNNING/BLOCKED/SUCCESS/FAILED/SKIPPED/CANCELED`。
4. 新增 `PipelineStepHandler`、`PipelineStepContext`、`StepResult` 和 `PipelineStepHandlerRegistry`。
5. 新增 `SourceWorkspacePreparer`，为每个 job 准备独立源码目录。
6. 新增 `PipelineVariableResolver`，统一变量替换和脱敏。
7. 新增 `BuildRuntimeManager` 和 Docker 实现。
8. 新增 `DockerJobContainerManager`。
9. 新增 `DockerCommandExecutor`。
10. 改造 `CommandStepHandler`，使用容器内命令执行。
11. 将现有 node handler 命名迁移为 step handler。
12. `ArtifactUpload` 和 `UnitTestReport` 输出标准 artifacts/reports 元数据。
13. 增加 focused tests。

完成后，`runsOn` 才真正成为流水线构建执行环境声明，而不是普通配置字段。

## 23. 第一版决策摘要

下面这些点作为第一版默认实现边界。

| 决策点 | 推荐默认值 | 影响范围 |
|---|---|---|
| 第一版执行资源池 | 只支持 `local-docker/default`，配置驱动，不落资源池表 | `runsOn.group` 校验、并发控制、Docker client 选择 |
| 默认并发数 | `max-concurrency: 2` | 单机资源消耗、ready job 排队体验 |
| 多流水线运行实例 | 第一版支持，多 run 共享资源池并发额度 | 并发构建部署、workspace 隔离 |
| 后端服务多副本部署 | 第一版不支持，按后端服务单副本调度实现 | 调度去重、故障接管、资源池许可 |
| 默认 job 超时 | 1800 秒 | 长构建是否容易超时、异常容器释放速度 |
| 默认 step 超时 | 不单独设置，继承 job 超时 | YAML 简洁度、handler 超时控制 |
| `retryTimes` 第一版语义 | job 级重试，从第一个 step 开始，默认 0 | `attempt`、workspace 清理、下游 `SKIPPED` 重置 |
| `failStrategy` 第一版语义 | 先只支持 fail-fast，不支持 continue-on-error | DAG 终态聚合、前端展示复杂度 |
| `needs` 的 stage 关系 | stage 只做 UI 分组，不做隐式串行屏障 | 用户 YAML 必须显式写依赖 |
| jobId 唯一范围 | 整条 pipeline 全局唯一 | 跨 stage `needs` 解析、日志查询 |
| stepId 唯一范围 | 整条 pipeline 全局唯一 | 日志查询和前端定位 |
| runtime 创建时机 | 第一个 `JOB_RUNTIME` step 懒创建 | 平台步骤阻塞时不占容器 |
| `ArtifactUpload` 执行位置 | 平台读取 workspace 上传，不在容器内上传 | 凭据安全、上传 SDK 复用 |
| `UnitTestReport` 执行位置 | 平台读取 workspace 解析，不在容器内解析 | 报告标准化、路径脱敏 |
| 产物存储 | 第一版只写 `resultJson.artifacts/reports`，`url` 可为空 | 不先建设对象存储或制品库表 |
| 完整日志存储 | 第一版 DB 存摘要，完整日志文件/对象存储后置 | `dev_pipeline_run_log.log_file_url` 可以先为空 |
| 失败容器保留 | 默认不保留，通过配置开关开启 | 安全、磁盘占用、排障体验 |
| Docker socket 挂载 | 禁止挂载 `/var/run/docker.sock` 到构建容器 | 防止构建脚本控制宿主 Docker |
| 自定义 volume/network/privileged | 第一版不开放 YAML 配置 | 降低安全风险和调度复杂度 |
| 镜像策略 | 本地不存在时自动 pull，失败则 job 失败 | 首次构建耗时、私有仓库后续接入 |
| 私有镜像认证 | 第一版预留 `registryCredentialId`，可先不实现真实认证 | 资源池和凭据服务边界 |
| workspace 保留周期 | 先保留本地目录，后续加定时清理策略 | 磁盘占用、失败排查 |
| 服务重启中的 `RUNNING` job | 单副本无法接管时标记 `FAILED`；多副本通过租约过期识别并处理 | 故障恢复、容器清理 |
| `BLOCKED` job 恢复 | 外部事件只唤醒引擎，handler 幂等判断继续/挂起/失败 | 审批、代码冲突、人工步骤 |
| 输出变量 | 只支持非敏感字符串，写入 `resultJson.outputs` | 下游变量解析、脱敏责任 |
| 凭据写法 | YAML 只允许引用 id，不允许明文密钥 | 凭据服务、日志脱敏 |
| 兼容字段 | API 和 DB 都按 stage/job/step 新模型输出，不再保留 `nodes/edges` 或 `nodeId/nodeType/nodeName` 兼容字段 | 前后端统一改造、减少历史包袱 |

## 24. 已确认事项与后续增强

经过审阅，主链路已经明确：YAML 使用 `sources/stages/jobs/steps`，job DAG 调度，job 级 Docker runtime，step handler 扩展点，run/job/step 新读模型。第一版关键决策如下。

### 24.1 已确认的事项

| 事项 | 结论 |
|---|---|---|
| Docker 执行池范围 | 第一版只支持本机 Docker，即 `local-docker/default`；远程 Docker 后续通过资源池表或多组 Docker host 配置扩展 |
| 后端服务部署形态 | 第一版不支持后端服务多副本调度，按后端服务单副本实现；多副本调度后续再接 DB/Redis 协调 |
| `failStrategy` 范围 | 第一版只支持 fail-fast，不支持 `continue-on-error` |
| 应用环境生效流水线 | 一个应用环境只允许一个生效流水线定义 |
| 源码 checkout 来源 | 配置 `sources` 时准备源码 workspace；`submit-branch` 使用应用绑定 GitLab 仓库和应用默认分支；未配置 `sources` 时创建空 workspace |
| 构建镜像来源 | 第一版允许 `runsOn.container` 指定镜像，但只支持公开镜像或 Docker daemon 已登录的私有镜像；registry 凭据登录后置 |
| 构建日志查看方式 | 第一版 DB 存摘要，完整日志文件/对象存储后置；`log_file_url` 可为空 |
| 制品上传目标 | 第一版如没有制品库，`ArtifactUpload` 只记录 workspace 相对路径和元数据，`url` 可为空 |

### 24.2 可以先默认、后续增强的事项

| 事项 | 第一版默认 | 后续增强 |
|---|---|---|
| executor group | 配置驱动 `local-docker/default` | 落库成 `devops_executor_group`，支持远程 Docker |
| 并发控制 | 后端服务单副本用 JVM `Semaphore` | 多副本时改为 DB 租约/计数、独立资源池占用表或 Redis 信号量 |
| 超时 | job 默认 1800 秒，step 继承 job | 支持不同 step 类型默认超时 |
| 重试 | job 级重试，默认 0 次 | 支持 step 级 retry 或 retry 条件 |
| fail strategy | fail-fast | `continue-on-error`、允许部分失败 |
| workspace 清理 | 先保留 workspace，依赖定期清理 | 按租户/项目配置保留周期 |
| 失败容器 | 默认删除 | 配置开启保留并自动过期 |
| 镜像治理 | 不开放 privileged、volume、networkMode | 镜像白名单、资源限制、非 root 用户 |
| 输出变量 | 只支持非敏感字符串 | typed outputs、跨 run 引用 |
| worker lease 字段 | 第一版不强制使用；可预留 `worker_id/lease_until` 降低后续迁移成本 | 多副本调度时启用租约抢占和续租 |

### 24.3 建议现在直接定死的事项

这些点如果反复摇摆，会拖慢实现，建议按当前方案固定：

1. stage 只做展示分组，不做隐式串行屏障；所有先后关系都用 `needs`。
2. `jobId` 和 `stepId` 都在整条 pipeline 内全局唯一。
3. `BLOCKED` 只用于 job；具体等待点由 step log 的 `WAITING_INPUT` 表达。
4. `PLATFORM` step 不创建 Docker runtime，`JOB_RUNTIME` step 懒创建 runtime。
5. step handler 是唯一执行扩展点，不再新增 node handler。
6. API 和 DB 都使用 stage/job/step 新模型，不保留旧图模型字段。
7. YAML 中不允许写明文凭据，只允许引用 credential/service connection id。

## 25. 实现设计

本节把方案拆成可编码的模块、类、表和实施顺序。第一版目标是：YAML 原生 job DAG 调度、本机 Docker 构建运行时、stage/job/step 新读模型，并保持后端服务单副本调度。

### 25.1 当前代码差距

| 模块 | 当前状态 | 需要调整 |
|---|---|---|
| `PipelineSpec` | 已有 `sources/stages/jobs/steps` 和 `ExecutableStep` | 增加 `Job.needs`、`ExecutableJob`、`ExecutableGraph`，提供 job DAG 视图 |
| `PipelineSpecValidationServiceImpl` | 已能解析 YAML/JSON 和校验 step | 增加 jobId 全局唯一、needs 缺失/自依赖/循环校验、failStrategy 白名单 |
| `PipelineExecutionEngine` | 仍按扁平 step 顺序执行 | 改成基于 `dev_pipeline_run_job` 的 job DAG 调度 |
| `PipelineNodeHandler` | 仍是 node 命名 | 迁移为 `PipelineStepHandler`、`PipelineStepContext`、`StepResult` |
| `dev_pipeline_run_log` | 已向 stage/job/step 字段迁移 | 后续只使用 `stepId/stepType/stepName`，不再新增 node 兼容字段 |
| Docker client | 已接入 docker-java client 配置 | 增加 job runtime、容器管理、docker exec 命令执行 |
| current-run read model | 历史上偏 `nodes/edges` | 改为 stage/job/step 层级结构 |

### 25.2 实施阶段

建议按四个迭代落地，降低一次性改动风险。

#### 阶段 A：YAML Job DAG 与持久化骨架

目标：先让配置模型、校验和 job 表稳定下来，不接 Docker。

改动：

1. `PipelineSpec.Job` 增加 `needs`，支持字符串和数组写法统一成 `List<String>`。
2. 新增 `PipelineSpec.ExecutableJob`：
   - `stageId/stageName`
   - `jobId/jobName`
   - `runsOn`
   - `needs`
   - `timeoutSeconds/retryTimes/failStrategy`
   - `steps`
3. 新增 `PipelineSpec.ExecutableGraph` 或 `PipelineJobGraph`：
   - `List<ExecutableJob> jobs`
   - `Map<String, ExecutableJob> jobById`
   - `Map<String, List<String>> dependents`
4. `PipelineSpecValidationServiceImpl` 增加校验：
   - duplicate job id
   - duplicate step id
   - missing needs
   - self needs
   - cycle needs
   - unsupported failStrategy，第一版只允许空或 `failFast`
5. 新增 `dev_pipeline_run_job` SQL、DO、Mapper、状态枚举。
6. run 创建时初始化每个 job 为 `PENDING`。

建议类：

```text
framework/pipeline/PipelineSpec.ExecutableJob
framework/pipeline/PipelineSpec.ExecutableGraph
service/pipeline/execution/job/PipelineJobGraphBuilder
service/pipeline/execution/job/PipelineRunJobStatusEnum
dal/dataobject/pipeline/job/PipelineRunJobDO
dal/mysql/pipeline/job/PipelineRunJobMapper
```

`dev_pipeline_run_job` 第一版建议包含预留字段：

```text
worker_id
lease_until
```

第一版单副本不使用这两个字段，只保留表结构，后续多副本调度不用再做破坏性迁移。

验收：

- YAML 里 `needs: test_job` 和 `needs: [test_job]` 都能解析。
- duplicate job id、missing/self/cyclic needs 能失败。
- 创建 run 后能查到每个 job 的 `PENDING` 记录。

#### 阶段 B：Step Handler 命名迁移与 job 调度

目标：先用现有 handler 行为跑通 job DAG，不急着接 Docker。

改动：

1. 新增接口：

```java
public interface PipelineStepHandler {
    boolean supports(String stepType);
    StepRuntimeRequirement runtimeRequirement();
    StepResult handle(PipelineStepContext ctx);
}
```

2. 新增：

```text
PipelineStepContext
StepResult
StepResultType
StepRuntimeRequirement
PipelineStepHandlerRegistry
PipelineStepLogHelper
```

3. 现有类重命名：

| 当前 | 目标 |
|---|---|
| `PipelineNodeHandler` | `PipelineStepHandler` |
| `PipelineNodeContext` | `PipelineStepContext` |
| `NodeOutcome` | `StepResultType` 或 `StepResult` |
| `BuildNodeHandler` | `CommandStepHandler` |
| 审批旧处理器 | `ApprovalStepHandler` |
| `CodeMergeNodeHandler` | `CodeMergeStepHandler` |
| `PipelineNodeLogHelper` | `PipelineStepLogHelper` |

4. `PipelineExecutionEngine` 改为 job 调度：
   - 初始化 jobs
   - 查找 ready jobs
   - 获取 `local-docker/default` 许可
   - job 状态 `PENDING -> RUNNING`
   - job 内 steps 串行执行
   - handler `SUSPEND`：job `BLOCKED`
   - handler `FAIL`：job `FAILED`，下游 `SKIPPED`
   - 全部 jobs 终态后聚合 run 状态
5. 第一版后端单副本调度：
   - 用 JVM `ExecutorService` 执行 ready jobs
   - 用 JVM `Semaphore` 控制 `local-docker/default.max-concurrency`
   - job 状态更新仍使用当前状态条件，避免同进程并发误启动

调度服务建议拆分：

```text
PipelineExecutionEngine
PipelineJobScheduler
PipelineRunJobService
PipelineRunStatusAggregator
ExecutorGroupPermitManager
```

职责：

| 类 | 职责 |
|---|---|
| `PipelineExecutionEngine` | run 级入口，加载 version/spec，启动调度 |
| `PipelineJobScheduler` | ready job 查找、执行、状态推进 |
| `PipelineRunJobService` | job 初始化、状态更新、跳过传播 |
| `PipelineRunStatusAggregator` | 从 job 状态聚合 run 状态 |
| `ExecutorGroupPermitManager` | 单副本资源池并发许可 |

验收：

- 无 needs 的两个 job 可以并发进入 `RUNNING`。
- 下游 job 等上游 `SUCCESS` 后再执行。
- 上游 `FAILED/CANCELED` 后，下游 `PENDING` 变 `SKIPPED`。
- `BLOCKED` job 不持有 executor permit。
- run 状态按 job 状态聚合。

#### 阶段 C：本机 Docker Job Runtime

目标：让 `Command` 真正在 job 容器内执行，用于验证 YAML、DAG、Docker runtime、日志和 workspace 链路。

新增包：

```text
framework/build/
  BuildRuntime
  BuildRuntimeManager
  CommandExecutor
  CommandExecutionRequest
  CommandExecutionResult
  LogSink
  SourceWorkspacePreparer

framework/docker/
  DockerJobRuntime
  DockerJobContainerManager
  DockerCommandExecutor
```

实现规则：

1. `BuildRuntimeManager#getOrCreate(run, job)` 懒创建 runtime。
2. `SourceWorkspacePreparer` 根据 YAML `sources` 准备 workspace；`submit-branch` 使用应用绑定 GitLab 仓库和应用默认分支。
3. `DockerJobContainerManager`：
   - inspect/pull image
   - create/start container
   - mount job workspace 到 `/workspace`
   - label 容器
   - stop/remove container
4. `DockerCommandExecutor`：
   - `docker exec`
   - workingDir `/workspace/source`
   - stdout/stderr 流式写入 `LogSink`
   - inspect exec exitCode
5. `CommandStepHandler`：
   - `runtimeRequirement() == JOB_RUNTIME`
   - 解析 `${VAR}`
   - 调用 `CommandExecutor`
   - exitCode 0 返回 `CONTINUE`
   - exitCode 非 0 返回 `FAIL`
6. `PLATFORM` step 不创建 runtime。

第一版配置：

```yaml
yudao:
  devops:
    executor-groups:
      local-docker/default:
        type: LOCAL_DOCKER
        max-concurrency: 2
        workspace-root: /data/devops/workspaces
        cache-root: /data/devops/cache
        enabled: true
    build:
      default-timeout-seconds: 1800
      keep-failed-container: false
```

验收：

- 一个 job 内两个 `Command` 能共享 workspace。
- 并行 jobs 的 `source/artifacts/reports/tmp` 目录互相隔离。
- Docker daemon 不可用时应用仍能启动，执行时 job 失败。
- job 完成后容器被清理。

#### 阶段 D：读模型与内置步骤补齐

目标：前端直接消费 stage/job/step 结构。

API 输出建议：

```json
{
  "runId": 1001,
  "runStatus": "RUNNING",
  "stages": [
    {
      "stageId": "check_stage",
      "stageName": "检查",
      "jobs": [
        {
          "jobId": "unit_test_job",
          "jobName": "单元测试",
          "status": "RUNNING",
          "needs": [],
          "runtimeType": "DOCKER",
          "executorGroup": "local-docker/default",
          "executorImage": "maven:3.9.9-eclipse-temurin-17",
          "steps": [
            {
              "stepId": "unit_test_step",
              "stepType": "Command",
              "stepName": "执行单元测试",
              "status": "RUNNING",
              "message": "执行中",
              "detailType": "RUN_LOGS",
              "detailRef": {
                "runLogId": 2001
              },
              "actions": []
            }
          ]
        }
      ]
    }
  ]
}
```

内置步骤第一版处理：

| step | 第一版行为 |
|---|---|
| `Command` | Docker exec 执行 `with.run` |
| 其他 step | 第一版不实现，不做占位成功；后续逐步开发对应 handler |

### 25.3 第一版编码顺序

建议实际提交顺序：

1. `PipelineSpec` 增加 `needs`、`ExecutableJob`、`ExecutableGraph` 和验证测试。
2. 新增 `dev_pipeline_run_job` SQL/DO/Mapper/Enum。
3. 新增 `PipelineStepHandler` 系列类型，迁移现有 handler 命名。
4. 改造 `PipelineExecutionEngine` 为 job DAG 调度，但先用现有 handler 行为。
5. 新增 executor group 配置和 `ExecutorGroupPermitManager`。
6. 新增 workspace/source preparer。
7. 新增 Docker job runtime 和 command executor。
8. 改造 `CommandStepHandler` 接入 Docker exec。
9. 改造 current-run 响应为 stage/job/step。
10. 补齐 artifacts/reports resultJson。

### 25.4 当前未决项复核

截至当前方案，第一版关键决策已经确定：

- 本机 Docker only。
- 后端服务单副本调度。
- fail-fast only。
- 源码从当前应用环境关联应用的 Git 代码库 checkout。
- 前端使用 stage/job/step 新读模型。
- artifact/report/log 可先只落 DB 元数据，URL 可为空。

当前没有阻塞第一版编码的未决项。第一版 step handler 范围已收敛：

| 细节 | 建议 |
|---|---|
| 第一版 step handler | 只实现 `Command`，执行 `with.run` shell 命令 |
| 其他 step | 不做占位成功，遇到未实现 step 明确返回不支持，后续逐步开发 |
