package cn.iocoder.yudao.module.devops.service.pipeline.execution;

import cn.hutool.core.collection.CollUtil;
import cn.hutool.core.util.StrUtil;
import cn.iocoder.yudao.framework.common.util.json.JsonUtils;
import cn.iocoder.yudao.module.devops.dal.dataobject.pipeline.PipelineDefinitionVersionDO;
import cn.iocoder.yudao.module.devops.dal.dataobject.pipeline.PipelineRunDO;
import cn.iocoder.yudao.module.devops.dal.dataobject.pipeline.log.PipelineRunLogDO;
import cn.iocoder.yudao.module.devops.dal.mysql.pipeline.PipelineDefinitionVersionMapper;
import cn.iocoder.yudao.module.devops.dal.mysql.pipeline.PipelineRunMapper;
import cn.iocoder.yudao.module.devops.dal.mysql.pipeline.log.PipelineRunLogMapper;
import cn.iocoder.yudao.module.devops.enums.PipelineRunLogLevelEnum;
import cn.iocoder.yudao.module.devops.enums.PipelineRunLogStatusEnum;
import cn.iocoder.yudao.module.devops.enums.PipelineRunStatusEnum;
import cn.iocoder.yudao.module.devops.framework.build.BuildExecutor;
import cn.iocoder.yudao.module.devops.framework.pipeline.PipelineSpec;
import cn.iocoder.yudao.module.devops.service.deployment.DeploymentOrderService;
import cn.iocoder.yudao.module.devops.service.pipeline.PipelineNodeRegistryServiceImpl;
import cn.iocoder.yudao.module.devops.service.pipeline.PipelineSpecValidationService;
import cn.iocoder.yudao.module.devops.service.pipeline.approval.PipelineApprovalService;
import cn.iocoder.yudao.module.devops.service.pipeline.execution.handler.NodeOutcome;
import cn.iocoder.yudao.module.devops.service.pipeline.execution.handler.PipelineNodeContext;
import cn.iocoder.yudao.module.devops.service.pipeline.execution.handler.PipelineNodeHandler;
import cn.iocoder.yudao.module.devops.service.pipeline.script.StepScriptGenerator;
import jakarta.annotation.Resource;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

import static cn.iocoder.yudao.framework.common.exception.util.ServiceExceptionUtil.exception;
import static cn.iocoder.yudao.module.devops.enums.ErrorCodeConstants.PIPELINE_NODE_TYPE_NOT_SUPPORTED;
import static cn.iocoder.yudao.module.devops.enums.ErrorCodeConstants.PIPELINE_VERSION_NOT_EXISTS;

/**
 * 流水线执行引擎(责任链驱动器)。
 *
 * <p>核心职责:
 * <ol>
 *     <li>拓扑排序节点(通过 {@link PipelineSpecValidationService#sortNodes})</li>
 *     <li>遍历节点,解析 handler(通过 {@link PipelineNodeHandler#supports})</li>
 *     <li>调用 {@link PipelineNodeHandler#handle},根据 {@link NodeOutcome} 流转</li>
 *     <li>CONTINUE → 下一节点; SUSPEND → 持久化位置并返回; FAIL → 置 run FAILED</li>
 *     <li>全部节点 CONTINUE → 置 run SUCCESS</li>
 * </ol>
 *
 * <p>幂等性:每次重入从头遍历,handler 内部检查 log status 跳过已完成节点。
 *
 * <p>取消支持:遍历运行中节点,BUILD 类调用 {@link BuildExecutor#cancel},平台类调用对应服务取消。
 */
@Slf4j
@Service
public class PipelineExecutionEngine {

