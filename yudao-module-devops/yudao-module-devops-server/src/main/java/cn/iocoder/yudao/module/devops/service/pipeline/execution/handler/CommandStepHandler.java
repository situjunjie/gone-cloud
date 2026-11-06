package cn.iocoder.yudao.module.devops.service.pipeline.execution.handler;

import cn.hutool.core.util.StrUtil;
import cn.iocoder.yudao.framework.common.util.json.JsonUtils;
import cn.iocoder.yudao.framework.common.exception.ServiceException;
import cn.iocoder.yudao.module.devops.dal.dataobject.host.EnvironmentHostDO;
import cn.iocoder.yudao.module.devops.dal.dataobject.pipeline.PipelineRunDO;
import cn.iocoder.yudao.module.devops.dal.mysql.pipeline.PipelineRunMapper;
import cn.iocoder.yudao.module.devops.enums.PipelineRunStatusEnum;
import cn.iocoder.yudao.module.devops.enums.PipelineRunLogStatusEnum;
import cn.iocoder.yudao.module.devops.dal.dataobject.pipeline.log.PipelineRunLogDO;
import cn.iocoder.yudao.module.devops.framework.build.ExecResult;
import cn.iocoder.yudao.module.devops.framework.pipeline.runtime.PipelineCommandContext;
import cn.iocoder.yudao.module.devops.framework.pipeline.runtime.PipelineCommandExecutor;
import cn.iocoder.yudao.module.devops.framework.pipeline.runtime.PipelineJobRuntime;
import cn.iocoder.yudao.module.devops.framework.pipeline.runtime.PipelineSshTarget;
import cn.iocoder.yudao.module.devops.framework.pipeline.runtime.SshPipelineCommandExecutor;
import cn.iocoder.yudao.module.devops.service.pipeline.execution.PipelineSshTargetResolver;
import cn.iocoder.yudao.module.devops.service.pipeline.PipelineNodeRegistryServiceImpl;
import cn.iocoder.yudao.module.devops.service.pipeline.execution.PipelineExecutionContext;
import cn.iocoder.yudao.module.devops.service.pipeline.execution.PipelineLogStorageService;
import cn.iocoder.yudao.module.devops.service.pipeline.execution.PipelineRunLogLineService;
import jakarta.annotation.Resource;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.time.LocalDateTime;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.util.UUID;
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
    private SshPipelineCommandExecutor sshPipelineCommandExecutor;
    @Resource
    private PipelineSshTargetResolver sshTargetResolver;
    @Resource
    private PipelineRunMapper pipelineRunMapper;
    @Resource
    private PipelineStepLogHelper logHelper;
    @Resource
    private PipelineLogStorageService pipelineLogStorageService;
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
    public StepRuntimeRequirement runtimeRequirement(PipelineExecutionContext ctx) {
        return PipelineSshTarget.isSsh(ctx.getResolvedWith())
                ? StepRuntimeRequirement.PLATFORM : StepRuntimeRequirement.JOB_RUNTIME;
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
        String script = getRunScript(ctx);
        if (StrUtil.isBlank(script)) {
            logHelper.markFailed(runLog, "命令为空", "with.run 不能为空");
            return StepResult.fail("命令为空", "with.run 不能为空");
        }
        PipelineJobRuntime runtime = ctx.getExecutorRuntime();
        boolean ssh = PipelineSshTarget.isSsh(ctx.getResolvedWith());
        if (runtime != null && !ssh) {
            fillRuntime(runLog, runtime);
            logHelper.update(runLog);
        }
        logHelper.markStarted(runLog, "执行命令");

        List<String> logLines = new ArrayList<>();
        pipelineLogStorageService.prepareStepLog(runLog);
        String runId = ctx.getRun().getId() + ":" + ctx.getJob().getJobId();
        PipelineCommandContext.PipelineCommandContextBuilder contextBuilder = PipelineCommandContext.builder()
                .runtime(runtime)
                .runId(runId)
                .env(buildEnv(ctx))
                .timeoutSeconds(resolveTimeoutSeconds(ctx));
        if (ssh) {
            contextBuilder.cancellationRequested(() -> isRunCanceled(ctx));
            if (isRunCanceled(ctx)) {
                return finishResult(ctx, runLog, ExecResult.failure(130, "SSH command canceled"), logLines);
            }
            if (PipelineSshTarget.hasUnsupportedKeys(ctx.getResolvedWith())) {
                return finishResult(ctx, runLog, ExecResult.failure(-1, "SSH Command contains unsupported parameters"), logLines);
            }
            long hostId;
            EnvironmentHostDO host;
            String workspace;
            try {
                hostId = PipelineSshTarget.hostId(ctx.getStep().getWith().get(PipelineSshTarget.HOST_ID));
                host = sshTargetResolver.resolveHost(ctx.getRun(), hostId);
                workspace = sshTargetResolver.resolveWorkspace(hostId, ctx.getWorkspace());
            } catch (ServiceException | IllegalArgumentException ex) {
                return finishResult(ctx, runLog, ExecResult.failure(-1, ex.getMessage()), logLines);
            }
            String probe = ".pipeline-workspace-" + UUID.randomUUID();
            String token = UUID.randomUUID().toString();
            contextBuilder.sshHost(host).workingDirectory(workspace).workspaceProbe(probe).workspaceToken(token);
            runLog.setRuntimeType("SSH");
            runLog.setRuntimeId(String.valueOf(hostId));
            runLog.setRuntimeName(host.getHostKey());
            runLog.setWorkspacePath(workspace);
            logHelper.update(runLog);
            Path probePath = ctx.getWorkspace().resolve(probe);
            ExecResult result;
            try {
                Files.writeString(probePath, token, StandardOpenOption.CREATE_NEW);
                result = sshPipelineCommandExecutor.exec(contextBuilder.build(), script,
                        (streamType, line) -> PipelineStepLogWriter.appendRealtimeLine(runLog, logLines, MAX_LOG_LINES,
                                pipelineRunLogLineService, streamType, line));
            } catch (IOException ex) {
                result = ExecResult.failure(-1, "SSH workspace probe could not be created");
            } finally {
                try {
                    Files.deleteIfExists(probePath);
                } catch (IOException ex) {
                    log.warn("[CommandStepHandler][runId({}) workspace probe cleanup failed]", ctx.getRun().getId());
                }
            }
            return finishResult(ctx, runLog, result, logLines);
        }
        ExecResult result = pipelineCommandExecutor.exec(contextBuilder.build(), script,
                (streamType, line) -> PipelineStepLogWriter.appendRealtimeLine(runLog, logLines, MAX_LOG_LINES,
                        pipelineRunLogLineService, streamType, line));

        return finishResult(ctx, runLog, result, logLines);
    }

    private StepResult finishResult(PipelineExecutionContext ctx, PipelineRunLogDO runLog, ExecResult result,
                                    List<String> logLines) {
        Map<String, Object> resultJson = new LinkedHashMap<>();
        resultJson.put("exitCode", result.getExitCode());
        resultJson.put("logLines", logLines);
        resultJson.put("logTruncated", Boolean.TRUE.equals(runLog.getLogTruncated()));
        runLog.setResultJson(JsonUtils.toJsonString(resultJson));
        if (PipelineSshTarget.isSsh(ctx.getResolvedWith()) && isRunCanceled(ctx)) {
            runLog.setStatus(PipelineRunLogStatusEnum.CANCELED.getStatus());
            runLog.setSummary("命令已取消");
            runLog.setFinishedAt(LocalDateTime.now());
            logHelper.update(runLog);
            pipelineLogStorageService.completeStepLog(runLog);
            return StepResult.fail("命令已取消", "SSH command canceled");
        }
        if (result.isSuccess()) {
            logHelper.markSuccess(runLog, "命令执行成功");
            return StepResult.continueWith("命令执行成功");
        }
        String errorMessage = StrUtil.blankToDefault(result.getErrorMessage(),
                "Command failed with exit code " + result.getExitCode());
        if (Boolean.TRUE.equals(runLog.getLogTruncated())) {
            errorMessage = errorMessage + "；运行日志已截断，请减少命令输出或调大日志上限后重试";
        }
        logHelper.markFailed(runLog, "命令执行失败", errorMessage);
        log.error("[CommandStepHandler][runId({}) stepId({}) 执行失败: {}]",
                ctx.getRun().getId(), ctx.getStep().getStepId(), errorMessage);
        return StepResult.fail("命令执行失败", errorMessage);
    }

    private boolean isRunCanceled(PipelineExecutionContext ctx) {
        PipelineRunDO latest = pipelineRunMapper.selectById(ctx.getRun().getId());
        return latest != null && PipelineRunStatusEnum.CANCELED.getStatus().equals(latest.getRunStatus());
    }

    @Override
    public void cancel(PipelineExecutionContext ctx) {
        String runId = ctx.getRun().getId() + ":" + ctx.getJob().getJobId();
        if (PipelineSshTarget.isSsh(ctx.getResolvedWith())) {
            sshPipelineCommandExecutor.cancel(runId);
        } else {
            pipelineCommandExecutor.cancel(runId);
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

    private String getRunScript(PipelineExecutionContext ctx) {
        Object run = ctx.getResolvedWith().get("run");
        return run == null ? null : String.valueOf(run);
    }

    @SuppressWarnings("unchecked")
    private Map<String, String> buildEnv(PipelineExecutionContext ctx) {
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
        if (PipelineSshTarget.isSsh(ctx.getResolvedWith())) {
            return env; // SSH uses the remote user's own tool/cache defaults.
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

    private long resolveTimeoutSeconds(PipelineExecutionContext ctx) {
        Integer timeoutSeconds = ctx.getStep().getTimeoutSeconds();
        return timeoutSeconds == null || timeoutSeconds <= 0 ? DEFAULT_TIMEOUT_SECONDS : timeoutSeconds;
    }

}
