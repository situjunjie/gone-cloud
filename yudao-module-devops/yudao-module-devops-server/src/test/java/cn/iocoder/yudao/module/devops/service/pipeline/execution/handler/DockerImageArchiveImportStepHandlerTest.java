package cn.iocoder.yudao.module.devops.service.pipeline.execution.handler;

import cn.iocoder.yudao.framework.common.util.json.JsonUtils;
import cn.iocoder.yudao.framework.test.core.ut.BaseMockitoUnitTest;
import cn.iocoder.yudao.module.devops.controller.admin.pipelinerun.vo.PipelineRunLogLineRespVO;
import cn.iocoder.yudao.module.devops.dal.dataobject.pipeline.PipelineRunDO;
import cn.iocoder.yudao.module.devops.dal.dataobject.pipeline.log.PipelineRunLogDO;
import cn.iocoder.yudao.module.devops.framework.build.ExecResult;
import cn.iocoder.yudao.module.devops.framework.build.LogSink;
import cn.iocoder.yudao.module.devops.framework.pipeline.PipelineSpec;
import cn.iocoder.yudao.module.devops.framework.pipeline.runtime.PipelineCommandContext;
import cn.iocoder.yudao.module.devops.framework.pipeline.runtime.PipelineCommandExecutor;
import cn.iocoder.yudao.module.devops.framework.pipeline.runtime.PipelineJobRuntime;
import cn.iocoder.yudao.module.devops.service.pipeline.PipelineNodeRegistryServiceImpl;
import cn.iocoder.yudao.module.devops.service.pipeline.execution.PipelineRunLogLineService;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;

import java.nio.file.Path;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * {@link DockerImageArchiveImportStepHandler} 的单元测试。
 */
public class DockerImageArchiveImportStepHandlerTest extends BaseMockitoUnitTest {

    @InjectMocks
    private DockerImageArchiveImportStepHandler handler;

    @Mock
    private PipelineCommandExecutor pipelineCommandExecutor;
    @Mock
    private PipelineStepLogHelper logHelper;
    @Mock
    private PipelineRunLogLineService pipelineRunLogLineService;

    @Test
    public void testRuntimeRequirement_jobRuntime() {
        assertEquals(StepRuntimeRequirement.JOB_RUNTIME, handler.runtimeRequirement());
    }

    @Test
    public void testHandle_success() {
        PipelineStepContext context = buildContext();
        PipelineRunLogDO runLog = buildRunLog();
        when(logHelper.getOrCreateLog(eq(context))).thenReturn(runLog);
        when(pipelineRunLogLineService.appendLine(eq(runLog), eq("stdout"), eq("importing image")))
                .thenReturn(buildLine("importing image"));
        doAnswer(invocation -> {
            LogSink sink = invocation.getArgument(2);
            sink.accept("stdout", "importing image");
            return ExecResult.success();
        }).when(pipelineCommandExecutor).exec(any(), eq("/usr/local/bin/import-image-archive-to-registry"), any());

        StepResult result = handler.handle(context);

        assertEquals(StepResultType.CONTINUE, result.getType());
        ArgumentCaptor<PipelineCommandContext> commandContextCaptor =
                ArgumentCaptor.forClass(PipelineCommandContext.class);
        verify(pipelineCommandExecutor).exec(commandContextCaptor.capture(),
                eq("/usr/local/bin/import-image-archive-to-registry"), any());
        Map<String, String> env = commandContextCaptor.getValue().getEnv();
        assertEquals("https://example.com/images/demo.oci.tar.zst", env.get("FILE_URL"));
        assertEquals("registry.cn-hangzhou.aliyuncs.com/ns/demo:800", env.get("IMAGE_REF"));
        assertEquals("oci-archive", env.get("ARCHIVE_FORMAT"));
        assertEquals("zstd", env.get("COMPRESSION"));
        assertEquals("robot", env.get("REGISTRY_USERNAME"));
        assertEquals("secret-pass", env.get("REGISTRY_PASSWORD"));
        assertEquals("false", env.get("REGISTRY_TLS_VERIFY"));
        verify(logHelper).markSuccess(eq(runLog), eq("镜像包导入镜像仓库成功"));
        Map<String, Object> resultJson = JsonUtils.parseMap(runLog.getResultJson());
        assertEquals("https://example.com/images/demo.oci.tar.zst", resultJson.get("fileUrl"));
        assertEquals("registry.cn-hangzhou.aliyuncs.com/ns/demo:800", resultJson.get("image"));
        assertFalse(runLog.getResultJson().contains("secret-pass"));
    }

    @Test
    public void testHandle_invalidConfig() {
        PipelineStepContext context = buildContext();
        context.getStep().setWith(Map.of("image", "registry/ns/demo:1.0"));
        PipelineRunLogDO runLog = buildRunLog();
        when(logHelper.getOrCreateLog(eq(context))).thenReturn(runLog);

        StepResult result = handler.handle(context);

        assertEquals(StepResultType.FAIL, result.getType());
        verify(logHelper).markFailed(eq(runLog), eq("镜像包导入参数无效"), eq("fileUrl 不能为空"));
    }

    @Test
    public void testCancel() {
        PipelineStepContext context = buildContext();

        handler.cancel(context);

        verify(pipelineCommandExecutor).cancel(eq("800:job-1:step-1"));
    }

    private PipelineStepContext buildContext() {
        PipelineRunDO run = new PipelineRunDO();
        run.setId(800L);
        run.setTenantId(1L);
        PipelineSpec.ExecutableJob job = new PipelineSpec.ExecutableJob();
        job.setJobId("job-1");
        PipelineSpec.ExecutableStep step = new PipelineSpec.ExecutableStep();
        step.setStepId("step-1");
        step.setStep(PipelineNodeRegistryServiceImpl.TYPE_DOCKER_IMAGE_ARCHIVE_IMPORT);
        step.setName("导入镜像");
        step.setWith(buildWith());
        PipelineJobRuntime runtime = PipelineJobRuntime.builder()
                .runtimeType("DOCKER")
                .runtimeId("container-1")
                .runtimeName("pipeline-1")
                .executorGroup("local-docker/default")
                .executorImage("gone-cloud/pipeline-builder:java17-node24-maven3.9")
                .workspace(Path.of("/tmp/workspace"))
                .build();
        Map<String, Object> sharedState = new ConcurrentHashMap<>();
        sharedState.put("jobRuntime:job-1", runtime);
        sharedState.put("FILE_URL", "https://example.com/images/demo.oci.tar.zst");
        return PipelineStepContext.builder()
                .run(run)
                .job(job)
                .step(step)
                .sharedState(sharedState)
                .build();
    }

    private Map<String, Object> buildWith() {
        Map<String, Object> with = new LinkedHashMap<>();
        with.put("fileUrl", "${FILE_URL}");
        with.put("image", "registry.cn-hangzhou.aliyuncs.com/ns/demo:${runId}");
        with.put("archiveFormat", "oci-archive");
        with.put("compression", "zstd");
        with.put("registryTlsVerify", false);
        with.put("certificate", Map.of(
                "type", "usernamePassword",
                "username", "robot",
                "password", "secret-pass"));
        return with;
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
