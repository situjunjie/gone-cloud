package cn.iocoder.yudao.module.devops.service.pipeline.execution.handler;

import cn.iocoder.yudao.framework.test.core.ut.BaseMockitoUnitTest;
import cn.iocoder.yudao.module.devops.dal.dataobject.pipeline.log.PipelineRunLogDO;
import cn.iocoder.yudao.module.devops.framework.pipeline.runtime.PipelineJobRuntime;
import cn.iocoder.yudao.module.devops.service.pipeline.execution.PipelineExecutionContext;
import org.junit.jupiter.api.Test;
import org.mockito.InjectMocks;
import org.mockito.Mock;

import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.when;

class CheckoutStepHandlerTest extends BaseMockitoUnitTest {
    @InjectMocks
    private CheckoutStepHandler handler;
    @Mock
    private PipelineStepLogHelper logHelper;

    @Test
    void successfulCheckoutRestoresPreparedFlagAfterApproval() {
        PipelineExecutionContext ctx = PipelineExecutionContext.builder().executorRuntime(
                PipelineJobRuntime.builder().runtimeType("KUBERNETES").runtimeId("preserved-pod").build()).build();
        PipelineRunLogDO runLog = new PipelineRunLogDO();
        runLog.setStatus("SUCCESS");
        when(logHelper.getOrCreateLog(ctx)).thenReturn(runLog);

        handler.handle(ctx);

        assertTrue(ctx.isSourcePrepared());
    }
}
