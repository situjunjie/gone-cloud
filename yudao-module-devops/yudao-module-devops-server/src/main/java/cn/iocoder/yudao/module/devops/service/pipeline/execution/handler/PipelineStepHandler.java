package cn.iocoder.yudao.module.devops.service.pipeline.execution.handler;

/**
 * 流水线步骤处理器。
 */
public interface PipelineStepHandler {

    /**
     * 判断当前处理器是否支持指定步骤类型。
     *
     * @param stepType 步骤类型
     * @return 是否支持
     */
    boolean supports(String stepType);

    /**
     * 获取步骤运行时需求。
     *
     * @return 运行时需求
     */
    StepRuntimeRequirement runtimeRequirement();

    /**
     * 执行流水线步骤。
     *
     * @param ctx 步骤处理上下文
     * @return 步骤处理结果
     */
    StepResult handle(PipelineStepContext ctx);

    /**
     * 取消正在执行或挂起的流水线步骤。
     *
     * @param ctx 步骤处理上下文
     */
    default void cancel(PipelineStepContext ctx) {
        // 默认无需处理。
    }

}
