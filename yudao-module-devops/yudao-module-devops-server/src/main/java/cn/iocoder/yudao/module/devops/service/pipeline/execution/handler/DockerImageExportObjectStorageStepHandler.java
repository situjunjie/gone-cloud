package cn.iocoder.yudao.module.devops.service.pipeline.execution.handler;

import cn.hutool.core.util.StrUtil;
import cn.iocoder.yudao.framework.common.util.json.JsonUtils;
import cn.iocoder.yudao.module.devops.dal.dataobject.pipeline.log.PipelineRunLogDO;
import cn.iocoder.yudao.module.devops.framework.build.ExecResult;
import cn.iocoder.yudao.module.devops.framework.pipeline.runtime.PipelineCommandContext;
import cn.iocoder.yudao.module.devops.framework.pipeline.runtime.PipelineCommandExecutor;
import cn.iocoder.yudao.module.devops.framework.pipeline.runtime.PipelineJobRuntime;
import cn.iocoder.yudao.module.devops.service.pipeline.PipelineNodeRegistryServiceImpl;
import cn.iocoder.yudao.module.devops.service.pipeline.execution.PipelineRunLogFileStorage;
import jakarta.annotation.Resource;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.nio.file.Path;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * 镜像导出并上传对象存储步骤处理器。
 */
@Slf4j
@Component
public class DockerImageExportObjectStorageStepHandler implements PipelineStepHandler {

    private static final long DEFAULT_TIMEOUT_SECONDS = 1800;
    private static final int MAX_LOG_LINES = 500;
    private static final String DEFAULT_ARCHIVE_FORMAT = "docker-archive";
    private static final String DEFAULT_COMPRESSION = "none";

    @Resource
    private PipelineCommandExecutor pipelineCommandExecutor;
    @Resource
    private PipelineStepLogHelper logHelper;
    @Resource
    private PipelineStepLogFileHelper logFileHelper;

    @Override
    public boolean supports(String stepType) {
        return PipelineNodeRegistryServiceImpl.TYPE_DOCKER_IMAGE_EXPORT_OBJECT_STORAGE.equals(stepType);
    }

    @Override
    public StepRuntimeRequirement runtimeRequirement() {
        return StepRuntimeRequirement.JOB_RUNTIME;
    }

    @Override
    public StepResult handle(PipelineStepContext ctx) {
        PipelineRunLogDO runLog = logHelper.getOrCreateLog(ctx);
        if ("SUCCESS".equals(runLog.getStatus())) {
            return StepResult.continueWith("步骤已完成");
        }
        ExportConfig config = parseConfig(ctx);
        if (!config.valid()) {
            logHelper.markFailed(runLog, "镜像导出参数无效", config.errorMessage());
            return StepResult.fail("镜像导出参数无效", config.errorMessage());
        }
        PipelineJobRuntime runtime = (PipelineJobRuntime) ctx.getSharedState().get(CommandStepHandler.runtimeKey(ctx));
        if (runtime != null) {
            fillRuntime(runLog, runtime);
            logHelper.update(runLog);
        }
        logHelper.markStarted(runLog, "导出镜像并上传对象存储");
        PipelineRunLogFileStorage.PipelineRunLogFiles logFiles = logFileHelper.prepareLogFiles(runLog, runtime);

        ExecResult result = pipelineCommandExecutor.exec(PipelineCommandContext.builder()
                        .runtime(runtime)
                        .runId(ctx.getRun().getId() + ":" + ctx.getJob().getJobId() + ":" + ctx.getStep().getStepId())
                        .env(buildEnv(config))
                        .stdoutLogPath(logFiles == null ? null : logFiles.getContainerStdoutPath())
                        .stderrLogPath(logFiles == null ? null : logFiles.getContainerStderrPath())
                        .timeoutSeconds(resolveTimeoutSeconds(ctx))
                        .build(),
                "/usr/local/bin/export-image-to-object-storage",
                null);

        List<String> logLines = logFileHelper.readSummaryLines(runLog, MAX_LOG_LINES);
        logFileHelper.uploadFullLog(runLog);
        Map<String, Object> resultJson = new LinkedHashMap<>();
        resultJson.put("image", config.image());
        resultJson.put("archiveFormat", config.archiveFormat());
        resultJson.put("compression", config.compression());
        resultJson.put("outputFileName", config.outputFileName());
        resultJson.put("storagePath", config.storagePath());
        resultJson.put("exitCode", result.getExitCode());
        resultJson.put("logLines", logLines);
        resultJson.put("logTruncated", Boolean.TRUE.equals(runLog.getLogTruncated()));
        resultJson.put("logFileUrl", runLog.getLogFileUrl());
        runLog.setResultJson(JsonUtils.toJsonString(resultJson));
        if (result.isSuccess()) {
            logHelper.markSuccess(runLog, "镜像导出并上传对象存储成功");
            return StepResult.builder()
                    .type(StepResultType.CONTINUE)
                    .summary("镜像导出并上传对象存储成功")
                    .outputs(Map.of(
                            "image", config.image(),
                            "archiveFormat", config.archiveFormat(),
                            "compression", config.compression(),
                            "outputFileName", config.outputFileName(),
                            "storagePath", config.storagePath()))
                    .build();
        }
        String errorMessage = StrUtil.blankToDefault(result.getErrorMessage(),
                "Image export failed with exit code " + result.getExitCode());
        logHelper.markFailed(runLog, "镜像导出或对象存储上传失败", errorMessage);
        log.error("[DockerImageExportObjectStorageStepHandler][runId({}) stepId({}) 执行失败: {}]",
                ctx.getRun().getId(), ctx.getStep().getStepId(), errorMessage);
        return StepResult.fail("镜像导出或对象存储上传失败", errorMessage);
    }

