package cn.iocoder.yudao.module.devops.service.pipeline.execution.handler;

import cn.iocoder.yudao.module.devops.service.deployment.DeploymentOrderService;
import cn.iocoder.yudao.module.devops.service.deployment.context.DeploymentOrderExecutionResult;
import cn.iocoder.yudao.module.devops.service.pipeline.PipelineNodeRegistryServiceImpl;
import jakarta.annotation.Resource;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

/**
 * K8s 集群部署步骤处理器。
 */
@Slf4j
@Component
public class K8sDeployStepHandler implements PipelineStepHandler {

    @Resource
    private DeploymentOrderService deploymentOrderService;

    @Override
    public boolean supports(String stepType) {
        return PipelineNodeRegistryServiceImpl.TYPE_K8S_DEPLOY.equals(stepType);
    }

    @Override
    public StepRuntimeRequirement runtimeRequirement() {
        return StepRuntimeRequirement.PLATFORM;
    }

    @Override
    public StepResult handle(PipelineStepContext ctx) {
        try {
            DeploymentOrderExecutionResult result = deploymentOrderService.startContainerDeploy(ctx.getRun(),
                    ctx.getStep(), ctx.getUserId());
            if (result.isSuccess()) {
                return StepResult.builder()
                        .type(StepResultType.CONTINUE)
                        .summary(result.getSummary())
                        .outputs(result.getOutputs())
                        .build();
            }
            return StepResult.fail(result.getSummary(), result.getErrorMessage());
        } catch (Exception ex) {
            log.error("[K8sDeployStepHandler][runId({}) stepId({}) 执行异常]",
                    ctx.getRun().getId(), ctx.getStep().getStepId(), ex);
            return StepResult.fail("K8s 集群部署失败", ex.getMessage());
        }
    }

    @Override
    public void cancel(PipelineStepContext ctx) {
        deploymentOrderService.cancelContainerDeploy(ctx.getRun(), ctx.getStep().getStepId(), ctx.getUserId());
    }

}
