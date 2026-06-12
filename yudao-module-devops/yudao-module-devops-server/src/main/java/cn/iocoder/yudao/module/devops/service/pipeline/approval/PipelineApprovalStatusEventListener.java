package cn.iocoder.yudao.module.devops.service.pipeline.approval;

import cn.iocoder.yudao.module.bpm.api.event.BpmProcessInstanceStatusEvent;
import cn.iocoder.yudao.module.devops.service.pipeline.execution.PipelineExecutionEngine;
import jakarta.annotation.Resource;
import org.springframework.context.ApplicationListener;
import org.springframework.stereotype.Component;

/**
 * 聚合启动模式下监听 BPM 审批结果，推进流水线执行。
 */
@Component
public class PipelineApprovalStatusEventListener implements ApplicationListener<BpmProcessInstanceStatusEvent> {

    @Resource
    private PipelineApprovalService pipelineApprovalService;
    @Resource
    private PipelineExecutionEngine pipelineExecutionEngine;

    @Override
    public void onApplicationEvent(BpmProcessInstanceStatusEvent event) {
        PipelineApprovalStatusHandleResult result = pipelineApprovalService.handleProcessInstanceStatus(event);
        if (Boolean.TRUE.equals(result.getHandled()) && Boolean.TRUE.equals(result.getApproved())) {
            // 审批通过后重入引擎执行(引擎会幂等跳过已完成节点，继续后续节点)
            pipelineExecutionEngine.execute(result.getRun(), null);
        }
    }

}
