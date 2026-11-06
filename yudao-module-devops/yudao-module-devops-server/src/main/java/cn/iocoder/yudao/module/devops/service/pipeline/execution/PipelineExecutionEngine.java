package cn.iocoder.yudao.module.devops.service.pipeline.execution;

import cn.hutool.core.collection.CollUtil;
import cn.hutool.core.util.StrUtil;
import cn.iocoder.yudao.framework.common.util.json.JsonUtils;
import cn.iocoder.yudao.module.devops.controller.admin.pipeline.vo.PipelineValidationRespVO;
import cn.iocoder.yudao.module.devops.dal.dataobject.pipeline.PipelineDefinitionVersionDO;
import cn.iocoder.yudao.module.devops.dal.dataobject.pipeline.PipelineRunDO;
import cn.iocoder.yudao.module.devops.dal.dataobject.pipeline.job.PipelineRunJobDO;
import cn.iocoder.yudao.module.devops.dal.dataobject.pipeline.log.PipelineRunLogDO;
import cn.iocoder.yudao.module.devops.dal.mysql.pipeline.PipelineDefinitionVersionMapper;
import cn.iocoder.yudao.module.devops.dal.mysql.pipeline.PipelineRunMapper;
import cn.iocoder.yudao.module.devops.dal.mysql.pipeline.job.PipelineRunJobMapper;
import cn.iocoder.yudao.module.devops.dal.mysql.pipeline.log.PipelineRunLogMapper;
import cn.iocoder.yudao.module.devops.enums.PipelineRunJobStatusEnum;
import cn.iocoder.yudao.module.devops.enums.PipelineRunLogLevelEnum;
import cn.iocoder.yudao.module.devops.enums.PipelineRunLogStatusEnum;
import cn.iocoder.yudao.module.devops.enums.PipelineRunStatusEnum;
import cn.iocoder.yudao.module.devops.framework.common.SensitiveKeyUtils;
import cn.iocoder.yudao.module.devops.framework.pipeline.PipelineSpec;
import cn.iocoder.yudao.module.devops.framework.pipeline.runtime.PipelineCacheConfig;
import cn.iocoder.yudao.module.devops.framework.pipeline.runtime.PipelineCacheConfigResolver;
import cn.iocoder.yudao.module.devops.framework.pipeline.runtime.PipelineJobRuntime;
import cn.iocoder.yudao.module.devops.framework.pipeline.runtime.PipelineJobRuntimeManager;
import cn.iocoder.yudao.module.devops.framework.pipeline.runtime.PipelineWorkspace;
import cn.iocoder.yudao.module.devops.framework.pipeline.runtime.PipelineWorkspaceService;
import cn.iocoder.yudao.module.devops.service.pipeline.PipelineSpecValidationService;
import cn.iocoder.yudao.module.devops.service.pipeline.execution.handler.PipelineStepHandler;
import cn.iocoder.yudao.module.devops.service.pipeline.execution.handler.PipelineStepHandlerRegistry;
import cn.iocoder.yudao.module.devops.service.pipeline.execution.handler.PipelineStepActionHelper;
import cn.iocoder.yudao.module.devops.service.pipeline.execution.handler.StepResult;
import cn.iocoder.yudao.module.devops.service.pipeline.execution.handler.StepResultType;
import cn.iocoder.yudao.module.devops.service.pipeline.execution.handler.StepRuntimeRequirement;
import cn.iocoder.yudao.module.devops.service.pipeline.plugin.PipelinePluginDispatcher;
import cn.iocoder.yudao.module.devops.service.variablegroup.VariableGroupService;
import jakarta.annotation.Resource;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.Executor;
import java.util.function.Function;
import java.util.stream.Collectors;

import static cn.iocoder.yudao.framework.common.exception.util.ServiceExceptionUtil.exception;
import static cn.iocoder.yudao.module.devops.enums.ErrorCodeConstants.PIPELINE_VERSION_NOT_EXISTS;

/**
 * 流水线执行引擎。
 */
@Slf4j
@Service
public class PipelineExecutionEngine {

