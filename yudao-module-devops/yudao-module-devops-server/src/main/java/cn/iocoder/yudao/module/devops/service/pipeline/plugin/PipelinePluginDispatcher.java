package cn.iocoder.yudao.module.devops.service.pipeline.plugin;

import cn.hutool.core.collection.CollUtil;
import cn.hutool.core.util.StrUtil;
import cn.iocoder.yudao.framework.tenant.core.util.TenantUtils;
import cn.iocoder.yudao.module.devops.dal.dataobject.pipeline.PipelineDefinitionDO;
import cn.iocoder.yudao.module.devops.dal.dataobject.pipeline.PipelineRunDO;
import cn.iocoder.yudao.module.devops.dal.dataobject.pipeline.job.PipelineRunJobDO;
import cn.iocoder.yudao.module.devops.dal.mysql.pipeline.PipelineDefinitionMapper;
import cn.iocoder.yudao.module.devops.framework.pipeline.PipelineSpec;
import cn.iocoder.yudao.module.devops.service.pipeline.execution.PipelineExecutionContext;
import cn.iocoder.yudao.module.system.api.user.AdminUserApi;
import cn.iocoder.yudao.module.system.api.user.dto.AdminUserRespDTO;
import lombok.Builder;
import lombok.Value;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.stereotype.Component;

import java.time.LocalDateTime;
import java.util.List;
import java.util.concurrent.Executor;
import java.util.concurrent.RejectedExecutionException;

/**
 * 流水线插件异步分发器。
 */
@Component
@Slf4j
public class PipelinePluginDispatcher {

    private final PipelinePluginRegistry pluginRegistry;
    private final PipelineDefinitionMapper pipelineDefinitionMapper;
    private final AdminUserApi adminUserApi;
    private final Executor pluginExecutor;

    public PipelinePluginDispatcher(PipelinePluginRegistry pluginRegistry,
                                    PipelineDefinitionMapper pipelineDefinitionMapper,
                                    AdminUserApi adminUserApi,
                                    @Qualifier("pipelinePluginExecutor") Executor pluginExecutor) {
        this.pluginRegistry = pluginRegistry;
        this.pipelineDefinitionMapper = pipelineDefinitionMapper;
        this.adminUserApi = adminUserApi;
        this.pluginExecutor = pluginExecutor;
    }

    /**
     * 按 Job 终态异步分发匹配的插件。
     *
     * @param jobContext Job 执行上下文
     * @param jobStatus Job 终态
     */
    public void dispatch(PipelineExecutionContext jobContext, String jobStatus) {
        try {
            String triggerState = PipelinePluginTriggerState.fromJobStatus(jobStatus);
            if (triggerState == null || jobContext == null || jobContext.getJob() == null
                    || jobContext.getRun() == null) {
                return;
            }
            List<PipelineSpec.PluginConfig> plugins = CollUtil.emptyIfNull(jobContext.getJob().getPlugins()).stream()
                    .filter(config -> config != null && CollUtil.emptyIfNull(config.getTriggerState()).contains(triggerState))
                    .map(PipelineSpec.PluginConfig::copyOf)
                    .toList();
            if (plugins.isEmpty()) {
                return;
            }
            PluginEvent event = createEvent(jobContext, jobStatus, plugins);
            pluginExecutor.execute(() -> executeEventSafely(event));
        } catch (RejectedExecutionException ex) {
            log.warn("[dispatch][runId({}) jobId({}) 插件任务被线程池拒绝]",
                    getRunId(jobContext), getJobId(jobContext));
        } catch (RuntimeException ex) {
            log.warn("[dispatch][runId({}) jobId({}) 插件任务提交失败: {}]",
                    getRunId(jobContext), getJobId(jobContext), ex.getClass().getSimpleName());
        }
    }

    private void executeEventSafely(PluginEvent event) {
        try {
            executeEventWithTenant(event);
        } catch (RuntimeException ex) {
            log.warn("[executeEventSafely][runId({}) jobId({}) 插件事件执行异常: {}]",
                    event.getRunId(), event.getJobId(), ex.getClass().getSimpleName());
        }
    }

    private void executeEventWithTenant(PluginEvent event) {
        if (event.getTenantId() == null) {
            executeEvent(event);
            return;
        }
        TenantUtils.execute(event.getTenantId(), () -> executeEvent(event));
    }

