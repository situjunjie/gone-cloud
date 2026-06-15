package cn.iocoder.yudao.module.devops.service.pipeline.execution.handler;

import cn.hutool.core.util.StrUtil;
import cn.iocoder.yudao.framework.common.util.json.JsonUtils;
import cn.iocoder.yudao.module.devops.dal.dataobject.pipeline.log.PipelineRunLogDO;
import cn.iocoder.yudao.module.devops.framework.build.*;
import cn.iocoder.yudao.module.devops.service.pipeline.PipelineNodeRegistryServiceImpl;
import jakarta.annotation.Resource;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * 构建节点处理器(统一覆盖所有 BUILD 类节点)。
 *
 * <p>流程:
 * <ol>
 *     <li>读取节点参数中的 script</li>
 *     <li>构建 {@link ExecContext}:注入环境变量(REPO_URL / BRANCH_NAME / COMMIT_SHA / APP_KEY / IMAGE_TAG / DOCKER_REGISTRY 凭据)</li>
 *     <li>使用 {@link LocalBuildExecutor} 在平台本机执行</li>
 *     <li>执行并流式写入 RunLog</li>
 *     <li>退出码 0 → CONTINUE,否则 FAIL</li>
 * </ol>
 */
@Slf4j
@Component
public class BuildNodeHandler implements PipelineNodeHandler {

    private static final long DEFAULT_TIMEOUT_SECONDS = 1800;
    private static final int MAX_LOG_LINES = 500;

    @Resource
    private LocalBuildExecutor localBuildExecutor;
    @Resource
    private PipelineNodeLogHelper logHelper;

    @Override
    public boolean supports(String nodeType) {
        return PipelineNodeRegistryServiceImpl.isCommandNode(nodeType);
    }

    @Override
    public NodeOutcome handle(PipelineNodeContext ctx) {
        PipelineRunLogDO runLog = logHelper.getOrCreateLog(ctx);
        var step = ctx.getStep();

        // 已成功完成则跳过(幂等重入)
        if ("SUCCESS".equals(runLog.getStatus())) {
            log.info("[BuildNodeHandler][runId({}) stepId({}) 已成功,跳过]",
                    ctx.getRun().getId(), step.getStepId());
            return NodeOutcome.CONTINUE;
        }

        try {
            logHelper.markStarted(runLog, "读取构建脚本");

            // 1. 读取节点参数中的脚本
            String script = getScript(step);
            if (StrUtil.isBlank(script)) {
                logHelper.markFailed(runLog, "脚本为空", "节点参数 run/script 未提供");
                return NodeOutcome.FAIL;
            }

            // Shell 类型(默认 bash);暂时统一用 bash -c 执行,shell 类型后续优化
            String shellType = (String) step.getWith().getOrDefault("shellType", "bash");

            // 节点环境变量(额外注入到 ExecContext.env)
            @SuppressWarnings("unchecked")
            List<Map<String, String>> nodeEnv =
                    (List<Map<String, String>>) step.getWith().getOrDefault("env", List.of());

            // 2. 构建 ExecContext（本机执行）
            Path workingDir = Paths.get("/tmp/devops-build/" + ctx.getRun().getId());
            Map<String, String> env = buildEnv(ctx, nodeEnv);

            ExecContext execContext = ExecContext.builder()
                    .workingDir(workingDir)
                    .runId(ctx.getRun().getId().toString())
                    .env(env)
                    .timeoutSeconds(DEFAULT_TIMEOUT_SECONDS)
                    .build();

            // 3. 执行脚本（使用本机执行器）
            List<String> logLines = new ArrayList<>();
            LogSink sink = line -> {
                if (logLines.size() < MAX_LOG_LINES) {
                    logLines.add(line);
                }
            };

            log.info("[BuildNodeHandler][runId({}) stepId({}) 开始执行脚本,工作目录={}]",
                    ctx.getRun().getId(), step.getStepId(), workingDir);

            ExecResult result = localBuildExecutor.exec(execContext, script, sink);

            // 4. 保存日志到 RunLog
            Map<String, Object> resultJson = new LinkedHashMap<>();
            resultJson.put("exitCode", result.getExitCode());
            resultJson.put("logLines", logLines.size() > MAX_LOG_LINES
                    ? logLines.subList(0, MAX_LOG_LINES)
                    : logLines);
            if (logLines.size() > MAX_LOG_LINES) {
                resultJson.put("logTruncated", true);
                resultJson.put("totalLines", logLines.size());
            }
            runLog.setResultJson(JsonUtils.toJsonString(resultJson));

            // 7. 根据退出码返回
            if (result.isSuccess()) {
                logHelper.markSuccess(runLog, "构建成功");
                log.info("[BuildNodeHandler][runId({}) stepId({}) 执行成功,退出码={}]",
                        ctx.getRun().getId(), step.getStepId(), result.getExitCode());
                return NodeOutcome.CONTINUE;
            } else {
                String errorMsg = StrUtil.isNotBlank(result.getErrorMessage())
                        ? result.getErrorMessage()
                        : "退出码: " + result.getExitCode();
                logHelper.markFailed(runLog, "构建失败", errorMsg);
                log.error("[BuildNodeHandler][runId({}) stepId({}) 执行失败,退出码={},错误={}]",
                        ctx.getRun().getId(), step.getStepId(), result.getExitCode(), errorMsg);
                return NodeOutcome.FAIL;
            }

        } catch (Exception ex) {
            log.error("[BuildNodeHandler][runId({}) stepId({}) 执行异常]",
                    ctx.getRun().getId(), step.getStepId(), ex);
            logHelper.markFailed(runLog, "构建异常", ex.getMessage());
            return NodeOutcome.FAIL;
        }
    }

