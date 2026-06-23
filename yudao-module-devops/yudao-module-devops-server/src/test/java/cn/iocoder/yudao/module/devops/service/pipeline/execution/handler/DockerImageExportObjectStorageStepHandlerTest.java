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
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * {@link DockerImageExportObjectStorageStepHandler} 的单元测试。
 */
public class DockerImageExportObjectStorageStepHandlerTest extends BaseMockitoUnitTest {

    @InjectMocks
    private DockerImageExportObjectStorageStepHandler handler;

    @Mock
    private PipelineCommandExecutor pipelineCommandExecutor;
    @Mock
    private PipelineStepLogHelper logHelper;
    @Mock
    private PipelineStepLogFileHelper logFileHelper;

    @Test
    public void testRuntimeRequirement_jobRuntime() {
        assertEquals(StepRuntimeRequirement.JOB_RUNTIME, handler.runtimeRequirement());
    }

    @Test
    public void testHandle_success() {
        PipelineStepContext context = buildContext();
        PipelineRunLogDO runLog = buildRunLog();
        when(logHelper.getOrCreateLog(eq(context))).thenReturn(runLog);
        when(logFileHelper.prepareLogFiles(eq(runLog), any())).thenReturn(buildFiles());
        when(logFileHelper.readSummaryLines(eq(runLog), eq(500))).thenReturn(java.util.List.of("exporting image"));
        doAnswer(invocation -> {
            runLog.setLogFileUrl("https://file/log");
            return null;
        }).when(logFileHelper).uploadFullLog(eq(runLog));
        when(pipelineCommandExecutor.exec(any(), eq("/usr/local/bin/export-image-to-object-storage"), isNull()))
                .thenReturn(ExecResult.success());

        StepResult result = handler.handle(context);

        assertEquals(StepResultType.CONTINUE, result.getType());
        assertEquals("registry.cn-hangzhou.aliyuncs.com/ns/demo:1.0", result.getOutputs().get("image"));
        assertEquals("oci-archive", result.getOutputs().get("archiveFormat"));
        assertEquals("zstd", result.getOutputs().get("compression"));
        assertEquals("demo-1.0.oci.tar", result.getOutputs().get("outputFileName"));
        assertEquals("s3://release-bucket/images/demo-1.0.oci.tar", result.getOutputs().get("storagePath"));
        ArgumentCaptor<PipelineCommandContext> commandContextCaptor =
                ArgumentCaptor.forClass(PipelineCommandContext.class);
        verify(pipelineCommandExecutor).exec(commandContextCaptor.capture(),
                eq("/usr/local/bin/export-image-to-object-storage"), isNull());
        Map<String, String> env = commandContextCaptor.getValue().getEnv();
        assertEquals("/workspace/.gone-devops/logs/run-log-900/stdout.log",
                commandContextCaptor.getValue().getStdoutLogPath());
        assertEquals("/workspace/.gone-devops/logs/run-log-900/stderr.log",
                commandContextCaptor.getValue().getStderrLogPath());
        assertEquals("registry.cn-hangzhou.aliyuncs.com/ns/demo:1.0", env.get("IMAGE_REF"));
        assertEquals("oci-archive", env.get("ARCHIVE_FORMAT"));
        assertEquals("zstd", env.get("COMPRESSION"));
        assertEquals("/workspace/jobs/job-1/artifacts/demo-1.0.oci.tar", env.get("OUTPUT_FILE"));
        assertEquals("false", env.get("REGISTRY_TLS_VERIFY"));
        assertEquals("s3", env.get("STORAGE_TYPE"));
        assertEquals("https://oss-cn-hangzhou.aliyuncs.com", env.get("STORAGE_ENDPOINT"));
        assertEquals("s3://release-bucket/images/demo-1.0.oci.tar", env.get("STORAGE_PATH"));
        assertEquals("cn-hangzhou", env.get("STORAGE_REGION"));
        assertEquals("true", env.get("STORAGE_FORCE_PATH_STYLE"));
        assertEquals("true", env.get("STORAGE_OVERWRITE"));
        verify(logHelper).markSuccess(eq(runLog), eq("镜像导出并上传对象存储成功"));
        Map<String, Object> resultJson = JsonUtils.parseMap(runLog.getResultJson());
        assertEquals("s3://release-bucket/images/demo-1.0.oci.tar", resultJson.get("storagePath"));
        assertEquals("https://file/log", resultJson.get("logFileUrl"));
        assertFalse(runLog.getResultJson().contains("secret-pass"));
        assertFalse(runLog.getResultJson().contains("secret-ak"));
    }

