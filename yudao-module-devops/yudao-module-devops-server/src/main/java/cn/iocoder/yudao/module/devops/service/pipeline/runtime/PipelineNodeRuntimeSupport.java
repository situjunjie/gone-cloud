package cn.iocoder.yudao.module.devops.service.pipeline.runtime;

import cn.hutool.core.util.StrUtil;
import cn.iocoder.yudao.framework.common.util.json.JsonUtils;
import cn.iocoder.yudao.module.devops.controller.admin.pipelinerun.vo.PipelineJenkinsCallbackReqVO;
import cn.iocoder.yudao.module.devops.dal.dataobject.pipeline.PipelineRunDO;
import cn.iocoder.yudao.module.devops.dal.dataobject.pipeline.log.PipelineRunLogDO;
import cn.iocoder.yudao.module.devops.dal.mysql.pipeline.PipelineRunMapper;
import cn.iocoder.yudao.module.devops.dal.mysql.pipeline.log.PipelineRunLogMapper;
import cn.iocoder.yudao.module.devops.enums.PipelineRunLogLevelEnum;
import cn.iocoder.yudao.module.devops.enums.PipelineRunLogStatusEnum;
import cn.iocoder.yudao.module.devops.enums.PipelineRunStatusEnum;
import cn.iocoder.yudao.module.devops.framework.pipeline.PipelineSpec;
import jakarta.annotation.Resource;
import org.springframework.stereotype.Component;

import java.time.LocalDateTime;

/**
 * 节点运行时状态更新支持。
 */
@Component
public class PipelineNodeRuntimeSupport {

    @Resource
    private PipelineRunMapper pipelineRunMapper;
    @Resource
    private PipelineRunLogMapper pipelineRunLogMapper;

    public void markStarted(PipelineNodeCallbackContext context) {
        PipelineRunLogDO log = getOrCreateLog(context);
        log.setStatus(PipelineRunLogStatusEnum.RUNNING.getStatus());
        log.setSummary(StrUtil.blankToDefault(context.getCallback().getMessage(), "节点开始执行"));
        log.setContextJson(JsonUtils.toJsonString(context.getCallback()));
        log.setStartedAt(log.getStartedAt() == null ? LocalDateTime.now() : log.getStartedAt());
        pipelineRunLogMapper.updateById(log);
    }

    public void markCompleted(PipelineNodeCallbackContext context) {
        PipelineRunLogDO log = getOrCreateLog(context);
        log.setStatus(PipelineRunLogStatusEnum.SUCCESS.getStatus());
        log.setSummary(StrUtil.blankToDefault(context.getCallback().getMessage(), "节点执行成功"));
        log.setResultJson(JsonUtils.toJsonString(context.getCallback()));
        log.setFinishedAt(LocalDateTime.now());
        pipelineRunLogMapper.updateById(log);
    }

    public void markFailed(PipelineNodeCallbackContext context) {
        PipelineRunLogDO log = getOrCreateLog(context);
        String message = StrUtil.blankToDefault(context.getCallback().getMessage(), "节点执行失败");
        log.setStatus(PipelineRunLogStatusEnum.FAILED.getStatus());
        log.setSummary(message);
        log.setErrorMessage(StrUtil.subPre(message, 1000));
        log.setResultJson(JsonUtils.toJsonString(context.getCallback()));
        log.setFinishedAt(LocalDateTime.now());
        pipelineRunLogMapper.updateById(log);

        PipelineRunDO run = context.getRun();
        run.setRunStatus(PipelineRunStatusEnum.FAILED.getStatus());
        run.setFinishedAt(LocalDateTime.now());
        run.setErrorMessage(StrUtil.subPre(message, 1000));
        pipelineRunMapper.updateById(run);
    }

    private PipelineRunLogDO getOrCreateLog(PipelineNodeCallbackContext context) {
        PipelineJenkinsCallbackReqVO callback = context.getCallback();
        PipelineRunLogDO log = pipelineRunLogMapper.selectByPipelineRunIdAndNodeId(
                context.getRun().getId(), callback.getNodeId());
        if (log != null) {
            return log;
        }
        PipelineSpec.Node node = context.getNode();
        log = new PipelineRunLogDO();
        log.setPipelineRunId(context.getRun().getId());
        log.setNodeId(callback.getNodeId());
        log.setNodeType(callback.getNodeType());
        log.setNodeName(StrUtil.blankToDefault(callback.getNodeName(), node.getName()));
        log.setLogLevel(PipelineRunLogLevelEnum.NODE.getLevel());
        log.setStatus(PipelineRunLogStatusEnum.PENDING.getStatus());
        log.setSort(100);
        pipelineRunLogMapper.insert(log);
        return log;
    }

}