    /**
     * 构建环境变量:注入 REPO_URL(含凭据)、BRANCH_NAME、COMMIT_SHA、APP_KEY、IMAGE_TAG、DOCKER_REGISTRY 凭据,
     * 并追加节点级环境变量。
     * 凭据只经 env 注入,脚本仅引用变量名。
     */
    private Map<String, String> buildEnv(PipelineNodeContext ctx, List<Map<String, String>> nodeEnv) {
        Map<String, String> env = new LinkedHashMap<>();

        // 基础变量(从 sharedState)
        String repoUrl = (String) ctx.getSharedState().get("repoUrl");
        String branchName = (String) ctx.getSharedState().get("branchName");
        String commitSha = (String) ctx.getSharedState().get("commitSha");
        String appKey = (String) ctx.getSharedState().get("appKey");

        if (StrUtil.isNotBlank(repoUrl)) {
            env.put("REPO_URL", repoUrl);
        }
        if (StrUtil.isNotBlank(branchName)) {
            env.put("BRANCH_NAME", branchName);
        }
        if (StrUtil.isNotBlank(commitSha)) {
            env.put("COMMIT_SHA", commitSha);
            // IMAGE_TAG 默认取 COMMIT_SHA
            env.put("IMAGE_TAG", commitSha);
        }
        if (StrUtil.isNotBlank(appKey)) {
            env.put("APP_KEY", appKey);
        }

        // Docker Registry 凭据
        String dockerRegistry = (String) ctx.getSharedState().get("dockerRegistry");
        String dockerUsername = (String) ctx.getSharedState().get("dockerUsername");
        String dockerPassword = (String) ctx.getSharedState().get("dockerPassword");

        if (StrUtil.isNotBlank(dockerRegistry)) {
            env.put("DOCKER_REGISTRY", dockerRegistry);
        }
        if (StrUtil.isNotBlank(dockerUsername)) {
            env.put("DOCKER_REGISTRY_USERNAME", dockerUsername);
        }
        if (StrUtil.isNotBlank(dockerPassword)) {
            env.put("DOCKER_REGISTRY_PASSWORD", dockerPassword);
        }

        // 追加节点级环境变量
        for (Map<String, String> entry : nodeEnv) {
            String key = entry.get("key");
            String value = entry.get("value");
            if (StrUtil.isNotBlank(key) && value != null) {
                env.put(key, value);
            }
        }

        return env;
    }

    private String getScript(cn.iocoder.yudao.module.devops.framework.pipeline.PipelineSpec.ExecutableStep step) {
        Object run = step.getWith().get("run");
        if (run != null) {
            return String.valueOf(run);
        }
        Object script = step.getWith().get("script");
        return script == null ? null : String.valueOf(script);
    }

}