    @Resource
    private PipelineDefinitionVersionMapper pipelineDefinitionVersionMapper;
    @Resource
    private PipelineRunMapper pipelineRunMapper;
    @Resource
    private PipelineRunLogMapper pipelineRunLogMapper;
    @Resource
    private PipelineSpecValidationService pipelineSpecValidationService;
    @Resource
    private List<PipelineNodeHandler> handlers;
    @Resource
    private StepScriptGenerator stepScriptGenerator;
    @Resource
    private BuildExecutor localBuildExecutor;
    @Resource
    private BuildExecutor sshBuildExecutor;
    @Resource
    private PipelineApprovalService pipelineApprovalService;
    @Resource
    private DeploymentOrderService deploymentOrderService;
    @Resource
    private cn.iocoder.yudao.module.devops.dal.mysql.application.ApplicationMapper applicationMapper;
    @Resource
    private cn.iocoder.yudao.module.devops.service.repositoryprovider.RepositoryProviderService repositoryProviderService;

    /**
     * 执行流水线节点链。
     *
     * @param run    流水线运行
     * @param userId 触发用户编号
     */
    public void execute(PipelineRunDO run, Long userId) {
        PipelineDefinitionVersionDO version = pipelineDefinitionVersionMapper.selectById(run.getDefinitionVersionId());
        if (version == null) {
            throw exception(PIPELINE_VERSION_NOT_EXISTS);
        }
        execute(run, version, userId);
    }

    /**
     * 执行流水线节点链(已加载 version)。
     *
     * @param run     流水线运行
     * @param version 流水线定义版本
     * @param userId  触发用户编号
     */
    public void execute(PipelineRunDO run, PipelineDefinitionVersionDO version, Long userId) {
        PipelineSpec spec = JsonUtils.parseObject(version.getSpecJson(), PipelineSpec.class);
        if (spec == null || CollUtil.isEmpty(spec.getNodes())) {
            log.warn("[PipelineExecutionEngine][runId({}) spec 为空或无节点,标记成功]", run.getId());
            markRunSuccess(run);
            return;
        }

        // 拓扑排序节点
        List<PipelineSpec.Node> sortedNodes = pipelineSpecValidationService.sortNodes(spec);
        log.info("[PipelineExecutionEngine][runId({}) 开始执行,共 {} 个节点]", run.getId(), sortedNodes.size());

        // 构建共享上下文
        PipelineNodeContext context = PipelineNodeContext.builder()
                .run(run)
                .version(version)
                .sharedState(buildSharedState(run))
                .userId(userId)
                .build();

        // 遍历节点链
        for (PipelineSpec.Node node : sortedNodes) {
            if (Boolean.FALSE.equals(node.getEnabled())) {
                log.info("[PipelineExecutionEngine][runId({}) nodeId({}) 已禁用,跳过]", run.getId(), node.getId());
                continue;
            }

            // 解析 handler:责任链按节点类型派发到对应 handler
            // 注意:不再按 isJenkinsExecutableNode 跳过 —— 新引擎通过 handler(BUILD/审批/部署)驱动全链,
            // 类型分类的清理属于 ST-6 范畴
            PipelineNodeHandler handler = resolveHandler(node.getType());
            if (handler == null) {
                log.error("[PipelineExecutionEngine][runId({}) nodeId({}) 未找到 handler,类型={}]",
                        run.getId(), node.getId(), node.getType());
                markRunFailed(run, "不支持的节点类型: " + node.getType());
                throw exception(PIPELINE_NODE_TYPE_NOT_SUPPORTED, node.getType());
            }

            // 更新上下文当前节点
            context.setNode(node);

            // 调用 handler
            log.info("[PipelineExecutionEngine][runId({}) nodeId({}) 开始处理,类型={}]",
                    run.getId(), node.getId(), node.getType());
            NodeOutcome outcome;
            try {
                outcome = handler.handle(context);
            } catch (Exception ex) {
                log.error("[PipelineExecutionEngine][runId({}) nodeId({}) 处理异常]",
                        run.getId(), node.getId(), ex);
                markRunFailed(run, "节点处理异常: " + ex.getMessage());
                return;
            }

            // 根据 outcome 流转
            log.info("[PipelineExecutionEngine][runId({}) nodeId({}) 处理完成,结果={}]",
                    run.getId(), node.getId(), outcome);

            if (outcome == NodeOutcome.SUSPEND) {
                log.info("[PipelineExecutionEngine][runId({}) nodeId({}) 挂起,等待外部事件]",
                        run.getId(), node.getId());
                return;
            }

            if (outcome == NodeOutcome.FAIL) {
                log.error("[PipelineExecutionEngine][runId({}) nodeId({}) 失败,终止流水线]",
                        run.getId(), node.getId());
                markRunFailed(run, "节点执行失败: " + node.getName());
                return;
            }

            // outcome == CONTINUE: 继续下一节点
        }

        // 全部节点执行完成
        log.info("[PipelineExecutionEngine][runId({}) 全部节点执行完成,标记成功]", run.getId());
        markRunSuccess(run);
    }