    private void executeEvent(PluginEvent event) {
        PipelinePluginExecutionContext context = PipelinePluginExecutionContext.builder()
                .tenantId(event.getTenantId())
                .runId(event.getRunId())
                .definitionId(event.getDefinitionId())
                .pipelineName(resolvePipelineName(event.getDefinitionId()))
                .triggerUserId(event.getTriggerUserId())
                .operator(resolveOperator(event.getTriggerUserId()))
                .stageId(event.getStageId())
                .stageName(event.getStageName())
                .jobId(event.getJobId())
                .jobName(event.getJobName())
                .status(event.getStatus())
                .startedAt(event.getStartedAt())
                .finishedAt(event.getFinishedAt())
                .durationMillis(event.getDurationMillis())
                .build();
        for (PipelineSpec.PluginConfig pluginConfig : event.getPlugins()) {
            PipelinePlugin plugin = pluginRegistry.resolve(pluginConfig.getPlugin());
            if (plugin == null) {
                log.warn("[executeEvent][runId({}) jobId({}) plugin({}) 未注册]",
                        event.getRunId(), event.getJobId(), pluginConfig.getPlugin());
                continue;
            }
            try {
                plugin.execute(context, pluginConfig.getWith());
                log.info("[executeEvent][runId({}) stageId({}) jobId({}) plugin({}) 执行成功]",
                        event.getRunId(), event.getStageId(), event.getJobId(), plugin.type());
            } catch (RuntimeException ex) {
                log.warn("[executeEvent][runId({}) stageId({}) jobId({}) plugin({}) 执行失败: {}]",
                        event.getRunId(), event.getStageId(), event.getJobId(), plugin.type(),
                        ex.getClass().getSimpleName());
            }
        }
    }

    private PluginEvent createEvent(PipelineExecutionContext context, String jobStatus,
                                    List<PipelineSpec.PluginConfig> plugins) {
        PipelineRunDO run = context.getRun();
        PipelineRunJobDO jobRun = context.getJobRun();
        PipelineSpec.ExecutableJob job = context.getJob();
        return PluginEvent.builder()
                .tenantId(run.getTenantId())
                .runId(run.getId())
                .definitionId(run.getDefinitionId())
                .triggerUserId(run.getTriggerUserId())
                .stageId(job.getStageId())
                .stageName(StrUtil.blankToDefault(job.getStageName(), jobRun == null ? null : jobRun.getStageName()))
                .jobId(job.getJobId())
                .jobName(StrUtil.blankToDefault(job.getName(), jobRun == null ? null : jobRun.getJobName()))
                .status(jobStatus)
                .startedAt(jobRun == null ? null : jobRun.getStartedAt())
                .finishedAt(jobRun == null ? null : jobRun.getFinishedAt())
                .durationMillis(jobRun == null ? null : jobRun.getDurationMillis())
                .plugins(plugins)
                .build();
    }

    private String resolvePipelineName(Long definitionId) {
        if (definitionId == null) {
            return "-";
        }
        try {
            PipelineDefinitionDO definition = pipelineDefinitionMapper.selectById(definitionId);
            return definition == null ? String.valueOf(definitionId)
                    : StrUtil.blankToDefault(definition.getName(), String.valueOf(definitionId));
        } catch (RuntimeException ex) {
            log.warn("[resolvePipelineName][definitionId({}) 查询流水线名称失败: {}]",
                    definitionId, ex.getClass().getSimpleName());
            return String.valueOf(definitionId);
        }
    }

    private String resolveOperator(Long userId) {
        if (userId == null) {
            return "-";
        }
        try {
            AdminUserRespDTO user = adminUserApi.getUser(userId).getCheckedData();
            return user == null ? String.valueOf(userId)
                    : StrUtil.blankToDefault(user.getNickname(), String.valueOf(userId));
        } catch (RuntimeException ex) {
            log.warn("[resolveOperator][userId({}) 查询操作人失败: {}]", userId, ex.getClass().getSimpleName());
            return String.valueOf(userId);
        }
    }

    private Long getRunId(PipelineExecutionContext context) {
        return context == null || context.getRun() == null ? null : context.getRun().getId();
    }

    private String getJobId(PipelineExecutionContext context) {
        return context == null || context.getJob() == null ? null : context.getJob().getJobId();
    }

    @Value
    @Builder
    private static class PluginEvent {
        Long tenantId;
        Long runId;
        Long definitionId;
        Long triggerUserId;
        String stageId;
        String stageName;
        String jobId;
        String jobName;
        String status;
        LocalDateTime startedAt;
        LocalDateTime finishedAt;
        Long durationMillis;
        List<PipelineSpec.PluginConfig> plugins;
    }

}
