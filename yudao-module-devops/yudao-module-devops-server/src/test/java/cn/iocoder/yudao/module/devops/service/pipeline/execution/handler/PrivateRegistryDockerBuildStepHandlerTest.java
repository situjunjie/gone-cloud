package cn.iocoder.yudao.module.devops.service.pipeline.execution.handler;

import cn.iocoder.yudao.framework.common.util.json.JsonUtils;
import cn.iocoder.yudao.framework.test.core.ut.BaseMockitoUnitTest;
import cn.iocoder.yudao.module.devops.controller.admin.pipelinerun.vo.PipelineRunLogLineRespVO;
import cn.iocoder.yudao.module.devops.dal.dataobject.pipeline.PipelineDefinitionVersionDO;
import cn.iocoder.yudao.module.devops.dal.dataobject.pipeline.PipelineRunDO;
import cn.iocoder.yudao.module.devops.dal.dataobject.pipeline.log.PipelineRunLogDO;
import cn.iocoder.yudao.module.devops.framework.docker.DockerClientFactory;
import cn.iocoder.yudao.module.devops.framework.pipeline.PipelineSpec;
import cn.iocoder.yudao.module.devops.framework.pipeline.runtime.PipelineWorkspace;
import cn.iocoder.yudao.module.devops.framework.pipeline.runtime.PipelineWorkspaceService;
import cn.iocoder.yudao.module.devops.service.pipeline.PipelineNodeRegistryServiceImpl;
import cn.iocoder.yudao.module.devops.service.pipeline.PipelineSpecValidationService;
import cn.iocoder.yudao.module.devops.service.pipeline.execution.PipelineRunLogLineService;
import cn.iocoder.yudao.module.devops.service.pipeline.execution.PipelineSourceWorkspacePreparer;
import com.github.dockerjava.api.DockerClient;
import com.github.dockerjava.api.command.BuildImageCmd;
import com.github.dockerjava.api.command.BuildImageResultCallback;
import com.github.dockerjava.api.command.PushImageCmd;
import com.github.dockerjava.api.model.AuthConfig;
import com.github.dockerjava.api.model.BuildResponseItem;
import com.github.dockerjava.api.model.PushResponseItem;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.springframework.test.util.ReflectionTestUtils;

import java.io.File;
import java.nio.file.Path;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
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
    private PipelineWorkspaceService pipelineWorkspaceService;
    @Mock
    private PipelineSourceWorkspacePreparer pipelineSourceWorkspacePreparer;
    @Mock
    private PipelineSpecValidationService pipelineSpecValidationService;
    @Mock
    private PipelineStepLogHelper logHelper;
    @Mock
    private PipelineRunLogLineService pipelineRunLogLineService;
    @Mock
    private PipelineStepLogFileHelper logFileHelper;

    @Test
    public void testRuntimeRequirement_platform() {
        assertEquals(StepRuntimeRequirement.PLATFORM, handler.runtimeRequirement());
    }

    @Test
    public void testHandle_success() {
        PipelineStepContext context = buildContext();
        PipelineRunLogDO runLog = buildRunLog();
        PipelineSpec spec = new PipelineSpec();
        when(logHelper.getOrCreateLog(eq(context))).thenReturn(runLog);
        when(pipelineWorkspaceService.createWorkspace(eq(context.getRun()), eq(context.getJob()), eq(null)))
                .thenReturn(PipelineWorkspace.builder()
                        .runWorkspace(Path.of("/tmp/workspace"))
                        .cacheWorkspace(Path.of("/tmp/cache"))
                        .cacheKey("definition-1")
                        .cacheMounts(List.of())
                        .build());
        when(pipelineSpecValidationService.parseSpec(eq("stages: {}"), any())).thenReturn(spec);
        when(dockerClientFactory.getDefaultClient()).thenReturn(dockerClient);
        when(dockerClient.buildImageCmd(any(File.class))).thenReturn(buildImageCmd);
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
        when(pipelineRunLogLineService.appendLine(eq(runLog), any(), any())).thenAnswer(invocation -> {
            PipelineRunLogLineRespVO line = new PipelineRunLogLineRespVO();
            line.setContent(invocation.getArgument(2));
            return line;
        });
        org.mockito.Mockito.doAnswer(invocation -> {
            runLog.setLogFileUrl("https://file/log");
            return null;
        }).when(logFileHelper).uploadFullLog(eq(runLog));

        StepResult result = handler.handle(context);

        assertEquals(StepResultType.CONTINUE, result.getType());
        assertEquals("my_image", result.getOutputs().get("artifact"));
        assertEquals("registry.cn-hangzhou.aliyuncs.com/ns/demo:1.0", result.getOutputs().get("image"));
        verify(pipelineSourceWorkspacePreparer).prepare(eq(context.getRun()), eq(spec), eq(Path.of("/tmp/workspace")),
                eq(context.getSharedState()));
        verify(buildImageCmd).withBuildArg(eq("PROFILE"), eq("prod"));
        ArgumentCaptor<AuthConfig> authConfigCaptor = ArgumentCaptor.forClass(AuthConfig.class);
        verify(pushImageCmd).withAuthConfig(authConfigCaptor.capture());
        assertEquals("robot", authConfigCaptor.getValue().getUsername());
        assertEquals("registry.cn-hangzhou.aliyuncs.com", authConfigCaptor.getValue().getRegistryAddress());
        verify(logHelper).markSuccess(eq(runLog), eq("镜像构建并推送成功"));
        Map<String, Object> resultJson = JsonUtils.parseMap(runLog.getResultJson());
        assertEquals("sha256:image-id", resultJson.get("imageId"));
        assertEquals("https://file/log", resultJson.get("logFileUrl"));
        assertFalse(runLog.getResultJson().contains("secret-pass"));
        assertFalse(runLog.getResultJson().contains("/tmp/workspace"));
    }

    private PipelineStepContext buildContext() {
        PipelineRunDO run = new PipelineRunDO();
        run.setId(800L);
        run.setTenantId(1L);
        PipelineDefinitionVersionDO version = new PipelineDefinitionVersionDO();
        version.setSpecJson("stages: {}");
        PipelineSpec.ExecutableJob job = new PipelineSpec.ExecutableJob();
        job.setJobId("job-1");
        PipelineSpec.ExecutableStep step = new PipelineSpec.ExecutableStep();
        step.setStepId("step-1");
        step.setStep(PipelineNodeRegistryServiceImpl.TYPE_PRIVATE_REGISTRY_DOCKER_BUILD);
        step.setName("镜像构建");
        step.setWith(buildWith());
        return PipelineStepContext.builder()
                .run(run)
                .version(version)
                .job(job)
                .step(step)
                .sharedState(new ConcurrentHashMap<>())
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
        with.put("dockerfilePath", "docker/Dockerfile");
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