    @Test
    public void testHandle_registryTlsVerifyDefaultTrue() {
        PipelineStepContext context = buildContext();
        context.getStep().getWith().remove("registryTlsVerify");
        PipelineRunLogDO runLog = buildRunLog();
        when(logHelper.getOrCreateLog(eq(context))).thenReturn(runLog);
        when(logFileHelper.prepareLogFiles(eq(runLog), any())).thenReturn(buildFiles());
        when(logFileHelper.readSummaryLines(eq(runLog), eq(500))).thenReturn(java.util.List.of());
        when(pipelineCommandExecutor.exec(any(), eq("/usr/local/bin/export-image-to-object-storage"), isNull()))
                .thenReturn(ExecResult.success());

        StepResult result = handler.handle(context);

        assertEquals(StepResultType.CONTINUE, result.getType());
        ArgumentCaptor<PipelineCommandContext> commandContextCaptor =
                ArgumentCaptor.forClass(PipelineCommandContext.class);
        verify(pipelineCommandExecutor).exec(commandContextCaptor.capture(),
                eq("/usr/local/bin/export-image-to-object-storage"), isNull());
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
        with.put("overwrite", "${STORAGE_OVERWRITE}");
        @SuppressWarnings("unchecked")
        Map<String, Object> storage = (Map<String, Object>) with.get("storage");
        storage.put("path", "s3://release-bucket/images/${artifactName}-${COMMIT_SHA}.oci.tar");
        context.getSharedState().put("ARCHIVE_FORMAT", "oci-archive");
        context.getSharedState().put("COMPRESSION", "gzip");
        context.getSharedState().put("TLS_VERIFY", "false");
        context.getSharedState().put("STORAGE_OVERWRITE", "true");
        PipelineRunLogDO runLog = buildRunLog();
        when(logHelper.getOrCreateLog(eq(context))).thenReturn(runLog);
        when(logFileHelper.prepareLogFiles(eq(runLog), any())).thenReturn(buildFiles());
        when(logFileHelper.readSummaryLines(eq(runLog), eq(500))).thenReturn(java.util.List.of());
        when(pipelineCommandExecutor.exec(any(), eq("/usr/local/bin/export-image-to-object-storage"), isNull()))
                .thenReturn(ExecResult.success());

        StepResult result = handler.handle(context);

        assertEquals(StepResultType.CONTINUE, result.getType());
        assertEquals("registry.cn-hangzhou.aliyuncs.com/ns/demo:abc123", result.getOutputs().get("image"));
        assertEquals("demo-abc123.oci.tar", result.getOutputs().get("outputFileName"));
        assertEquals("s3://release-bucket/images/demo-abc123.oci.tar", result.getOutputs().get("storagePath"));
        ArgumentCaptor<PipelineCommandContext> commandContextCaptor =
                ArgumentCaptor.forClass(PipelineCommandContext.class);
        verify(pipelineCommandExecutor).exec(commandContextCaptor.capture(),
                eq("/usr/local/bin/export-image-to-object-storage"), isNull());
        Map<String, String> env = commandContextCaptor.getValue().getEnv();
        assertEquals("gzip", env.get("COMPRESSION"));
        assertEquals("false", env.get("REGISTRY_TLS_VERIFY"));
        assertEquals("true", env.get("STORAGE_OVERWRITE"));
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
        step.setStep(PipelineNodeRegistryServiceImpl.TYPE_DOCKER_IMAGE_EXPORT_OBJECT_STORAGE);
        step.setName("导出镜像");
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
        return PipelineStepContext.builder()
                .run(run)
                .job(job)
                .step(step)
                .sharedState(sharedState)
                .build();
    }

    private Map<String, Object> buildWith() {
        Map<String, Object> storage = new LinkedHashMap<>();
        storage.put("type", "s3");
        storage.put("endpoint", "https://oss-cn-hangzhou.aliyuncs.com");
        storage.put("path", "s3://release-bucket/images/demo-1.0.oci.tar");
        storage.put("region", "cn-hangzhou");
        storage.put("forcePathStyle", true);
        storage.put("certificate", Map.of(
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
        with.put("storage", storage);
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

    private PipelineRunLogFileStorage.PipelineRunLogFiles buildFiles() {
        PipelineRunLogFileStorage.PipelineRunLogFiles files = new PipelineRunLogFileStorage.PipelineRunLogFiles();
        files.setContainerStdoutPath("/workspace/.gone-devops/logs/run-log-900/stdout.log");
        files.setContainerStderrPath("/workspace/.gone-devops/logs/run-log-900/stderr.log");
        return files;
    }

}
