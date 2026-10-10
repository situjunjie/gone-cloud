package cn.iocoder.yudao.module.devops.service.pipeline.execution.handler;

import cn.iocoder.yudao.framework.common.util.json.JsonUtils;
import cn.iocoder.yudao.framework.test.core.ut.BaseMockitoUnitTest;
import cn.iocoder.yudao.module.devops.dal.dataobject.pipeline.PipelineRunDO;
import cn.iocoder.yudao.module.devops.dal.dataobject.pipeline.log.PipelineRunLogDO;
import cn.iocoder.yudao.module.devops.framework.build.ExecResult;
import cn.iocoder.yudao.module.devops.framework.config.DevopsConfig;
import cn.iocoder.yudao.module.devops.framework.pipeline.PipelineSpec;
import cn.iocoder.yudao.module.devops.framework.pipeline.runtime.PipelineCommandContext;
import cn.iocoder.yudao.module.devops.framework.pipeline.runtime.PipelineCommandExecutor;
import cn.iocoder.yudao.module.devops.framework.pipeline.runtime.PipelineJobRuntime;
import cn.iocoder.yudao.module.devops.service.pipeline.execution.PipelineExecutionContext;
import cn.iocoder.yudao.module.devops.service.pipeline.execution.PipelineLogStorageService;
import cn.iocoder.yudao.module.devops.service.pipeline.execution.PipelineRunLogLineService;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.Spy;

import java.nio.charset.StandardCharsets;
import java.util.Base64;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

class KanikoDockerBuildStepHandlerTest extends BaseMockitoUnitTest {

    @InjectMocks
    private KanikoDockerBuildStepHandler handler;
    @Mock
    private PipelineCommandExecutor pipelineCommandExecutor;
    @Mock
    private PipelineStepLogHelper logHelper;
    @Mock
    private PipelineLogStorageService pipelineLogStorageService;
    @Mock
    private PipelineRunLogLineService pipelineRunLogLineService;
    @Spy
    private DevopsConfig devopsConfig = new DevopsConfig();

    @Test
    void buildUsesSidecarCredentialsAndRegisteredParameters() {
        PipelineExecutionContext ctx = context(Map.of("imageTag", "registry.example.com/team/app:v1",
                "registryUsername", "builder", "registryPassword", "pass'$literal",
                "target", "production", "timeoutSeconds", 123,
                "buildArgs", List.of(Map.of("name", "MESSAGE", "value", "space ' $(touch /tmp/unwanted)"))));
        PipelineRunLogDO runLog = new PipelineRunLogDO();
        when(logHelper.getOrCreateLog(ctx)).thenReturn(runLog);
        when(pipelineCommandExecutor.exec(any(), anyString(), any())).thenReturn(ExecResult.success());

        assertEquals(StepResultType.CONTINUE, handler.handle(ctx).getType());

        ArgumentCaptor<PipelineCommandContext> command = ArgumentCaptor.forClass(PipelineCommandContext.class);
        ArgumentCaptor<String> script = ArgumentCaptor.forClass(String.class);
        verify(pipelineCommandExecutor).exec(command.capture(), script.capture(), any());
        assertEquals("kaniko", command.getValue().getContainerName());
        assertEquals("/workspace", command.getValue().getWorkingDirectory());
        assertEquals(123, command.getValue().getTimeoutSeconds());
        assertTrue(script.getValue().contains("--target='production'"));
        assertTrue(script.getValue().contains("--destination='registry.example.com/team/app:v1'"));
        assertTrue(script.getValue().contains("trap "));
        String encoded = Base64.getEncoder().encodeToString("builder:pass'$literal".getBytes(StandardCharsets.UTF_8));
        Map<String, Object> auth = JsonUtils.parseMap(command.getValue().getEnv().get("KANIKO_AUTH_CONFIG"));
        assertEquals(Map.of("registry.example.com", Map.of("auth", encoded)), auth.get("auths"));
        assertFalse(script.getValue().contains(encoded));
        assertFalse(runLog.getResultJson().contains(encoded));
        assertFalse(runLog.getResultJson().contains("pass'$literal"));
        assertEquals("registry.example.com/team/app:v1", ctx.getSharedState().get("builtImage"));
    }

