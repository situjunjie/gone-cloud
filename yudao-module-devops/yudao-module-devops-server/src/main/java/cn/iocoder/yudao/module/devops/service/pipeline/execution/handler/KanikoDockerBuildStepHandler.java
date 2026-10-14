package cn.iocoder.yudao.module.devops.service.pipeline.execution.handler;

import cn.hutool.core.util.StrUtil;
import cn.iocoder.yudao.framework.common.util.json.JsonUtils;
import cn.iocoder.yudao.module.devops.dal.dataobject.pipeline.log.PipelineRunLogDO;
import cn.iocoder.yudao.module.devops.framework.build.BuildContextPermissions;
import cn.iocoder.yudao.module.devops.framework.build.ExecResult;
import cn.iocoder.yudao.module.devops.framework.config.DevopsConfig;
import cn.iocoder.yudao.module.devops.framework.pipeline.runtime.PipelineCommandContext;
import cn.iocoder.yudao.module.devops.framework.pipeline.runtime.PipelineCommandExecutor;
import cn.iocoder.yudao.module.devops.framework.pipeline.runtime.PipelineJobRuntime;
import cn.iocoder.yudao.module.devops.framework.pipeline.runtime.ShellQuoting;
import cn.iocoder.yudao.module.devops.service.pipeline.PipelineNodeRegistryServiceImpl;
import cn.iocoder.yudao.module.devops.service.pipeline.execution.PipelineExecutionContext;
import cn.iocoder.yudao.module.devops.service.pipeline.execution.PipelineLogStorageService;
import cn.iocoder.yudao.module.devops.service.pipeline.execution.PipelineRunLogLineService;
import jakarta.annotation.Resource;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.nio.charset.StandardCharsets;
import java.util.Base64;
import lombok.Data;

/**
 * Kaniko Docker 镜像构建步骤处理器。
 *
 * <p>在 K8s 环境中使用 Kaniko 构建镜像，无需 Docker Daemon。
 * <p>Kaniko 是一个在容器或 Kubernetes 集群内从 Dockerfile 构建容器镜像的工具，
 * 不依赖 Docker daemon。
 *
 * @see <a href="https://github.com/GoogleContainerTools/kaniko">Kaniko GitHub</a>
 */
@Slf4j
@Component
public class KanikoDockerBuildStepHandler implements PipelineStepHandler {

    private static final long DEFAULT_TIMEOUT_SECONDS = 3600; // 镜像构建默认超时 1 小时
    private static final int MAX_LOG_LINES = 1000;

    @Resource
    private PipelineCommandExecutor pipelineCommandExecutor;

    @Resource
    private PipelineStepLogHelper logHelper;

    @Resource
    private PipelineLogStorageService pipelineLogStorageService;

    @Resource
    private PipelineRunLogLineService pipelineRunLogLineService;

    @Resource
    private DevopsConfig devopsConfig;

    @Override
    public boolean supports(String stepType) {
        return PipelineNodeRegistryServiceImpl.TYPE_KANIKO_BUILD.equals(stepType)
            || "KANIKO_DOCKER_BUILD".equals(stepType); // 向后兼容
    }

    @Override
    public StepRuntimeRequirement runtimeRequirement() {
        return StepRuntimeRequirement.JOB_RUNTIME;
    }

    @Override
    public boolean sourceWorkspaceRequired() {
        return true;
    }

