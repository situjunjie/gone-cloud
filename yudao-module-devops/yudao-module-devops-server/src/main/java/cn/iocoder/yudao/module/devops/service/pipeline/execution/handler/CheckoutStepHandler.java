package cn.iocoder.yudao.module.devops.service.pipeline.execution.handler;

import cn.hutool.core.util.StrUtil;
import cn.iocoder.yudao.module.devops.dal.dataobject.pipeline.log.PipelineRunLogDO;
import cn.iocoder.yudao.module.devops.framework.build.ExecResult;
import cn.iocoder.yudao.module.devops.framework.git.GitCheckoutScript;
import cn.iocoder.yudao.module.devops.framework.pipeline.runtime.PipelineCommandContext;
import cn.iocoder.yudao.module.devops.framework.pipeline.runtime.PipelineCommandExecutor;
import cn.iocoder.yudao.module.devops.framework.pipeline.runtime.PipelineJobRuntime;
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

/**
 * Checkout 步骤处理器 - 在K8s中执行git clone。
 */
@Slf4j
@Component
public class CheckoutStepHandler implements PipelineStepHandler {

    private static final long DEFAULT_TIMEOUT_SECONDS = 600;
    private static final int DEFAULT_DEPTH = 1; // 默认浅克隆
    private static final int MAX_LOG_LINES = 500;

    @Resource
    private PipelineCommandExecutor pipelineCommandExecutor;
    @Resource
    private PipelineStepLogHelper logHelper;
    @Resource
    private PipelineLogStorageService pipelineLogStorageService;
    @Resource
    private PipelineRunLogLineService pipelineRunLogLineService;

    @Override
    public boolean supports(String stepType) {
        return PipelineNodeRegistryServiceImpl.TYPE_CHECKOUT.equals(stepType);
    }

    @Override
    public StepRuntimeRequirement runtimeRequirement() {
        return StepRuntimeRequirement.JOB_RUNTIME;
    }

    @Override
    public boolean sourceWorkspaceRequired() {
        return false; // Checkout本身就是准备源码的步骤
    }

    @Override
    public StepResult handle(PipelineExecutionContext ctx) {
        PipelineRunLogDO runLog = logHelper.getOrCreateLog(ctx);
        if ("SUCCESS".equals(runLog.getStatus())) {
            ctx.setSourcePrepared(true);
            return StepResult.continueWith("步骤已完成");
        }

        // 1. 解析参数
        String repository = resolveString(ctx, "repository");
        String branch = resolveString(ctx, "branch");
        String ref = resolveString(ctx, "ref");
        Integer depth = getInteger(ctx, "depth", DEFAULT_DEPTH);
        String submodules = resolveString(ctx, "submodules");
        String path = resolveString(ctx, "path");

        if (StrUtil.isBlank(repository)) {
            logHelper.markFailed(runLog, "Checkout失败", "repository参数不能为空");
            return StepResult.fail("Checkout失败", "repository参数不能为空");
        }

        PipelineJobRuntime runtime = ctx.getExecutorRuntime();
        if (runtime != null) {
            fillRuntime(runLog, runtime);
            logHelper.update(runLog);
        }
        logHelper.markStarted(runLog, "检出源码");

        // 2. 准备日志
        List<String> logLines = new ArrayList<>();
        pipelineLogStorageService.prepareStepLog(runLog);

        try {
            // 3. 构建git脚本
            java.net.URI repositoryUri = java.net.URI.create(repository);
            if (repositoryUri.getUserInfo() != null) {
                throw new IllegalArgumentException("Use gitUsername/gitPassword for repository credentials");
            }
            String script = GitCheckoutScript.build(repository, branch, ref, depth, submodules, path);

            // 4. 构建环境变量（包含git凭证）
            Map<String, String> envVars = buildGitEnvironment(ctx);

            // 5. 执行脚本
            ExecResult execResult = pipelineCommandExecutor.exec(
                    PipelineCommandContext.builder()
                            .runtime(runtime)
                            .runId(ctx.getRun().getId() + ":" + ctx.getJob().getJobId())
                            .env(envVars)
                            .timeoutSeconds(getTimeout(ctx))
                            .build(),
                    script,
                    (streamType, line) -> PipelineStepLogWriter.appendRealtimeLine(runLog, logLines, MAX_LOG_LINES,
                            pipelineRunLogLineService, streamType, maskCredentials(line, envVars))
            );

            // 6. 处理结果
            Map<String, Object> resultJson = new LinkedHashMap<>();
            resultJson.put("exitCode", execResult.getExitCode());
            resultJson.put("repository", repository);
            resultJson.put("branch", StrUtil.blankToDefault(branch, ref));
            resultJson.put("logLines", logLines);
            resultJson.put("logTruncated", Boolean.TRUE.equals(runLog.getLogTruncated()));
            runLog.setResultJson(cn.iocoder.yudao.framework.common.util.json.JsonUtils.toJsonString(resultJson));

            if (execResult.isSuccess()) {
                logHelper.markSuccess(runLog, "源码检出成功");
                if (StrUtil.isBlank(path) || ".".equals(path)) {
                    ctx.setSourcePrepared(true);
                }
                return StepResult.continueWith("源码检出成功");
            } else {
                String errorMsg = "Git checkout失败，exit code: " + execResult.getExitCode();
                logHelper.markFailed(runLog, "源码检出失败", errorMsg);
                return StepResult.fail("源码检出失败", errorMsg);
            }

        } catch (Exception ex) {
            log.error("[CheckoutStepHandler][runId({}) stepId({}) 执行异常]",
                    ctx.getRun().getId(), ctx.getStep().getStepId(), ex);
            logHelper.markFailed(runLog, "源码检出失败", ex.getMessage());
            return StepResult.fail("源码检出失败", ex.getMessage());
        }
    }

