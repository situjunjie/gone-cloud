package cn.iocoder.yudao.module.devops.service.pipeline.execution.handler;

import cn.hutool.core.util.StrUtil;
import cn.iocoder.yudao.framework.common.util.json.JsonUtils;
import cn.iocoder.yudao.module.devops.controller.admin.pipelinerun.vo.PipelineRunLogLineRespVO;
import cn.iocoder.yudao.module.devops.dal.dataobject.pipeline.log.PipelineRunLogDO;
import cn.iocoder.yudao.module.devops.framework.build.ExecResult;
import cn.iocoder.yudao.module.devops.framework.pipeline.runtime.PipelineCommandContext;
import cn.iocoder.yudao.module.devops.framework.pipeline.runtime.PipelineCommandExecutor;
import cn.iocoder.yudao.module.devops.framework.pipeline.runtime.PipelineJobRuntime;
import cn.iocoder.yudao.module.devops.service.pipeline.PipelineNodeRegistryServiceImpl;
import cn.iocoder.yudao.module.devops.service.pipeline.execution.PipelineRunLogLineService;
import jakarta.annotation.Resource;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.nio.file.Path;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * 镜像归档导入镜像仓库步骤处理器。
 */
@Slf4j
@Component
public class DockerImageArchiveImportStepHandler implements PipelineStepHandler {

    private static final long DEFAULT_TIMEOUT_SECONDS = 1800;
    private static final int MAX_LOG_LINES = 500;
    private static final String DEFAULT_ARCHIVE_FORMAT = "docker-archive";
    private static final String DEFAULT_COMPRESSION = "none";

    @Resource
    private PipelineCommandExecutor pipelineCommandExecutor;
    @Resource
    private PipelineStepLogHelper logHelper;
    @Resource
    private PipelineRunLogLineService pipelineRunLogLineService;

    @Override
    public boolean supports(String stepType) {
        return PipelineNodeRegistryServiceImpl.TYPE_DOCKER_IMAGE_ARCHIVE_IMPORT.equals(stepType);
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
        ImportConfig config = parseConfig(ctx);
        if (!config.valid()) {
            logHelper.markFailed(runLog, "镜像包导入参数无效", config.errorMessage());
            return StepResult.fail("镜像包导入参数无效", config.errorMessage());
        }
        PipelineJobRuntime runtime = (PipelineJobRuntime) ctx.getSharedState().get(CommandStepHandler.runtimeKey(ctx));
        if (runtime != null) {
            fillRuntime(runLog, runtime);
            logHelper.update(runLog);
        }
        logHelper.markStarted(runLog, "导入镜像包到镜像仓库");

        List<String> logLines = new ArrayList<>();
        ExecResult result = pipelineCommandExecutor.exec(PipelineCommandContext.builder()
                        .runtime(runtime)
                        .runId(ctx.getRun().getId() + ":" + ctx.getJob().getJobId() + ":" + ctx.getStep().getStepId())
                        .env(buildEnv(config))
                        .timeoutSeconds(resolveTimeoutSeconds(ctx))
                        .build(),
                "/usr/local/bin/import-image-archive-to-registry",
                (streamType, line) -> appendLine(runLog, logLines, streamType, line));

        Map<String, Object> resultJson = new LinkedHashMap<>();
        resultJson.put("fileUrl", config.fileUrl());
        resultJson.put("image", config.image());
        resultJson.put("archiveFormat", config.archiveFormat());
        resultJson.put("compression", config.compression());
        resultJson.put("exitCode", result.getExitCode());
        resultJson.put("logLines", logLines);
        resultJson.put("logTruncated", Boolean.TRUE.equals(runLog.getLogTruncated()));
        runLog.setResultJson(JsonUtils.toJsonString(resultJson));
        if (result.isSuccess()) {
            logHelper.markSuccess(runLog, "镜像包导入镜像仓库成功");
            return StepResult.continueWith("镜像包导入镜像仓库成功");
        }
        String errorMessage = StrUtil.blankToDefault(result.getErrorMessage(),
                "Image archive import failed with exit code " + result.getExitCode());
        logHelper.markFailed(runLog, "镜像包导入镜像仓库失败", errorMessage);
        log.error("[DockerImageArchiveImportStepHandler][runId({}) stepId({}) 执行失败: {}]",
                ctx.getRun().getId(), ctx.getStep().getStepId(), errorMessage);
        return StepResult.fail("镜像包导入镜像仓库失败", errorMessage);
    }

