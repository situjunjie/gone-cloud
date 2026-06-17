package cn.iocoder.yudao.module.devops.service.pipeline.execution.handler;

import cn.hutool.core.util.StrUtil;
import cn.iocoder.yudao.framework.common.util.json.JsonUtils;
import cn.iocoder.yudao.module.devops.controller.admin.pipelinerun.vo.PipelineRunLogLineRespVO;
import cn.iocoder.yudao.module.devops.controller.admin.pipeline.vo.PipelineValidationRespVO;
import cn.iocoder.yudao.module.devops.dal.dataobject.pipeline.log.PipelineRunLogDO;
import cn.iocoder.yudao.module.devops.framework.docker.DockerClientFactory;
import cn.iocoder.yudao.module.devops.framework.pipeline.PipelineSpec;
import cn.iocoder.yudao.module.devops.framework.pipeline.runtime.PipelineWorkspaceService;
import cn.iocoder.yudao.module.devops.service.pipeline.PipelineNodeRegistryServiceImpl;
import cn.iocoder.yudao.module.devops.service.pipeline.PipelineSpecValidationService;
import cn.iocoder.yudao.module.devops.service.pipeline.execution.PipelineSourceWorkspacePreparer;
import cn.iocoder.yudao.module.devops.service.pipeline.execution.PipelineRunLogLineService;
import com.github.dockerjava.api.DockerClient;
import com.github.dockerjava.api.async.ResultCallback;
import com.github.dockerjava.api.command.BuildImageCmd;
import com.github.dockerjava.api.command.BuildImageResultCallback;
import com.github.dockerjava.api.model.AuthConfig;
import com.github.dockerjava.api.model.BuildResponseItem;
import com.github.dockerjava.api.model.PushResponseItem;
import jakarta.annotation.Resource;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.nio.file.Path;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.TimeUnit;

/**
 * 自定义私有镜像仓库 Docker 构建步骤处理器。
 */
@Slf4j
@Component
public class PrivateRegistryDockerBuildStepHandler implements PipelineStepHandler {

    private static final int MAX_LOG_LINES = 500;
    private static final String CERTIFICATE_TYPE_USERNAME_PASSWORD = "usernamePassword";

    @Resource
    private DockerClientFactory dockerClientFactory;
    @Resource
    private PipelineWorkspaceService pipelineWorkspaceService;
    @Resource
    private PipelineSourceWorkspacePreparer pipelineSourceWorkspacePreparer;
    @Resource
    private PipelineSpecValidationService pipelineSpecValidationService;
    @Resource
    private PipelineStepLogHelper logHelper;
    @Resource
    private PipelineRunLogLineService pipelineRunLogLineService;

    @Override
    public boolean supports(String stepType) {
        return PipelineNodeRegistryServiceImpl.TYPE_PRIVATE_REGISTRY_DOCKER_BUILD.equals(stepType);
    }

    @Override
    public StepRuntimeRequirement runtimeRequirement() {
        return StepRuntimeRequirement.PLATFORM;
    }

    @Override
    public StepResult handle(PipelineStepContext ctx) {
        PipelineRunLogDO runLog = logHelper.getOrCreateLog(ctx);
        if ("SUCCESS".equals(runLog.getStatus())) {
            return StepResult.continueWith("步骤已完成");
        }
        BuildConfig config = parseConfig(ctx);
        if (!config.valid()) {
            logHelper.markFailed(runLog, "镜像构建参数无效", config.errorMessage());
            return StepResult.fail("镜像构建参数无效", config.errorMessage());
        }
        runLog.setRuntimeType("PLATFORM");
        logHelper.markStarted(runLog, "构建并推送 Docker 镜像");

        List<String> logLines = new ArrayList<>();
        String imageId = null;
        String errorMessage = null;
        Path workspace = null;
        try {
            workspace = prepareWorkspace(ctx);
            runLog.setWorkspacePath(workspace.toString());
            logHelper.update(runLog);
            imageId = buildAndPush(config, workspace, runLog, logLines);
        } catch (Exception ex) {
            errorMessage = StrUtil.blankToDefault(ex.getMessage(), "Docker build/push failed");
            log.error("[PrivateRegistryDockerBuildStepHandler][runId({}) stepId({}) 执行失败: {}]",
                    ctx.getRun().getId(), ctx.getStep().getStepId(), errorMessage);
        }

        Map<String, Object> resultJson = new LinkedHashMap<>();
        resultJson.put("artifact", config.artifact());
        resultJson.put("image", config.image());
        resultJson.put("imageId", imageId);
        resultJson.put("registry", config.registry());
        resultJson.put("dockerfilePath", config.dockerfilePath());
        resultJson.put("contextPath", config.contextPath());
        resultJson.put("logLines", logLines);
        resultJson.put("logTruncated", Boolean.TRUE.equals(runLog.getLogTruncated()));
        runLog.setResultJson(JsonUtils.toJsonString(resultJson));
        if (StrUtil.isBlank(errorMessage)) {
            logHelper.markSuccess(runLog, "镜像构建并推送成功");
            return StepResult.builder()
                    .type(StepResultType.CONTINUE)
                    .summary("镜像构建并推送成功")
                    .outputs(Map.of("artifact", config.artifact(), "image", config.image()))
                    .build();
        }
        logHelper.markFailed(runLog, "镜像构建或推送失败", errorMessage);
        return StepResult.fail("镜像构建或推送失败", errorMessage);
    }

