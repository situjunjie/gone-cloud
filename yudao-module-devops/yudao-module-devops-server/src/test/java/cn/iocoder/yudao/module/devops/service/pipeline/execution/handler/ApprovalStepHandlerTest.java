package cn.iocoder.yudao.module.devops.service.pipeline.execution.handler;

import cn.iocoder.yudao.framework.test.core.ut.BaseMockitoUnitTest;
import cn.iocoder.yudao.module.devops.dal.dataobject.pipeline.PipelineRunDO;
import cn.iocoder.yudao.module.devops.framework.pipeline.PipelineSpec;
import cn.iocoder.yudao.module.devops.service.pipeline.PipelineNodeRegistryServiceImpl;
import cn.iocoder.yudao.module.devops.service.pipeline.approval.PipelineApprovalExecutionStatus;
import cn.iocoder.yudao.module.devops.service.pipeline.approval.PipelineApprovalService;
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
 * {@link ApprovalStepHandler} 的单元测试。
 */
public class ApprovalStepHandlerTest extends BaseMockitoUnitTest {

    @InjectMocks
    private ApprovalStepHandler handler;

    @Mock
    private PipelineApprovalService pipelineApprovalService;

    @Test
    public void testSupports() {
        assertTrue(handler.supports(PipelineNodeRegistryServiceImpl.TYPE_APPROVAL));
        assertFalse(handler.supports(PipelineNodeRegistryServiceImpl.TYPE_COMMAND));
        assertEquals(StepRuntimeRequirement.PLATFORM, handler.runtimeRequirement());
    }

    @Test
    public void testHandle_suspend() {
        PipelineRunDO run = buildRun();
        PipelineSpec.ExecutableStep step = buildStep();
        PipelineStepContext context = buildContext(run, step);
        when(pipelineApprovalService.startApproval(eq(run), eq(step), eq(7L)))
                .thenReturn(PipelineApprovalExecutionStatus.SUSPEND);

        StepResult result = handler.handle(context);

        assertEquals(StepResultType.SUSPEND, result.getType());
        assertEquals("等待审批", result.getSummary());
    }

    @Test
    public void testHandle_success() {
        PipelineRunDO run = buildRun();
        PipelineSpec.ExecutableStep step = buildStep();
        PipelineStepContext context = buildContext(run, step);
        when(pipelineApprovalService.startApproval(eq(run), eq(step), eq(7L)))
                .thenReturn(PipelineApprovalExecutionStatus.SUCCESS);

        StepResult result = handler.handle(context);

        assertEquals(StepResultType.CONTINUE, result.getType());
        assertEquals("审批通过", result.getSummary());
    }

    @Test
    public void testHandle_fail() {
        PipelineRunDO run = buildRun();
        PipelineSpec.ExecutableStep step = buildStep();
        PipelineStepContext context = buildContext(run, step);
        when(pipelineApprovalService.startApproval(eq(run), eq(step), eq(7L)))
                .thenReturn(PipelineApprovalExecutionStatus.FAIL);

        StepResult result = handler.handle(context);

        assertEquals(StepResultType.FAIL, result.getType());
        assertEquals("审批未通过或已取消", result.getErrorMessage());
    }

    @Test
    public void testCancel() {
        PipelineRunDO run = buildRun();
        PipelineSpec.ExecutableStep step = buildStep();
        PipelineStepContext context = buildContext(run, step);

        handler.cancel(context);

        verify(pipelineApprovalService).cancelApproval(eq(run), eq("approval"), eq(7L));
    }

    private PipelineStepContext buildContext(PipelineRunDO run, PipelineSpec.ExecutableStep step) {
        return PipelineStepContext.builder()
                .run(run)
                .step(step)
                .sharedState(new ConcurrentHashMap<>())
                .userId(7L)
                .build();
    }

    private PipelineRunDO buildRun() {
        PipelineRunDO run = new PipelineRunDO();
        run.setId(800L);
        run.setTenantId(1L);
        run.setTriggerUserId(7L);
        return run;
    }

    private PipelineSpec.ExecutableStep buildStep() {
        PipelineSpec.ExecutableStep step = new PipelineSpec.ExecutableStep();
        step.setStepId("approval");
        step.setStep(PipelineNodeRegistryServiceImpl.TYPE_APPROVAL);
        step.setName("发布审批");
        step.setWith(Map.of("processDefinitionKey", "devops_deploy_approval"));
        return step;
    }

}
