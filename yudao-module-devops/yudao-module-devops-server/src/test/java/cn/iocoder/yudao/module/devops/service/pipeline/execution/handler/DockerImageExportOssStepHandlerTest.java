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
 * {@link DockerImageExportOssStepHandler} 的单元测试。
 */
public class DockerImageExportOssStepHandlerTest extends BaseMockitoUnitTest {

    @InjectMocks
    private DockerImageExportOssStepHandler handler;

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
        when(pipelineRunLogLineService.appendLine(eq(runLog), eq("stdout"), eq("exporting image")))
                .thenReturn(buildLine("exporting image"));
        doAnswer(invocation -> {
            LogSink sink = invocation.getArgument(2);
            sink.accept("stdout", "exporting image");
            return ExecResult.success();
        }).when(pipelineCommandExecutor).exec(any(), eq("/usr/local/bin/export-image-to-oss"), any());

        StepResult result = handler.handle(context);

        assertEquals(StepResultType.CONTINUE, result.getType());
        assertEquals("registry.cn-hangzhou.aliyuncs.com/ns/demo:1.0", result.getOutputs().get("image"));
        assertEquals("oci-archive", result.getOutputs().get("archiveFormat"));
        assertEquals("zstd", result.getOutputs().get("compression"));
        assertEquals("demo-1.0.oci.tar", result.getOutputs().get("outputFileName"));
        assertEquals("oss://release-bucket/images/demo-1.0.oci.tar", result.getOutputs().get("ossPath"));
        ArgumentCaptor<PipelineCommandContext> commandContextCaptor =
                ArgumentCaptor.forClass(PipelineCommandContext.class);
        verify(pipelineCommandExecutor).exec(commandContextCaptor.capture(),
                eq("/usr/local/bin/export-image-to-oss"), any());
        Map<String, String> env = commandContextCaptor.getValue().getEnv();
        assertEquals("registry.cn-hangzhou.aliyuncs.com/ns/demo:1.0", env.get("IMAGE_REF"));
        assertEquals("oci-archive", env.get("ARCHIVE_FORMAT"));
        assertEquals("zstd", env.get("COMPRESSION"));
        assertEquals("/workspace/jobs/job-1/artifacts/demo-1.0.oci.tar", env.get("OUTPUT_FILE"));
        assertEquals("false", env.get("REGISTRY_TLS_VERIFY"));
        assertEquals("true", env.get("OSS_OVERWRITE"));
        verify(logHelper).markSuccess(eq(runLog), eq("镜像导出并上传 OSS 成功"));
        Map<String, Object> resultJson = JsonUtils.parseMap(runLog.getResultJson());
        assertEquals("oss://release-bucket/images/demo-1.0.oci.tar", resultJson.get("ossPath"));
        assertFalse(runLog.getResultJson().contains("secret-pass"));
        assertFalse(runLog.getResultJson().contains("secret-ak"));
    }

    @Test
    public void testHandle_registryTlsVerifyDefaultTrue() {
        PipelineStepContext context = buildContext();
        context.getStep().getWith().remove("registryTlsVerify");
        PipelineRunLogDO runLog = buildRunLog();
        when(logHelper.getOrCreateLog(eq(context))).thenReturn(runLog);
        when(pipelineCommandExecutor.exec(any(), eq("/usr/local/bin/export-image-to-oss"), any()))
                .thenReturn(ExecResult.success());

        StepResult result = handler.handle(context);

        assertEquals(StepResultType.CONTINUE, result.getType());
        ArgumentCaptor<PipelineCommandContext> commandContextCaptor =
                ArgumentCaptor.forClass(PipelineCommandContext.class);
        verify(pipelineCommandExecutor).exec(commandContextCaptor.capture(),
                eq("/usr/local/bin/export-image-to-oss"), any());
        assertEquals("true", commandContextCaptor.getValue().getEnv().get("REGISTRY_TLS_VERIFY"));
    }

    @Test
    public void testHandle_resolveYamlPlaceholders() {
        PipelineStepContext context = buildContext();
        context.getRun().setCommitSha("abc123");
        context.getSharedState().put("artifactName", "demo");
        Map<String, Object> with = context.getStep().getWith();
        with.put("image", "registry.cn-hangzhou.aliyuncs.com/ns/demo:${COMMIT_SHA}");
        with.put("archiveFormat", "${ARCHIVE_FORMAT}");
        with.put("compression", "${COMPRESSION}");
        with.put("outputFileName", "${artifactName}-${commitSha}.oci.tar");
        with.put("registryTlsVerify", "${TLS_VERIFY}");
        with.put("overwrite", "${OSS_OVERWRITE}");
        @SuppressWarnings("unchecked")
        Map<String, Object> oss = (Map<String, Object>) with.get("oss");
        oss.put("path", "oss://release-bucket/images/${artifactName}-${COMMIT_SHA}.oci.tar");
        context.getSharedState().put("ARCHIVE_FORMAT", "oci-archive");
        context.getSharedState().put("COMPRESSION", "gzip");
        context.getSharedState().put("TLS_VERIFY", "false");
        context.getSharedState().put("OSS_OVERWRITE", "true");
        PipelineRunLogDO runLog = buildRunLog();
        when(logHelper.getOrCreateLog(eq(context))).thenReturn(runLog);
        when(pipelineCommandExecutor.exec(any(), eq("/usr/local/bin/export-image-to-oss"), any()))
                .thenReturn(ExecResult.success());

        StepResult result = handler.handle(context);

        assertEquals(StepResultType.CONTINUE, result.getType());
        assertEquals("registry.cn-hangzhou.aliyuncs.com/ns/demo:abc123", result.getOutputs().get("image"));
        assertEquals("demo-abc123.oci.tar", result.getOutputs().get("outputFileName"));
        assertEquals("oss://release-bucket/images/demo-abc123.oci.tar", result.getOutputs().get("ossPath"));
        ArgumentCaptor<PipelineCommandContext> commandContextCaptor =
                ArgumentCaptor.forClass(PipelineCommandContext.class);
        verify(pipelineCommandExecutor).exec(commandContextCaptor.capture(),
                eq("/usr/local/bin/export-image-to-oss"), any());
        Map<String, String> env = commandContextCaptor.getValue().getEnv();
        assertEquals("gzip", env.get("COMPRESSION"));
        assertEquals("false", env.get("REGISTRY_TLS_VERIFY"));
        assertEquals("true", env.get("OSS_OVERWRITE"));
        assertEquals("/workspace/jobs/job-1/artifacts/demo-abc123.oci.tar", env.get("OUTPUT_FILE"));
    }

    @Test
    public void testHandle_invalidConfig() {
        PipelineStepContext context = buildContext();
        context.getStep().setWith(Map.of("image", "registry/ns/demo:1.0"));
        PipelineRunLogDO runLog = buildRunLog();
        when(logHelper.getOrCreateLog(eq(context))).thenReturn(runLog);

        StepResult result = handler.handle(context);

        assertEquals(StepResultType.FAIL, result.getType());
        verify(logHelper).markFailed(eq(runLog), eq("镜像导出参数无效"), eq("registryCertificate 不能为空"));
    }

    private PipelineStepContext buildContext() {
        PipelineRunDO run = new PipelineRunDO();
        run.setId(800L);
        run.setTenantId(1L);
        PipelineSpec.ExecutableJob job = new PipelineSpec.ExecutableJob();
        job.setJobId("job-1");
        PipelineSpec.ExecutableStep step = new PipelineSpec.ExecutableStep();
        step.setStepId("step-1");
        step.setStep(PipelineNodeRegistryServiceImpl.TYPE_DOCKER_IMAGE_EXPORT_OSS);
        step.setName("导出镜像");
        step.setWith(buildWith());
        PipelineJobRuntime runtime = PipelineJobRuntime.builder()
                .runtimeType("DOCKER")
                .runtimeId("container-1")
                .runtimeName("pipeline-1")
                .executorGroup("local-docker/default")
                .executorImage("gone-cloud/image-export-oss:skopeo-ossutil")
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

    private Map<String, Object> buildWith() {
        Map<String, Object> oss = new LinkedHashMap<>();
        oss.put("endpoint", "oss-cn-hangzhou.aliyuncs.com");
        oss.put("path", "oss://release-bucket/images/demo-1.0.oci.tar");
        oss.put("certificate", Map.of(
                "type", "accessKey",
                "accessKeyId", "ak",
                "accessKeySecret", "secret-ak"));
        Map<String, Object> with = new LinkedHashMap<>();
        with.put("image", "registry.cn-hangzhou.aliyuncs.com/ns/demo:1.0");
        with.put("archiveFormat", "oci-archive");
        with.put("compression", "zstd");
        with.put("outputFileName", "demo-1.0.oci.tar");
        with.put("registryTlsVerify", false);
        with.put("registryCertificate", Map.of(
                "type", "usernamePassword",
                "username", "robot",
                "password", "secret-pass"));
        with.put("oss", oss);
        with.put("overwrite", true);
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