    @Resource
    private PipelineDefinitionVersionMapper pipelineDefinitionVersionMapper;
    @Resource
    private PipelineRunMapper pipelineRunMapper;
    @Resource
    private PipelineRunJobMapper pipelineRunJobMapper;
    @Resource
    private PipelineRunLogMapper pipelineRunLogMapper;
    @Resource
    private PipelineSpecValidationService pipelineSpecValidationService;
    @Resource
    private PipelineStepHandlerRegistry stepHandlerRegistry;
    @Resource
    private PipelineJobRuntimeManager pipelineJobRuntimeManager;
    @Resource
    private PipelineWorkspaceService pipelineWorkspaceService;
    @Resource
    private PipelineCacheConfigResolver pipelineCacheConfigResolver;
    @Resource
    private VariableGroupService variableGroupService;
    @Resource
    private PipelineSourceWorkspacePreparer pipelineSourceWorkspacePreparer;
    @Resource
    private PipelineLogStorageService pipelineLogStorageService;
    @Resource
    private PipelineStepActionHelper pipelineStepActionHelper;
    @Resource
    private PipelinePluginDispatcher pipelinePluginDispatcher;
    @Resource
    private cn.iocoder.yudao.module.devops.dal.mysql.application.ApplicationMapper applicationMapper;
    @Resource(name = "pipelineJobExecutionExecutor")
    private Executor pipelineJobExecutionExecutor;

    public void execute(PipelineRunDO run, Long userId) {
        PipelineDefinitionVersionDO version = pipelineDefinitionVersionMapper.selectById(run.getDefinitionVersionId());
        if (version == null) {
            throw exception(PIPELINE_VERSION_NOT_EXISTS);
        }
        execute(run, version, userId);
    }

    public void execute(PipelineRunDO run, PipelineDefinitionVersionDO version, Long userId) {
        markRunRunningIfQueued(run);
        PipelineSpec spec = pipelineSpecValidationService.parseSpec(version.getSpecJson(), new PipelineValidationRespVO());
        if (spec == null) {
            markRunSuccess(run);
            return;
        }
        PipelineSpec.ExecutableGraph graph = spec.toExecutableGraph();
        if (CollUtil.isEmpty(graph.getJobs())) {
            log.warn("[execute][runId({}) 无可执行任务,标记成功]", run.getId());
            markRunSuccess(run);
            return;
        }

        PipelineCacheConfig cacheConfig = pipelineCacheConfigResolver.resolveVersionConfig(version.getCacheConfigJson());
        boolean firstExecution = isFirstExecution(run.getId());
        initializeJobs(run, graph);
        PipelineWorkspace workspace = pipelineWorkspaceService.createWorkspace(run, cacheConfig);
        if (firstExecution) {
            // 首次执行清空 run workspace；恢复/审批/冲突继续时保留现场。
            pipelineWorkspaceService.resetRunWorkspace(run);
            workspace = pipelineWorkspaceService.createWorkspace(run, cacheConfig);
        }
        PipelineExecutionContext runCtx = buildRunContext(run, version, spec, workspace, cacheConfig, userId);
        restoreOutputRegistry(runCtx);
        boolean progressed;
        do {
            progressed = skipJobsWithFailedDependencies(run.getId(), graph);
            Map<String, PipelineRunJobDO> jobRunMap = loadJobRunMap(run.getId());
            List<ReadyJob> readyJobs = findReadyJobs(graph, jobRunMap);
            if (CollUtil.isEmpty(readyJobs)) {
                continue;
            }
            progressed = true;
            executeReadyJobs(runCtx, readyJobs);
            if (hasFailedFailFastJob(run.getId(), readyJobs)) {
                skipPendingJobs(run.getId(), "上游任务失败，failFast 跳过");
                break;
            }
        } while (progressed && hasPendingJobs(run.getId()));

        aggregateRunStatus(run);
        archiveTerminalRunLogs(run);
    }

    public void cancel(PipelineRunDO run, Long userId) {
        log.info("[cancel][runId({}) 开始取消]", run.getId());
        run.setRunStatus(PipelineRunStatusEnum.CANCELED.getStatus());
        run.setFinishedAt(LocalDateTime.now());
        pipelineRunMapper.updateById(run);
        cancelPlatformSteps(run, userId);
        for (PipelineRunJobDO jobRun : pipelineRunJobMapper.selectListByPipelineRunIdAndStatuses(run.getId(),
                List.of(PipelineRunJobStatusEnum.PENDING.getStatus(), PipelineRunJobStatusEnum.RUNNING.getStatus(),
                        PipelineRunJobStatusEnum.BLOCKED.getStatus()))) {
            markJobCanceled(jobRun);
        }
        for (PipelineRunLogDO runLog : pipelineRunLogMapper.selectListByPipelineRunId(run.getId())) {
            if (!PipelineRunLogLevelEnum.NODE.getLevel().equals(runLog.getLogLevel()) || isTerminalLogStatus(runLog.getStatus())) {
                continue;
            }
            runLog.setStatus(PipelineRunLogStatusEnum.CANCELED.getStatus());
            runLog.setFinishedAt(LocalDateTime.now());
            pipelineRunLogMapper.updateById(runLog);
            pipelineLogStorageService.completeStepLog(runLog);
        }
        PipelineRunDO update = new PipelineRunDO();
        update.setId(run.getId());
        update.setRunStatus(PipelineRunStatusEnum.CANCELED.getStatus());
        update.setFinishedAt(LocalDateTime.now());
        pipelineRunMapper.updateById(update);
        pipelineLogStorageService.archiveRunLogs(run);
        log.info("[cancel][runId({}) 取消完成]", run.getId());
    }