    /**
     * 取消流水线运行:取消所有运行中的节点。
     *
     * @param run    流水线运行
     * @param userId 操作人编号
     */
    public void cancel(PipelineRunDO run, Long userId) {
        log.info("[PipelineExecutionEngine][runId({}) 开始取消]", run.getId());

        List<PipelineRunLogDO> logs = pipelineRunLogMapper.selectListByPipelineRunId(run.getId());
        for (PipelineRunLogDO runLog : logs) {
            if (!PipelineRunLogLevelEnum.NODE.getLevel().equals(runLog.getLogLevel())) {
                continue;
            }
            if (isTerminalStatus(runLog.getStatus())) {
                continue;
            }

            log.info("[PipelineExecutionEngine][runId({}) 取消节点 nodeId({}) 类型={}]",
                    run.getId(), runLog.getNodeId(), runLog.getNodeType());

            try {
                // BUILD 类节点:调用 BuildExecutor.cancel
                if (stepScriptGenerator.supports(runLog.getNodeType())) {
                    String runIdStr = run.getId().toString();
                    localBuildExecutor.cancel(runIdStr);
                    sshBuildExecutor.cancel(runIdStr);
                    markLogCanceled(runLog);
                }
                // 审批节点:调用 PipelineApprovalService.cancelApproval
                else if (PipelineNodeRegistryServiceImpl.TYPE_APPROVAL.equals(runLog.getNodeType())) {
                    pipelineApprovalService.cancelApproval(run, runLog.getNodeId(), userId);
                    markLogCanceled(runLog);
                }
                // 容器部署节点:调用 DeploymentOrderService.cancelContainerDeploy
                else if (PipelineNodeRegistryServiceImpl.TYPE_CONTAINER_DEPLOY.equals(runLog.getNodeType())) {
                    deploymentOrderService.cancelContainerDeploy(run, runLog.getNodeId(), userId);
                    markLogCanceled(runLog);
                }
            } catch (Exception ex) {
                log.error("[PipelineExecutionEngine][runId({}) 取消节点失败 nodeId={}]",
                        run.getId(), runLog.getNodeId(), ex);
            }
        }

        // 标记 run 为已取消
        PipelineRunDO update = new PipelineRunDO();
        update.setId(run.getId());
        update.setRunStatus(PipelineRunStatusEnum.CANCELED.getStatus());
        update.setFinishedAt(LocalDateTime.now());
        pipelineRunMapper.updateById(update);

        log.info("[PipelineExecutionEngine][runId({}) 取消完成]", run.getId());
    }

    /**
     * 解析节点类型对应的 handler。
     */
    private PipelineNodeHandler resolveHandler(String nodeType) {
        for (PipelineNodeHandler handler : handlers) {
            if (handler.supports(nodeType)) {
                return handler;
            }
        }
        return null;
    }

