package cn.iocoder.yudao.module.devops.service.pipeline.execution.handler;

import cn.hutool.core.util.StrUtil;
import cn.iocoder.yudao.module.devops.dal.dataobject.pipeline.log.PipelineRunLogDO;
import cn.iocoder.yudao.module.devops.dal.mysql.pipeline.log.PipelineRunLogMapper;
import cn.iocoder.yudao.module.devops.enums.PipelineRunLogLevelEnum;
import cn.iocoder.yudao.module.devops.enums.PipelineRunLogStatusEnum;
import jakarta.annotation.Resource;
import org.springframework.stereotype.Component;

import java.time.LocalDateTime;

/**
 * 流水线步骤日志写入辅助。
 */
@Component
public class PipelineStepLogHelper {

    @Resource
    private PipelineRunLogMapper pipelineRunLogMapper;

    public PipelineRunLogDO getOrCreateLog(PipelineStepContext ctx) {
        PipelineRunLogDO log = pipelineRunLogMapper.selectByPipelineRunIdAndNodeId(
                ctx.getRun().getId(), ctx.getStep().getStepId());
        if (log != null) {
            return log;
        }
        var step = ctx.getStep();
        log = new PipelineRunLogDO();
        log.setPipelineRunId(ctx.getRun().getId());
        log.setTenantId(ctx.getRun().getTenantId());
        log.setStageId(step.getStageId());
        log.setStageName(step.getStageName());
        log.setJobId(step.getJobId());
        log.setJobName(step.getJobName());
        log.setStepId(step.getStepId());
        log.setStepType(step.getStep());
        log.setStepName(StrUtil.blankToDefault(step.getName(), step.getStep()));
        log.setLogLevel(PipelineRunLogLevelEnum.NODE.getLevel());
        log.setStatus(PipelineRunLogStatusEnum.PENDING.getStatus());
        log.setSort(100);
        log.setAttempt(ctx.getJobRun() == null || ctx.getJobRun().getAttempt() == null
                ? 1 : ctx.getJobRun().getAttempt());
        if (step.getRunsOn() != null) {
            log.setRuntimeType("DOCKER");
            log.setExecutorGroup(step.getRunsOn().getGroup());
            log.setExecutorImage(step.getRunsOn().getContainer());
        } else {
            log.setRuntimeType("PLATFORM");
        }
        if (ctx.getWorkspace() != null) {
            log.setWorkspacePath(ctx.getWorkspace().toString());
        }
        pipelineRunLogMapper.insert(log);
        return log;
    }

    public void markStarted(PipelineRunLogDO log, String summary) {
        log.setStatus(PipelineRunLogStatusEnum.RUNNING.getStatus());
        log.setSummary(StrUtil.blankToDefault(summary, "步骤开始执行"));
        log.setStartedAt(log.getStartedAt() == null ? LocalDateTime.now() : log.getStartedAt());
        pipelineRunLogMapper.updateById(log);
    }

    public void markSuccess(PipelineRunLogDO log, String summary) {
        log.setStatus(PipelineRunLogStatusEnum.SUCCESS.getStatus());
        log.setSummary(StrUtil.blankToDefault(summary, "步骤执行成功"));
        log.setFinishedAt(LocalDateTime.now());
        pipelineRunLogMapper.updateById(log);
    }

    public void markFailed(PipelineRunLogDO log, String summary, String errorMessage) {
        log.setStatus(PipelineRunLogStatusEnum.FAILED.getStatus());
        log.setSummary(StrUtil.blankToDefault(summary, "步骤执行失败"));
        log.setErrorMessage(StrUtil.subPre(errorMessage, 1000));
        log.setFinishedAt(LocalDateTime.now());
        pipelineRunLogMapper.updateById(log);
    }

    public void markSuspended(PipelineRunLogDO log, String summary) {
        log.setStatus(PipelineRunLogStatusEnum.WAITING_INPUT.getStatus());
        log.setSummary(StrUtil.blankToDefault(summary, "等待外部处理"));
        log.setStartedAt(log.getStartedAt() == null ? LocalDateTime.now() : log.getStartedAt());
        pipelineRunLogMapper.updateById(log);
    }

    public void update(PipelineRunLogDO log) {
        pipelineRunLogMapper.updateById(log);
    }

}
