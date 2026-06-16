package cn.iocoder.yudao.module.devops.service.pipeline.execution.handler;

import cn.iocoder.yudao.module.devops.dal.dataobject.pipeline.PipelineRunDO;
import cn.iocoder.yudao.module.devops.dal.dataobject.pipeline.log.PipelineRunLogDO;
import cn.iocoder.yudao.module.devops.framework.pipeline.PipelineSpec;
import cn.iocoder.yudao.module.devops.service.pipeline.PipelineNodeRegistryServiceImpl;
import cn.iocoder.yudao.module.devops.service.pipeline.execution.CodeMergeExecutionStatus;
import cn.iocoder.yudao.module.devops.service.pipeline.execution.CodeMergeService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.when;

/**
 * {@link CodeMergeStepHandler} 单元测试。
 */
class CodeMergeStepHandlerTest {

    @InjectMocks
    private CodeMergeStepHandler handler;

    @Mock
    private CodeMergeService codeMergeService;
    @Mock
    private PipelineStepLogHelper logHelper;

    @BeforeEach
    void setUp() {
        MockitoAnnotations.openMocks(this);
    }

    @Test
    void testHandle_success_outputsMergedBranchAndCommit() {
        PipelineStepContext ctx = context();
        PipelineRunLogDO log = new PipelineRunLogDO();
        when(logHelper.getOrCreateLog(ctx)).thenReturn(log);
        when(codeMergeService.executeCodeMergeStep(eq(ctx.getRun()), eq(log), eq(List.of("feature/a", "feature/b")),
                eq(true), eq("master"), eq("release/test"), eq(true), eq(99L))).thenAnswer(invocation -> {
            ctx.getRun().setBranchName("release/test");
            ctx.getRun().setCommitSha("abc123");
            return CodeMergeExecutionStatus.SUCCESS;
        });

        StepResult result = handler.handle(ctx);

        assertEquals(StepResultType.CONTINUE, result.getType());
        assertEquals("release/test", result.getOutputs().get("mergedBranch"));
        assertEquals("abc123", result.getOutputs().get("mergedCommitSha"));
    }

    @Test
    void testHandle_conflict_suspend() {
        PipelineStepContext ctx = context();
        PipelineRunLogDO log = new PipelineRunLogDO();
        when(logHelper.getOrCreateLog(ctx)).thenReturn(log);
        when(codeMergeService.executeCodeMergeStep(eq(ctx.getRun()), eq(log), eq(List.of("feature/a", "feature/b")),
                eq(true), eq("master"), eq("release/test"), eq(true), eq(99L)))
                .thenReturn(CodeMergeExecutionStatus.SUSPEND);

        StepResult result = handler.handle(ctx);

        assertEquals(StepResultType.SUSPEND, result.getType());
    }

    @Test
    void testSupports() {
        assertTrue(handler.supports(PipelineNodeRegistryServiceImpl.TYPE_CODE_MERGE));
    }

    private PipelineStepContext context() {
        PipelineRunDO run = new PipelineRunDO();
        run.setId(1L);
        PipelineSpec.ExecutableJob job = new PipelineSpec.ExecutableJob();
        job.setJobId("code_merge_job");
        PipelineSpec.ExecutableStep step = new PipelineSpec.ExecutableStep();
        step.setStepId("code_merge_step");
        step.setStep(PipelineNodeRegistryServiceImpl.TYPE_CODE_MERGE);
        step.setName("代码合并");
        step.setWith(new LinkedHashMap<>(Map.of(
                "baseBranch", "${SOURCE_BRANCH}",
                "targetBranch", "${BRANCH_NAME}",
                "branches", List.of("feature/a", "feature/b"),
                "pushOnSuccess", true)));
        return PipelineStepContext.builder()
                .run(run)
                .job(job)
                .step(step)
                .sharedState(new LinkedHashMap<>(Map.of(
                        "sourceBranch", "master",
                        "branchName", "release/test")))
                .userId(99L)
                .build();
    }

}