    private void cancelPlatformSteps(PipelineRunDO run, Long userId) {
        PipelineDefinitionVersionDO version = pipelineDefinitionVersionMapper.selectById(run.getDefinitionVersionId());
        if (version == null) {
            return;
        }
        PipelineSpec spec = pipelineSpecValidationService.parseSpec(version.getSpecJson(), new PipelineValidationRespVO());
        if (spec == null) {
            return;
        }
        PipelineSpec.ExecutableGraph graph = spec.toExecutableGraph();
        Map<String, PipelineSpec.ExecutableStep> stepMap = graph.getSteps().stream()
                .collect(Collectors.toMap(PipelineSpec.ExecutableStep::getStepId, Function.identity(),
                        (first, second) -> first, LinkedHashMap::new));
        Map<String, PipelineSpec.ExecutableJob> jobMap = graph.getJobMap();
        Map<String, PipelineRunJobDO> jobRunMap = loadJobRunMap(run.getId());
        PipelineWorkspace workspace = pipelineWorkspaceService.createWorkspace(run,
                pipelineCacheConfigResolver.resolveVersionConfig(version.getCacheConfigJson()));
        PipelineExecutionContext runCtx = buildRunContext(run, version, spec, workspace,
                pipelineCacheConfigResolver.resolveVersionConfig(version.getCacheConfigJson()), userId);
        for (PipelineRunLogDO runLog : pipelineRunLogMapper.selectListByPipelineRunId(run.getId())) {
            if (!PipelineRunLogLevelEnum.NODE.getLevel().equals(runLog.getLogLevel())
                    || isTerminalLogStatus(runLog.getStatus())) {
                continue;
            }
            PipelineSpec.ExecutableStep step = stepMap.get(runLog.getStepId());
            PipelineSpec.ExecutableJob job = step == null ? null : jobMap.get(step.getJobId());
            PipelineStepHandler handler = step == null ? null : stepHandlerRegistry.resolve(step.getStep());
            if (job == null || handler == null) {
                continue;
            }
            try {
                handler.cancel(runCtx.forJob(job, jobRunMap.get(job.getJobId())).forStep(step));
            } catch (Exception ex) {
                log.warn("[cancelPlatformSteps][runId({}) stepId({}) 平台步骤取消异常]",
                        run.getId(), runLog.getStepId(), ex);
            }
        }
    }

    private void initializeJobs(PipelineRunDO run, PipelineSpec.ExecutableGraph graph) {
        Map<String, PipelineRunJobDO> existingMap = loadJobRunMap(run.getId());
        int sort = 0;
        for (PipelineSpec.ExecutableJob job : graph.getJobs()) {
            sort += 100;
            if (existingMap.containsKey(job.getJobId())) {
                continue;
            }
            PipelineRunJobDO jobRun = new PipelineRunJobDO();
            jobRun.setPipelineRunId(run.getId());
            jobRun.setTenantId(run.getTenantId());
            jobRun.setStageId(job.getStageId());
            jobRun.setStageName(job.getStageName());
            jobRun.setJobId(job.getJobId());
            jobRun.setJobName(StrUtil.blankToDefault(job.getName(), job.getJobId()));
            jobRun.setStatus(PipelineRunJobStatusEnum.PENDING.getStatus());
            jobRun.setNeedsJson(JsonUtils.toJsonString(job.getNeeds()));
            jobRun.setAttempt(1);
            jobRun.setSort(sort);
            if (job.getRunsOn() != null) {
                jobRun.setRuntimeType("DOCKER");
                jobRun.setExecutorGroup(job.getRunsOn().getGroup());
                jobRun.setExecutorImage(job.getRunsOn().getContainer());
            }
            pipelineRunJobMapper.insert(jobRun);
        }
    }