    private void fillRuntime(PipelineRunLogDO runLog, PipelineJobRuntime runtime) {
        runLog.setRuntimeType(runtime.getRuntimeType());
        runLog.setRuntimeId(runtime.getRuntimeId());
        runLog.setRuntimeName(runtime.getRuntimeName());
        runLog.setExecutorGroup(runtime.getExecutorGroup());
        runLog.setExecutorImage(runtime.getExecutorImage());
        runLog.setWorkspacePath(runtime.getWorkspace() == null ? null : runtime.getWorkspace().toString());
    }

    private String maskCredentials(String line, Map<String, String> env) {
        String password = env.get("GIT_PASSWORD");
        return StrUtil.isBlank(password) ? line : line.replace(password, "****");
    }


    /**
     * 构建git环境变量
     */
    private Map<String, String> buildGitEnvironment(PipelineExecutionContext ctx) {
        Map<String, String> env = new LinkedHashMap<>();

        // 从step参数或全局变量中读取
        String gitUsername = resolveString(ctx, "gitUsername");
        String gitPassword = resolveString(ctx, "gitPassword");
        String gitUserName = resolveString(ctx, "gitUserName");
        String gitUserEmail = resolveString(ctx, "gitUserEmail");

        if (StrUtil.isNotBlank(gitUsername)) {
            env.put("GIT_USERNAME", gitUsername);
        }
        if (StrUtil.isNotBlank(gitPassword)) {
            env.put("GIT_PASSWORD", gitPassword);
        }
        if (StrUtil.isNotBlank(gitUserName)) {
            env.put("GIT_USER_NAME", gitUserName);
        }
        if (StrUtil.isNotBlank(gitUserEmail)) {
            env.put("GIT_USER_EMAIL", gitUserEmail);
        }

        return env;
    }

    private String resolveString(PipelineExecutionContext ctx, String key) {
        Object value = ctx.getResolvedWith().get(key);
        return value == null ? null : String.valueOf(value);
    }

    private Integer getInteger(PipelineExecutionContext ctx, String key, Integer defaultValue) {
        Object value = ctx.getResolvedWith().get(key);
        if (value == null) {
            return defaultValue;
        }
        if (value instanceof Number) {
            return ((Number) value).intValue();
        }
        try {
            return Integer.parseInt(String.valueOf(value));
        } catch (NumberFormatException e) {
            return defaultValue;
        }
    }

    private Long getTimeout(PipelineExecutionContext ctx) {
        Integer timeoutSeconds = getInteger(ctx, "timeoutSeconds", null);
        return timeoutSeconds != null ? timeoutSeconds.longValue() : DEFAULT_TIMEOUT_SECONDS;
    }
}