    @Test
    void insecureRegistryAddsSkipTlsVerifyFlags() {
        PipelineExecutionContext ctx = context(Map.of("imageTag", "192.168.16.102:8082/gigi-docker/e2e:v1",
                "insecure", true, "cache", false));
        when(logHelper.getOrCreateLog(ctx)).thenReturn(new PipelineRunLogDO());
        when(pipelineCommandExecutor.exec(any(), anyString(), any())).thenReturn(ExecResult.success());

        assertEquals(StepResultType.CONTINUE, handler.handle(ctx).getType());

        ArgumentCaptor<String> script = ArgumentCaptor.forClass(String.class);
        verify(pipelineCommandExecutor).exec(any(), script.capture(), any());
        assertTrue(script.getValue().contains("--insecure"));
        assertTrue(script.getValue().contains("--skip-tls-verify"));
    }

    @Test
    void registryMirrorIsForwardedToKaniko() {
        PipelineExecutionContext ctx = context(Map.of("imageTag", "registry.example.com/team/app:v1",
                "registryMirror", "docker.m.daocloud.io"));
        when(logHelper.getOrCreateLog(ctx)).thenReturn(new PipelineRunLogDO());
        when(pipelineCommandExecutor.exec(any(), anyString(), any())).thenReturn(ExecResult.success());

        assertEquals(StepResultType.CONTINUE, handler.handle(ctx).getType());

        ArgumentCaptor<String> script = ArgumentCaptor.forClass(String.class);
        verify(pipelineCommandExecutor).exec(any(), script.capture(), any());
        assertTrue(script.getValue().contains("--registry-mirror='docker.m.daocloud.io'"));
    }

    @Test
    void secureRegistryOmitsSkipTlsVerifyFlags() {
        PipelineExecutionContext ctx = context(Map.of("imageTag", "registry.example.com/team/app:v1"));
        when(logHelper.getOrCreateLog(ctx)).thenReturn(new PipelineRunLogDO());
        when(pipelineCommandExecutor.exec(any(), anyString(), any())).thenReturn(ExecResult.success());

        assertEquals(StepResultType.CONTINUE, handler.handle(ctx).getType());

        ArgumentCaptor<String> script = ArgumentCaptor.forClass(String.class);
        verify(pipelineCommandExecutor).exec(any(), script.capture(), any());
        assertFalse(script.getValue().contains("--insecure"));
        assertFalse(script.getValue().contains("--skip-tls-verify"));
    }

    @Test
    void legacyImageAndMapArgsRemainSupported() {
        PipelineExecutionContext ctx = context(Map.of("image", "example/app:v1", "buildArgs", Map.of("VERSION", "1")));
        when(logHelper.getOrCreateLog(ctx)).thenReturn(new PipelineRunLogDO());
        when(pipelineCommandExecutor.exec(any(), anyString(), any())).thenReturn(ExecResult.failure(7, "build failed"));
        assertEquals(StepResultType.FAIL, handler.handle(ctx).getType());
        verify(pipelineCommandExecutor).exec(any(), contains("--build-arg 'VERSION=1'"), any());
        assertFalse(ctx.getSharedState().containsKey("builtImage"));
    }

    @Test
    void missingPasswordFailsBeforeExecuting() {
        PipelineExecutionContext ctx = context(Map.of("imageTag", "example/app:v1", "registryUsername", "builder"));
        when(logHelper.getOrCreateLog(ctx)).thenReturn(new PipelineRunLogDO());
        assertEquals(StepResultType.FAIL, handler.handle(ctx).getType());
        verifyNoInteractions(pipelineCommandExecutor);
    }

    @Test
    void dockerRuntimeFailsBeforeExecuting() {
        PipelineExecutionContext ctx = context(Map.of("imageTag", "example/app:v1"));
        ctx.getExecutorRuntime().setRuntimeType("DOCKER");
        when(logHelper.getOrCreateLog(ctx)).thenReturn(new PipelineRunLogDO());
        assertEquals(StepResultType.FAIL, handler.handle(ctx).getType());
        verifyNoInteractions(pipelineCommandExecutor);
    }

    private PipelineExecutionContext context(Map<String, Object> parameters) {
        PipelineRunDO run = new PipelineRunDO();
        run.setId(1L);
        PipelineSpec.ExecutableJob job = new PipelineSpec.ExecutableJob();
        job.setJobId("build");
        PipelineSpec.ExecutableStep step = new PipelineSpec.ExecutableStep();
        step.setStepId("image");
        step.setStep("KanikoBuild");
        step.setWith(parameters);
        return PipelineExecutionContext.builder().run(run).job(job).step(step)
                .executorRuntime(PipelineJobRuntime.builder().runtimeType("KUBERNETES")
                        .runtimeId("pod-1").build()).build();
    }
}
