package cn.iocoder.yudao.module.devops.service.pipeline.execution.handler;

import org.springframework.stereotype.Component;

import java.util.List;

/**
 * 流水线步骤处理器注册表。
 */
@Component
public class PipelineStepHandlerRegistry {

    private final List<PipelineStepHandler> handlers;

    public PipelineStepHandlerRegistry(List<PipelineStepHandler> handlers) {
        this.handlers = handlers;
    }

    public PipelineStepHandler resolve(String stepType) {
        for (PipelineStepHandler handler : handlers) {
            if (handler.supports(stepType)) {
                return handler;
            }
        }
        return null;
    }

}
