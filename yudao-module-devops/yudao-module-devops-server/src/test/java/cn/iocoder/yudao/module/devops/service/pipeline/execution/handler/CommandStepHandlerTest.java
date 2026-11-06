package cn.iocoder.yudao.module.devops.service.pipeline.execution.handler;

import cn.iocoder.yudao.module.devops.service.pipeline.execution.PipelineExecutionContext;
import cn.iocoder.yudao.framework.common.util.json.JsonUtils;
import cn.iocoder.yudao.framework.test.core.ut.BaseMockitoUnitTest;
import cn.iocoder.yudao.module.devops.dal.dataobject.pipeline.PipelineRunDO;
import cn.iocoder.yudao.module.devops.dal.mysql.pipeline.PipelineRunMapper;
import cn.iocoder.yudao.module.devops.dal.dataobject.pipeline.log.PipelineRunLogDO;
import cn.iocoder.yudao.module.devops.framework.build.ExecResult;
import cn.iocoder.yudao.module.devops.framework.build.LogSink;
import cn.iocoder.yudao.module.devops.framework.pipeline.PipelineSpec;
import cn.iocoder.yudao.module.devops.framework.pipeline.runtime.PipelineCommandContext;
import cn.iocoder.yudao.module.devops.framework.pipeline.runtime.PipelineCommandExecutor;
import cn.iocoder.yudao.module.devops.framework.pipeline.runtime.PipelineJobRuntime;
import cn.iocoder.yudao.module.devops.service.pipeline.PipelineNodeRegistryServiceImpl;
import cn.iocoder.yudao.module.devops.service.pipeline.execution.PipelineLogStorageService;
import cn.iocoder.yudao.module.devops.service.pipeline.execution.PipelineRunLogLineService;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;

