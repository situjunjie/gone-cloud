package cn.iocoder.yudao.module.devops.service.pipeline.execution.handler;

import cn.iocoder.yudao.module.devops.service.pipeline.execution.PipelineExecutionContext;
import cn.iocoder.yudao.framework.test.core.ut.BaseMockitoUnitTest;
import cn.iocoder.yudao.module.devops.dal.dataobject.pipeline.PipelineRunDO;
import cn.iocoder.yudao.module.devops.framework.pipeline.PipelineSpec;
import cn.iocoder.yudao.module.devops.service.deployment.DeploymentOrderService;
import cn.iocoder.yudao.module.devops.service.deployment.context.DeploymentOrderExecutionResult;
import cn.iocoder.yudao.module.devops.service.pipeline.PipelineNodeRegistryServiceImpl;
import org.junit.jupiter.api.Test;
import org.mockito.InjectMocks;
import org.mockito.Mock;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * {@link K8sDeployStepHandler} 的单元测试。
 */
public class K8sDeployStepHandlerTest extends BaseMockitoUnitTest {

    @InjectMocks
    private K8sDeployStepHandler handler;

    @Mock
    private DeploymentOrderService deploymentOrderService;

    @Test
    public void testSupports() {
        assertTrue(handler.supports(PipelineNodeRegistryServiceImpl.TYPE_K8S_DEPLOY));
        assertFalse(handler.supports(PipelineNodeRegistryServiceImpl.TYPE_K8S_IMAGE_UPGRADE));
        assertEquals(StepRuntimeRequirement.PLATFORM, handler.runtimeRequirement());
    }

    @Test
    public void testHandle_success() {
        PipelineExecutionContext context = buildContext(PipelineNodeRegistryServiceImpl.TYPE_K8S_DEPLOY);
        when(deploymentOrderService.startContainerDeploy(eq(context.getRun()), eq(context.getStep()), eq(7L)))
                .thenReturn(DeploymentOrderExecutionResult.builder()
                        .deploymentOrderId(100L)
                        .success(true)
                        .summary("部署成功")
                        .outputs(Map.of("DEPLOYMENT_ORDER_ID", 100L))
                        .build());

        StepResult result = handler.handle(context);

        assertEquals(StepResultType.CONTINUE, result.getType());
        assertEquals("部署成功", result.getSummary());
        assertEquals(100L, result.getOutputs().get("DEPLOYMENT_ORDER_ID"));
        assertEquals(PipelineStepActionKeyConstants.DEPLOYMENT_ORDER_OPEN_DETAIL, result.getActions().get(0).getKey());
        assertEquals("GET", result.getActions().get(0).getTarget().getMethod());
        assertEquals("/devops/deployment-order/100", result.getActions().get(0).getTarget().getApiPath());
        assertEquals(100L, result.getActions().get(0).getPayload().get("deploymentOrderId"));
    }

    @Test
    public void testHandle_fail() {
        PipelineExecutionContext context = buildContext(PipelineNodeRegistryServiceImpl.TYPE_K8S_DEPLOY);
        when(deploymentOrderService.startContainerDeploy(eq(context.getRun()), eq(context.getStep()), eq(7L)))
                .thenReturn(DeploymentOrderExecutionResult.builder()
                        .success(false)
                        .summary("部署失败")
                        .errorMessage("apply failed")
                        .build());

        StepResult result = handler.handle(context);

        assertEquals(StepResultType.FAIL, result.getType());
        assertEquals("部署失败", result.getSummary());
        assertEquals("apply failed", result.getErrorMessage());
    }

    @Test
    public void testCancel() {
        PipelineExecutionContext context = buildContext(PipelineNodeRegistryServiceImpl.TYPE_K8S_DEPLOY);

        handler.cancel(context);

        verify(deploymentOrderService).cancelContainerDeploy(eq(context.getRun()), eq("deploy"), eq(7L));
    }

    private PipelineExecutionContext buildContext(String stepType) {
        PipelineRunDO run = new PipelineRunDO();
        run.setId(800L);
        run.setTenantId(1L);
        PipelineSpec.ExecutableStep step = new PipelineSpec.ExecutableStep();
        step.setStepId("deploy");
        step.setStep(stepType);
        step.setName(stepType);
        return PipelineExecutionContext.builder()
                .run(run)
                .step(step)
                .sharedState(new ConcurrentHashMap<>())
                .userId(7L)
                .build();
    }

}