    @Override
    public void cancel(PipelineStepContext ctx) {
        pipelineCommandExecutor.cancel(ctx.getRun().getId() + ":" + ctx.getJob().getJobId() + ":" + ctx.getStep().getStepId());
    }

    private ExportConfig parseConfig(PipelineStepContext ctx) {
        Map<String, Object> with = ctx.getResolvedWith();
        if (with == null) {
            return ExportConfig.invalid("with 不能为空");
        }
        String image = stringValue(with.get("image"));
        if (StrUtil.isBlank(image)) {
            return ExportConfig.invalid("image 不能为空");
        }
        String archiveFormat = StrUtil.blankToDefault(stringValue(with.get("archiveFormat")), DEFAULT_ARCHIVE_FORMAT);
        if (!List.of("docker-archive", "oci-archive").contains(archiveFormat)) {
            return ExportConfig.invalid("archiveFormat 仅支持 docker-archive、oci-archive");
        }
        String compression = StrUtil.blankToDefault(stringValue(with.get("compression")), DEFAULT_COMPRESSION);
        if (!List.of("none", "gzip", "zstd").contains(compression)) {
            return ExportConfig.invalid("compression 仅支持 none、gzip、zstd");
        }
        String outputFileName = StrUtil.blankToDefault(stringValue(with.get("outputFileName")),
                defaultOutputFileName(image, archiveFormat));
        if (outputFileName.contains("/") || outputFileName.contains("\\") || outputFileName.contains("..")) {
            return ExportConfig.invalid("outputFileName 只能是文件名，不能包含路径");
        }
        Credential registryCredential = parseRegistryCredential(with.get("registryCertificate"));
        if (!registryCredential.valid()) {
            return ExportConfig.invalid(registryCredential.errorMessage());
        }
        StorageConfig storageConfig = parseStorageConfig(with.get("storage"));
        if (!storageConfig.valid()) {
            return ExportConfig.invalid(storageConfig.errorMessage());
        }
        String outputFile = "/workspace/jobs/" + ctx.getJob().getJobId() + "/artifacts/" + outputFileName;
        return ExportConfig.valid(image, archiveFormat, compression, outputFileName, outputFile,
                booleanValue(with.get("registryTlsVerify"), true), registryCredential.username(),
                registryCredential.password(), storageConfig.type(), storageConfig.endpoint(), storageConfig.path(),
                storageConfig.region(), storageConfig.forcePathStyle(), storageConfig.accessKeyId(),
                storageConfig.accessKeySecret(), booleanValue(with.get("overwrite"), false));
    }

