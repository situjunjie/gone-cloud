package cn.iocoder.yudao.module.devops.service.pipeline.execution.handler;

import cn.hutool.core.util.StrUtil;
import cn.iocoder.yudao.framework.common.util.json.JsonUtils;
import cn.iocoder.yudao.module.devops.dal.dataobject.buildhost.BuildHostDO;
import cn.iocoder.yudao.module.devops.dal.dataobject.pipeline.log.PipelineRunLogDO;
import cn.iocoder.yudao.module.devops.enums.BuildHostTypeEnum;
import cn.iocoder.yudao.module.devops.framework.build.*;
import cn.iocoder.yudao.module.devops.framework.pipeline.PipelineSpec;
import cn.iocoder.yudao.module.devops.service.buildhost.BuildHostSelectCriteria;
import cn.iocoder.yudao.module.devops.service.buildhost.BuildHostSelector;
import cn.iocoder.yudao.module.devops.service.pipeline.script.StepScriptGenerator;
import jakarta.annotation.Resource;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import static cn.iocoder.yudao.framework.common.exception.util.ServiceExceptionUtil.exception;
import static cn.iocoder.yudao.module.devops.enums.ErrorCodeConstants.BUILD_HOST_NO_AVAILABLE;

/**
 * 构建节点处理器(统一覆盖所有 BUILD 类节点)。
 *
 * <p>流程:
 * <ol>
 *     <li>调用 {@link StepScriptGenerator#generate} 生成纯 shell 脚本</li>
 *     <li>调用 {@link BuildHostSelector#selectDefault} 选择构建主机</li>
 *     <li>构建 {@link ExecContext}:注入环境变量(REPO_URL / BRANCH_NAME / COMMIT_SHA / APP_KEY / IMAGE_TAG / DOCKER_REGISTRY 凭据)</li>
 *     <li>根据主机类型选择 {@link LocalBuildExecutor} 或 {@link SshBuildExecutor}</li>
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
    private StepScriptGenerator stepScriptGenerator;
    @Resource
    private BuildHostSelector buildHostSelector;
    @Resource
    private LocalBuildExecutor localBuildExecutor;
    @Resource
    private SshBuildExecutor sshBuildExecutor;
    @Resource
    private PipelineNodeLogHelper logHelper;

    @Override
    public boolean supports(String nodeType) {
        return stepScriptGenerator.supports(nodeType);
    }

    @Override
    public NodeOutcome handle(PipelineNodeContext ctx) {
        PipelineRunLogDO runLog = logHelper.getOrCreateLog(ctx);
        PipelineSpec.Node node = ctx.getNode();

        // 已成功完成则跳过(幂等重入)
        if ("SUCCESS".equals(runLog.getStatus())) {
            log.info("[BuildNodeHandler][runId({}) nodeId({}) 已成功,跳过]",
                    ctx.getRun().getId(), node.getId());
            return NodeOutcome.CONTINUE;
        }

        try {
            logHelper.markStarted(runLog, "生成构建脚本");

            // 1. 生成脚本
            String script = stepScriptGenerator.generate(node);
            if (StrUtil.isBlank(script)) {
                logHelper.markFailed(runLog, "脚本生成失败", "生成的脚本为空");
                return NodeOutcome.FAIL;
            }

            // 2. 选择构建主机
            BuildHostDO host;
            try {
                host = buildHostSelector.selectDefault();
            } catch (Exception ex) {
                log.error("[BuildNodeHandler][runId({}) nodeId({}) 选择构建主机失败]",
                        ctx.getRun().getId(), node.getId(), ex);
                logHelper.markFailed(runLog, "选择构建主机失败", ex.getMessage());
                return NodeOutcome.FAIL;
            }

            if (host == null) {
                logHelper.markFailed(runLog, "无可用构建主机", "BuildHostSelector 返回 null");
                throw exception(BUILD_HOST_NO_AVAILABLE);
            }

            log.info("[BuildNodeHandler][runId({}) nodeId({}) 选中构建主机: {}({})]",
                    ctx.getRun().getId(), node.getId(), host.getName(), host.getType());

            // 3. 构建 ExecContext
            Path workingDir = resolveWorkingDir(ctx, host);
            Map<String, String> env = buildEnv(ctx);
            SshTarget sshTarget = BuildHostTypeEnum.isSsh(host.getType()) ? mapToSshTarget(host) : null;

            ExecContext execContext = ExecContext.builder()
                    .workingDir(workingDir)
                    .runId(ctx.getRun().getId().toString())
                    .env(env)
                    .timeoutSeconds(DEFAULT_TIMEOUT_SECONDS)
                    .sshTarget(sshTarget)
                    .build();

            // 4. 选择 Executor
            BuildExecutor executor = BuildHostTypeEnum.isSsh(host.getType()) ? sshBuildExecutor : localBuildExecutor;

            // 5. 执行脚本,流式写日志
            List<String> logLines = new ArrayList<>();
            LogSink sink = line -> {
                if (logLines.size() < MAX_LOG_LINES) {
                    logLines.add(line);
                }
            };

            log.info("[BuildNodeHandler][runId({}) nodeId({}) 开始执行脚本,主机={},工作目录={}]",
                    ctx.getRun().getId(), node.getId(), host.getName(), workingDir);

            ExecResult result = executor.exec(execContext, script, sink);

            // 6. 保存日志到 RunLog
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
                log.info("[BuildNodeHandler][runId({}) nodeId({}) 执行成功,退出码={}]",
                        ctx.getRun().getId(), node.getId(), result.getExitCode());
                return NodeOutcome.CONTINUE;
            } else {
                String errorMsg = StrUtil.isNotBlank(result.getErrorMessage())
                        ? result.getErrorMessage()
                        : "退出码: " + result.getExitCode();
                logHelper.markFailed(runLog, "构建失败", errorMsg);
                log.error("[BuildNodeHandler][runId({}) nodeId({}) 执行失败,退出码={},错误={}]",
                        ctx.getRun().getId(), node.getId(), result.getExitCode(), errorMsg);
                return NodeOutcome.FAIL;
            }

        } catch (Exception ex) {
            log.error("[BuildNodeHandler][runId({}) nodeId({}) 执行异常]",
                    ctx.getRun().getId(), node.getId(), ex);
            logHelper.markFailed(runLog, "构建异常", ex.getMessage());
            return NodeOutcome.FAIL;
        }
    }

    /**
     * 解析工作目录:优先使用 context.remoteBuildWorkspace,否则用 host.workspaceRoot + runId。
     */
    private Path resolveWorkingDir(PipelineNodeContext ctx, BuildHostDO host) {
        if (StrUtil.isNotBlank(ctx.getRemoteBuildWorkspace())) {
            return Paths.get(ctx.getRemoteBuildWorkspace());
        }
        String workspaceRoot = StrUtil.isNotBlank(host.getWorkspaceRoot())
                ? host.getWorkspaceRoot()
                : "/tmp/devops-builds";
        return Paths.get(workspaceRoot, "run-" + ctx.getRun().getId());
    }

    /**
     * 构建环境变量:注入 REPO_URL(含凭据)、BRANCH_NAME、COMMIT_SHA、APP_KEY、IMAGE_TAG、DOCKER_REGISTRY 凭据。
     * 凭据只经 env 注入,脚本仅引用变量名。
     */
    private Map<String, String> buildEnv(PipelineNodeContext ctx) {
        Map<String, String> env = new LinkedHashMap<>();

        // 基础变量
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

        return env;
    }

    /**
     * 映射 BuildHostDO 到 SshTarget(仅 SSH 主机类型)。
     */
    private SshTarget mapToSshTarget(BuildHostDO host) {
        return SshTarget.builder()
                .host(host.getHost())
                .port(host.getPort())
                .username(host.getUsername())
                .password(host.getPassword())
                .privateKey(host.getPrivateKey())
                .passphrase(host.getPassphrase())
                .build();
    }

}
