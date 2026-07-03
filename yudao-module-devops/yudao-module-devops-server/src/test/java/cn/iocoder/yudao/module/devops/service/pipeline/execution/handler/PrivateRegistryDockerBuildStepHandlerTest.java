package cn.iocoder.yudao.module.devops.service.pipeline.execution.handler;

import cn.iocoder.yudao.framework.common.util.json.JsonUtils;
import cn.iocoder.yudao.framework.test.core.ut.BaseMockitoUnitTest;
import cn.iocoder.yudao.module.devops.dal.dataobject.pipeline.PipelineRunDO;
import cn.iocoder.yudao.module.devops.dal.dataobject.pipeline.log.PipelineRunLogDO;
import cn.iocoder.yudao.module.devops.framework.docker.DockerClientFactory;
import cn.iocoder.yudao.module.devops.framework.pipeline.PipelineSpec;
import cn.iocoder.yudao.module.devops.framework.pipeline.runtime.PipelineWorkspace;
import cn.iocoder.yudao.module.devops.service.pipeline.PipelineNodeRegistryServiceImpl;
import cn.iocoder.yudao.module.devops.service.pipeline.execution.PipelineExecutionContext;
import cn.iocoder.yudao.module.devops.service.pipeline.execution.PipelineLogStorageService;
import cn.iocoder.yudao.module.devops.service.pipeline.execution.PipelineRunLogLineService;
import com.github.dockerjava.api.DockerClient;
import com.github.dockerjava.api.command.BuildImageCmd;
import com.github.dockerjava.api.command.BuildImageResultCallback;
import com.github.dockerjava.api.command.PushImageCmd;
import com.github.dockerjava.api.model.AuthConfig;
import com.github.dockerjava.api.model.BuildResponseItem;
import com.github.dockerjava.api.model.PushResponseItem;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.springframework.test.util.ReflectionTestUtils;

import java.io.File;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * {@link PrivateRegistryDockerBuildStepHandler} 的单元测试。
 */
public class PrivateRegistryDockerBuildStepHandlerTest extends BaseMockitoUnitTest {

    @InjectMocks
    private PrivateRegistryDockerBuildStepHandler handler;

    @Mock
    private DockerClientFactory dockerClientFactory;
    @Mock
    private DockerClient dockerClient;
    @Mock
    private BuildImageCmd buildImageCmd;
    @Mock
    private PushImageCmd pushImageCmd;
    @Mock
    private PipelineStepLogHelper logHelper;
    @Mock
    private PipelineLogStorageService pipelineLogStorageService;
    @Mock
    private PipelineRunLogLineService pipelineRunLogLineService;

    @TempDir
    private Path tempDir;

    @Test
    public void testRuntimeRequirement_platform() {
        assertEquals(StepRuntimeRequirement.PLATFORM, handler.runtimeRequirement());
    }