    private Credential parseRegistryCredential(Object value) {
        if (!(value instanceof Map<?, ?> certificate)) {
            return Credential.invalid("registryCertificate 不能为空");
        }
        if (!"usernamePassword".equals(stringValue(certificate.get("type")))) {
            return Credential.invalid("registryCertificate.type 当前仅支持 usernamePassword");
        }
        String username = stringValue(certificate.get("username"));
        String password = stringValue(certificate.get("password"));
        if (StrUtil.isBlank(username) || StrUtil.isBlank(password)) {
            return Credential.invalid("registryCertificate.username、registryCertificate.password 不能为空");
        }
        return Credential.valid(username, password);
    }

    private StorageConfig parseStorageConfig(Object value) {
        if (!(value instanceof Map<?, ?> storage)) {
            return StorageConfig.invalid("storage 不能为空");
        }
        String type = stringValue(storage.get("type"));
        String endpoint = stringValue(storage.get("endpoint"));
        String path = stringValue(storage.get("path"));
        String region = stringValue(storage.get("region"));
        if (StrUtil.isBlank(type)) {
            return StorageConfig.invalid("storage.type 不能为空");
        }
        if (!"s3".equals(type)) {
            return StorageConfig.invalid("storage.type 当前仅支持 s3");
        }
        if (StrUtil.isBlank(endpoint) || StrUtil.isBlank(path)) {
            return StorageConfig.invalid("storage.endpoint、storage.path 不能为空");
        }
        if (!path.startsWith("s3://")) {
            return StorageConfig.invalid("storage.path 必须使用 s3://bucket/path/file 格式");
        }
        if (!(storage.get("certificate") instanceof Map<?, ?> certificate)) {
            return StorageConfig.invalid("storage.certificate 不能为空");
        }
        if (!"accessKey".equals(stringValue(certificate.get("type")))) {
            return StorageConfig.invalid("storage.certificate.type 当前仅支持 accessKey");
        }
        String accessKeyId = stringValue(certificate.get("accessKeyId"));
        String accessKeySecret = stringValue(certificate.get("accessKeySecret"));
        if (StrUtil.isBlank(accessKeyId) || StrUtil.isBlank(accessKeySecret)) {
            return StorageConfig.invalid("storage.certificate.accessKeyId、storage.certificate.accessKeySecret 不能为空");
        }
        return StorageConfig.valid(type, endpoint, path, region, booleanValue(storage.get("forcePathStyle"), false),
                accessKeyId, accessKeySecret);
    }

    private Map<String, String> buildEnv(ExportConfig config) {
        Map<String, String> env = new LinkedHashMap<>();
        env.put("IMAGE_REF", config.image());
        env.put("ARCHIVE_FORMAT", config.archiveFormat());
        env.put("COMPRESSION", config.compression());
        env.put("OUTPUT_FILE", config.outputFile());
        env.put("REGISTRY_USERNAME", config.registryUsername());
        env.put("REGISTRY_PASSWORD", config.registryPassword());
        env.put("REGISTRY_TLS_VERIFY", String.valueOf(config.registryTlsVerify()));
        env.put("STORAGE_TYPE", config.storageType());
        env.put("STORAGE_ENDPOINT", config.storageEndpoint());
        env.put("STORAGE_PATH", config.storagePath());
        env.put("STORAGE_REGION", StrUtil.emptyToDefault(config.storageRegion(), ""));
        env.put("STORAGE_FORCE_PATH_STYLE", String.valueOf(config.storageForcePathStyle()));
        env.put("STORAGE_ACCESS_KEY_ID", config.storageAccessKeyId());
        env.put("STORAGE_ACCESS_KEY_SECRET", config.storageAccessKeySecret());
        env.put("STORAGE_OVERWRITE", String.valueOf(config.overwrite()));
        return env;
    }

