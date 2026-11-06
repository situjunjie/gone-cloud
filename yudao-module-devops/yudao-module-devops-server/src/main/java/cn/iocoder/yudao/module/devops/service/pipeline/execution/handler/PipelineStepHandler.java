package cn.iocoder.yudao.module.devops.service.pipeline.execution.handler;

import cn.iocoder.yudao.module.devops.service.pipeline.execution.PipelineExecutionContext;

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
     * 根据当前步骤参数解析运行时需求，默认保持原处理器行为。
     *
     * @param ctx 步骤处理上下文
     * @return 当前步骤的运行时需求
     */
    default StepRuntimeRequirement runtimeRequirement(PipelineExecutionContext ctx) {
        return runtimeRequirement();
    }

    /**
     * 当前步骤执行前是否需要准备源码到 run workspace。
     *
     * @return 是否需要源码工作区
     */
    default boolean sourceWorkspaceRequired() {
        return false;
    }

    /**
     * 执行流水线步骤。
     *
     * @param ctx 步骤处理上下文
     * @return 步骤处理结果
     */
    StepResult handle(PipelineExecutionContext ctx);

    /**
     * 取消正在执行或挂起的流水线步骤。
     *
     * @param ctx 步骤处理上下文
     */
    default void cancel(PipelineExecutionContext ctx) {
        // 默认无需处理。
    }

}
