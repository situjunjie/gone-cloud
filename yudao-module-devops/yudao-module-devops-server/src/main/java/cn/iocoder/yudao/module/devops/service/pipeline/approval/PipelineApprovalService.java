package cn.iocoder.yudao.module.devops.service.pipeline.approval;

import cn.iocoder.yudao.module.bpm.api.event.BpmProcessInstanceStatusEvent;
import cn.iocoder.yudao.module.devops.dal.dataobject.pipeline.PipelineRunDO;
import cn.iocoder.yudao.module.devops.framework.pipeline.PipelineSpec;

public interface PipelineApprovalService {

    String BUSINESS_KEY_PREFIX = "devops:pipeline-approval:";

    boolean isApprovalBusinessKey(String businessKey);

    void startApproval(PipelineRunDO run, PipelineSpec.Node node, Long userId);

    /**
     * 取消审批节点：取消关联的 BPM 流程实例，并将审批节点日志置为已取消。
     *
     * @param run    流水线运行
     * @param nodeId 审批节点 ID
     * @param userId 操作人编号
     */
    void cancelApproval(PipelineRunDO run, String nodeId, Long userId);

    PipelineApprovalStatusHandleResult handleProcessInstanceStatus(BpmProcessInstanceStatusEvent event);

}