    @Override
    public void cancel(PipelineStepContext ctx) {
        pipelineCommandExecutor.cancel(ctx.getRun().getId() + ":" + ctx.getJob().getJobId() + ":"
                + ctx.getStep().getStepId());
    }

    private ImportConfig parseConfig(PipelineStepContext ctx) {
        Map<String, Object> with = ctx.getResolvedWith();
        if (with == null) {
            return ImportConfig.invalid("with 不能为空");
        }
        String fileUrl = stringValue(with.get("fileUrl"));
        if (StrUtil.isBlank(fileUrl)) {
            return ImportConfig.invalid("fileUrl 不能为空");
        }
        String image = stringValue(with.get("image"));
        if (StrUtil.isBlank(image)) {
            return ImportConfig.invalid("image 不能为空");
        }
        String archiveFormat = StrUtil.blankToDefault(stringValue(with.get("archiveFormat")), DEFAULT_ARCHIVE_FORMAT);
        if (!List.of("docker-archive", "oci-archive").contains(archiveFormat)) {
            return ImportConfig.invalid("archiveFormat 仅支持 docker-archive、oci-archive");
        }
        String compression = StrUtil.blankToDefault(stringValue(with.get("compression")), DEFAULT_COMPRESSION);
        if (!List.of("none", "gzip", "zstd").contains(compression)) {
            return ImportConfig.invalid("compression 仅支持 none、gzip、zstd");
        }
        Credential credential = parseCredential(with.get("certificate"));
        if (!credential.valid()) {
            return ImportConfig.invalid(credential.errorMessage());
        }
        return ImportConfig.valid(fileUrl, image, archiveFormat, compression,
                booleanValue(with.get("registryTlsVerify"), true), credential.username(), credential.password());
    }

    private Credential parseCredential(Object value) {
        if (!(value instanceof Map<?, ?> certificate)) {
            return Credential.invalid("certificate 不能为空");
        }
        if (!"usernamePassword".equals(stringValue(certificate.get("type")))) {
            return Credential.invalid("certificate.type 当前仅支持 usernamePassword");
        }
        String username = stringValue(certificate.get("username"));
        String password = stringValue(certificate.get("password"));
        if (StrUtil.isBlank(username) || StrUtil.isBlank(password)) {
            return Credential.invalid("certificate.username、certificate.password 不能为空");
        }
        return Credential.valid(username, password);
    }

    private Map<String, String> buildEnv(ImportConfig config) {
        Map<String, String> env = new LinkedHashMap<>();
        env.put("FILE_URL", config.fileUrl());
        env.put("IMAGE_REF", config.image());
        env.put("ARCHIVE_FORMAT", config.archiveFormat());
        env.put("COMPRESSION", config.compression());
        env.put("REGISTRY_USERNAME", config.registryUsername());
        env.put("REGISTRY_PASSWORD", config.registryPassword());
        env.put("REGISTRY_TLS_VERIFY", String.valueOf(config.registryTlsVerify()));
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

    private void appendLine(PipelineRunLogDO runLog, List<String> logLines, String streamType, String line) {
        PipelineRunLogLineRespVO logLine = pipelineRunLogLineService.appendLine(runLog, streamType, line);
        if (logLines.size() < MAX_LOG_LINES) {
            logLines.add(logLine == null ? line : logLine.getContent());
        }
    }

    private long resolveTimeoutSeconds(PipelineStepContext ctx) {
        Integer timeoutSeconds = ctx.getStep().getTimeoutSeconds();
        return timeoutSeconds == null || timeoutSeconds <= 0 ? DEFAULT_TIMEOUT_SECONDS : timeoutSeconds;
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

    private record ImportConfig(String fileUrl, String image, String archiveFormat, String compression,
                                boolean registryTlsVerify, String registryUsername, String registryPassword,
                                String errorMessage) {

        private static ImportConfig valid(String fileUrl, String image, String archiveFormat, String compression,
                                          boolean registryTlsVerify, String registryUsername,
                                          String registryPassword) {
            return new ImportConfig(fileUrl, image, archiveFormat, compression, registryTlsVerify,
                    registryUsername, registryPassword, null);
        }

        private static ImportConfig invalid(String errorMessage) {
            return new ImportConfig(null, null, null, null, true, null, null, errorMessage);
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

}
