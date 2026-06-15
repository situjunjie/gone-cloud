package cn.iocoder.yudao.module.devops.service.pipeline.execution.handler;

import cn.iocoder.yudao.module.devops.service.pipeline.PipelineNodeRegistryServiceImpl;
import cn.iocoder.yudao.module.devops.service.pipeline.approval.PipelineApprovalExecutionStatus;
import cn.iocoder.yudao.module.devops.service.pipeline.approval.PipelineApprovalService;
import jakarta.annotation.Resource;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

/**
 * 审批节点处理器。
 */
@Slf4j
@Component
public class ApprovalNodeHandler implements PipelineNodeHandler {

    @Resource
    private PipelineApprovalService pipelineApprovalService;

    @Override
    public boolean supports(String nodeType) {
        return PipelineNodeRegistryServiceImpl.TYPE_APPROVAL.equals(nodeType);
    }

    @Override
    public NodeOutcome handle(PipelineNodeContext ctx) {
        try {
            PipelineApprovalExecutionStatus status = pipelineApprovalService.startApproval(ctx.getRun(), ctx.getStep(),
                    ctx.getUserId());
            if (status == PipelineApprovalExecutionStatus.SUCCESS) {
                return NodeOutcome.CONTINUE;
            }
            if (status == PipelineApprovalExecutionStatus.SUSPEND) {
                return NodeOutcome.SUSPEND;
            }
            log.error("[ApprovalNodeHandler][runId({}) stepId({}) 审批节点失败]",
                    ctx.getRun().getId(), ctx.getStep().getStepId());
            return NodeOutcome.FAIL;
        } catch (Exception ex) {
            log.error("[ApprovalNodeHandler][runId({}) stepId({}) 审批节点异常]",
                    ctx.getRun().getId(), ctx.getStep().getStepId(), ex);
            return NodeOutcome.FAIL;
        }
    }

}
