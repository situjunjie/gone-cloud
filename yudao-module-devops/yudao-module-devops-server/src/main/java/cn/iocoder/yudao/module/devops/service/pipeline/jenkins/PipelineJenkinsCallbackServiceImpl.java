package cn.iocoder.yudao.module.devops.service.pipeline.jenkins;

import cn.hutool.core.collection.CollUtil;
import cn.hutool.core.util.StrUtil;
import cn.iocoder.yudao.module.devops.controller.admin.pipelinerun.vo.PipelineJenkinsCallbackReqVO;
import cn.iocoder.yudao.module.devops.controller.admin.pipelinerun.vo.PipelineJenkinsCallbackRespVO;
import cn.iocoder.yudao.module.devops.controller.admin.pipeline.vo.PipelineValidationRespVO;
import cn.iocoder.yudao.module.devops.dal.dataobject.pipeline.PipelineDefinitionVersionDO;
import cn.iocoder.yudao.module.devops.dal.dataobject.pipeline.PipelineRunDO;
import cn.iocoder.yudao.module.devops.dal.dataobject.pipeline.log.PipelineRunLogDO;
import cn.iocoder.yudao.module.devops.dal.mysql.pipeline.PipelineDefinitionVersionMapper;
import cn.iocoder.yudao.module.devops.dal.mysql.pipeline.PipelineRunMapper;
import cn.iocoder.yudao.module.devops.dal.mysql.pipeline.log.PipelineRunLogMapper;
import cn.iocoder.yudao.module.devops.dal.redis.RedisKeyConstants;
import cn.iocoder.yudao.module.devops.enums.PipelineRunLogStatusEnum;
import cn.iocoder.yudao.module.devops.enums.PipelineRunStatusEnum;
import cn.iocoder.yudao.module.devops.framework.jenkins.JenkinsProperties;
import cn.iocoder.yudao.module.devops.framework.pipeline.PipelineSpec;
import cn.iocoder.yudao.module.devops.service.deployment.DeploymentOrderService;
import cn.iocoder.yudao.module.devops.service.pipeline.PipelineNodeRegistryServiceImpl;
import cn.iocoder.yudao.module.devops.service.pipeline.PipelineSpecValidationService;
import cn.iocoder.yudao.module.devops.service.pipeline.runtime.PipelineNodeCallbackAction;
import cn.iocoder.yudao.module.devops.service.pipeline.runtime.PipelineNodeCallbackContext;
import cn.iocoder.yudao.module.devops.service.pipeline.runtime.PipelineNodeRuntimeHandler;
import jakarta.annotation.Resource;
import org.springframework.cache.annotation.CacheEvict;
import org.springframework.stereotype.Service;
import org.springframework.validation.annotation.Validated;

import java.time.LocalDateTime;
import java.util.List;

import static cn.iocoder.yudao.framework.common.exception.util.ServiceExceptionUtil.exception;
import static cn.iocoder.yudao.module.devops.enums.ErrorCodeConstants.PIPELINE_JENKINS_CALLBACK_INVALID;
import static cn.iocoder.yudao.module.devops.enums.ErrorCodeConstants.PIPELINE_JENKINS_CALLBACK_TOKEN_INVALID;
import static cn.iocoder.yudao.module.devops.enums.ErrorCodeConstants.PIPELINE_RUN_NOT_EXISTS;
import static cn.iocoder.yudao.module.devops.enums.ErrorCodeConstants.PIPELINE_VERSION_NOT_EXISTS;

/**
 * Jenkins 统一回调 Service 实现。
 */
@Service
@Validated
public class PipelineJenkinsCallbackServiceImpl implements PipelineJenkinsCallbackService {

    @Resource
    private JenkinsProperties jenkinsProperties;
    @Resource
    private PipelineRunMapper pipelineRunMapper;
    @Resource
    private PipelineRunLogMapper pipelineRunLogMapper;
    @Resource
    private PipelineDefinitionVersionMapper pipelineDefinitionVersionMapper;
    @Resource
    private PipelineSpecValidationService pipelineSpecValidationService;
    @Resource
    private DeploymentOrderService deploymentOrderService;