    private void executeJob(PipelineExecutionContext jobCtx) {
        PipelineRunDO run = jobCtx.getRun();
        PipelineSpec.ExecutableJob job = jobCtx.getJob();
        PipelineRunJobDO jobRun = jobCtx.getJobRun();
        if (Boolean.FALSE.equals(job.getEnabled())) {
            markJobSkipped(jobRun, "任务已禁用");
            return;
        }
        markJobRunning(jobRun);
        PipelineJobRuntime runtime = null;
        try {
            log.info("[executeJob][runId({}) jobId({}) 开始执行，共有 {} 个步骤]",
                    run.getId(), job.getJobId(), job.getSteps().size());
            for (PipelineSpec.ExecutableStep step : job.getSteps()) {
                if (isRunCanceled(run.getId())) {
                    markJobCanceled(jobRun);
                    return;
                }
                log.info("[executeJob][runId({}) jobId({}) 执行步骤: {} ({})]",
                        run.getId(), job.getJobId(), step.getStepId(), step.getStep());
                if (Boolean.FALSE.equals(step.getEnabled())) {
                    continue;
                }
                PipelineStepHandler handler = stepHandlerRegistry.resolve(step.getStep());
                if (handler == null) {
                    markJobFailed(jobCtx, "不支持的步骤类型：" + step.getStep());
                    return;
                }
                PipelineExecutionContext ctx = jobCtx.toBuilder()
                        .executorRuntime(runtime)
                        .build()
                        .forStep(step);
                // CodeMerge步骤会自己处理所有git操作，跳过预先准备source
                // 其他需要source的步骤，如果CodeMerge已经准备过source，也跳过
                boolean isCodeMergeStep = "CodeMerge".equals(step.getStep()) || "CODE_MERGE".equals(step.getStep());
                if (handler.runtimeRequirement(ctx) == StepRuntimeRequirement.JOB_RUNTIME) {
                    runtime = ensureJobRuntime(ctx, runtime);
                    ctx.setExecutorRuntime(runtime);
                } else if (!isCodeMergeStep) {
                    // Platform handlers consume the host workspace, independently of a job's runtime.
                    ctx.setExecutorRuntime(null);
                }
                if (handler.sourceWorkspaceRequired() && !isCodeMergeStep && !ctx.isSourcePreparedByCodeMerge()) {
                    prepareSourceOnce(ctx);
                }
                // Source/runtime preparation may outlive a cancellation request.
                if (isRunCanceled(run.getId())) {
                    markJobCanceled(jobRun);
                    return;
                }
                StepResult result = handler.handle(ctx);
                if (isRunCanceled(run.getId())) {
                    markJobCanceled(jobRun);
                    return;
                }
                if (result.getType() == StepResultType.SUSPEND) {
                    markJobBlocked(jobRun, result.getSummary());
                    return;
                }
                if (result.getType() == StepResultType.FAIL) {
                    markJobFailed(jobCtx, StrUtil.blankToDefault(result.getErrorMessage(), result.getSummary()));
                    return;
                }
                publishStepOutputs(ctx, result);
            }
            markJobSuccess(jobCtx);
        } catch (Exception ex) {
            log.error("[executeJob][runId({}) jobId({}) 执行异常]", run.getId(), job.getJobId(), ex);
            if (isRunCanceled(run.getId())) {
                markJobCanceled(jobRun);
            } else {
                markJobFailed(jobCtx, ex.getMessage());
            }
        } finally {
            if (runtime != null) {
                pipelineJobRuntimeManager.destroyRuntime(runtime);
            }
        }
    }

    /**
     * 确保当前 job 拥有唯一的运行时。
     *
     * <p>runtime 是 job 级别资源，不是 step 级别资源；这里只在第一次遇到需要 {@link StepRuntimeRequirement#JOB_RUNTIME}
     * 的 step 时懒创建，后续 step 复用同一个 runtime。纯平台步骤或挂起在平台步骤上的 job 不会提前占用容器。
     */
    private PipelineJobRuntime ensureJobRuntime(PipelineExecutionContext ctx, PipelineJobRuntime runtime) {
        if (runtime != null) {
            return runtime;
        }
        PipelineWorkspace workspace = pipelineWorkspaceService.createWorkspace(ctx.getRun(), ctx.getJob(), ctx.getCacheConfig());
        PipelineRunJobDO jobRun = ctx.getJobRun();
        PipelineJobRuntime createdRuntime = pipelineJobRuntimeManager.createRuntime(ctx.getRun(), ctx.getJob(), workspace);
        fillJobRuntime(jobRun, createdRuntime);
        pipelineRunJobMapper.updateById(jobRun);
        return createdRuntime;
    }

