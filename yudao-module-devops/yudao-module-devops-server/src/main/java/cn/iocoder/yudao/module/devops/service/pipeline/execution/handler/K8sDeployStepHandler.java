package cn.iocoder.yudao.module.devops.service.pipeline.execution.handler;

import cn.iocoder.yudao.module.devops.service.deployment.DeploymentOrderService;
import cn.iocoder.yudao.module.devops.service.deployment.context.DeploymentOrderExecutionResult;
import cn.iocoder.yudao.module.devops.service.pipeline.PipelineNodeRegistryServiceImpl;
import cn.iocoder.yudao.module.devops.service.pipeline.execution.PipelineExecutionContext;
import jakarta.annotation.Resource;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

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
    public StepResult handle(PipelineExecutionContext ctx) {
        try {
            DeploymentOrderExecutionResult result = deploymentOrderService.startContainerDeploy(ctx.getRun(),
                    ctx.getResolvedStep(), ctx.getUserId());
            if (result.isSuccess()) {
                return StepResult.builder()
                        .type(StepResultType.CONTINUE)
                        .summary(result.getSummary())
                        .outputs(result.getOutputs())
                        .actions(buildDeploymentOrderActions(ctx, result))
                        .build();
            }
            return StepResult.builder()
                    .type(StepResultType.FAIL)
                    .summary(result.getSummary())
                    .errorMessage(result.getErrorMessage())
                    .outputs(result.getOutputs())
                    .actions(buildDeploymentOrderActions(ctx, result))
                    .build();
        } catch (Exception ex) {
            log.error("[K8sDeployStepHandler][runId({}) stepId({}) 执行异常]",
                    ctx.getRun().getId(), ctx.getStep().getStepId(), ex);
            return StepResult.fail("K8s 集群部署失败", ex.getMessage());
        }
    }

    @Override
    public void cancel(PipelineExecutionContext ctx) {
        deploymentOrderService.cancelContainerDeploy(ctx.getRun(), ctx.getStep().getStepId(), ctx.getUserId());
    }

    private List<PipelineStepAction> buildDeploymentOrderActions(PipelineExecutionContext ctx,
                                                                 DeploymentOrderExecutionResult result) {
        Long deploymentOrderId = resolveDeploymentOrderId(result);
        if (deploymentOrderId == null) {
            return List.of();
        }
        Map<String, Object> payload = new LinkedHashMap<>();
        payload.put("runId", ctx.getRun().getId());
        payload.put("stepId", ctx.getStep().getStepId());
        payload.put("deploymentOrderId", deploymentOrderId);
        payload.put("modal", "DEPLOYMENT_ORDER_DETAIL");
        return List.of(PipelineStepAction.builder()
                .key(PipelineStepActionKeyConstants.DEPLOYMENT_ORDER_OPEN_DETAIL)
                .label("查看部署单")
                .type(PipelineStepActionTypeEnum.API.getType())
                .style("PRIMARY")
                .permission("devops:pipeline:query")
                .enabled(true)
                .target(PipelineStepActionTarget.builder()
                        .apiPath("/devops/deployment-order/" + deploymentOrderId)
                        .method("GET")
                        .build())
                .payload(payload)
                .order(150)
                .build());
    }

    private Long resolveDeploymentOrderId(DeploymentOrderExecutionResult result) {
        if (result == null) {
            return null;
        }
        if (result.getDeploymentOrderId() != null) {
            return result.getDeploymentOrderId();
        }
        Object outputValue = result.getOutputs() == null ? null : result.getOutputs().get("DEPLOYMENT_ORDER_ID");
        if (outputValue instanceof Number number) {
            return number.longValue();
        }
        if (outputValue == null) {
            return null;
        }
        try {
            return Long.valueOf(String.valueOf(outputValue));
        } catch (NumberFormatException ignored) {
            return null;
        }
    }

}