    @Override
    public StepResult handle(PipelineExecutionContext ctx) {
        PipelineRunLogDO runLog = logHelper.getOrCreateLog(ctx);
        if ("SUCCESS".equals(runLog.getStatus())) {
            return StepResult.continueWith("步骤已完成");
        }

        // 1. 解析构建参数
        BuildConfig buildConfig;
        try {
            buildConfig = parseBuildConfig(ctx);
        } catch (IllegalArgumentException ex) {
            logHelper.markFailed(runLog, "配置错误", ex.getMessage());
            return StepResult.fail("配置错误", ex.getMessage());
        }
        if (!buildConfig.isValid()) {
            String errorMsg = "构建配置不完整: " + buildConfig.getValidationError();
            logHelper.markFailed(runLog, "配置错误", errorMsg);
            return StepResult.fail("配置错误", errorMsg);
        }

        // 2. 填充运行时信息
        PipelineJobRuntime runtime = ctx.getExecutorRuntime();
        if (runtime != null) {
            fillRuntime(runLog, runtime);
            logHelper.update(runLog);
        }

        logHelper.markStarted(runLog, "使用 Kaniko 构建 Docker 镜像");

        // 3. 检查运行时类型
        if (runtime == null || !"KUBERNETES".equals(runtime.getRuntimeType())) {
            String errorMsg = "Kaniko 构建仅支持 Kubernetes 运行时";
            logHelper.markFailed(runLog, "运行时不支持", errorMsg);
            return StepResult.fail("运行时不支持", errorMsg);
        }

        // 4. 构建 Kaniko 命令
        String kanikoCommand = buildKanikoCommand(buildConfig);

        // 5. 执行构建
        List<String> logLines = new ArrayList<>();
        pipelineLogStorageService.prepareStepLog(runLog);

        ExecResult result = pipelineCommandExecutor.exec(
                PipelineCommandContext.builder()
                        .runtime(runtime)
                        .containerName("kaniko")
                        .workingDirectory("/workspace")
                        .runId(ctx.getRun().getId() + ":" + ctx.getJob().getJobId())
                        .env(buildEnv(ctx, buildConfig))
                        .timeoutSeconds(buildConfig.getTimeoutSeconds())
                        .build(),
                kanikoCommand,
                (streamType, line) -> PipelineStepLogWriter.appendRealtimeLine(
                        runLog, logLines, MAX_LOG_LINES, pipelineRunLogLineService, streamType, line)
        );

        // 6. 处理结果
        Map<String, Object> resultJson = new LinkedHashMap<>();
        resultJson.put("image", buildConfig.getImageTag());
        resultJson.put("dockerfile", buildConfig.getDockerfile());
        resultJson.put("exitCode", result.getExitCode());
        resultJson.put("logLines", logLines);
        resultJson.put("logTruncated", Boolean.TRUE.equals(runLog.getLogTruncated()));
        runLog.setResultJson(JsonUtils.toJsonString(resultJson));

        if (result.isSuccess()) {
            logHelper.markSuccess(runLog, "镜像构建成功");
            // 将镜像标签写入共享状态，供后续步骤使用
            ctx.getSharedState().put("builtImage", buildConfig.getImageTag());
            return StepResult.continueWith("镜像构建成功: " + buildConfig.getImageTag());
        }

        String errorMessage = StrUtil.blankToDefault(result.getErrorMessage(),
                "Kaniko build failed with exit code " + result.getExitCode());
        if (Boolean.TRUE.equals(runLog.getLogTruncated())) {
            errorMessage = errorMessage + "；运行日志已截断，请查看完整日志排查问题";
        }
        logHelper.markFailed(runLog, "镜像构建失败", errorMessage);
        log.error("[handle][runId({}) stepId({}) Kaniko 构建失败: {}]",
                ctx.getRun().getId(), ctx.getStep().getStepId(), errorMessage);

        return StepResult.fail("镜像构建失败", errorMessage);
    }

    @Override
    public void cancel(PipelineExecutionContext ctx) {
        pipelineCommandExecutor.cancel(ctx.getRun().getId() + ":" + ctx.getJob().getJobId());
    }

