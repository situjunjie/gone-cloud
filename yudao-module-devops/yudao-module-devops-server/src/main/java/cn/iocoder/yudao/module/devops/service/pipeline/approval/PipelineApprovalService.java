package cn.iocoder.yudao.module.devops.service.pipeline.approval;

import cn.iocoder.yudao.module.bpm.api.event.BpmProcessInstanceStatusEvent;
import cn.iocoder.yudao.module.devops.dal.dataobject.pipeline.PipelineRunDO;
import cn.iocoder.yudao.module.devops.framework.pipeline.PipelineSpec;

/**
 * 流水线审批服务。
 */
public interface PipelineApprovalService {

    String BUSINESS_KEY_PREFIX = "devops:pipeline-approval:";

    /**
     * 判断业务编号是否属于流水线审批。
     *
     * @param businessKey BPM 业务编号
     * @return 是否属于流水线审批
     */
    boolean isApprovalBusinessKey(String businessKey);

    /**
     * 发起或恢复审批步骤。
     *
     * @param run 流水线运行记录
     * @param step 可执行步骤定义
     * @param userId 操作人编号
     * @return 审批步骤执行状态
     */
    PipelineApprovalExecutionStatus startApproval(PipelineRunDO run, PipelineSpec.ExecutableStep step, Long userId);

    /**
     * 取消审批步骤：取消关联的 BPM 流程实例，并将审批步骤日志置为已取消。
     *
     * @param run    流水线运行记录
     * @param stepId 审批步骤编号
     * @param userId 操作人编号
     */
    void cancelApproval(PipelineRunDO run, String stepId, Long userId);

    /**
     * 处理 BPM 流程实例状态事件。
     *
     * @param event BPM 流程实例状态事件
     * @return 事件处理结果
     */
    PipelineApprovalStatusHandleResult handleProcessInstanceStatus(BpmProcessInstanceStatusEvent event);

}