    private void prepareSourceOnce(PipelineExecutionContext ctx) {
        if (ctx.isSourcePrepared()) {
            return;
        }
        synchronized (ctx.getSourcePrepareLock()) {
            if (ctx.isSourcePrepared()) {
                return;
            }
            if (ctx.getWorkspace() == null) {
                throw new IllegalStateException("Pipeline workspace is not initialized");
            }
            pipelineSourceWorkspacePreparer.prepare(ctx.getRun(), ctx.getSpec(), ctx.getWorkspace(), ctx.getSharedState());
            ctx.setSourcePrepared(true);
        }
    }

    private PipelineExecutionContext buildRunContext(PipelineRunDO run, PipelineDefinitionVersionDO version,
                                                    PipelineSpec spec, PipelineWorkspace workspace,
                                                    PipelineCacheConfig cacheConfig, Long userId) {
        return PipelineExecutionContext.builder()
                .run(run)
                .version(version)
                .spec(spec)
                .pipelineWorkspace(workspace)
                .cacheConfig(cacheConfig)
                .sharedState(new ConcurrentHashMap<>(buildSharedState(run)))
                .userId(userId)
                .build();
    }

    private boolean isFirstExecution(Long runId) {
        return CollUtil.isEmpty(pipelineRunJobMapper.selectListByPipelineRunId(runId))
                && CollUtil.isEmpty(pipelineRunLogMapper.selectListByPipelineRunId(runId));
    }

    private void restoreOutputRegistry(PipelineExecutionContext runCtx) {
        Map<String, Map<String, Map<String, Object>>> snapshot = new LinkedHashMap<>();
        for (PipelineRunLogDO runLog : pipelineRunLogMapper.selectListByPipelineRunId(runCtx.getRun().getId())) {
            if (!PipelineRunLogLevelEnum.NODE.getLevel().equals(runLog.getLogLevel())
                    || !PipelineRunLogStatusEnum.SUCCESS.getStatus().equals(runLog.getStatus())) {
                continue;
            }
            Map<String, Object> resultJson = JsonUtils.parseMap(runLog.getResultJson());
            Object outputsValue = resultJson == null ? null : resultJson.get("outputs");
            if (!(outputsValue instanceof Map<?, ?> outputs) || CollUtil.isEmpty(outputs)) {
                continue;
            }
            Map<String, Object> stepOutputs = new LinkedHashMap<>();
            outputs.forEach((key, value) -> {
                if (key != null && value != null) {
                    stepOutputs.put(String.valueOf(key), value);
                }
            });
            if (CollUtil.isEmpty(stepOutputs)) {
                continue;
            }
            snapshot.computeIfAbsent(runLog.getJobId(), ignored -> new LinkedHashMap<>())
                    .put(runLog.getStepId(), stepOutputs);
        }
        runCtx.getOutputRegistry().restore(snapshot);
    }

    private void publishStepOutputs(PipelineExecutionContext ctx, StepResult result) {
        if (result == null || (CollUtil.isEmpty(result.getOutputs()) && CollUtil.isEmpty(result.getActions()))) {
            return;
        }
        ctx.publishOutputs(result.getOutputs());
        PipelineRunLogDO runLog = pipelineRunLogMapper.selectByPipelineRunIdAndNodeId(
                ctx.getRun().getId(), ctx.getStep().getStepId());
        if (runLog == null) {
            return;
        }
        pipelineStepActionHelper.mergeStepResult(runLog, result);
        pipelineRunLogMapper.updateById(runLog);
    }

    private Map<String, PipelineRunJobDO> loadJobRunMap(Long runId) {
        return pipelineRunJobMapper.selectListByPipelineRunId(runId).stream()
                .collect(Collectors.toMap(PipelineRunJobDO::getJobId, Function.identity(), (first, second) -> first,
                        LinkedHashMap::new));
    }

    private List<ReadyJob> findReadyJobs(PipelineSpec.ExecutableGraph graph, Map<String, PipelineRunJobDO> jobRunMap) {
        List<ReadyJob> readyJobs = new ArrayList<>();
        for (PipelineSpec.ExecutableJob job : graph.getJobs()) {
            PipelineRunJobDO jobRun = jobRunMap.get(job.getJobId());
            if (jobRun == null || !PipelineRunJobStatusEnum.PENDING.getStatus().equals(jobRun.getStatus())) {
                continue;
            }
            if (!dependenciesSucceeded(job, jobRunMap)) {
                continue;
            }
            readyJobs.add(new ReadyJob(job, jobRun));
        }
        return readyJobs;
    }

