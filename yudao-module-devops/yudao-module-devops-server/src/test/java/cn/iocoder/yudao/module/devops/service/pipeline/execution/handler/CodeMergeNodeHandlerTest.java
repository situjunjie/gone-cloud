package cn.iocoder.yudao.module.devops.service.pipeline.execution.handler;

import cn.iocoder.yudao.framework.test.core.ut.BaseMockitoUnitTest;
import cn.iocoder.yudao.module.devops.dal.dataobject.pipeline.PipelineRunDO;
import cn.iocoder.yudao.module.devops.framework.pipeline.PipelineSpec;
import cn.iocoder.yudao.module.devops.service.pipeline.PipelineNodeRegistryServiceImpl;
import cn.iocoder.yudao.module.devops.service.pipeline.execution.CodeMergeExecutionStatus;
import cn.iocoder.yudao.module.devops.service.pipeline.execution.CodeMergeService;
import org.junit.jupiter.api.Test;
import org.mockito.InjectMocks;
import org.mockito.Mock;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.when;

/**
 * {@link CodeMergeNodeHandler} 的单元测试。
 */
public class CodeMergeNodeHandlerTest extends BaseMockitoUnitTest {

    @InjectMocks
    private CodeMergeNodeHandler handler;

    @Mock
    private CodeMergeService codeMergeService;

    @Test
    public void testSupports() {
        assertTrue(handler.supports(PipelineNodeRegistryServiceImpl.TYPE_CODE_MERGE));
    }

    @Test
    public void testHandle_firstSuccess_continue() {
        PipelineNodeContext ctx = buildContext();
        doAnswer(invocation -> {
            ctx.getRun().setBranchName("release/test");
            ctx.getRun().setCommitSha("abc123");
            return CodeMergeExecutionStatus.SUCCESS;
        }).when(codeMergeService).executeCodeMergeNode(ctx.getRun(), "builtin.code_merge", "代码合并", 99L);

        NodeOutcome outcome = handler.handle(ctx);

        assertEquals(NodeOutcome.CONTINUE, outcome);
        assertEquals("release/test", ctx.getSharedState().get("branchName"));
        assertEquals("abc123", ctx.getSharedState().get("commitSha"));
    }

    @Test
    public void testHandle_conflict_suspend() {
        PipelineNodeContext ctx = buildContext();
        when(codeMergeService.executeCodeMergeNode(ctx.getRun(), "builtin.code_merge", "代码合并", 99L))
                .thenReturn(CodeMergeExecutionStatus.SUSPEND);

        NodeOutcome outcome = handler.handle(ctx);

        assertEquals(NodeOutcome.SUSPEND, outcome);
    }

    @Test
    public void testHandle_successIdempotent_continue() {
        PipelineNodeContext ctx = buildContext();
        when(codeMergeService.executeCodeMergeNode(ctx.getRun(), "builtin.code_merge", "代码合并", 99L))
                .thenReturn(CodeMergeExecutionStatus.SUCCESS);

        NodeOutcome outcome = handler.handle(ctx);

        assertEquals(NodeOutcome.CONTINUE, outcome);
    }

    @Test
    public void testHandle_exception_fail() {
        PipelineNodeContext ctx = buildContext();
        when(codeMergeService.executeCodeMergeNode(ctx.getRun(), "builtin.code_merge", "代码合并", 99L))
                .thenThrow(new RuntimeException("git failed"));

        NodeOutcome outcome = handler.handle(ctx);

        assertEquals(NodeOutcome.FAIL, outcome);
    }

    private PipelineNodeContext buildContext() {
        PipelineRunDO run = new PipelineRunDO();
        run.setId(800L);
        PipelineSpec.ExecutableStep step = new PipelineSpec.ExecutableStep();
        step.setStepId("builtin.code_merge");
        step.setStep(PipelineNodeRegistryServiceImpl.TYPE_CODE_MERGE);
        step.setName("代码合并");
        return PipelineNodeContext.builder()
                .run(run)
                .step(step)
                .userId(99L)
                .build();
    }

}
