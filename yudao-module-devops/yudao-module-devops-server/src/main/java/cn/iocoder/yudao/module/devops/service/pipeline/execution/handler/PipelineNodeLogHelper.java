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
 * RunLog 写入辅助。handler 复用该组件写 node-level log。
 */
@Component
public class PipelineNodeLogHelper {

    @Resource
    private PipelineRunLogMapper pipelineRunLogMapper;

    /**
     * 获取或创建节点级日志,状态 PENDING。
     */
    public PipelineRunLogDO getOrCreateLog(PipelineNodeContext ctx) {
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
        log.setNodeId(step.getStepId());
        log.setNodeType(step.getStep());
        log.setNodeName(StrUtil.blankToDefault(step.getName(), step.getStep()));
        log.setLogLevel(PipelineRunLogLevelEnum.NODE.getLevel());
        log.setStatus(PipelineRunLogStatusEnum.PENDING.getStatus());
        log.setSort(100);
        log.setAttempt(1);
        if (step.getRunsOn() != null) {
            log.setRuntimeType("DOCKER");
            log.setExecutorGroup(step.getRunsOn().getGroup());
            log.setExecutorImage(step.getRunsOn().getContainer());
        }
        pipelineRunLogMapper.insert(log);
        return log;
    }

    /**
     * 标记节点开始(RUNNING)。
     */
    public void markStarted(PipelineRunLogDO log, String summary) {
        log.setStatus(PipelineRunLogStatusEnum.RUNNING.getStatus());
        log.setSummary(StrUtil.blankToDefault(summary, "节点开始执行"));
        log.setStartedAt(log.getStartedAt() == null ? LocalDateTime.now() : log.getStartedAt());
        pipelineRunLogMapper.updateById(log);
    }

    /**
     * 标记节点成功(SUCCESS)。
     */
    public void markSuccess(PipelineRunLogDO log, String summary) {
        log.setStatus(PipelineRunLogStatusEnum.SUCCESS.getStatus());
        log.setSummary(StrUtil.blankToDefault(summary, "节点执行成功"));
        log.setFinishedAt(LocalDateTime.now());
        pipelineRunLogMapper.updateById(log);
    }

    /**
     * 标记节点失败(FAILED)。
     */
    public void markFailed(PipelineRunLogDO log, String summary, String errorMessage) {
        log.setStatus(PipelineRunLogStatusEnum.FAILED.getStatus());
        log.setSummary(StrUtil.blankToDefault(summary, "节点执行失败"));
        log.setErrorMessage(StrUtil.subPre(errorMessage, 1000));
        log.setFinishedAt(LocalDateTime.now());
        pipelineRunLogMapper.updateById(log);
    }

    /**
     * 标记节点挂起(WAITING_INPUT)。
     */
    public void markSuspended(PipelineRunLogDO log, String summary) {
        log.setStatus(PipelineRunLogStatusEnum.WAITING_INPUT.getStatus());
        log.setSummary(StrUtil.blankToDefault(summary, "等待人工处理"));
        log.setStartedAt(log.getStartedAt() == null ? LocalDateTime.now() : log.getStartedAt());
        pipelineRunLogMapper.updateById(log);
    }

}
