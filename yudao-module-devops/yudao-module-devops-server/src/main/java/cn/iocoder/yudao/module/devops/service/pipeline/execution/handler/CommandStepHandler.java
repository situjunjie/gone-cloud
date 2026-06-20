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

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Command 步骤处理器。
 */
@Slf4j
@Component
public class CommandStepHandler implements PipelineStepHandler {

    private static final long DEFAULT_TIMEOUT_SECONDS = 1800;
    private static final int MAX_LOG_LINES = 500;

    @Resource
    private PipelineCommandExecutor pipelineCommandExecutor;
    @Resource
    private PipelineStepLogHelper logHelper;
    @Resource
    private PipelineRunLogLineService pipelineRunLogLineService;

    @Override
    public boolean supports(String stepType) {
        return PipelineNodeRegistryServiceImpl.TYPE_COMMAND.equals(stepType);
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
        String script = getRunScript(ctx);
        if (StrUtil.isBlank(script)) {
            logHelper.markFailed(runLog, "命令为空", "with.run 不能为空");
            return StepResult.fail("命令为空", "with.run 不能为空");
        }
        PipelineJobRuntime runtime = (PipelineJobRuntime) ctx.getSharedState().get(runtimeKey(ctx));
        if (runtime != null) {
            fillRuntime(runLog, runtime);
            logHelper.update(runLog);
        }
        logHelper.markStarted(runLog, "执行命令");

        List<String> logLines = new ArrayList<>();
        ExecResult result = pipelineCommandExecutor.exec(PipelineCommandContext.builder()
                        .runtime(runtime)
                        .runId(ctx.getRun().getId() + ":" + ctx.getJob().getJobId())
                        .env(buildEnv(ctx))
                        .timeoutSeconds(resolveTimeoutSeconds(ctx))
                        .build(),
                script,
                (streamType, line) -> {
                    PipelineRunLogLineRespVO logLine = pipelineRunLogLineService.appendLine(runLog, streamType, line);
                    if (logLines.size() < MAX_LOG_LINES) {
                        logLines.add(logLine == null ? line : logLine.getContent());
                    }
                });

        Map<String, Object> resultJson = new LinkedHashMap<>();
        resultJson.put("exitCode", result.getExitCode());
        resultJson.put("logLines", logLines);
        resultJson.put("logTruncated", Boolean.TRUE.equals(runLog.getLogTruncated()));
        runLog.setResultJson(JsonUtils.toJsonString(resultJson));
        if (result.isSuccess()) {
            logHelper.markSuccess(runLog, "命令执行成功");
            return StepResult.continueWith("命令执行成功");
        }
        String errorMessage = StrUtil.blankToDefault(result.getErrorMessage(),
                "Command failed with exit code " + result.getExitCode());
        logHelper.markFailed(runLog, "命令执行失败", errorMessage);
        log.error("[CommandStepHandler][runId({}) stepId({}) 执行失败: {}]",
                ctx.getRun().getId(), ctx.getStep().getStepId(), errorMessage);
        return StepResult.fail("命令执行失败", errorMessage);
    }

    @Override
    public void cancel(PipelineStepContext ctx) {
        pipelineCommandExecutor.cancel(ctx.getRun().getId() + ":" + ctx.getJob().getJobId());
    }

    public static String runtimeKey(PipelineStepContext ctx) {
        return "jobRuntime:" + ctx.getJob().getJobId();
    }

    private void fillRuntime(PipelineRunLogDO runLog, PipelineJobRuntime runtime) {
        runLog.setRuntimeType(runtime.getRuntimeType());
        runLog.setRuntimeId(runtime.getRuntimeId());
        runLog.setRuntimeName(runtime.getRuntimeName());
        runLog.setExecutorGroup(runtime.getExecutorGroup());
        runLog.setExecutorImage(runtime.getExecutorImage());
        runLog.setWorkspacePath(runtime.getWorkspace() == null ? null : runtime.getWorkspace().toString());
    }

    private String getRunScript(PipelineStepContext ctx) {
        Object run = ctx.getResolvedWith().get("run");
        return run == null ? null : String.valueOf(run);
    }

    @SuppressWarnings("unchecked")
    private Map<String, String> buildEnv(PipelineStepContext ctx) {
        Map<String, String> env = new LinkedHashMap<>();
        putIfString(env, "REPO_URL", ctx.getSharedState().get("repoUrl"));
        putIfString(env, "BRANCH_NAME", ctx.getSharedState().get("branchName"));
        putIfString(env, "SOURCE_BRANCH", ctx.getSharedState().get("sourceBranch"));
        putIfString(env, "COMMIT_SHA", ctx.getSharedState().get("commitSha"));
        putIfString(env, "APP_KEY", ctx.getSharedState().get("appKey"));
        if (!env.containsKey("IMAGE_TAG") && env.containsKey("COMMIT_SHA")) {
            env.put("IMAGE_TAG", env.get("COMMIT_SHA"));
        }
        Object envConfig = ctx.getResolvedWith().get("env");
        if (envConfig instanceof Map<?, ?> envMap) {
            envMap.forEach((key, value) -> {
                if (key != null && value != null) {
                    env.put(String.valueOf(key), String.valueOf(value));
                }
            });
        } else if (envConfig instanceof List<?> envList) {
            for (Object item : envList) {
                if (!(item instanceof Map<?, ?> itemMap)) {
                    continue;
                }
                Object key = itemMap.get("key");
                Object value = itemMap.get("value");
                if (key != null && value != null) {
                    env.put(String.valueOf(key), String.valueOf(value));
                }
            }
        }
        env.putIfAbsent("MAVEN_CONFIG", "/root/.m2");
        env.putIfAbsent("NPM_CONFIG_CACHE", "/root/.npm");
        env.putIfAbsent("PNPM_STORE_PATH", "/root/.pnpm-store");
        env.putIfAbsent("GRADLE_USER_HOME", "/root/.gradle");
        return env;
    }

    private void putIfString(Map<String, String> env, String key, Object value) {
        if (value instanceof String str && StrUtil.isNotBlank(str)) {
            env.put(key, str);
        }
    }

    private long resolveTimeoutSeconds(PipelineStepContext ctx) {
        Integer timeoutSeconds = ctx.getStep().getTimeoutSeconds();
        return timeoutSeconds == null || timeoutSeconds <= 0 ? DEFAULT_TIMEOUT_SECONDS : timeoutSeconds;
    }

}
