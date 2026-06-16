package cn.iocoder.yudao.module.devops.service.pipeline.execution.handler;

import cn.iocoder.yudao.module.devops.service.pipeline.PipelineNodeRegistryServiceImpl;
import cn.iocoder.yudao.module.devops.service.pipeline.approval.PipelineApprovalExecutionStatus;
import cn.iocoder.yudao.module.devops.service.pipeline.approval.PipelineApprovalService;
import jakarta.annotation.Resource;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

/**
 * 审批步骤处理器。
 */
@Slf4j
@Component
public class ApprovalStepHandler implements PipelineStepHandler {

    @Resource
    private PipelineApprovalService pipelineApprovalService;

    @Override
    public boolean supports(String stepType) {
        return PipelineNodeRegistryServiceImpl.TYPE_APPROVAL.equals(stepType);
    }

    @Override
    public StepRuntimeRequirement runtimeRequirement() {
        return StepRuntimeRequirement.PLATFORM;
    }

    @Override
    public StepResult handle(PipelineStepContext ctx) {
        try {
            PipelineApprovalExecutionStatus status = pipelineApprovalService.startApproval(ctx.getRun(), ctx.getStep(),
                    ctx.getUserId());
            if (status == PipelineApprovalExecutionStatus.SUCCESS) {
                return StepResult.continueWith("审批通过");
            }
            if (status == PipelineApprovalExecutionStatus.SUSPEND) {
                return StepResult.suspend("等待审批");
            }
            log.error("[ApprovalStepHandler][runId({}) stepId({}) 审批失败]",
                    ctx.getRun().getId(), ctx.getStep().getStepId());
            return StepResult.fail("审批失败", "审批未通过或已取消");
        } catch (Exception ex) {
            log.error("[ApprovalStepHandler][runId({}) stepId({}) 审批异常]",
                    ctx.getRun().getId(), ctx.getStep().getStepId(), ex);
            return StepResult.fail("审批失败", ex.getMessage());
        }
    }

    @Override
    public void cancel(PipelineStepContext ctx) {
        pipelineApprovalService.cancelApproval(ctx.getRun(), ctx.getStep().getStepId(), ctx.getUserId());
    }

}
