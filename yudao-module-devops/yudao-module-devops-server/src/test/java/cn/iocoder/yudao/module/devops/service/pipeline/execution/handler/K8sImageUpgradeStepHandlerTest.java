package cn.iocoder.yudao.module.devops.service.pipeline.execution.handler;

import cn.iocoder.yudao.framework.test.core.ut.BaseMockitoUnitTest;
import cn.iocoder.yudao.module.devops.dal.dataobject.pipeline.PipelineRunDO;
import cn.iocoder.yudao.module.devops.framework.pipeline.PipelineSpec;
import cn.iocoder.yudao.module.devops.service.deployment.DeploymentOrderService;
import cn.iocoder.yudao.module.devops.service.deployment.context.DeploymentOrderExecutionResult;
import cn.iocoder.yudao.module.devops.service.pipeline.PipelineNodeRegistryServiceImpl;
import org.junit.jupiter.api.Test;
import org.mockito.InjectMocks;
import org.mockito.Mock;

import java.util.concurrent.ConcurrentHashMap;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * {@link K8sImageUpgradeStepHandler} 的单元测试。
 */
public class K8sImageUpgradeStepHandlerTest extends BaseMockitoUnitTest {

    @InjectMocks
    private K8sImageUpgradeStepHandler handler;

    @Mock
    private DeploymentOrderService deploymentOrderService;

    @Test
    public void testSupports() {
        assertTrue(handler.supports(PipelineNodeRegistryServiceImpl.TYPE_K8S_IMAGE_UPGRADE));
        assertFalse(handler.supports(PipelineNodeRegistryServiceImpl.TYPE_K8S_DEPLOY));
        assertEquals(StepRuntimeRequirement.PLATFORM, handler.runtimeRequirement());
    }

    @Test
    public void testHandle_success() {
        PipelineStepContext context = buildContext();
        when(deploymentOrderService.startContainerDeploy(eq(context.getRun()), eq(context.getStep()), eq(7L)))
                .thenReturn(DeploymentOrderExecutionResult.builder()
                        .success(true)
                        .summary("镜像版本升级成功")
                        .build());

        StepResult result = handler.handle(context);

        assertEquals(StepResultType.CONTINUE, result.getType());
        assertEquals("镜像版本升级成功", result.getSummary());
    }

    @Test
    public void testCancel() {
        PipelineStepContext context = buildContext();

        handler.cancel(context);

        verify(deploymentOrderService).cancelContainerDeploy(eq(context.getRun()), eq("upgrade"), eq(7L));
    }

    private PipelineStepContext buildContext() {
        PipelineRunDO run = new PipelineRunDO();
        run.setId(800L);
        run.setTenantId(1L);
        PipelineSpec.ExecutableStep step = new PipelineSpec.ExecutableStep();
        step.setStepId("upgrade");
        step.setStep(PipelineNodeRegistryServiceImpl.TYPE_K8S_IMAGE_UPGRADE);
        step.setName("K8s 镜像版本升级");
        return PipelineStepContext.builder()
                .run(run)
                .step(step)
                .sharedState(new ConcurrentHashMap<>())
                .userId(7L)
                .build();
    }

}
