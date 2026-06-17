package cn.iocoder.yudao.module.devops.service.deployment;

import cn.iocoder.yudao.framework.common.pojo.PageResult;
import cn.iocoder.yudao.module.devops.controller.admin.deployment.vo.DeploymentOrderPageReqVO;
import cn.iocoder.yudao.module.devops.controller.admin.deployment.vo.DeploymentOrderRespVO;
import cn.iocoder.yudao.module.devops.dal.dataobject.pipeline.PipelineRunDO;
import cn.iocoder.yudao.module.devops.framework.pipeline.PipelineSpec;
import cn.iocoder.yudao.module.devops.service.deployment.context.DeploymentOrderExecutionResult;

public interface DeploymentOrderService {

    PageResult<DeploymentOrderRespVO> getDeploymentOrderPage(DeploymentOrderPageReqVO reqVO);

    DeploymentOrderRespVO getDeploymentOrder(Long id);

    void cancelDeploymentOrder(Long id, Long userId);

    void retryDeploymentOrder(Long id, Long userId);

    DeploymentOrderExecutionResult startContainerDeploy(PipelineRunDO run, PipelineSpec.ExecutableStep step, Long userId);

    /**
     * 取消容器部署节点：将运行中的部署单置为已取消（用于流水线整体取消）。
     *
     * @param run    流水线运行
     * @param nodeId 容器部署节点 ID
     * @param userId 操作人编号
     */
    void cancelContainerDeploy(PipelineRunDO run, String nodeId, Long userId);

}