    private final List<PipelineNodeRuntimeHandler> handlers;

    public PipelineJenkinsCallbackServiceImpl(List<PipelineNodeRuntimeHandler> handlers) {
        this.handlers = handlers;
    }

    @Override
    @CacheEvict(value = RedisKeyConstants.APPLICATION_RELEASE_CURRENT_RUN,
            key = "#root.target.getApplicationEnvIdByPipelineRunId(#pipelineRunId)")
    public PipelineJenkinsCallbackRespVO handleCallback(Long pipelineRunId, String callbackToken,
                                                        PipelineJenkinsCallbackReqVO reqVO) {
        validateCallbackToken(callbackToken);
        PipelineRunDO run = validatePipelineRunExists(pipelineRunId);
        PipelineDefinitionVersionDO version = validateVersionExists(reqVO.getPipelineVersionId());
        PipelineSpec.Node node = findNode(version, reqVO.getNodeId());
        if (!node.getType().equals(reqVO.getNodeType())) {
            throw exception(PIPELINE_JENKINS_CALLBACK_INVALID, "节点类型不匹配");
        }
        fillJenkinsBuildMetadata(run, reqVO);
        PipelineNodeRuntimeHandler handler = findHandler(reqVO.getNodeType());
        if (isDuplicate(run.getId(), reqVO)) {
            return new PipelineJenkinsCallbackRespVO(true, true);
        }
        PipelineNodeCallbackContext context = new PipelineNodeCallbackContext(run, node, reqVO);
        switch (reqVO.getAction()) {
            case PipelineNodeCallbackAction.STARTED -> handler.onStarted(context);
            case PipelineNodeCallbackAction.COMPLETED -> {
                handler.onCompleted(context);
                advanceRunIfAllJenkinsNodesCompleted(run, version);
            }
            case PipelineNodeCallbackAction.FAILED -> handler.onFailed(context);
            default -> throw exception(PIPELINE_JENKINS_CALLBACK_INVALID, "不支持的 action：" + reqVO.getAction());
        }
        return new PipelineJenkinsCallbackRespVO(true, false);
    }

    private PipelineNodeRuntimeHandler findHandler(String nodeType) {
        return handlers.stream()
                .filter(handler -> handler.supports(nodeType))
                .findFirst()
                .orElseThrow(() -> exception(PIPELINE_JENKINS_CALLBACK_INVALID, "未实现节点处理器：" + nodeType));
    }

    public Long getApplicationEnvIdByPipelineRunId(Long pipelineRunId) {
        PipelineRunDO run = pipelineRunMapper.selectById(pipelineRunId);
        return run == null ? null : run.getApplicationEnvId();
    }

    private void validateCallbackToken(String callbackToken) {
        if (StrUtil.isBlank(jenkinsProperties.getCallbackToken())
                || !StrUtil.equals(callbackToken, jenkinsProperties.getCallbackToken())) {
            throw exception(PIPELINE_JENKINS_CALLBACK_TOKEN_INVALID);
        }
    }

    private PipelineRunDO validatePipelineRunExists(Long id) {
        PipelineRunDO run = pipelineRunMapper.selectById(id);
        if (run == null) {
            throw exception(PIPELINE_RUN_NOT_EXISTS);
        }
        return run;
    }

    private PipelineDefinitionVersionDO validateVersionExists(Long id) {
        PipelineDefinitionVersionDO version = pipelineDefinitionVersionMapper.selectById(id);
        if (version == null) {
            throw exception(PIPELINE_VERSION_NOT_EXISTS);
        }
        return version;
    }