    /**
     * 解析构建配置。
     */
    private BuildConfig parseBuildConfig(PipelineExecutionContext ctx) {
        Map<String, Object> with = ctx.getResolvedWith();
        BuildConfig config = new BuildConfig();

        config.setDockerfile(getStringValue(with, "dockerfile", "Dockerfile"));
        config.setContext(getStringValue(with, "context", "."));
        config.setImageTag(StrUtil.blankToDefault(getStringValue(with, "imageTag", null),
                getStringValue(with, "image", null)));
        config.setRegistry(getStringValue(with, "registry", null));
        config.setCache(getBooleanValue(with, "cache", true));
        config.setCacheRepo(getStringValue(with, "cacheRepo", null));
        config.setBuildArgs(getBuildArgs(with));
        config.setRegistryUsername(getStringValue(with, "registryUsername", ""));
        config.setRegistryPassword(getStringValue(with, "registryPassword", ""));
        config.setTarget(getStringValue(with, "target", ""));
        // 内网仓库常用 HTTP 或自签证书，需显式开启 insecure 才能推送
        config.setInsecure(getBooleanValue(with, "insecure", false));
        // 内网集群无法直连 docker.io 时，通过镜像源拉取基础镜像
        config.setRegistryMirror(getStringValue(with, "registryMirror", null));
        config.setTimeoutSeconds(resolveTimeoutSeconds(ctx));
        if (StrUtil.isBlank(config.getRegistryUsername()) != StrUtil.isBlank(config.getRegistryPassword())) {
            throw new IllegalArgumentException("registryUsername 和 registryPassword 必须同时提供");
        }

        return config;
    }

    /**
     * 构建 Kaniko 命令。
     */
    private String buildKanikoCommand(BuildConfig config) {
        StringBuilder cmd = new StringBuilder();
        cmd.append("set -eu\nset +x\n");
        // 构建上下文必须是“任何 UID 都可读”的：COPY 会原样保留源文件权限，
        // 上下文里残留的 0600 文件会在镜像里同样是 0600，非 root 运行用户读不到就返回 403。
        // 应用环境工作区跨 run 复用，历史 run 留下的 0600 文件不会被重新 checkout 修复，
        // 因此这里每次构建前都归一化一次。
        cmd.append(BuildContextPermissions.normalizeCommand(config.getContext(), true));
        // Keep registry credentials out of the workspace and remove them even when the build fails.
        // umask 只收窄到写 config.json 的那一步：mktemp -d 建出的目录本身就是 0700，
        // 若在脚本开头设置 umask 077，它会继承给 /kaniko/executor 及 Dockerfile 里的 RUN，
        // 使镜像内新建文件变成 0600。
        cmd.append("/busybox/mkdir -p /kaniko/.docker\n")
                .append("auth_dir=$(/busybox/mktemp -d /kaniko/.docker/pipeline-XXXXXX)\n")
                .append("trap '/busybox/rm -rf \"$auth_dir\"' EXIT HUP INT TERM\n")
                .append("export DOCKER_CONFIG=\"$auth_dir\"\n")
                .append("if [ -n \"${KANIKO_AUTH_CONFIG:-}\" ]; then\n")
                .append("  (umask 077; printf '%s' \"$KANIKO_AUTH_CONFIG\" > \"$auth_dir/config.json\")\n")
                .append("fi\nunset KANIKO_AUTH_CONFIG\n");
        cmd.append("/kaniko/executor");

        // Dockerfile 路径
        cmd.append(" --dockerfile=").append(ShellQuoting.quote(config.getDockerfile()));

        // 构建上下文
        cmd.append(" --context=").append(ShellQuoting.quote(config.getContext()));

        // 目标镜像
        cmd.append(" --destination=").append(ShellQuoting.quote(config.getImageTag()));

        // 缓存配置
        if (config.isCache()) {
            cmd.append(" --cache=true");
            if (StrUtil.isNotBlank(config.getCacheRepo())) {
                cmd.append(" --cache-repo=").append(ShellQuoting.quote(config.getCacheRepo()));
            }
        } else {
            cmd.append(" --cache=false");
        }

        // 构建参数
        if (config.getBuildArgs() != null && !config.getBuildArgs().isEmpty()) {
            for (Map.Entry<String, String> entry : config.getBuildArgs().entrySet()) {
                cmd.append(" --build-arg ").append(ShellQuoting.quote(entry.getKey() + "=" + entry.getValue()));
            }
        }
        if (StrUtil.isNotBlank(config.getTarget())) {
            cmd.append(" --target=").append(ShellQuoting.quote(config.getTarget()));
        }

        // 内网 HTTP 仓库 / 自签证书仓库
        if (config.isInsecure()) {
            cmd.append(" --insecure --skip-tls-verify");
        }

        // 基础镜像镜像源（仅对 docker.io 生效）
        if (StrUtil.isNotBlank(config.getRegistryMirror())) {
            cmd.append(" --registry-mirror=").append(ShellQuoting.quote(config.getRegistryMirror()));
        }

        // 其他优化选项
        cmd.append(" --compressed-caching=false"); // 禁用压缩缓存，加快速度
        cmd.append(" --snapshot-mode=redo"); // 快照模式
        cmd.append(" --use-new-run"); // 使用新的 RUN 实现

        return cmd.toString();
    }

