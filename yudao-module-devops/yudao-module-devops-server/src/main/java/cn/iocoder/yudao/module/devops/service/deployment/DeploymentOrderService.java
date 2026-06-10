package cn.iocoder.yudao.module.devops.service.deployment;

import cn.iocoder.yudao.framework.common.pojo.PageResult;
import cn.iocoder.yudao.module.devops.controller.admin.deployment.vo.DeploymentOrderPageReqVO;
import cn.iocoder.yudao.module.devops.controller.admin.deployment.vo.DeploymentOrderRespVO;
import cn.iocoder.yudao.module.devops.dal.dataobject.pipeline.PipelineRunDO;
import cn.iocoder.yudao.module.devops.framework.pipeline.PipelineSpec;

public interface DeploymentOrderService {

    PageResult<DeploymentOrderRespVO> getDeploymentOrderPage(DeploymentOrderPageReqVO reqVO);

    DeploymentOrderRespVO getDeploymentOrder(Long id);

    void cancelDeploymentOrder(Long id, Long userId);

    void retryDeploymentOrder(Long id, Long userId);

    void startContainerDeploy(PipelineRunDO run, PipelineSpec.Node node, Long userId);

}