    @Override
    public void cancel(PipelineStepContext ctx) {
        // docker-java build/push is synchronous in the current handler; cancellation is handled by job interruption.
    }

    @SuppressWarnings("unchecked")
    private BuildConfig parseConfig(PipelineStepContext ctx) {
        Map<String, Object> with = ctx.getStep().getWith();
        if (with == null) {
            return BuildConfig.invalid("with 不能为空");
        }
        String artifact = stringValue(with.get("artifact"));
        String image = stringValue(with.get("image"));
        String dockerfilePath = stringValue(with.get("dockerfilePath"));
        if (StrUtil.isBlank(artifact) || StrUtil.isBlank(image) || StrUtil.isBlank(dockerfilePath)) {
            return BuildConfig.invalid("artifact、image、dockerfilePath 不能为空");
        }
        Object certificateValue = with.get("certificate");
        if (!(certificateValue instanceof Map<?, ?> certificate)) {
            return BuildConfig.invalid("certificate 不能为空");
        }
        String certificateType = stringValue(certificate.get("type"));
        if (!CERTIFICATE_TYPE_USERNAME_PASSWORD.equals(certificateType)) {
            return BuildConfig.invalid("certificate.type 当前仅支持 usernamePassword");
        }
        String username = stringValue(certificate.get("username"));
        String password = stringValue(certificate.get("password"));
        if (StrUtil.isBlank(username) || StrUtil.isBlank(password)) {
            return BuildConfig.invalid("certificate.username、certificate.password 不能为空");
        }
        List<BuildVariable> variables = new ArrayList<>();
        Object variablesValue = with.get("variables");
        if (variablesValue instanceof List<?> variableList) {
            for (Object item : variableList) {
                if (item instanceof Map<?, ?> variableMap) {
                    String key = stringValue(variableMap.get("key"));
                    String value = stringValue(variableMap.get("value"));
                    if (StrUtil.isNotBlank(key)) {
                        variables.add(new BuildVariable(key, StrUtil.nullToEmpty(value)));
                    }
                }
            }
        } else if (variablesValue instanceof Map<?, ?> variableMap) {
            variableMap.forEach((key, value) -> {
                if (key != null) {
                    variables.add(new BuildVariable(String.valueOf(key), StrUtil.nullToEmpty(stringValue(value))));
                }
            });
        }
        String contextPath = StrUtil.blankToDefault(stringValue(with.get("contextPath")),
                defaultContextPath(dockerfilePath));
        return BuildConfig.valid(artifact, image, resolveRegistry(image), username, password,
                dockerfilePath, contextPath, Boolean.TRUE.equals(with.get("noCache")), variables);
    }

    private Path prepareWorkspace(PipelineStepContext ctx) {
        Path workspace = pipelineWorkspaceService.createWorkspace(ctx.getRun(), ctx.getJob());
        PipelineSpec spec = pipelineSpecValidationService.parseSpec(ctx.getVersion().getSpecJson(),
                new PipelineValidationRespVO());
        pipelineSourceWorkspacePreparer.prepare(ctx.getRun(), spec, workspace, ctx.getSharedState());
        return workspace;
    }