    /**
     * 构建环境变量。
     */
    private Map<String, String> buildEnv(PipelineExecutionContext ctx, BuildConfig config) {
        Map<String, String> env = new LinkedHashMap<>();

        // 基础环境变量
        putIfString(env, "IMAGE_TAG", config.getImageTag());
        putIfString(env, "DOCKERFILE", config.getDockerfile());
        putIfString(env, "BUILD_CONTEXT", config.getContext());

        // 从共享状态获取
        putIfString(env, "COMMIT_SHA", ctx.getSharedState().get("commitSha"));
        putIfString(env, "BRANCH_NAME", ctx.getSharedState().get("branchName"));

        if (StrUtil.isNotBlank(config.getRegistryUsername())) {
            String registry = config.getRegistry();
            if (StrUtil.isBlank(registry)) {
                String first = config.getImageTag().split("/", 2)[0];
                registry = config.getImageTag().contains("/")
                        && (first.contains(".") || first.contains(":") || "localhost".equals(first))
                        ? first : "https://index.docker.io/v1/";
            }
            String auth = Base64.getEncoder().encodeToString(
                    (config.getRegistryUsername() + ":" + config.getRegistryPassword()).getBytes(StandardCharsets.UTF_8));
            env.put("KANIKO_AUTH_CONFIG", JsonUtils.toJsonString(Map.of("auths", Map.of(registry, Map.of("auth", auth)))));
        }

        return env;
    }

    private void fillRuntime(PipelineRunLogDO runLog, PipelineJobRuntime runtime) {
        runLog.setRuntimeType(runtime.getRuntimeType());
        runLog.setRuntimeId(runtime.getRuntimeId());
        runLog.setRuntimeName(runtime.getRuntimeName());
        runLog.setExecutorGroup(runtime.getExecutorGroup());
        runLog.setExecutorImage(devopsConfig.getBuild().getCluster().getKanikoImage());
        runLog.setWorkspacePath(runtime.getWorkspace() == null ? null : runtime.getWorkspace().toString());
    }

    private long resolveTimeoutSeconds(PipelineExecutionContext ctx) {
        Integer timeoutSeconds = ctx.getStep().getTimeoutSeconds();
        if (timeoutSeconds == null) {
            Object value = ctx.getResolvedWith().get("timeoutSeconds");
            if (value != null) {
                try {
                    timeoutSeconds = Integer.valueOf(String.valueOf(value));
                } catch (NumberFormatException ex) {
                    throw new IllegalArgumentException("timeoutSeconds 必须是正整数");
                }
                if (timeoutSeconds <= 0) {
                    throw new IllegalArgumentException("timeoutSeconds 必须是正整数");
                }
            }
        }
        return timeoutSeconds == null || timeoutSeconds <= 0 ? DEFAULT_TIMEOUT_SECONDS : timeoutSeconds;
    }

