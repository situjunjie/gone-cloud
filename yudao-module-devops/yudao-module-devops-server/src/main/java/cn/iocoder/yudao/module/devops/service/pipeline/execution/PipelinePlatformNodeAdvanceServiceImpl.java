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
import cn.iocoder.yudao.module.devops.enums.PipelineRunLogStatusEnum;
import cn.iocoder.yudao.module.devops.enums.PipelineRunStatusEnum;
import cn.iocoder.yudao.module.devops.framework.pipeline.PipelineSpec;
import cn.iocoder.yudao.module.devops.service.deployment.DeploymentOrderService;
import cn.iocoder.yudao.module.devops.service.pipeline.PipelineNodeRegistryServiceImpl;
import cn.iocoder.yudao.module.devops.service.pipeline.PipelineSpecValidationService;
import cn.iocoder.yudao.module.devops.service.pipeline.approval.PipelineApprovalService;
import jakarta.annotation.Resource;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.util.List;

import static cn.iocoder.yudao.framework.common.exception.util.ServiceExceptionUtil.exception;
import static cn.iocoder.yudao.module.devops.enums.ErrorCodeConstants.PIPELINE_NODE_TYPE_NOT_SUPPORTED;
import static cn.iocoder.yudao.module.devops.enums.ErrorCodeConstants.PIPELINE_VERSION_NOT_EXISTS;

/**
 * 平台侧流水线节点推进 Service 实现。
 */
@Service
public class PipelinePlatformNodeAdvanceServiceImpl implements PipelinePlatformNodeAdvanceService {

    @Resource
    private PipelineDefinitionVersionMapper pipelineDefinitionVersionMapper;
    @Resource
    private PipelineRunMapper pipelineRunMapper;
    @Resource
    private PipelineRunLogMapper pipelineRunLogMapper;
    @Resource
    private PipelineSpecValidationService pipelineSpecValidationService;
    @Resource
    private PipelineApprovalService pipelineApprovalService;
    @Resource
    private DeploymentOrderService deploymentOrderService;

    @Override
    public void advance(PipelineRunDO run) {
        PipelineDefinitionVersionDO version = pipelineDefinitionVersionMapper.selectById(run.getDefinitionVersionId());
        if (version == null) {
            throw exception(PIPELINE_VERSION_NOT_EXISTS);
        }
        advance(run, version);
    }

    @Override
    public void advance(PipelineRunDO run, PipelineDefinitionVersionDO version) {
        PipelineSpec spec = JsonUtils.parseObject(version.getSpecJson(), PipelineSpec.class);
        List<PipelineSpec.Node> nodes = spec == null || CollUtil.isEmpty(spec.getNodes())
                ? List.of() : pipelineSpecValidationService.sortNodes(spec);
        for (PipelineSpec.Node node : nodes) {
            if (PipelineNodeRegistryServiceImpl.isJenkinsExecutableNode(node.getType())) {
                continue;
            }
            PipelineRunLogDO log = pipelineRunLogMapper.selectByPipelineRunIdAndNodeId(run.getId(), node.getId());
            if (log != null && PipelineRunLogStatusEnum.SUCCESS.getStatus().equals(log.getStatus())) {
                continue;
            }
            if (log != null && (PipelineRunLogStatusEnum.WAITING_INPUT.getStatus().equals(log.getStatus())
                    || PipelineRunLogStatusEnum.RUNNING.getStatus().equals(log.getStatus()))) {
                return;
            }
            if (PipelineNodeRegistryServiceImpl.TYPE_APPROVAL.equals(node.getType())) {
                pipelineApprovalService.startApproval(run, node, run.getTriggerUserId());
                return;
            }
            if (PipelineNodeRegistryServiceImpl.TYPE_CONTAINER_DEPLOY.equals(node.getType())) {
                deploymentOrderService.startContainerDeploy(run, node, run.getTriggerUserId());
                return;
            }
            markRunFailed(run, "未实现平台节点：" + node.getType());
            throw exception(PIPELINE_NODE_TYPE_NOT_SUPPORTED, node.getType());
        }
        markRunSuccess(run);
    }

    private void markRunSuccess(PipelineRunDO run) {
        PipelineRunDO update = new PipelineRunDO();
        update.setId(run.getId());
        update.setRunStatus(PipelineRunStatusEnum.SUCCESS.getStatus());
        update.setFinishedAt(LocalDateTime.now());
        update.setErrorMessage(null);
        update.setJenkinsBuildNumber(run.getJenkinsBuildNumber());
        pipelineRunMapper.updateById(update);
    }

    private void markRunFailed(PipelineRunDO run, String message) {
        PipelineRunDO update = new PipelineRunDO();
        update.setId(run.getId());
        update.setRunStatus(PipelineRunStatusEnum.FAILED.getStatus());
        update.setFinishedAt(LocalDateTime.now());
        update.setErrorMessage(StrUtil.subPre(message, 1000));
        pipelineRunMapper.updateById(update);
    }

}
