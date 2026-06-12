package cn.iocoder.yudao.module.devops.service.pipeline.execution.handler;

import cn.iocoder.yudao.module.devops.dal.dataobject.pipeline.log.PipelineRunLogDO;
import cn.iocoder.yudao.module.devops.framework.pipeline.PipelineSpec;
import cn.iocoder.yudao.module.devops.service.pipeline.PipelineNodeRegistryServiceImpl;
import cn.iocoder.yudao.module.devops.service.pipeline.approval.PipelineApprovalService;
import jakarta.annotation.Resource;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

/**
 * 审批节点处理器。
 *
 * <p>触发 BPM 流程实例并返回 SUSPEND,等 BPM 完成事件推进链。
 */
@Slf4j
@Component
public class ApprovalNodeHandler implements PipelineNodeHandler {

    @Resource
    private PipelineApprovalService pipelineApprovalService;
    @Resource
    private PipelineNodeLogHelper logHelper;

    @Override
    public boolean supports(String nodeType) {
        return PipelineNodeRegistryServiceImpl.TYPE_APPROVAL.equals(nodeType);
    }

    @Override
    public NodeOutcome handle(PipelineNodeContext ctx) {
        PipelineRunLogDO runLog = logHelper.getOrCreateLog(ctx);
        PipelineSpec.Node node = ctx.getNode();

        // 已成功完成则跳过(幂等重入)
        if ("SUCCESS".equals(runLog.getStatus())) {
            log.info("[ApprovalNodeHandler][runId({}) nodeId({}) 已成功,跳过]",
                    ctx.getRun().getId(), node.getId());
            return NodeOutcome.CONTINUE;
        }

        // 正在等待审批则继续挂起
        if ("WAITING_INPUT".equals(runLog.getStatus())) {
            log.info("[ApprovalNodeHandler][runId({}) nodeId({}) 仍在审批中,保持挂起]",
                    ctx.getRun().getId(), node.getId());
            return NodeOutcome.SUSPEND;
        }

        // 首次进入:触发审批
        try {
            logHelper.markStarted(runLog, "发起审批");
            pipelineApprovalService.startApproval(ctx.getRun(), node, ctx.getUserId());
            logHelper.markSuspended(runLog, "等待审批");
            log.info("[ApprovalNodeHandler][runId({}) nodeId({}) 审批已触发,挂起等结果]",
                    ctx.getRun().getId(), node.getId());
            return NodeOutcome.SUSPEND;
        } catch (Exception ex) {
            log.error("[ApprovalNodeHandler][runId({}) nodeId({}) 触发审批失败]",
                    ctx.getRun().getId(), node.getId(), ex);
            logHelper.markFailed(runLog, "触发审批失败", ex.getMessage());
            return NodeOutcome.FAIL;
        }
    }

}
