package cn.iocoder.yudao.module.devops.service.pipeline.execution.handler;

import cn.iocoder.yudao.framework.common.exception.util.ServiceExceptionUtil;
import cn.iocoder.yudao.framework.common.util.json.JsonUtils;
import cn.iocoder.yudao.framework.test.core.ut.BaseMockitoUnitTest;
import cn.iocoder.yudao.module.devops.dal.dataobject.pipeline.PipelineRunDO;
import cn.iocoder.yudao.module.devops.framework.pipeline.PipelineSpec;
import cn.iocoder.yudao.module.devops.service.change.ChangeService;
import cn.iocoder.yudao.module.devops.service.pipeline.PipelineNodeRegistryServiceImpl;
import cn.iocoder.yudao.module.devops.service.pipeline.execution.context.PipelineRunChangeSnapshotContext;
import org.junit.jupiter.api.Test;
import org.mockito.InjectMocks;
import org.mockito.Mock;

import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

import static cn.iocoder.yudao.module.devops.enums.ErrorCodeConstants.CHANGE_BRANCH_MERGE_FAIL;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.verify;

/**
 * {@link ChangePublishFinalizeStepHandler} 的单元测试。
 */
public class ChangePublishFinalizeStepHandlerTest extends BaseMockitoUnitTest {

    @InjectMocks
    private ChangePublishFinalizeStepHandler handler;

    @Mock
    private ChangeService changeService;

    @Test
    public void testSupports() {
        assertTrue(handler.supports(PipelineNodeRegistryServiceImpl.TYPE_CHANGE_PUBLISH_FINALIZE));
        assertFalse(handler.supports(PipelineNodeRegistryServiceImpl.TYPE_COMMAND));
        assertEquals(StepRuntimeRequirement.PLATFORM, handler.runtimeRequirement());
    }

    @Test
    public void testHandle_successWithSnapshots() {
        PipelineStepContext context = buildContext(buildRunWithSnapshots());

        StepResult result = handler.handle(context);

        verify(changeService).finalizePublishedChange(eq(11L));
        verify(changeService).finalizePublishedChange(eq(12L));
        assertEquals(StepResultType.CONTINUE, result.getType());
        assertEquals("发布收尾完成", result.getSummary());
        assertEquals(2, result.getOutputs().get("finalizedChangeCount"));
    }

    @Test
    public void testHandle_successWithSingleFallback() {
        PipelineRunDO run = new PipelineRunDO();
        run.setId(800L);
        run.setChangeId(99L);

        StepResult result = handler.handle(buildContext(run));

        verify(changeService).finalizePublishedChange(eq(99L));
        assertEquals(StepResultType.CONTINUE, result.getType());
    }

    @Test
    public void testHandle_fail() {
        PipelineRunDO run = buildRunWithSnapshots();
        doThrow(ServiceExceptionUtil.exception(CHANGE_BRANCH_MERGE_FAIL, "conflict"))
                .when(changeService).finalizePublishedChange(eq(11L));

        StepResult result = handler.handle(buildContext(run));

        assertEquals(StepResultType.FAIL, result.getType());
        assertEquals(String.valueOf(CHANGE_BRANCH_MERGE_FAIL.getCode()), result.getErrorCode());
    }

    private PipelineStepContext buildContext(PipelineRunDO run) {
        PipelineSpec.ExecutableStep step = new PipelineSpec.ExecutableStep();
        step.setStepId("finalize");
        step.setStep(PipelineNodeRegistryServiceImpl.TYPE_CHANGE_PUBLISH_FINALIZE);
        step.setName("发布收尾");
        step.setWith(Map.of());
        return PipelineStepContext.builder()
                .run(run)
                .step(step)
                .sharedState(new ConcurrentHashMap<>())
                .userId(7L)
                .build();
    }

    private PipelineRunDO buildRunWithSnapshots() {
        PipelineRunDO run = new PipelineRunDO();
        run.setId(800L);
        run.setChangeSnapshotJson(JsonUtils.toJsonString(List.of(
                new PipelineRunChangeSnapshotContext(11L, "sha-11"),
                new PipelineRunChangeSnapshotContext(12L, "sha-12"))));
        return run;
    }

}
