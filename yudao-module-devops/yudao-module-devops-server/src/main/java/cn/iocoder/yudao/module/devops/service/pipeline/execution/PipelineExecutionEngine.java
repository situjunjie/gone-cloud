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
import cn.iocoder.yudao.module.devops.framework.pipeline.PipelineSpec;
import cn.iocoder.yudao.module.devops.framework.pipeline.runtime.PipelineJobRuntime;
import cn.iocoder.yudao.module.devops.framework.pipeline.runtime.PipelineJobRuntimeManager;
import cn.iocoder.yudao.module.devops.framework.pipeline.runtime.PipelineWorkspaceService;
import cn.iocoder.yudao.module.devops.service.pipeline.PipelineNodeRegistryServiceImpl;
import cn.iocoder.yudao.module.devops.service.pipeline.PipelineSpecValidationService;
import cn.iocoder.yudao.module.devops.service.pipeline.execution.handler.CommandStepHandler;
import cn.iocoder.yudao.module.devops.service.pipeline.execution.handler.PipelineStepContext;
import cn.iocoder.yudao.module.devops.service.pipeline.execution.handler.PipelineStepHandler;
import cn.iocoder.yudao.module.devops.service.pipeline.execution.handler.PipelineStepHandlerRegistry;
import cn.iocoder.yudao.module.devops.service.pipeline.execution.handler.StepResult;
import cn.iocoder.yudao.module.devops.service.pipeline.execution.handler.StepResultType;
import cn.iocoder.yudao.module.devops.service.pipeline.execution.handler.StepRuntimeRequirement;
import jakarta.annotation.Resource;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.nio.file.Path;
import java.time.LocalDateTime;
import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
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
    private PipelineSourceWorkspacePreparer pipelineSourceWorkspacePreparer;
    @Resource
    private cn.iocoder.yudao.module.devops.dal.mysql.application.ApplicationMapper applicationMapper;

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

        initializeJobs(run, graph);
        Map<String, Object> sharedState = buildSharedState(run);
        boolean progressed;
        do {
            progressed = skipJobsWithFailedDependencies(run.getId(), graph);
            Map<String, PipelineRunJobDO> jobRunMap = loadJobRunMap(run.getId());
            for (PipelineSpec.ExecutableJob job : graph.getJobs()) {
                PipelineRunJobDO jobRun = jobRunMap.get(job.getJobId());
                if (jobRun == null || !PipelineRunJobStatusEnum.PENDING.getStatus().equals(jobRun.getStatus())) {
                    continue;
                }
                if (!dependenciesSucceeded(job, jobRunMap)) {
                    continue;
                }
                progressed = true;
                executeJob(run, version, spec, job, jobRun, sharedState, userId);
                if (isFailFast(job.getFailStrategy())) {
                    PipelineRunJobDO latestJobRun = pipelineRunJobMapper
                            .selectByPipelineRunIdAndJobId(run.getId(), job.getJobId());
                    if (latestJobRun != null && PipelineRunJobStatusEnum.FAILED.getStatus().equals(latestJobRun.getStatus())) {
                        skipPendingJobs(run.getId(), "上游任务失败，failFast 跳过");
                        break;
                    }
                }
            }
        } while (progressed && hasPendingJobs(run.getId()));

        aggregateRunStatus(run);
    }

    public void cancel(PipelineRunDO run, Long userId) {
        log.info("[cancel][runId({}) 开始取消]", run.getId());
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
        }
        PipelineRunDO update = new PipelineRunDO();
        update.setId(run.getId());
        update.setRunStatus(PipelineRunStatusEnum.CANCELED.getStatus());
        update.setFinishedAt(LocalDateTime.now());
        pipelineRunMapper.updateById(update);
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
                handler.cancel(buildStepContext(run, version, job, jobRunMap.get(job.getJobId()), step,
                        null, new ConcurrentHashMap<>(), userId));
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

    private void executeJob(PipelineRunDO run, PipelineDefinitionVersionDO version, PipelineSpec spec,
                            PipelineSpec.ExecutableJob job, PipelineRunJobDO jobRun,
                            Map<String, Object> sharedState, Long userId) {
        if (Boolean.FALSE.equals(job.getEnabled())) {
            markJobSkipped(jobRun, "任务已禁用");
            return;
        }
        markJobRunning(jobRun);
        PipelineJobRuntime runtime = null;
        Path workspace = null;
        try {
            for (PipelineSpec.ExecutableStep step : job.getSteps()) {
                if (Boolean.FALSE.equals(step.getEnabled())) {
                    continue;
                }
                PipelineStepHandler handler = stepHandlerRegistry.resolve(step.getStep());
                if (handler == null) {
                    markJobFailed(jobRun, "不支持的步骤类型：" + step.getStep());
                    return;
                }
                PipelineStepContext ctx = buildStepContext(run, version, job, jobRun, step, runtime, sharedState, userId);
                if (handler.runtimeRequirement() == StepRuntimeRequirement.JOB_RUNTIME && runtime == null) {
                    workspace = pipelineWorkspaceService.createWorkspace(run, job);
                    pipelineSourceWorkspacePreparer.prepare(run, spec, workspace, sharedState);
                    runtime = pipelineJobRuntimeManager.createRuntime(run, job, workspace);
                    sharedState.put(CommandStepHandler.runtimeKey(ctx), runtime);
                    fillJobRuntime(jobRun, runtime);
                    pipelineRunJobMapper.updateById(jobRun);
                    ctx.setWorkspace(runtime.getWorkspace());
                }
                StepResult result = handler.handle(ctx);
                if (result.getType() == StepResultType.SUSPEND) {
                    markJobBlocked(jobRun, result.getSummary());
                    return;
                }
                if (result.getType() == StepResultType.FAIL) {
                    markJobFailed(jobRun, StrUtil.blankToDefault(result.getErrorMessage(), result.getSummary()));
                    return;
                }
                mergeStepOutputs(sharedState, result);
            }
            markJobSuccess(jobRun);
        } catch (Exception ex) {
            log.error("[executeJob][runId({}) jobId({}) 执行异常]", run.getId(), job.getJobId(), ex);
            markJobFailed(jobRun, ex.getMessage());
        } finally {
            if (runtime != null) {
                pipelineJobRuntimeManager.destroyRuntime(runtime);
            }
        }
    }

    private PipelineStepContext buildStepContext(PipelineRunDO run, PipelineDefinitionVersionDO version,
                                                 PipelineSpec.ExecutableJob job, PipelineRunJobDO jobRun,
                                                 PipelineSpec.ExecutableStep step, PipelineJobRuntime runtime,
                                                 Map<String, Object> sharedState, Long userId) {
        return PipelineStepContext.builder()
                .run(run)
                .version(version)
                .jobRun(jobRun)
                .job(job)
                .step(step)
                .workspace(runtime == null ? null : runtime.getWorkspace())
                .sharedState(sharedState)
                .userId(userId)
                .build();
    }

    private Map<String, PipelineRunJobDO> loadJobRunMap(Long runId) {
        return pipelineRunJobMapper.selectListByPipelineRunId(runId).stream()
                .collect(Collectors.toMap(PipelineRunJobDO::getJobId, Function.identity(), (first, second) -> first,
                        LinkedHashMap::new));
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

    private void markJobSuccess(PipelineRunJobDO jobRun) {
        jobRun.setStatus(PipelineRunJobStatusEnum.SUCCESS.getStatus());
        jobRun.setSummary("任务执行成功");
        jobRun.setFinishedAt(LocalDateTime.now());
        jobRun.setErrorMessage(null);
        pipelineRunJobMapper.updateById(jobRun);
    }

    private void markJobFailed(PipelineRunJobDO jobRun, String errorMessage) {
        jobRun.setStatus(PipelineRunJobStatusEnum.FAILED.getStatus());
        jobRun.setSummary("任务执行失败");
        jobRun.setErrorMessage(StrUtil.subPre(errorMessage, 2000));
        jobRun.setFinishedAt(LocalDateTime.now());
        pipelineRunJobMapper.updateById(jobRun);
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

    private Map<String, Object> buildSharedState(PipelineRunDO run) {
        Map<String, Object> sharedState = new ConcurrentHashMap<>();
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

    private void mergeStepOutputs(Map<String, Object> sharedState, StepResult result) {
        if (result == null || CollUtil.isEmpty(result.getOutputs())) {
            return;
        }
        sharedState.putAll(result.getOutputs());
    }

    private void putIfNotBlank(Map<String, Object> map, String key, String value) {
        if (StrUtil.isNotBlank(value)) {
            map.put(key, value);
        }
    }

}
