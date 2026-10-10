package cn.iocoder.yudao.module.devops.service.pipeline.execution;

import cn.iocoder.yudao.module.devops.dal.dataobject.pipeline.PipelineDefinitionVersionDO;
import cn.iocoder.yudao.module.devops.dal.dataobject.pipeline.PipelineRunDO;
import cn.iocoder.yudao.module.devops.dal.dataobject.pipeline.job.PipelineRunJobDO;
import cn.iocoder.yudao.module.devops.framework.pipeline.PipelineSpec;
import cn.iocoder.yudao.module.devops.framework.pipeline.runtime.PipelineCacheConfig;
import cn.iocoder.yudao.module.devops.framework.pipeline.runtime.PipelineJobRuntime;
import cn.iocoder.yudao.module.devops.framework.pipeline.runtime.PipelineWorkspace;
import lombok.Builder;
import lombok.Data;

import java.nio.file.Path;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;
import java.util.concurrent.atomic.AtomicBoolean;

/**
 * 流水线执行上下文。
 *
 * <p>同一个 {@link PipelineRunDO} 共享 run 级字段；并发任务通过 {@link #forJob} 和 {@link #forStep}
 * 派生独立上下文，避免当前任务、当前步骤和执行器运行态互相覆盖。
 */
@Data
@Builder
public class PipelineExecutionContext {

    /**
     * 流水线运行记录。
     */
    private PipelineRunDO run;
    /**
     * 流水线定义版本记录。
     */
    private PipelineDefinitionVersionDO version;
    /**
     * 流水线可执行定义。
     */
    private PipelineSpec spec;
    /**
     * run 级共享工作目录。
     */
    private PipelineWorkspace pipelineWorkspace;
    /**
     * 缓存配置。
     */
    private PipelineCacheConfig cacheConfig;
    /**
     * 系统级共享状态。不要用于存储步骤输出或执行器运行态。
     */
    @Builder.Default
    private ConcurrentMap<String, Object> sharedState = new ConcurrentHashMap<>();
    /**
     * 步骤输出变量注册表。
     */
    @Builder.Default
    private PipelineOutputRegistry outputRegistry = new PipelineOutputRegistry();
    /**
     * 源码准备锁。
     */
    @Builder.Default
    private Object sourcePrepareLock = new Object();
    /**
     * 源码工作目录是否已准备。
     */
    @Builder.Default
    private AtomicBoolean sourcePrepared = new AtomicBoolean(false);
    /**
     * 源码工作目录是否由 CodeMerge 准备。
     */
    @Builder.Default
    private AtomicBoolean sourcePreparedByCodeMerge = new AtomicBoolean(false);
    @Builder.Default
    private ConcurrentMap<String, AtomicBoolean> remoteSourcePrepared = new ConcurrentHashMap<>();
    /**
     * 操作用户编号。
     */
    private Long userId;
    /**
     * 可执行任务定义。
     */
    private PipelineSpec.ExecutableJob job;
    /**
     * 流水线任务运行记录。
     */
    private PipelineRunJobDO jobRun;
    /**
     * 当前任务的执行器运行态。
     */
    private PipelineJobRuntime executorRuntime;
    /**
     * 可执行步骤定义。
     */
    private PipelineSpec.ExecutableStep step;

    public PipelineExecutionContextBuilder toBuilder() {
        return PipelineExecutionContext.builder()
                .run(run)
                .version(version)
                .spec(spec)
                .pipelineWorkspace(pipelineWorkspace)
                .cacheConfig(cacheConfig)
                .sharedState(sharedState)
                .outputRegistry(outputRegistry)
                .sourcePrepareLock(sourcePrepareLock)
                .sourcePrepared(sourcePrepared)
                .sourcePreparedByCodeMerge(sourcePreparedByCodeMerge)
                .remoteSourcePrepared(remoteSourcePrepared)
                .userId(userId)
                .job(job)
                .jobRun(jobRun)
                .executorRuntime(executorRuntime)
                .step(step);
    }