    @Test
    public void testHandle_success() throws Exception {
        PipelineExecutionContext context = buildContext();
        PipelineRunLogDO runLog = buildRunLog();
        Path workspace = tempDir.resolve("workspace");
        Files.createDirectories(workspace.resolve("deploy/nginx"));
        Files.writeString(workspace.resolve("deploy/nginx/Dockerfile"), "FROM nginx:1.27-alpine\n");
        context.setPipelineWorkspace(PipelineWorkspace.builder()
                .runWorkspace(workspace)
                .cacheWorkspace(tempDir.resolve("cache"))
                .cacheKey("definition-1")
                .cacheMounts(List.of())
                .build());
        Path stepLogFile = tempDir.resolve("pipeline-step.log");
        when(logHelper.getOrCreateLog(eq(context))).thenReturn(runLog);
        when(pipelineLogStorageService.prepareStepLog(eq(runLog))).thenReturn(stepLogFile);
        when(dockerClientFactory.getDefaultClient()).thenReturn(dockerClient);
        when(dockerClient.buildImageCmd()).thenReturn(buildImageCmd);
        when(buildImageCmd.withBaseDirectory(any(File.class))).thenReturn(buildImageCmd);
        when(buildImageCmd.withDockerfile(any(File.class))).thenReturn(buildImageCmd);
        when(buildImageCmd.withTag(eq("registry.cn-hangzhou.aliyuncs.com/ns/demo:1.0"))).thenReturn(buildImageCmd);
        when(buildImageCmd.withNoCache(eq(true))).thenReturn(buildImageCmd);
        when(buildImageCmd.withRemove(eq(true))).thenReturn(buildImageCmd);
        when(buildImageCmd.withBuildArg(eq("PROFILE"), eq("prod"))).thenReturn(buildImageCmd);
        when(buildImageCmd.exec(any(BuildImageResultCallback.class))).thenAnswer(invocation -> {
            BuildImageResultCallback callback = invocation.getArgument(0);
            BuildResponseItem responseItem = new BuildResponseItem();
            ReflectionTestUtils.setField(responseItem, "stream", "Successfully built sha256:image-id");
            callback.onNext(responseItem);
            callback.onComplete();
            return callback;
        });
        when(dockerClient.pushImageCmd(eq("registry.cn-hangzhou.aliyuncs.com/ns/demo:1.0"))).thenReturn(pushImageCmd);
        when(pushImageCmd.withAuthConfig(any(AuthConfig.class))).thenReturn(pushImageCmd);
        when(pushImageCmd.exec(any())).thenAnswer(invocation -> {
            com.github.dockerjava.api.async.ResultCallback<PushResponseItem> callback = invocation.getArgument(0);
            callback.onNext(new PushResponseItem());
            callback.onComplete();
            return callback;
        });

        StepResult result = handler.handle(context);

        assertEquals(StepResultType.CONTINUE, result.getType());
        assertEquals("my_image", result.getOutputs().get("artifact"));
        assertEquals("registry.cn-hangzhou.aliyuncs.com/ns/demo:1.0", result.getOutputs().get("image"));
        verify(dockerClient).buildImageCmd();
        verify(dockerClient, never()).buildImageCmd(any(File.class));
        verify(buildImageCmd).withBaseDirectory(eq(workspace.toFile()));
        verify(buildImageCmd).withDockerfile(eq(workspace.resolve("deploy/nginx/Dockerfile").toFile()));
        verify(buildImageCmd).withBuildArg(eq("PROFILE"), eq("prod"));
        ArgumentCaptor<AuthConfig> authConfigCaptor = ArgumentCaptor.forClass(AuthConfig.class);
        verify(pushImageCmd).withAuthConfig(authConfigCaptor.capture());
        assertEquals("robot", authConfigCaptor.getValue().getUsername());
        assertEquals("registry.cn-hangzhou.aliyuncs.com", authConfigCaptor.getValue().getRegistryAddress());
        verify(pipelineRunLogLineService).appendLine(eq(runLog), eq("stdout"), eq("开始构建镜像 registry.cn-hangzhou.aliyuncs.com/ns/demo:1.0"));
        verify(pipelineRunLogLineService).appendLine(eq(runLog), eq("stdout"), eq("Successfully built sha256:image-id"));
        verify(logHelper).markSuccess(eq(runLog), eq("镜像构建并推送成功"));
        Map<String, Object> resultJson = JsonUtils.parseMap(runLog.getResultJson());
        assertEquals("sha256:image-id", resultJson.get("imageId"));
        assertFalse(runLog.getResultJson().contains("secret-pass"));
        assertFalse(runLog.getResultJson().contains(workspace.toString()));
    }

    private PipelineExecutionContext buildContext() {
        PipelineRunDO run = new PipelineRunDO();
        run.setId(800L);
        run.setTenantId(1L);
        PipelineSpec.ExecutableJob job = new PipelineSpec.ExecutableJob();
        job.setJobId("job-1");
        PipelineSpec.ExecutableStep step = new PipelineSpec.ExecutableStep();
        step.setStepId("step-1");
        step.setStep(PipelineNodeRegistryServiceImpl.TYPE_PRIVATE_REGISTRY_DOCKER_BUILD);
        step.setName("镜像构建");
        step.setWith(buildWith());
        return PipelineExecutionContext.builder()
                .run(run)
                .job(job)
                .step(step)
                .build();
    }

    private Map<String, Object> buildWith() {
        Map<String, Object> with = new LinkedHashMap<>();
        with.put("artifact", "my_image");
        with.put("image", "registry.cn-hangzhou.aliyuncs.com/ns/demo:1.0");
        with.put("certificate", Map.of(
                "type", "usernamePassword",
                "username", "robot",
                "password", "secret-pass"));
        with.put("dockerfilePath", "deploy/nginx/Dockerfile");
        with.put("contextPath", ".");
        with.put("noCache", true);
        with.put("variables", List.of(Map.of("key", "PROFILE", "value", "prod")));
        return with;
    }

    private PipelineRunLogDO buildRunLog() {
        PipelineRunLogDO runLog = new PipelineRunLogDO();
        runLog.setId(900L);
        runLog.setPipelineRunId(800L);
        runLog.setStepId("step-1");
        return runLog;
    }

}