    private void executeReadyJobs(PipelineExecutionContext runCtx, List<ReadyJob> readyJobs) {
        if (readyJobs.size() == 1) {
            ReadyJob readyJob = readyJobs.get(0);
            executeJob(runCtx.forJob(readyJob.job(), readyJob.jobRun()));
            return;
        }
        List<CompletableFuture<Void>> futures = readyJobs.stream()
                .map(readyJob -> CompletableFuture.runAsync(() ->
                        executeJob(runCtx.forJob(readyJob.job(), readyJob.jobRun())), pipelineJobExecutionExecutor))
                .toList();
        CompletableFuture.allOf(futures.toArray(new CompletableFuture[0])).join();
    }

    private boolean hasFailedFailFastJob(Long runId, List<ReadyJob> readyJobs) {
        for (ReadyJob readyJob : readyJobs) {
            if (!isFailFast(readyJob.job().getFailStrategy())) {
                continue;
            }
            PipelineRunJobDO latestJobRun = pipelineRunJobMapper
                    .selectByPipelineRunIdAndJobId(runId, readyJob.job().getJobId());
            if (latestJobRun != null && PipelineRunJobStatusEnum.FAILED.getStatus().equals(latestJobRun.getStatus())) {
                return true;
            }
        }
        return false;
    }

    private boolean dependenciesSucceeded(PipelineSpec.ExecutableJob job, Map<String, PipelineRunJobDO> jobRunMap) {
        for (String need : job.getNeeds()) {
            PipelineRunJobDO dependency = jobRunMap.get(need);
            if (dependency == null || !PipelineRunJobStatusEnum.SUCCESS.getStatus().equals(dependency.getStatus())) {
                return false;
            }
        }
        return true;
    }

    private boolean skipJobsWithFailedDependencies(Long runId, PipelineSpec.ExecutableGraph graph) {
        boolean progressed = false;
        Map<String, PipelineRunJobDO> jobRunMap = loadJobRunMap(runId);
        for (PipelineSpec.ExecutableJob job : graph.getJobs()) {
            PipelineRunJobDO jobRun = jobRunMap.get(job.getJobId());
            if (jobRun == null || !PipelineRunJobStatusEnum.PENDING.getStatus().equals(jobRun.getStatus())) {
                continue;
            }
            if (hasFailedDependency(job.getNeeds(), jobRunMap.values())) {
                markJobSkipped(jobRun, "依赖任务未成功，跳过执行");
                progressed = true;
            }
        }
        return progressed;
    }

    private boolean hasFailedDependency(List<String> needs, Collection<PipelineRunJobDO> jobRuns) {
        Map<String, String> statuses = jobRuns.stream()
                .collect(Collectors.toMap(PipelineRunJobDO::getJobId, PipelineRunJobDO::getStatus, (first, second) -> first));
        for (String need : needs) {
            String status = statuses.get(need);
            if (PipelineRunJobStatusEnum.FAILED.getStatus().equals(status)
                    || PipelineRunJobStatusEnum.SKIPPED.getStatus().equals(status)
                    || PipelineRunJobStatusEnum.CANCELED.getStatus().equals(status)) {
                return true;
            }
        }
        return false;
    }

    private boolean hasPendingJobs(Long runId) {
        return CollUtil.isNotEmpty(pipelineRunJobMapper.selectListByPipelineRunIdAndStatuses(runId,
                List.of(PipelineRunJobStatusEnum.PENDING.getStatus())));
    }

    private void skipPendingJobs(Long runId, String summary) {
        for (PipelineRunJobDO jobRun : pipelineRunJobMapper.selectListByPipelineRunIdAndStatuses(runId,
                List.of(PipelineRunJobStatusEnum.PENDING.getStatus()))) {
            markJobSkipped(jobRun, summary);
        }
    }