    /**
     * 派生任务级上下文。
     *
     * @param job    可执行任务定义
     * @param jobRun 任务运行记录
     * @return 任务级上下文
     */
    public PipelineExecutionContext forJob(PipelineSpec.ExecutableJob job, PipelineRunJobDO jobRun) {
        return toBuilder()
                .job(job)
                .jobRun(jobRun)
                .executorRuntime(null)
                .step(null)
                .build();
    }

    /**
     * 派生步骤级上下文。
     *
     * @param step 可执行步骤定义
     * @return 步骤级上下文
     */
    public PipelineExecutionContext forStep(PipelineSpec.ExecutableStep step) {
        return toBuilder().step(step).build();
    }

    /**
     * 获取 run workspace 路径。
     *
     * @return run workspace 路径；未初始化时返回 null
     */
    public Path getWorkspace() {
        return pipelineWorkspace == null ? null : pipelineWorkspace.getRunWorkspace();
    }

    /**
     * 获取解析后的 step.with。
     *
     * @return 解析后的 with 参数
     */
    public Map<String, Object> getResolvedWith() {
        if (step == null) {
            return new LinkedHashMap<>();
        }
        return PipelineVariableResolver.resolveStepWith(this);
    }

    /**
     * 获取解析后的步骤定义。
     *
     * @return 解析后的步骤定义
     */
    public PipelineSpec.ExecutableStep getResolvedStep() {
        return PipelineVariableResolver.resolveStep(this);
    }

    /**
     * 发布当前步骤输出变量。
     *
     * @param outputs 输出变量
     */
    public void publishOutputs(Map<String, Object> outputs) {
        if (job == null || step == null) {
            return;
        }
        outputRegistry.publish(job.getJobId(), step.getStepId(), outputs);
    }

    public boolean isSourcePrepared() {
        if (isKubernetesRuntime()) {
            return remoteSourceFlag("source").get();
        }
        return sourcePrepared != null && sourcePrepared.get();
    }

    public void setSourcePrepared(boolean sourcePrepared) {
        if (isKubernetesRuntime()) {
            remoteSourceFlag("source").set(sourcePrepared);
            return;
        }
        if (this.sourcePrepared == null) {
            this.sourcePrepared = new AtomicBoolean(sourcePrepared);
            return;
        }
        this.sourcePrepared.set(sourcePrepared);
    }

    public boolean isSourcePreparedByCodeMerge() {
        if (isKubernetesRuntime()) {
            return remoteSourceFlag("merge").get();
        }
        return sourcePreparedByCodeMerge != null && sourcePreparedByCodeMerge.get();
    }

    public void setSourcePreparedByCodeMerge(boolean sourcePreparedByCodeMerge) {
        if (isKubernetesRuntime()) {
            remoteSourceFlag("merge").set(sourcePreparedByCodeMerge);
            return;
        }
        if (this.sourcePreparedByCodeMerge == null) {
            this.sourcePreparedByCodeMerge = new AtomicBoolean(sourcePreparedByCodeMerge);
            return;
        }
        this.sourcePreparedByCodeMerge.set(sourcePreparedByCodeMerge);
    }

    private boolean isKubernetesRuntime() {
        return executorRuntime != null && "KUBERNETES".equals(executorRuntime.getRuntimeType());
    }

    private AtomicBoolean remoteSourceFlag(String kind) {
        Map<String, Object> metadata = executorRuntime.getMetadata();
        Object storage = metadata == null ? null : metadata.get("workspaceStorageType");
        // Persistent workspaces are shared within a run; EmptyDir belongs to one Pod.
        String scope = "SHARED_PVC".equals(storage) || "DYNAMIC_PVC".equals(storage)
                ? metadata.get("environmentId") + ":" + metadata.get("namespace") + ":" + storage
                : executorRuntime.getRuntimeId();
        return remoteSourcePrepared.computeIfAbsent(scope + ":" + kind, ignored -> new AtomicBoolean());
    }

}