    private void fillRuntime(PipelineRunLogDO runLog, PipelineJobRuntime runtime) {
        runLog.setRuntimeType(runtime.getRuntimeType());
        runLog.setRuntimeId(runtime.getRuntimeId());
        runLog.setRuntimeName(runtime.getRuntimeName());
        runLog.setExecutorGroup(runtime.getExecutorGroup());
        runLog.setExecutorImage(runtime.getExecutorImage());
        Path workspace = runtime.getWorkspace();
        runLog.setWorkspacePath(workspace == null ? null : workspace.toString());
    }

    private long resolveTimeoutSeconds(PipelineStepContext ctx) {
        Integer timeoutSeconds = ctx.getStep().getTimeoutSeconds();
        return timeoutSeconds == null || timeoutSeconds <= 0 ? DEFAULT_TIMEOUT_SECONDS : timeoutSeconds;
    }

    private String defaultOutputFileName(String image, String archiveFormat) {
        String normalized = image.replaceAll("[^A-Za-z0-9._-]+", "-");
        String extension = "oci-archive".equals(archiveFormat) ? ".oci.tar" : ".docker.tar";
        return StrUtil.subPre(normalized, 120) + extension;
    }

    private String stringValue(Object value) {
        return value == null ? null : String.valueOf(value);
    }

    private boolean booleanValue(Object value, boolean defaultValue) {
        if (value instanceof Boolean bool) {
            return bool;
        }
        if (value instanceof String str && StrUtil.isNotBlank(str)) {
            if ("true".equalsIgnoreCase(str) || "false".equalsIgnoreCase(str)) {
                return Boolean.parseBoolean(str);
            }
        }
        return defaultValue;
    }

    private record ExportConfig(String image, String archiveFormat, String compression, String outputFileName,
                                String outputFile, boolean registryTlsVerify, String registryUsername,
                                String registryPassword, String storageType, String storageEndpoint,
                                String storagePath, String storageRegion, boolean storageForcePathStyle,
                                String storageAccessKeyId, String storageAccessKeySecret, boolean overwrite,
                                String errorMessage) {

        private static ExportConfig valid(String image, String archiveFormat, String compression,
                                          String outputFileName, String outputFile, boolean registryTlsVerify,
                                          String registryUsername, String registryPassword, String storageType,
                                          String storageEndpoint, String storagePath, String storageRegion,
                                          boolean storageForcePathStyle, String storageAccessKeyId,
                                          String storageAccessKeySecret, boolean overwrite) {
            return new ExportConfig(image, archiveFormat, compression, outputFileName, outputFile, registryTlsVerify,
                    registryUsername, registryPassword, storageType, storageEndpoint, storagePath, storageRegion,
                    storageForcePathStyle, storageAccessKeyId, storageAccessKeySecret, overwrite, null);
        }

        private static ExportConfig invalid(String errorMessage) {
            return new ExportConfig(null, null, null, null, null, true, null, null, null, null, null, null,
                    false, null, null, false, errorMessage);
        }

        private boolean valid() {
            return StrUtil.isBlank(errorMessage);
        }

    }

    private record Credential(String username, String password, String errorMessage) {

        private static Credential valid(String username, String password) {
            return new Credential(username, password, null);
        }

        private static Credential invalid(String errorMessage) {
            return new Credential(null, null, errorMessage);
        }

        private boolean valid() {
            return StrUtil.isBlank(errorMessage);
        }

    }

    private record StorageConfig(String type, String endpoint, String path, String region, boolean forcePathStyle,
                                 String accessKeyId, String accessKeySecret, String errorMessage) {

        private static StorageConfig valid(String type, String endpoint, String path, String region,
                                           boolean forcePathStyle, String accessKeyId, String accessKeySecret) {
            return new StorageConfig(type, endpoint, path, region, forcePathStyle, accessKeyId, accessKeySecret, null);
        }

        private static StorageConfig invalid(String errorMessage) {
            return new StorageConfig(null, null, null, null, false, null, null, errorMessage);
        }

        private boolean valid() {
            return StrUtil.isBlank(errorMessage);
        }

    }

}