import java.nio.file.Path;
import java.nio.file.Files;
import org.junit.jupiter.api.io.TempDir;
import cn.iocoder.yudao.module.devops.framework.pipeline.runtime.SshPipelineCommandExecutor;
import cn.iocoder.yudao.module.devops.framework.pipeline.runtime.PipelineWorkspace;
import cn.iocoder.yudao.module.devops.service.pipeline.execution.PipelineSshTargetResolver;
import cn.iocoder.yudao.module.devops.dal.dataobject.host.EnvironmentHostDO;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;
import java.util.Map;

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
    private PipelineLogStorageService pipelineLogStorageService;
    @Mock
    private PipelineRunLogLineService pipelineRunLogLineService;
    @Mock
    private PipelineRunMapper pipelineRunMapper;

    @Mock
    private SshPipelineCommandExecutor sshPipelineCommandExecutor;
    @Mock
    private PipelineSshTargetResolver sshTargetResolver;
    @TempDir
    Path workspace;

    @Test
    public void testSshDispatchAndProbeCleanup() throws Exception {
        PipelineExecutionContext context = buildContext();
        context.getStep().setWith(Map.of("run", "echo hello", "sshHostId", 42L, "env", Map.of("PROFILE", "test")));
        context.setExecutorRuntime(null);
        PipelineWorkspace pipelineWorkspace = mock(PipelineWorkspace.class);
        when(pipelineWorkspace.getRunWorkspace()).thenReturn(workspace);
        context.setPipelineWorkspace(pipelineWorkspace);
        EnvironmentHostDO host = new EnvironmentHostDO();
        host.setHostKey("builder");
        when(sshTargetResolver.resolveHost(context.getRun(), 42L)).thenReturn(host);
        when(sshTargetResolver.resolveWorkspace(42L, workspace)).thenReturn("/srv/source");
        PipelineRunLogDO runLog = buildRunLog();
        when(logHelper.getOrCreateLog(context)).thenReturn(runLog);
        mockPipelineLogFile(runLog);
        doAnswer(call -> {
            PipelineCommandContext command = call.getArgument(0);
            assertSame(host, command.getSshHost());
            assertFalse(command.isCancellationRequested());
            PipelineRunDO canceled = new PipelineRunDO();
            canceled.setRunStatus(cn.iocoder.yudao.module.devops.enums.PipelineRunStatusEnum.CANCELED.getStatus());
            when(pipelineRunMapper.selectById(800L)).thenReturn(canceled);
            assertTrue(command.isCancellationRequested());
            when(pipelineRunMapper.selectById(800L)).thenReturn(null);
            assertEquals("/srv/source", command.getWorkingDirectory());
            assertEquals(command.getWorkspaceToken(), Files.readString(workspace.resolve(command.getWorkspaceProbe())));
            assertFalse(command.getEnv().containsKey("MAVEN_CONFIG"));
            assertEquals("test", command.getEnv().get("PROFILE"));
            LogSink sink = call.getArgument(2);
            sink.accept("stderr", "remote failure");
            return ExecResult.failure(7, "exit 7");
        }).when(sshPipelineCommandExecutor).exec(any(), eq("echo hello"), any());

        assertEquals(StepRuntimeRequirement.PLATFORM, handler.runtimeRequirement(context));
        assertEquals(StepResultType.FAIL, handler.handle(context).getType());
        assertEquals("SSH", runLog.getRuntimeType());
        assertEquals("42", runLog.getRuntimeId());
        assertEquals(7, JsonUtils.parseMap(runLog.getResultJson()).get("exitCode"));
        try (var files = Files.list(workspace)) {
            assertEquals(0, files.count());
        }
        verifyNoInteractions(pipelineCommandExecutor);
        handler.cancel(context);
        verify(sshPipelineCommandExecutor).cancel("800:job-1");
    }

    @Test
    public void testCanceledSshBeforeRegistrationDoesNotResolveOrExecute() {
        PipelineExecutionContext context = buildContext();
        context.getStep().setWith(Map.of("run", "mvn compile", "sshHostId", 42L));
        PipelineRunDO canceled = new PipelineRunDO();
        canceled.setRunStatus(cn.iocoder.yudao.module.devops.enums.PipelineRunStatusEnum.CANCELED.getStatus());
        when(pipelineRunMapper.selectById(800L)).thenReturn(canceled);
        PipelineRunLogDO runLog = buildRunLog();
        when(logHelper.getOrCreateLog(context)).thenReturn(runLog);
        mockPipelineLogFile(runLog);

        assertEquals(StepResultType.FAIL, handler.handle(context).getType());

        assertEquals("CANCELED", runLog.getStatus());
        verify(pipelineLogStorageService).completeStepLog(runLog);
        verify(logHelper, never()).markFailed(any(), any(), any());
        verifyNoInteractions(sshTargetResolver, sshPipelineCommandExecutor, pipelineCommandExecutor);
    }

    @Test
    public void testSshTargetFailureMarksStepFailed() {
        PipelineExecutionContext context = buildContext();
        context.getStep().setWith(Map.of("run", "echo hello", "sshHostId", 42L));
        PipelineRunLogDO runLog = buildRunLog();
        when(logHelper.getOrCreateLog(context)).thenReturn(runLog);
        mockPipelineLogFile(runLog);
        when(sshTargetResolver.resolveHost(context.getRun(), 42L)).thenThrow(new IllegalArgumentException("invalid target"));
        assertEquals(StepResultType.FAIL, handler.handle(context).getType());
        verify(logHelper).markFailed(runLog, "命令执行失败", "invalid target");
        verifyNoInteractions(pipelineCommandExecutor, sshPipelineCommandExecutor);
    }

    @Test
    public void testHandle_appendRealtimeLines() {
        PipelineExecutionContext context = buildContext();
        PipelineRunLogDO runLog = buildRunLog();
        when(logHelper.getOrCreateLog(eq(context))).thenReturn(runLog);
        mockPipelineLogFile(runLog);
        doAnswer(invocation -> {
            LogSink sink = invocation.getArgument(2);
            sink.accept("stdout", "hello");
            return ExecResult.success();
        }).when(pipelineCommandExecutor).exec(any(), eq("echo hello"), any());

        StepResult result = handler.handle(context);

        assertEquals(StepResultType.CONTINUE, result.getType());
        verify(pipelineLogStorageService).prepareStepLog(eq(runLog));
        verify(pipelineRunLogLineService).appendLine(eq(runLog), eq("stdout"), eq("hello"));
        verify(logHelper).markSuccess(eq(runLog), eq("命令执行成功"));
        Map<String, Object> resultJson = JsonUtils.parseMap(runLog.getResultJson());
        assertEquals(0, resultJson.get("exitCode"));
        assertEquals("hello", ((java.util.List<?>) resultJson.get("logLines")).get(0));
    }

    @Test
    public void testHandle_addDefaultCacheEnv() {
        PipelineExecutionContext context = buildContext();
        PipelineRunLogDO runLog = buildRunLog();
        when(logHelper.getOrCreateLog(eq(context))).thenReturn(runLog);
        mockPipelineLogFile(runLog);
        when(pipelineCommandExecutor.exec(any(), eq("echo hello"), any())).thenReturn(ExecResult.success());

        handler.handle(context);

        ArgumentCaptor<PipelineCommandContext> commandContextCaptor =
                ArgumentCaptor.forClass(PipelineCommandContext.class);
        verify(pipelineCommandExecutor).exec(commandContextCaptor.capture(), eq("echo hello"), any());
        Map<String, String> env = commandContextCaptor.getValue().getEnv();
        assertEquals("/root/.m2", env.get("MAVEN_CONFIG"));
        assertEquals("/root/.npm", env.get("NPM_CONFIG_CACHE"));
        assertEquals("/root/.pnpm-store", env.get("PNPM_STORE_PATH"));
        assertEquals("/root/.gradle", env.get("GRADLE_USER_HOME"));
    }

    @Test
    public void testHandle_resolveYamlPlaceholders() {
        PipelineExecutionContext context = buildContext();
        context.getRun().setCommitSha("abc123");
        context.getSharedState().put("imageTag", "v1.0.0");
        context.getStep().setWith(Map.of(
                "run", "echo ${COMMIT_SHA} ${imageTag}",
                "env", Map.of("IMAGE_TAG", "${imageTag}", "COMMIT_SHA", "${commitSha}")));
        PipelineRunLogDO runLog = buildRunLog();
        when(logHelper.getOrCreateLog(eq(context))).thenReturn(runLog);
        mockPipelineLogFile(runLog);
        when(pipelineCommandExecutor.exec(any(), eq("echo abc123 v1.0.0"), any()))
                .thenReturn(ExecResult.success());

        StepResult result = handler.handle(context);

        assertEquals(StepResultType.CONTINUE, result.getType());
        ArgumentCaptor<PipelineCommandContext> commandContextCaptor =
                ArgumentCaptor.forClass(PipelineCommandContext.class);
        verify(pipelineCommandExecutor).exec(commandContextCaptor.capture(), eq("echo abc123 v1.0.0"), any());
        assertEquals("v1.0.0", commandContextCaptor.getValue().getEnv().get("IMAGE_TAG"));
        assertEquals("abc123", commandContextCaptor.getValue().getEnv().get("COMMIT_SHA"));
    }

    @Test
    public void testCancel() {
        PipelineExecutionContext context = buildContext();

        handler.cancel(context);

        verify(pipelineCommandExecutor).cancel(eq("800:job-1"));
    }

    private PipelineExecutionContext buildContext() {
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
        return PipelineExecutionContext.builder()
                .run(run)
                .job(job)
                .step(step)
                .executorRuntime(runtime)
                .build();
    }

    private PipelineRunLogDO buildRunLog() {
        PipelineRunLogDO runLog = new PipelineRunLogDO();
        runLog.setId(900L);
        runLog.setPipelineRunId(800L);
        runLog.setStepId("step-1");
        return runLog;
    }

    private void mockPipelineLogFile(PipelineRunLogDO runLog) {
        when(pipelineLogStorageService.prepareStepLog(eq(runLog))).thenReturn(Path.of("/tmp/pipeline-step.log"));
    }

}
