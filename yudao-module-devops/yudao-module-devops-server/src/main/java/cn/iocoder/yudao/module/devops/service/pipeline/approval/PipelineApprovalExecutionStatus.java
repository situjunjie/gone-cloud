package cn.iocoder.yudao.module.devops.service.pipeline.approval;

/**
 * 流水线审批节点执行状态。
 */
public enum PipelineApprovalExecutionStatus {

    /**
     * 审批已通过，责任链可继续。
     */
    SUCCESS,

    /**
     * 审批已发起或仍在等待外部审批结果，责任链挂起。
     */
    SUSPEND,

    /**
     * 审批被拒绝、取消或异常失败，责任链终止。
     */
    FAIL

}
