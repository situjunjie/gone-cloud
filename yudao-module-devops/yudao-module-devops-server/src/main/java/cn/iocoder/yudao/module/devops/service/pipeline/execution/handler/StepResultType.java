package cn.iocoder.yudao.module.devops.service.pipeline.execution.handler;

/**
 * 流水线步骤处理结果类型。
 */
public enum StepResultType {

    /**
     * 继续执行后续步骤。
     */
    CONTINUE,
    /**
     * 挂起当前任务，等待外部处理。
     */
    SUSPEND,
    /**
     * 标记步骤执行失败。
     */
    FAIL

}
