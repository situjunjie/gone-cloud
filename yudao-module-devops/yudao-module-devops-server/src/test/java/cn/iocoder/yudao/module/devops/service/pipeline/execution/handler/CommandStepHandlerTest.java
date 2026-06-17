package cn.iocoder.yudao.module.devops.service.pipeline.execution.handler;

import cn.iocoder.yudao.framework.common.util.json.JsonUtils;
import cn.iocoder.yudao.framework.test.core.ut.BaseMockitoUnitTest;
import cn.iocoder.yudao.module.devops.controller.admin.pipelinerun.vo.PipelineRunLogLineRespVO;
import cn.iocoder.yudao.module.devops.dal.dataobject.pipeline.PipelineRunDO;
import cn.iocoder.yudao.module.devops.dal.dataobject.pipeline.log.PipelineRunLogDO;
import cn.iocoder.yudao.module.devops.framework.build.ExecResult;
import cn.iocoder.yudao.module.devops.framework.build.LogSink;
import cn.iocoder.yudao.module.devops.framework.pipeline.PipelineSpec;
import cn.iocoder.yudao.module.devops.framework.pipeline.runtime.PipelineCommandExecutor;
import cn.iocoder.yudao.module.devops.framework.pipeline.runtime.PipelineJobRuntime;
import cn.iocoder.yudao.module.devops.service.pipeline.PipelineNodeRegistryServiceImpl;
import cn.iocoder.yudao.module.devops.service.pipeline.execution.PipelineRunLogLineService;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;

import java.nio.file.Path;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * {@link CommandStepHandler} 的单元测试。
 */
public class CommandStepHandlerTest extends BaseMockitoUnitTest {

    @InjectMocks
    private CommandStepHandler handler;

    @Mock
    private PipelineCommandExecutor pipelineCommandExecutor;
    @Mock
    private PipelineStepLogHelper logHelper;
    @Mock
    private PipelineRunLogLineService pipelineRunLogLineService;

    @Test
    public void testHandle_appendRealtimeLines() {
        PipelineStepContext context = buildContext();
        PipelineRunLogDO runLog = buildRunLog();
        when(logHelper.getOrCreateLog(eq(context))).thenReturn(runLog);
        when(pipelineRunLogLineService.appendLine(eq(runLog), eq("stdout"), eq("hello")))
                .thenReturn(buildLine("hello"));
        doAnswer(invocation -> {
            LogSink sink = invocation.getArgument(2);
            sink.accept("stdout", "hello");
            return ExecResult.success();
        }).when(pipelineCommandExecutor).exec(any(), eq("echo hello"), any());

        StepResult result = handler.handle(context);

        assertEquals(StepResultType.CONTINUE, result.getType());
        verify(pipelineRunLogLineService).appendLine(eq(runLog), eq("stdout"), eq("hello"));
        verify(logHelper).markSuccess(eq(runLog), eq("命令执行成功"));
        Map<String, Object> resultJson = JsonUtils.parseMap(runLog.getResultJson());
        assertEquals(0, resultJson.get("exitCode"));
    }

    @Test
    public void testCancel() {
        PipelineStepContext context = buildContext();

        handler.cancel(context);

        verify(pipelineCommandExecutor).cancel(eq("800:job-1"));
    }

    private PipelineStepContext buildContext() {
        PipelineRunDO run = new PipelineRunDO();
        run.setId(800L);
        run.setTenantId(1L);
        PipelineSpec.ExecutableJob job = new PipelineSpec.ExecutableJob();
        job.setJobId("job-1");
        PipelineSpec.ExecutableStep step = new PipelineSpec.ExecutableStep();
        step.setStepId("step-1");
        step.setStep(PipelineNodeRegistryServiceImpl.TYPE_COMMAND);
        step.setName("命令");
        step.setWith(Map.of("run", "echo hello"));
        PipelineJobRuntime runtime = PipelineJobRuntime.builder()
                .runtimeType("DOCKER")
                .runtimeId("container-1")
                .runtimeName("pipeline-1")
                .executorGroup("local-docker/default")
                .executorImage("busybox:latest")
                .workspace(Path.of("/tmp/workspace"))
                .build();
        Map<String, Object> sharedState = new ConcurrentHashMap<>();
        sharedState.put("jobRuntime:job-1", runtime);
        return PipelineStepContext.builder()
                .run(run)
                .job(job)
                .step(step)
                .sharedState(sharedState)
                .build();
    }

    private PipelineRunLogDO buildRunLog() {
        PipelineRunLogDO runLog = new PipelineRunLogDO();
        runLog.setId(900L);
        runLog.setPipelineRunId(800L);
        runLog.setStepId("step-1");
        return runLog;
    }

    private PipelineRunLogLineRespVO buildLine(String content) {
        PipelineRunLogLineRespVO line = new PipelineRunLogLineRespVO();
        line.setContent(content);
        return line;
    }

}
