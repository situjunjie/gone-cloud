package cn.iocoder.yudao.module.devops.service.pipeline.execution.handler;

/**
 * 流水线步骤运行时需求。
 */
public enum StepRuntimeRequirement {

    /**
     * 平台运行时，不需要创建任务隔离环境。
     */
    PLATFORM,
    /**
     * 任务运行时，需要创建隔离环境后执行。
     */
    JOB_RUNTIME

}