    private String buildAndPush(BuildConfig config, Path workspace, PipelineRunLogDO runLog, List<String> logLines) {
        DockerClient client = dockerClientFactory.getDefaultClient();
        AuthConfig authConfig = new AuthConfig()
                .withRegistryAddress(config.registry())
                .withUsername(config.username())
                .withPassword(config.password());
        Path context = resolveWorkspacePath(workspace, config.contextPath());
        Path dockerfile = resolveWorkspacePath(workspace, config.dockerfilePath());
        BuildImageCmd buildCmd = client.buildImageCmd(context.toFile())
                .withDockerfile(dockerfile.toFile())
                .withTag(config.image())
                .withNoCache(config.noCache())
                .withRemove(true);
        for (BuildVariable variable : config.variables()) {
            buildCmd.withBuildArg(variable.key(), variable.value());
        }
        appendLine(runLog, logLines, "stdout", "开始构建镜像 " + config.image());
        BuildImageResultCallback buildCallback = new BuildImageResultCallback() {
            @Override
            public void onNext(BuildResponseItem item) {
                appendResponseItem(runLog, logLines, item);
                super.onNext(item);
            }
        };
        buildCmd.exec(buildCallback);
        String imageId = buildCallback.awaitImageId(resolveTimeoutSeconds(), TimeUnit.SECONDS);
        if (StrUtil.isBlank(imageId)) {
            throw new IllegalStateException("Docker build timeout or image id is empty");
        }
        appendLine(runLog, logLines, "stdout", "开始推送镜像 " + config.image());
        boolean pushed;
        try {
            pushed = client.pushImageCmd(config.image())
                    .withAuthConfig(authConfig)
                    .exec(new ResultCallback.Adapter<PushResponseItem>() {
                        @Override
                        public void onNext(PushResponseItem item) {
                            appendResponseItem(runLog, logLines, item);
                            super.onNext(item);
                        }
                    }).awaitCompletion(resolveTimeoutSeconds(), TimeUnit.SECONDS);
        } catch (InterruptedException ex) {
            Thread.currentThread().interrupt();
            throw new IllegalStateException("Docker push interrupted", ex);
        }
        if (!pushed) {
            throw new IllegalStateException("Docker push timeout");
        }
        return imageId;
    }

    private void appendResponseItem(PipelineRunLogDO runLog, List<String> logLines,
                                    com.github.dockerjava.api.model.ResponseItem item) {
        if (item == null) {
            return;
        }
        if (item.isErrorIndicated()) {
            appendLine(runLog, logLines, "stderr", StrUtil.blankToDefault(item.getError(), "Docker operation failed"));
            return;
        }
        String line = firstNotBlank(item.getStream(), item.getStatus(), item.getId());
        if (StrUtil.isNotBlank(line)) {
            appendLine(runLog, logLines, "stdout", StrUtil.trim(line));
        }
    }

    private void appendLine(PipelineRunLogDO runLog, List<String> logLines, String streamType, String line) {
        PipelineRunLogLineRespVO logLine = pipelineRunLogLineService.appendLine(runLog, streamType, line);
        if (logLines.size() < MAX_LOG_LINES) {
            logLines.add(logLine == null ? line : logLine.getContent());
        }
    }

    private long resolveTimeoutSeconds() {
        return 1800;
    }

    private String resolveRegistry(String image) {
        String firstSegment = image.contains("/") ? StrUtil.subBefore(image, "/", false) : "";
        if (StrUtil.isBlank(firstSegment) || (!firstSegment.contains(".") && !firstSegment.contains(":")
                && !"localhost".equals(firstSegment))) {
            return AuthConfig.DEFAULT_SERVER_ADDRESS;
        }
        return firstSegment;
    }

    private String defaultContextPath(String dockerfilePath) {
        int separatorIndex = dockerfilePath.lastIndexOf('/');
        if (separatorIndex <= 0) {
            return ".";
        }
        return dockerfilePath.substring(0, separatorIndex);
    }

    private Path resolveWorkspacePath(Path workspace, String relativePath) {
        Path resolved = workspace.resolve(StrUtil.blankToDefault(relativePath, ".")).normalize();
        if (!resolved.startsWith(workspace.normalize())) {
            throw new IllegalArgumentException("路径不能超出工作目录: " + relativePath);
        }
        return resolved;
    }

    private String stringValue(Object value) {
        return value == null ? null : String.valueOf(value);
    }

    private String firstNotBlank(String... values) {
        for (String value : values) {
            if (StrUtil.isNotBlank(value)) {
                return value;
            }
        }
        return null;
    }

    private record BuildConfig(String artifact, String image, String registry, String username, String password,
                               String dockerfilePath, String contextPath, boolean noCache,
                               List<BuildVariable> variables, String errorMessage) {

        private static BuildConfig valid(String artifact, String image, String registry, String username,
                                         String password, String dockerfilePath, String contextPath, boolean noCache,
                                         List<BuildVariable> variables) {
            return new BuildConfig(artifact, image, registry, username, password, dockerfilePath, contextPath,
                    noCache, variables, null);
        }

        private static BuildConfig invalid(String errorMessage) {
            return new BuildConfig(null, null, null, null, null, null, null, false, List.of(), errorMessage);
        }

        private boolean valid() {
            return StrUtil.isBlank(errorMessage);
        }

    }

    private record BuildVariable(String key, String value) {
    }

}
