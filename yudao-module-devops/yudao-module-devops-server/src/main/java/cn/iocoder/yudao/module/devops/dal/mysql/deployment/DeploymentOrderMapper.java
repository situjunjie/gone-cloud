package cn.iocoder.yudao.module.devops.dal.mysql.deployment;

import cn.iocoder.yudao.framework.common.pojo.PageResult;
import cn.iocoder.yudao.framework.mybatis.core.mapper.BaseMapperX;
import cn.iocoder.yudao.framework.mybatis.core.query.LambdaQueryWrapperX;
import cn.iocoder.yudao.module.devops.controller.admin.deployment.vo.DeploymentOrderPageReqVO;
import cn.iocoder.yudao.module.devops.dal.dataobject.deployment.DeploymentOrderDO;
import org.apache.ibatis.annotations.Mapper;

@Mapper
public interface DeploymentOrderMapper extends BaseMapperX<DeploymentOrderDO> {

    default DeploymentOrderDO selectByPipelineRunIdAndNodeId(Long pipelineRunId, String nodeId) {
        return selectOne(new LambdaQueryWrapperX<DeploymentOrderDO>()
                .eq(DeploymentOrderDO::getPipelineRunId, pipelineRunId)
                .eq(DeploymentOrderDO::getNodeId, nodeId)
                .last("LIMIT 1"));
    }

    default DeploymentOrderDO selectByPipelineRunLogId(Long pipelineRunLogId) {
        return selectOne(DeploymentOrderDO::getPipelineRunLogId, pipelineRunLogId);
    }

    default PageResult<DeploymentOrderDO> selectPage(DeploymentOrderPageReqVO reqVO) {
        return selectPage(reqVO, new LambdaQueryWrapperX<DeploymentOrderDO>()
                .eqIfPresent(DeploymentOrderDO::getPipelineRunId, reqVO.getPipelineRunId())
                .eqIfPresent(DeploymentOrderDO::getAppId, reqVO.getAppId())
                .eqIfPresent(DeploymentOrderDO::getApplicationEnvId, reqVO.getApplicationEnvId())
                .eqIfPresent(DeploymentOrderDO::getEnvironmentId, reqVO.getEnvironmentId())
                .eqIfPresent(DeploymentOrderDO::getDeployStatus, reqVO.getDeployStatus())
                .likeIfPresent(DeploymentOrderDO::getWorkloadName, reqVO.getWorkloadName())
                .betweenIfPresent(DeploymentOrderDO::getTriggeredAt, reqVO.getTriggeredAt())
                .orderByDesc(DeploymentOrderDO::getTriggeredAt)
                .orderByDesc(DeploymentOrderDO::getId));
    }

}