    private void aggregateRunStatus(PipelineRunDO run) {
        if (isRunCanceled(run.getId())) {
            return;
        }
        List<PipelineRunJobDO> jobs = pipelineRunJobMapper.selectListByPipelineRunId(run.getId());
        if (jobs.stream().anyMatch(job -> PipelineRunJobStatusEnum.RUNNING.getStatus().equals(job.getStatus()))) {
            updateRunStatus(run.getId(), PipelineRunStatusEnum.RUNNING.getStatus(), null);
            return;
        }
        if (jobs.stream().anyMatch(job -> PipelineRunJobStatusEnum.BLOCKED.getStatus().equals(job.getStatus()))) {
            updateRunStatus(run.getId(), PipelineRunStatusEnum.WAITING_INPUT.getStatus(), null);
            return;
        }
        if (jobs.stream().allMatch(job -> PipelineRunJobStatusEnum.SUCCESS.getStatus().equals(job.getStatus()))) {
            updateRunStatus(run.getId(), PipelineRunStatusEnum.SUCCESS.getStatus(), null);
            return;
        }
        if (jobs.stream().allMatch(job -> isTerminalJobStatus(job.getStatus()))) {
            updateRunStatus(run.getId(), PipelineRunStatusEnum.FAILED.getStatus(), "流水线任务执行失败");
        }
    }

    private void markJobRunning(PipelineRunJobDO jobRun) {
        jobRun.setStatus(PipelineRunJobStatusEnum.RUNNING.getStatus());
        jobRun.setStartedAt(jobRun.getStartedAt() == null ? LocalDateTime.now() : jobRun.getStartedAt());
        jobRun.setFinishedAt(null);
        jobRun.setErrorMessage(null);
        pipelineRunJobMapper.updateById(jobRun);
    }

    private boolean isRunCanceled(Long runId) {
        PipelineRunDO latest = pipelineRunMapper.selectById(runId);
        return latest != null && PipelineRunStatusEnum.CANCELED.getStatus().equals(latest.getRunStatus());
    }

    private void markJobSuccess(PipelineExecutionContext jobCtx) {
        PipelineRunJobDO jobRun = jobCtx.getJobRun();
        jobRun.setStatus(PipelineRunJobStatusEnum.SUCCESS.getStatus());
        jobRun.setSummary("任务执行成功");
        jobRun.setFinishedAt(LocalDateTime.now());
        jobRun.setErrorMessage(null);
        pipelineRunJobMapper.updateById(jobRun);
        pipelinePluginDispatcher.dispatch(jobCtx, PipelineRunJobStatusEnum.SUCCESS.getStatus());
    }

    private void markJobFailed(PipelineExecutionContext jobCtx, String errorMessage) {
        PipelineRunJobDO jobRun = jobCtx.getJobRun();
        jobRun.setStatus(PipelineRunJobStatusEnum.FAILED.getStatus());
        jobRun.setSummary("任务执行失败");
        jobRun.setErrorMessage(StrUtil.subPre(errorMessage, 2000));
        jobRun.setFinishedAt(LocalDateTime.now());
        pipelineRunJobMapper.updateById(jobRun);
        pipelinePluginDispatcher.dispatch(jobCtx, PipelineRunJobStatusEnum.FAILED.getStatus());
    }

    private void markJobBlocked(PipelineRunJobDO jobRun, String summary) {
        jobRun.setStatus(PipelineRunJobStatusEnum.BLOCKED.getStatus());
        jobRun.setSummary(StrUtil.blankToDefault(summary, "任务等待外部处理"));
        pipelineRunJobMapper.updateById(jobRun);
    }

    private void markJobSkipped(PipelineRunJobDO jobRun, String summary) {
        jobRun.setStatus(PipelineRunJobStatusEnum.SKIPPED.getStatus());
        jobRun.setSummary(summary);
        jobRun.setFinishedAt(LocalDateTime.now());
        pipelineRunJobMapper.updateById(jobRun);
    }

    private void markJobCanceled(PipelineRunJobDO jobRun) {
        jobRun.setStatus(PipelineRunJobStatusEnum.CANCELED.getStatus());
        jobRun.setSummary("任务已取消");
        jobRun.setFinishedAt(LocalDateTime.now());
        pipelineRunJobMapper.updateById(jobRun);
    }

    private void fillJobRuntime(PipelineRunJobDO jobRun, PipelineJobRuntime runtime) {
        jobRun.setRuntimeType(runtime.getRuntimeType());
        jobRun.setRuntimeId(runtime.getRuntimeId());
        jobRun.setRuntimeName(runtime.getRuntimeName());
        jobRun.setExecutorGroup(runtime.getExecutorGroup());
        jobRun.setExecutorImage(runtime.getExecutorImage());
        jobRun.setWorkspacePath(runtime.getWorkspace() == null ? null : runtime.getWorkspace().toString());
    }

    private boolean isFailFast(String failStrategy) {
        return StrUtil.isBlank(failStrategy) || "failFast".equals(failStrategy);
    }