    private String getStringValue(Map<String, Object> map, String key, String defaultValue) {
        Object value = map.get(key);
        return value == null ? defaultValue : String.valueOf(value);
    }

    private boolean getBooleanValue(Map<String, Object> map, String key, boolean defaultValue) {
        Object value = map.get(key);
        if (value instanceof Boolean) {
            return (Boolean) value;
        }
        return defaultValue;
    }

    @SuppressWarnings("unchecked")
    private Map<String, String> getBuildArgs(Map<String, Object> map) {
        Object buildArgs = map.get("buildArgs");
        if (buildArgs instanceof Map) {
            Map<String, String> result = new LinkedHashMap<>();
            ((Map<?, ?>) buildArgs).forEach((key, value) -> {
                if (key != null && value != null) {
                    result.put(String.valueOf(key), String.valueOf(value));
                }
            });
            return result;
        }
        if (buildArgs instanceof List<?> list) {
            Map<String, String> result = new LinkedHashMap<>();
            for (Object item : list) {
                if (!(item instanceof Map<?, ?> entry)) {
                    throw new IllegalArgumentException("buildArgs 必须是键值对象或 name/value 列表");
                }
                Object name = entry.containsKey("name") ? entry.get("name") : entry.get("key");
                if (name == null || !String.valueOf(name).matches("[A-Za-z_][A-Za-z0-9_]*") || entry.get("value") == null) {
                    throw new IllegalArgumentException("buildArgs 条目必须包含有效 name 和 value");
                }
                result.put(String.valueOf(name), String.valueOf(entry.get("value")));
            }
            return result;
        }
        if (buildArgs != null) {
            throw new IllegalArgumentException("buildArgs 必须是键值对象或 name/value 列表");
        }
        return Map.of();
    }

    private void putIfString(Map<String, String> env, String key, Object value) {
        if (value instanceof String str && StrUtil.isNotBlank(str)) {
            env.put(key, str);
        }
    }

    /**
     * 构建配置。
     */
    @Data
    @lombok.ToString(exclude = {"registryUsername", "registryPassword", "buildArgs"})
    private static class BuildConfig {
        private String dockerfile;
        private String context;
        private String imageTag;
        private String registry;
        private boolean cache;
        private String cacheRepo;
        private Map<String, String> buildArgs;
        private String registryUsername;
        private String registryPassword;
        private String target;
        private boolean insecure;
        private String registryMirror;
        private long timeoutSeconds;

        public boolean isValid() {
            return StrUtil.isNotBlank(imageTag);
        }

        public String getValidationError() {
            if (StrUtil.isBlank(imageTag)) {
                return "imageTag 参数不能为空";
            }
            return null;
        }

        // Getters and Setters
        public String getDockerfile() { return dockerfile; }
        public void setDockerfile(String dockerfile) { this.dockerfile = dockerfile; }
        public String getContext() { return context; }
        public void setContext(String context) { this.context = context; }
        public String getImageTag() { return imageTag; }
        public void setImageTag(String imageTag) { this.imageTag = imageTag; }
        public String getRegistry() { return registry; }
        public void setRegistry(String registry) { this.registry = registry; }
        public boolean isCache() { return cache; }
        public void setCache(boolean cache) { this.cache = cache; }
        public String getCacheRepo() { return cacheRepo; }
        public void setCacheRepo(String cacheRepo) { this.cacheRepo = cacheRepo; }
        public Map<String, String> getBuildArgs() { return buildArgs; }
        public void setBuildArgs(Map<String, String> buildArgs) { this.buildArgs = buildArgs; }
        public boolean isInsecure() { return insecure; }
        public void setInsecure(boolean insecure) { this.insecure = insecure; }
        public String getRegistryMirror() { return registryMirror; }
        public void setRegistryMirror(String registryMirror) { this.registryMirror = registryMirror; }
    }

}
