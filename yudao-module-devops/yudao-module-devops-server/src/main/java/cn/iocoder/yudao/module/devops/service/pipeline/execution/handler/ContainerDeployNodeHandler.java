package cn.iocoder.yudao.module.devops.service.pipeline.execution.handler;

import cn.iocoder.yudao.module.devops.dal.dataobject.pipeline.log.PipelineRunLogDO;
import cn.iocoder.yudao.module.devops.service.deployment.DeploymentOrderService;
import cn.iocoder.yudao.module.devops.service.pipeline.PipelineNodeRegistryServiceImpl;
import jakarta.annotation.Resource;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

/**
 * 容器部署节点处理器。
 *
 * <p>触发 {@link DeploymentOrderService} 创建部署单并返回 CONTINUE(同步完成)或 FAIL。
 */
@Slf4j
@Component
public class ContainerDeployNodeHandler implements PipelineNodeHandler {

    @Resource
    private DeploymentOrderService deploymentOrderService;
    @Resource
    private PipelineNodeLogHelper logHelper;

    @Override
    public boolean supports(String nodeType) {
        return PipelineNodeRegistryServiceImpl.TYPE_CONTAINER_DEPLOY.equals(nodeType);
    }

    @Override
    public NodeOutcome handle(PipelineNodeContext ctx) {
        PipelineRunLogDO runLog = logHelper.getOrCreateLog(ctx);

        // 已成功完成则跳过(幂等重入)
        if ("SUCCESS".equals(runLog.getStatus())) {
            log.info("[ContainerDeployNodeHandler][runId({}) nodeId({}) 已成功,跳过]",
                    ctx.getRun().getId(), ctx.getNode().getId());
            return NodeOutcome.CONTINUE;
        }

        // 触发容器部署
        try {
            logHelper.markStarted(runLog, "开始容器部署");
            deploymentOrderService.startContainerDeploy(ctx.getRun(), ctx.getNode(), ctx.getUserId());
            logHelper.markSuccess(runLog, "容器部署已触发");
            log.info("[ContainerDeployNodeHandler][runId({}) nodeId({}) 部署成功]",
                    ctx.getRun().getId(), ctx.getNode().getId());
            return NodeOutcome.CONTINUE;
        } catch (Exception ex) {
            log.error("[ContainerDeployNodeHandler][runId({}) nodeId({}) 部署失败]",
                    ctx.getRun().getId(), ctx.getNode().getId(), ex);
            logHelper.markFailed(runLog, "容器部署失败", ex.getMessage());
            return NodeOutcome.FAIL;
        }
    }

}