    private PipelineSpec.Node findNode(PipelineDefinitionVersionDO version, String nodeId) {
        PipelineValidationRespVO validation = new PipelineValidationRespVO();
        PipelineSpec spec = pipelineSpecValidationService.parseSpec(version.getSpecJson(), validation);
        if (spec == null || CollUtil.isEmpty(spec.getNodes())) {
            throw exception(PIPELINE_JENKINS_CALLBACK_INVALID, "流水线版本 DSL 无效");
        }
        return spec.getNodes().stream()
                .filter(node -> nodeId.equals(node.getId()))
                .findFirst()
                .orElseThrow(() -> exception(PIPELINE_JENKINS_CALLBACK_INVALID, "节点不存在：" + nodeId));
    }

    private void fillJenkinsBuildMetadata(PipelineRunDO run, PipelineJenkinsCallbackReqVO reqVO) {
        boolean changed = false;
        if (StrUtil.isNotBlank(reqVO.getJenkinsBuildNumber())
                && !reqVO.getJenkinsBuildNumber().equals(run.getJenkinsBuildNumber())) {
            run.setJenkinsBuildNumber(reqVO.getJenkinsBuildNumber());
            changed = true;
        }
        if (changed) {
            pipelineRunMapper.updateById(run);
        }
    }

    private boolean isDuplicate(Long pipelineRunId, PipelineJenkinsCallbackReqVO reqVO) {
        PipelineRunLogDO log = pipelineRunLogMapper.selectByPipelineRunIdAndNodeId(pipelineRunId, reqVO.getNodeId());
        if (log == null) {
            return false;
        }
        return switch (reqVO.getAction()) {
            case PipelineNodeCallbackAction.STARTED -> PipelineRunLogStatusEnum.RUNNING.getStatus().equals(log.getStatus())
                    || PipelineRunLogStatusEnum.SUCCESS.getStatus().equals(log.getStatus())
                    || PipelineRunLogStatusEnum.FAILED.getStatus().equals(log.getStatus())
                    || PipelineRunLogStatusEnum.CANCELED.getStatus().equals(log.getStatus());
            case PipelineNodeCallbackAction.COMPLETED -> PipelineRunLogStatusEnum.SUCCESS.getStatus().equals(log.getStatus());
            case PipelineNodeCallbackAction.FAILED -> PipelineRunLogStatusEnum.FAILED.getStatus().equals(log.getStatus());
            default -> false;
        };
    }

    private void advanceRunIfAllJenkinsNodesCompleted(PipelineRunDO run, PipelineDefinitionVersionDO version) {
        PipelineValidationRespVO validation = new PipelineValidationRespVO();
        PipelineSpec spec = pipelineSpecValidationService.parseSpec(version.getSpecJson(), validation);
        if (spec == null || CollUtil.isEmpty(spec.getNodes())) {
            return;
        }
        for (PipelineSpec.Node node : spec.getNodes()) {
            if (PipelineNodeRegistryServiceImpl.TYPE_CONTAINER_DEPLOY.equals(node.getType())) {
                continue;
            }
            PipelineRunLogDO log = pipelineRunLogMapper.selectByPipelineRunIdAndNodeId(run.getId(), node.getId());
            if (log == null || !PipelineRunLogStatusEnum.SUCCESS.getStatus().equals(log.getStatus())) {
                return;
            }
        }
        PipelineSpec.Node containerDeployNode = findContainerDeployNode(spec);
        if (containerDeployNode != null) {
            deploymentOrderService.startContainerDeploy(run, containerDeployNode, run.getTriggerUserId());
            return;
        }
        PipelineRunDO update = new PipelineRunDO();
        update.setId(run.getId());
        update.setRunStatus(PipelineRunStatusEnum.SUCCESS.getStatus());
        update.setFinishedAt(LocalDateTime.now());
        update.setErrorMessage(null);
        update.setJenkinsBuildNumber(run.getJenkinsBuildNumber());
        pipelineRunMapper.updateById(update);
    }

    private PipelineSpec.Node findContainerDeployNode(PipelineSpec spec) {
        return spec.getNodes().stream()
                .filter(node -> PipelineNodeRegistryServiceImpl.TYPE_CONTAINER_DEPLOY.equals(node.getType()))
                .findFirst()
                .orElse(null);
    }

}