    private boolean isTerminalJobStatus(String status) {
        return PipelineRunJobStatusEnum.SUCCESS.getStatus().equals(status)
                || PipelineRunJobStatusEnum.FAILED.getStatus().equals(status)
                || PipelineRunJobStatusEnum.SKIPPED.getStatus().equals(status)
                || PipelineRunJobStatusEnum.CANCELED.getStatus().equals(status);
    }

    private boolean isTerminalLogStatus(String status) {
        return PipelineRunLogStatusEnum.SUCCESS.getStatus().equals(status)
                || PipelineRunLogStatusEnum.FAILED.getStatus().equals(status)
                || PipelineRunLogStatusEnum.CANCELED.getStatus().equals(status);
    }

    private void markRunRunningIfQueued(PipelineRunDO run) {
        if (run.getRunStatus() != null && !PipelineRunStatusEnum.QUEUED.getStatus().equals(run.getRunStatus())) {
            return;
        }
        run.setRunStatus(PipelineRunStatusEnum.RUNNING.getStatus());
        run.setStartedAt(run.getStartedAt() == null ? LocalDateTime.now() : run.getStartedAt());
        run.setFinishedAt(null);
        run.setErrorMessage(null);
        pipelineRunMapper.updateById(run);
    }

    private void markRunSuccess(PipelineRunDO run) {
        updateRunStatus(run.getId(), PipelineRunStatusEnum.SUCCESS.getStatus(), null);
    }

    private void updateRunStatus(Long runId, Integer status, String errorMessage) {
        PipelineRunDO update = new PipelineRunDO();
        update.setId(runId);
        update.setRunStatus(status);
        if (PipelineRunStatusEnum.SUCCESS.getStatus().equals(status)
                || PipelineRunStatusEnum.FAILED.getStatus().equals(status)
                || PipelineRunStatusEnum.CANCELED.getStatus().equals(status)) {
            update.setFinishedAt(LocalDateTime.now());
        }
        update.setErrorMessage(StrUtil.subPre(errorMessage, 1000));
        pipelineRunMapper.updateById(update);
    }

    private void archiveTerminalRunLogs(PipelineRunDO run) {
        PipelineRunDO latest = pipelineRunMapper.selectById(run.getId());
        if (latest == null || !isTerminalRunStatus(latest.getRunStatus())) {
            return;
        }
        pipelineLogStorageService.archiveRunLogs(latest);
    }

    private boolean isTerminalRunStatus(Integer status) {
        return PipelineRunStatusEnum.SUCCESS.getStatus().equals(status)
                || PipelineRunStatusEnum.FAILED.getStatus().equals(status)
                || PipelineRunStatusEnum.CANCELED.getStatus().equals(status);
    }

    private Map<String, Object> buildSharedState(PipelineRunDO run) {
        Map<String, Object> sharedState = new ConcurrentHashMap<>();
        mergeInputContext(sharedState, run.getInputContextJson());
        variableGroupService.getEffectiveVariables(run.getAppId(), run.getApplicationEnvId())
                .forEach(sharedState::putIfAbsent);
        if (run.getAppId() == null) {
            return sharedState;
        }
        var application = applicationMapper.selectById(run.getAppId());
        if (application == null) {
            return sharedState;
        }
        String repoUrl = application.getRepoUrl();
        putIfNotBlank(sharedState, "repoUrl", repoUrl);
        putIfNotBlank(sharedState, "branchName", run.getBranchName());
        putIfNotBlank(sharedState, "sourceBranch", application.getDefaultBranchName());
        putIfNotBlank(sharedState, "commitSha", run.getCommitSha());
        putIfNotBlank(sharedState, "appKey", application.getAppKey());
        return sharedState;
    }

    private void mergeInputContext(Map<String, Object> sharedState, String inputContextJson) {
        if (StrUtil.isBlank(inputContextJson)) {
            return;
        }
        Map<String, Object> inputContext = JsonUtils.parseMap(inputContextJson);
        if (CollUtil.isEmpty(inputContext)) {
            return;
        }
        inputContext.forEach((key, value) -> {
            if (StrUtil.isNotBlank(key) && value != null && !isSensitiveInputKey(key)) {
                sharedState.put(key, value);
            }
        });
    }

    private boolean isSensitiveInputKey(String key) {
        return SensitiveKeyUtils.isSensitive(key);
    }

    private void putIfNotBlank(Map<String, Object> map, String key, String value) {
        if (StrUtil.isNotBlank(value)) {
            map.put(key, value);
        }
    }

    private record ReadyJob(PipelineSpec.ExecutableJob job, PipelineRunJobDO jobRun) {
    }

}
