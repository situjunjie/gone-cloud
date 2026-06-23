package cn.iocoder.yudao.module.devops.service.pipeline.execution.handler;

import cn.iocoder.yudao.framework.common.util.json.JsonUtils;
import cn.iocoder.yudao.framework.test.core.ut.BaseMockitoUnitTest;
import cn.iocoder.yudao.module.devops.dal.dataobject.pipeline.PipelineRunDO;
import cn.iocoder.yudao.module.devops.dal.dataobject.pipeline.log.PipelineRunLogDO;
import cn.iocoder.yudao.module.devops.framework.build.ExecResult;
import cn.iocoder.yudao.module.devops.framework.pipeline.PipelineSpec;
import cn.iocoder.yudao.module.devops.framework.pipeline.runtime.PipelineCommandContext;
import cn.iocoder.yudao.module.devops.framework.pipeline.runtime.PipelineCommandExecutor;
import cn.iocoder.yudao.module.devops.framework.pipeline.runtime.PipelineJobRuntime;
import cn.iocoder.yudao.module.devops.service.pipeline.PipelineNodeRegistryServiceImpl;
import cn.iocoder.yudao.module.devops.service.pipeline.execution.PipelineRunLogFileStorage;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;

import java.nio.file.Path;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.isNull;
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
    private PipelineStepLogFileHelper logFileHelper;

    @Test
    public void testHandle_prepareFileLogsAndUpload() {
        PipelineStepContext context = buildContext();
        PipelineRunLogDO runLog = buildRunLog();
        when(logHelper.getOrCreateLog(eq(context))).thenReturn(runLog);
        when(logFileHelper.prepareLogFiles(eq(runLog), any())).thenReturn(buildFiles());
        when(pipelineCommandExecutor.exec(any(), eq("echo hello"), isNull())).thenReturn(ExecResult.success());
        when(logFileHelper.readSummaryLines(eq(runLog), eq(500))).thenReturn(java.util.List.of("hello"));
        doAnswer(invocation -> {
            runLog.setLogFileUrl("https://file/log");
            return null;
        }).when(logFileHelper).uploadFullLog(eq(runLog));

        StepResult result = handler.handle(context);

        assertEquals(StepResultType.CONTINUE, result.getType());
        ArgumentCaptor<PipelineCommandContext> commandContextCaptor =
                ArgumentCaptor.forClass(PipelineCommandContext.class);
        verify(pipelineCommandExecutor).exec(commandContextCaptor.capture(), eq("echo hello"), isNull());
        assertEquals("/workspace/.gone-devops/logs/run-log-900/stdout.log",
                commandContextCaptor.getValue().getStdoutLogPath());
        assertEquals("/workspace/.gone-devops/logs/run-log-900/stderr.log",
                commandContextCaptor.getValue().getStderrLogPath());
        assertEquals("https://file/log", runLog.getLogFileUrl());
        verify(logHelper).markSuccess(eq(runLog), eq("命令执行成功"));
        Map<String, Object> resultJson = JsonUtils.parseMap(runLog.getResultJson());
        assertEquals(0, resultJson.get("exitCode"));
        assertEquals("https://file/log", resultJson.get("logFileUrl"));
    }

    @Test
    public void testHandle_addDefaultCacheEnv() {
        PipelineStepContext context = buildContext();
        PipelineRunLogDO runLog = buildRunLog();
        when(logHelper.getOrCreateLog(eq(context))).thenReturn(runLog);
        when(logFileHelper.prepareLogFiles(eq(runLog), any())).thenReturn(buildFiles());
        when(pipelineCommandExecutor.exec(any(), eq("echo hello"), isNull())).thenReturn(ExecResult.success());
        when(logFileHelper.readSummaryLines(eq(runLog), eq(500))).thenReturn(java.util.List.of());

        handler.handle(context);

        ArgumentCaptor<PipelineCommandContext> commandContextCaptor =
                ArgumentCaptor.forClass(PipelineCommandContext.class);
        verify(pipelineCommandExecutor).exec(commandContextCaptor.capture(), eq("echo hello"), isNull());
        Map<String, String> env = commandContextCaptor.getValue().getEnv();
        assertEquals("/root/.m2", env.get("MAVEN_CONFIG"));
        assertEquals("/root/.npm", env.get("NPM_CONFIG_CACHE"));
        assertEquals("/root/.pnpm-store", env.get("PNPM_STORE_PATH"));
        assertEquals("/root/.gradle", env.get("GRADLE_USER_HOME"));
    }

    @Test
    public void testHandle_resolveYamlPlaceholders() {
        PipelineStepContext context = buildContext();
        context.getRun().setCommitSha("abc123");
        context.getSharedState().put("imageTag", "v1.0.0");
        context.getStep().setWith(Map.of(
                "run", "echo ${COMMIT_SHA} ${imageTag}",
                "env", Map.of("IMAGE_TAG", "${imageTag}", "COMMIT_SHA", "${commitSha}")));
        PipelineRunLogDO runLog = buildRunLog();
        when(logHelper.getOrCreateLog(eq(context))).thenReturn(runLog);
        when(logFileHelper.prepareLogFiles(eq(runLog), any())).thenReturn(buildFiles());
        when(logFileHelper.readSummaryLines(eq(runLog), eq(500))).thenReturn(java.util.List.of());
        when(pipelineCommandExecutor.exec(any(), eq("echo abc123 v1.0.0"), isNull()))
                .thenReturn(ExecResult.success());

        StepResult result = handler.handle(context);

        assertEquals(StepResultType.CONTINUE, result.getType());
        ArgumentCaptor<PipelineCommandContext> commandContextCaptor =
                ArgumentCaptor.forClass(PipelineCommandContext.class);
        verify(pipelineCommandExecutor).exec(commandContextCaptor.capture(), eq("echo abc123 v1.0.0"), isNull());
        assertEquals("v1.0.0", commandContextCaptor.getValue().getEnv().get("IMAGE_TAG"));
        assertEquals("abc123", commandContextCaptor.getValue().getEnv().get("COMMIT_SHA"));
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

    private PipelineRunLogFileStorage.PipelineRunLogFiles buildFiles() {
        PipelineRunLogFileStorage.PipelineRunLogFiles files = new PipelineRunLogFileStorage.PipelineRunLogFiles();
        files.setContainerStdoutPath("/workspace/.gone-devops/logs/run-log-900/stdout.log");
        files.setContainerStderrPath("/workspace/.gone-devops/logs/run-log-900/stderr.log");
        return files;
    }

}