    /**
     * 从 run 字段和 application 数据重建 sharedState。
     *
     * <p>BUILD 节点依赖的环境变量来源:
     * <ul>
     *     <li>repoUrl(含 token):由 application.repoUrl + provider.accessToken 构造</li>
     *     <li>branchName:run.branchName(代码合并后写入的部署分支)</li>
     *     <li>commitSha:run.commitSha(部署分支最新提交)</li>
     *     <li>appKey:application.appKey</li>
     * </ul>
     *
     * <p>每次 execute 都重建,保证 SUSPEND 重入(如审批回调)时仍能正确注入环境变量。
     */
    private Map<String, Object> buildSharedState(PipelineRunDO run) {
        Map<String, Object> sharedState = new ConcurrentHashMap<>();
        if (run.getAppId() == null) {
            return sharedState;
        }
        var application = applicationMapper.selectById(run.getAppId());
        if (application == null) {
            return sharedState;
        }
        String repoUrl = application.getRepoUrl();
        if (application.getRepositoryProviderId() != null) {
            var provider = repositoryProviderService.getRepositoryProvider(application.getRepositoryProviderId());
            if (provider != null && StrUtil.isNotBlank(provider.getAccessToken())) {
                repoUrl = buildAuthenticatedRepoUrl(application.getRepoUrl(), provider.getAccessToken());
            }
        }
        putIfNotBlank(sharedState, "repoUrl", repoUrl);
        putIfNotBlank(sharedState, "branchName", run.getBranchName());
        putIfNotBlank(sharedState, "commitSha", run.getCommitSha());
        putIfNotBlank(sharedState, "appKey", application.getAppKey());
        return sharedState;
    }

    private void putIfNotBlank(Map<String, Object> map, String key, String value) {
        if (StrUtil.isNotBlank(value)) {
            map.put(key, value);
        }
    }

    /**
     * 构建带凭据的仓库 URL(token 仅注入 env,不落日志)。
     */
    private String buildAuthenticatedRepoUrl(String repoUrl, String accessToken) {
        if (StrUtil.isBlank(accessToken)) {
            return repoUrl;
        }
        java.net.URI uri = java.net.URI.create(repoUrl);
        String token = java.net.URLEncoder.encode(accessToken, java.nio.charset.StandardCharsets.UTF_8);
        String path = StrUtil.nullToEmpty(uri.getRawPath());
        return uri.getScheme() + "://oauth2:" + token + "@" + uri.getAuthority() + path;
    }

    /**
     * 标记 run 成功。
     */
    private void markRunSuccess(PipelineRunDO run) {
        PipelineRunDO update = new PipelineRunDO();
        update.setId(run.getId());
        update.setRunStatus(PipelineRunStatusEnum.SUCCESS.getStatus());
        update.setFinishedAt(LocalDateTime.now());
        update.setErrorMessage(null);
        pipelineRunMapper.updateById(update);
    }

    /**
     * 标记 run 失败。
     */
    private void markRunFailed(PipelineRunDO run, String message) {
        PipelineRunDO update = new PipelineRunDO();
        update.setId(run.getId());
        update.setRunStatus(PipelineRunStatusEnum.FAILED.getStatus());
        update.setFinishedAt(LocalDateTime.now());
        update.setErrorMessage(StrUtil.subPre(message, 1000));
        pipelineRunMapper.updateById(update);
    }

    /**
     * 标记节点日志为已取消。
     */
    private void markLogCanceled(PipelineRunLogDO log) {
        log.setStatus(PipelineRunLogStatusEnum.CANCELED.getStatus());
        log.setFinishedAt(LocalDateTime.now());
        pipelineRunLogMapper.updateById(log);
    }

    /**
     * 判断日志状态是否为终态。
     */
    private boolean isTerminalStatus(String status) {
        return PipelineRunLogStatusEnum.SUCCESS.getStatus().equals(status)
                || PipelineRunLogStatusEnum.FAILED.getStatus().equals(status)
                || PipelineRunLogStatusEnum.CANCELED.getStatus().equals(status);
    }

}
