package cn.iocoder.yudao.module.devops.service.deployment;

import cn.hutool.core.util.StrUtil;
import cn.iocoder.yudao.framework.common.pojo.PageResult;
import cn.iocoder.yudao.framework.common.util.json.JsonUtils;
import cn.iocoder.yudao.module.devops.controller.admin.deployment.vo.DeploymentOrderPageReqVO;
import cn.iocoder.yudao.module.devops.controller.admin.deployment.vo.DeploymentOrderRespVO;
import cn.iocoder.yudao.module.devops.dal.dataobject.application.ApplicationDO;
import cn.iocoder.yudao.module.devops.dal.dataobject.application.ApplicationEnvDO;
import cn.iocoder.yudao.module.devops.dal.dataobject.deployment.DeploymentOrderDO;
import cn.iocoder.yudao.module.devops.dal.dataobject.environment.EnvironmentDO;
import cn.iocoder.yudao.module.devops.dal.dataobject.pipeline.PipelineRunDO;
import cn.iocoder.yudao.module.devops.dal.dataobject.pipeline.log.PipelineRunLogDO;
import cn.iocoder.yudao.module.devops.dal.redis.RedisKeyConstants;
import cn.iocoder.yudao.module.devops.dal.mysql.application.ApplicationEnvMapper;
import cn.iocoder.yudao.module.devops.dal.mysql.application.ApplicationMapper;
import cn.iocoder.yudao.module.devops.dal.mysql.deployment.DeploymentOrderMapper;
import cn.iocoder.yudao.module.devops.dal.mysql.environment.EnvironmentMapper;
import cn.iocoder.yudao.module.devops.dal.mysql.pipeline.log.PipelineRunLogMapper;
import cn.iocoder.yudao.module.devops.enums.DeploymentModeEnum;
import cn.iocoder.yudao.module.devops.enums.DeploymentOrderStatusEnum;
import cn.iocoder.yudao.module.devops.enums.DeploymentOrderStepKeyEnum;
import cn.iocoder.yudao.module.devops.enums.EnvironmentInfraTypeEnum;
import cn.iocoder.yudao.module.devops.enums.PipelineRunLogLevelEnum;
import cn.iocoder.yudao.module.devops.enums.PipelineRunLogStatusEnum;
import cn.iocoder.yudao.module.devops.framework.kubernetes.KubernetesClientFactory;
import cn.iocoder.yudao.module.devops.framework.kubernetes.KubernetesDeploymentManifestSupport;
import cn.iocoder.yudao.module.devops.framework.kubernetes.KubernetesEnvironmentConfig;
import cn.iocoder.yudao.module.devops.framework.pipeline.PipelineSpec;
import cn.iocoder.yudao.module.devops.service.deployment.context.ContainerDeployConfigContext;
import cn.iocoder.yudao.module.devops.service.deployment.context.DeploymentOrderExecutionResult;
import cn.iocoder.yudao.module.devops.service.deployment.context.DeploymentOrderResultContext;
import cn.iocoder.yudao.module.devops.service.pipeline.PipelineNodeRegistryServiceImpl;
import cn.iocoder.yudao.module.devops.service.pipeline.execution.handler.PipelineStepAction;
import cn.iocoder.yudao.module.devops.service.pipeline.execution.handler.PipelineStepActionHelper;
import cn.iocoder.yudao.module.devops.service.pipeline.execution.handler.PipelineStepActionKeyConstants;
import cn.iocoder.yudao.module.devops.service.pipeline.execution.handler.PipelineStepActionTarget;
import cn.iocoder.yudao.module.devops.service.pipeline.execution.handler.PipelineStepActionTypeEnum;
import io.fabric8.kubernetes.api.model.Container;
import io.fabric8.kubernetes.api.model.HasMetadata;
import io.fabric8.kubernetes.api.model.ObjectMeta;
import io.fabric8.kubernetes.api.model.apps.Deployment;
import io.fabric8.kubernetes.api.model.apps.DeploymentCondition;
import io.fabric8.kubernetes.client.KubernetesClient;
import io.fabric8.kubernetes.client.KubernetesClientException;
import jakarta.annotation.Resource;
import org.springframework.cache.annotation.CacheEvict;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.validation.annotation.Validated;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;

import static cn.iocoder.yudao.framework.common.exception.util.ServiceExceptionUtil.exception;
import static cn.iocoder.yudao.module.devops.enums.ErrorCodeConstants.*;

/**
 * DevOps 部署单 Service 实现。
 */
@Service
@Validated
public class DeploymentOrderServiceImpl implements DeploymentOrderService {

    private static final String DEPLOY_TYPE_K8S_DEPLOYMENT = "K8S_DEPLOYMENT";
    private static final String DEPLOY_TYPE_K8S_IMAGE_UPGRADE = "K8S_IMAGE_UPGRADE";
    private static final String LEGACY_CONTAINER_DEPLOY_NODE_TYPE = "CONTAINER_DEPLOY";
    private static final String WORKLOAD_KIND_DEPLOYMENT = "DEPLOYMENT";
    private static final int DEFAULT_ROLLOUT_TIMEOUT_SECONDS = 300;
    private static final long ROLLOUT_POLL_INTERVAL_MILLIS = 2000L;

    @Resource
    private DeploymentOrderMapper deploymentOrderMapper;
    @Resource
    private PipelineRunLogMapper pipelineRunLogMapper;
    @Resource
    private ApplicationMapper applicationMapper;
    @Resource
    private ApplicationEnvMapper applicationEnvMapper;
    @Resource
    private EnvironmentMapper environmentMapper;
    @Resource
    private KubernetesClientFactory kubernetesClientFactory;
    @Resource
    private KubernetesDeploymentManifestSupport kubernetesDeploymentManifestSupport;
    @Resource
    private KubernetesDeploymentStatusCollector kubernetesDeploymentStatusCollector;
    @Resource
    private PipelineStepActionHelper pipelineStepActionHelper;

    @Override
    public PageResult<DeploymentOrderRespVO> getDeploymentOrderPage(DeploymentOrderPageReqVO reqVO) {
        PageResult<DeploymentOrderDO> pageResult = deploymentOrderMapper.selectPage(reqVO);
        return new PageResult<>(pageResult.getList().stream().map(order -> buildRespVO(order, false)).toList(),
                pageResult.getTotal());
    }

    @Override
    public DeploymentOrderRespVO getDeploymentOrder(Long id) {
        DeploymentOrderDO order = validateDeploymentOrderExists(id);
        return buildRespVO(order, true);
    }

    @Override
    @CacheEvict(value = RedisKeyConstants.APPLICATION_RELEASE_CURRENT_RUN,
            key = "#root.target.getApplicationEnvIdByDeploymentOrderId(#id)")
    @Transactional(rollbackFor = Exception.class)
    public void cancelDeploymentOrder(Long id, Long userId) {
        DeploymentOrderDO order = validateDeploymentOrderExists(id);
        if (!DeploymentOrderStatusEnum.CREATED.getStatus().equals(order.getDeployStatus())
                && !DeploymentOrderStatusEnum.RUNNING.getStatus().equals(order.getDeployStatus())) {
            throw exception(DEPLOYMENT_ORDER_STATE_INVALID);
        }
        order.setDeployStatus(DeploymentOrderStatusEnum.CANCELED.getStatus());
        order.setFinishedAt(LocalDateTime.now());
        deploymentOrderMapper.updateById(order);
        updateOrderLogCanceled(order, "部署已取消");
    }

    @Override
    public void cancelContainerDeploy(PipelineRunDO run, String nodeId, Long userId) {
        DeploymentOrderDO order = deploymentOrderMapper.selectByPipelineRunIdAndNodeId(run.getId(), nodeId);
        if (order == null) {
            return;
        }
        // 仅运行中/已创建的部署单需要取消，已结束的保持原状
        if (!DeploymentOrderStatusEnum.CREATED.getStatus().equals(order.getDeployStatus())
                && !DeploymentOrderStatusEnum.RUNNING.getStatus().equals(order.getDeployStatus())) {
            return;
        }
        order.setDeployStatus(DeploymentOrderStatusEnum.CANCELED.getStatus());
        order.setFinishedAt(LocalDateTime.now());
        deploymentOrderMapper.updateById(order);
        updateOrderLogCanceled(order, "流水线已取消");
    }

    @Override
    @CacheEvict(value = RedisKeyConstants.APPLICATION_RELEASE_CURRENT_RUN,
            key = "#root.target.getApplicationEnvIdByDeploymentOrderId(#id)")
    public void retryDeploymentOrder(Long id, Long userId) {
        DeploymentOrderDO order = validateDeploymentOrderExists(id);
        if (!DeploymentOrderStatusEnum.FAILED.getStatus().equals(order.getDeployStatus())
                && !DeploymentOrderStatusEnum.CANCELED.getStatus().equals(order.getDeployStatus())) {
            throw exception(DEPLOYMENT_ORDER_STATE_INVALID);
        }
        order.setAttempt(order.getAttempt() == null ? 1 : order.getAttempt() + 1);
        order.setDeployStatus(DeploymentOrderStatusEnum.RUNNING.getStatus());
        order.setCurrentStage(DeploymentOrderStepKeyEnum.PREPARE_CONTEXT.getKey());
        order.setStartedAt(LocalDateTime.now());
        order.setFinishedAt(null);
        order.setErrorMessage(null);
        order.setResultJson(JsonUtils.toJsonString(new DeploymentOrderResultContext()));
        deploymentOrderMapper.updateById(order);
        updateOrderLogRunning(order, "正在重试容器部署");
        executeDeployment(order);
    }

    @Override
    @CacheEvict(value = RedisKeyConstants.APPLICATION_RELEASE_CURRENT_RUN, key = "#run.applicationEnvId")
    public DeploymentOrderExecutionResult startContainerDeploy(PipelineRunDO run, PipelineSpec.ExecutableStep step, Long userId) {
        DeploymentOrderDO existing = deploymentOrderMapper.selectByPipelineRunIdAndNodeId(run.getId(), step.getStepId());
        if (existing != null && DeploymentOrderStatusEnum.SUCCESS.getStatus().equals(existing.getDeployStatus())) {
            return buildExecutionResult(existing, true, "部署已完成", null);
        }
        if (existing != null && DeploymentOrderStatusEnum.RUNNING.getStatus().equals(existing.getDeployStatus())) {
            return buildExecutionResult(existing, false, "部署仍在执行中", "部署仍在执行中");
        }
        DeploymentOrderDO order = existing == null ? createDeploymentOrder(run, step, userId)
                : prepareExistingOrderForRetry(existing);
        updateOrderLogRunning(order, "开始容器部署");
        return executeDeployment(order);
    }

    private DeploymentOrderDO createDeploymentOrder(PipelineRunDO run, PipelineSpec.ExecutableStep step, Long userId) {
        ApplicationEnvDO applicationEnv = validateApplicationEnvExists(run.getApplicationEnvId());
        ApplicationDO application = validateApplicationExists(run.getAppId());
        EnvironmentDO environment = validateEnvironmentExists(applicationEnv.getEnvId());
        ContainerDeployConfigContext config = buildConfig(run, step, application, environment);

        PipelineRunLogDO log = createOrGetNodeLog(run, step, config);
        DeploymentOrderDO order = new DeploymentOrderDO();
        order.setTenantId(run.getTenantId());
        order.setPipelineRunId(run.getId());
        order.setPipelineRunLogId(log.getId());
        order.setNodeId(step.getStepId());
        order.setNodeType(step.getStep());
        order.setDefinitionId(run.getDefinitionId());
        order.setDefinitionVersionId(run.getDefinitionVersionId());
        order.setAppId(run.getAppId());
        order.setApplicationEnvId(run.getApplicationEnvId());
        order.setEnvironmentId(environment.getId());
        order.setInfraType(EnvironmentInfraTypeEnum.K8S.getInfraType());
        order.setDeployType(resolveDeployType(step));
        order.setDeployStatus(DeploymentOrderStatusEnum.CREATED.getStatus());
        order.setAttempt(1);
        order.setCurrentStage(DeploymentOrderStepKeyEnum.PREPARE_CONTEXT.getKey());
        order.setTriggerType(run.getTriggerType());
        order.setTriggerUserId(userId == null ? run.getTriggerUserId() : userId);
        order.setTriggeredAt(LocalDateTime.now());
        order.setNamespace(config.getNamespace());
        order.setWorkloadKind(WORKLOAD_KIND_DEPLOYMENT);
        order.setWorkloadName(config.getWorkloadName());
        order.setContainerName(config.getContainerName());
        order.setImage(config.getImage());
        order.setReplicas(config.getReplicas());
        order.setConfigJson(JsonUtils.toJsonString(config));
        order.setResultJson(JsonUtils.toJsonString(new DeploymentOrderResultContext()));
        deploymentOrderMapper.insert(order);
        return order;
    }

    private DeploymentOrderDO prepareExistingOrderForRetry(DeploymentOrderDO order) {
        order.setAttempt(order.getAttempt() == null ? 1 : order.getAttempt() + 1);
        order.setDeployStatus(DeploymentOrderStatusEnum.CREATED.getStatus());
        order.setCurrentStage(DeploymentOrderStepKeyEnum.PREPARE_CONTEXT.getKey());
        order.setStartedAt(null);
        order.setFinishedAt(null);
        order.setErrorMessage(null);
        order.setResultJson(JsonUtils.toJsonString(new DeploymentOrderResultContext()));
        deploymentOrderMapper.updateById(order);
        return order;
    }

    private DeploymentOrderExecutionResult executeDeployment(DeploymentOrderDO order) {
        try {
            order.setDeployStatus(DeploymentOrderStatusEnum.RUNNING.getStatus());
            order.setStartedAt(order.getStartedAt() == null ? LocalDateTime.now() : order.getStartedAt());
            deploymentOrderMapper.updateById(order);
            ContainerDeployConfigContext config = parseConfig(order);
            EnvironmentDO environment = validateEnvironmentExists(order.getEnvironmentId());
            KubernetesEnvironmentConfig infraConfig = parseKubernetesConfig(environment);
            try (KubernetesClient client = kubernetesClientFactory.create(infraConfig.getKubeconfig())) {
                try {
                    updateStage(order, DeploymentOrderStepKeyEnum.CONNECT_CLUSTER, "已连接 Kubernetes 集群");
                    Deployment previousDeployment = loadExistingDeployment(client, order);
                    fillPreviousSnapshot(order, previousDeployment);
                    Deployment appliedDeployment = DEPLOY_TYPE_K8S_IMAGE_UPGRADE.equals(order.getDeployType())
                            ? applyDeployment(client, order, prepareTargetDeployment(order, config, previousDeployment))
                            : applyManifestResources(client, order, config);
                    Deployment readyDeployment = waitRolloutReady(client, order, config, appliedDeployment);
                    return markSuccess(client, order, readyDeployment);
                } catch (Exception ex) {
                    captureFinalSnapshotQuietly(client, order, null, resolveFailureReason(ex), sanitizeMessage(ex.getMessage()));
                    throw ex;
                }
            }
        } catch (DeploymentCanceledException ex) {
            // Cancellation has already persisted CANCELED state from cancelDeploymentOrder.
            return buildExecutionResult(order, false, "部署已取消", "部署已取消");
        } catch (Exception ex) {
            String errorMessage = sanitizeMessage(ex.getMessage());
            markFailed(order, errorMessage);
            return buildExecutionResult(order, false, "容器部署失败", errorMessage);
        }
    }

    private Deployment loadExistingDeployment(KubernetesClient client, DeploymentOrderDO order) {
        updateStage(order, DeploymentOrderStepKeyEnum.LOAD_WORKLOAD, "正在读取 Deployment：" + order.getWorkloadName());
        try {
            return client.apps().deployments()
                    .inNamespace(order.getNamespace())
                    .withName(order.getWorkloadName())
                    .get();
        } catch (KubernetesClientException ex) {
            throw exception(DEPLOYMENT_KUBERNETES_APPLY_FAIL, sanitizeMessage(ex.getMessage()));
        }
    }

    private Deployment prepareTargetDeployment(DeploymentOrderDO order, ContainerDeployConfigContext config,
                                               Deployment existingDeployment) {
        if (DEPLOY_TYPE_K8S_IMAGE_UPGRADE.equals(order.getDeployType())) {
            if (existingDeployment == null) {
                throw exception(DEPLOYMENT_KUBERNETES_WORKLOAD_NOT_EXISTS, order.getWorkloadName());
            }
            Container container = findContainerInExistingDeployment(existingDeployment, config.getContainerName());
            container.setImage(config.getImage());
            if (config.getReplicas() != null) {
                existingDeployment.getSpec().setReplicas(config.getReplicas());
            }
            return existingDeployment;
        }
        return kubernetesDeploymentManifestSupport.prepareDeployment(config);
    }

    private Container findContainerInExistingDeployment(Deployment deployment, String containerName) {
        List<Container> containers = deployment.getSpec() == null
                || deployment.getSpec().getTemplate() == null
                || deployment.getSpec().getTemplate().getSpec() == null
                ? null : deployment.getSpec().getTemplate().getSpec().getContainers();
        if (containers == null || containers.isEmpty()) {
            throw exception(DEPLOYMENT_KUBERNETES_CONTAINER_NOT_EXISTS, containerName);
        }
        return containers.stream()
                .filter(container -> containerName.equals(container.getName()))
                .findFirst()
                .orElseThrow(() -> exception(DEPLOYMENT_KUBERNETES_CONTAINER_NOT_EXISTS, containerName));
    }

    private Deployment applyManifestResources(KubernetesClient client, DeploymentOrderDO order,
                                              ContainerDeployConfigContext config) {
        updateStage(order, DeploymentOrderStepKeyEnum.APPLY_SPEC, "正在提交 Kubernetes YAML：" + order.getWorkloadName());
        List<HasMetadata> resources = kubernetesDeploymentManifestSupport.prepareSupportedResources(config);
        Deployment appliedDeployment = null;
        for (int index = 0; index < resources.size(); index++) {
            HasMetadata resource = resources.get(index);
            annotateResource(order, resource);
            updateResourceApplyResult(order, index, "CREATE_OR_REPLACE", resource, null, "APPLYING", null);
            try {
                HasMetadata applied = client.resource(resource).inNamespace(order.getNamespace()).createOrReplace();
                updateResourceApplyResult(order, index, "CREATE_OR_REPLACE", resource, applied, "APPLIED", null);
                if (applied instanceof Deployment deployment) {
                    appliedDeployment = deployment;
                    updateAppliedDeploymentSnapshot(order, deployment);
                } else if (resource instanceof Deployment deployment) {
                    appliedDeployment = deployment;
                }
            } catch (RuntimeException ex) {
                String errorMessage = sanitizeMessage(ex.getMessage());
                updateResourceApplyResult(order, index, "CREATE_OR_REPLACE", resource, null, "FAILED", errorMessage);
                if (ex instanceof KubernetesClientException) {
                    throw exception(DEPLOYMENT_KUBERNETES_APPLY_FAIL, errorMessage);
                }
                throw ex;
            }
        }
        return appliedDeployment;
    }

    private Deployment applyDeployment(KubernetesClient client, DeploymentOrderDO order, Deployment deployment) {
        updateStage(order, DeploymentOrderStepKeyEnum.APPLY_SPEC, "正在提交 Deployment YAML：" + order.getWorkloadName());
        annotateResource(order, deployment);
        updateResourceApplyResult(order, 0, "UPDATE", deployment, null, "APPLYING", null);
        try {
            Deployment applied = DEPLOY_TYPE_K8S_IMAGE_UPGRADE.equals(order.getDeployType())
                    ? client.apps().deployments().inNamespace(order.getNamespace()).resource(deployment).update()
                    : client.apps().deployments().inNamespace(order.getNamespace()).resource(deployment).createOrReplace();
            updateResourceApplyResult(order, 0, DEPLOY_TYPE_K8S_IMAGE_UPGRADE.equals(order.getDeployType())
                    ? "UPDATE" : "CREATE_OR_REPLACE", deployment, applied, "APPLIED", null);
            updateAppliedDeploymentSnapshot(order, applied);
            return applied;
        } catch (RuntimeException ex) {
            String errorMessage = sanitizeMessage(ex.getMessage());
            updateResourceApplyResult(order, 0, DEPLOY_TYPE_K8S_IMAGE_UPGRADE.equals(order.getDeployType())
                    ? "UPDATE" : "CREATE_OR_REPLACE", deployment, null, "FAILED", errorMessage);
            if (ex instanceof KubernetesClientException) {
                throw exception(DEPLOYMENT_KUBERNETES_APPLY_FAIL, errorMessage);
            }
            throw ex;
        }
    }

    private void annotateResource(DeploymentOrderDO order, HasMetadata resource) {
        if (resource.getMetadata().getAnnotations() == null) {
            resource.getMetadata().setAnnotations(new LinkedHashMap<>());
        }
        resource.getMetadata().getAnnotations().put(KubernetesDeploymentStatusCollector.PIPELINE_RUN_ID_KEY,
                String.valueOf(order.getPipelineRunId()));
        resource.getMetadata().getAnnotations().put(KubernetesDeploymentStatusCollector.DEPLOYMENT_ORDER_ID_KEY,
                String.valueOf(order.getId()));
        if (resource instanceof Deployment deployment) {
            deployment.getMetadata().getAnnotations().put("kubernetes.io/change-cause",
                    "gone devops deploy " + order.getId() + " image " + order.getImage());
            annotatePodTemplate(order, deployment);
        }
    }

    private void annotatePodTemplate(DeploymentOrderDO order, Deployment deployment) {
        if (deployment.getSpec().getTemplate().getMetadata() == null) {
            deployment.getSpec().getTemplate().setMetadata(new ObjectMeta());
        }
        ObjectMeta metadata = deployment.getSpec().getTemplate().getMetadata();
        if (metadata.getLabels() == null) {
            metadata.setLabels(new LinkedHashMap<>());
        }
        if (metadata.getAnnotations() == null) {
            metadata.setAnnotations(new LinkedHashMap<>());
        }
        String orderId = String.valueOf(order.getId());
        metadata.getLabels().put(KubernetesDeploymentStatusCollector.DEPLOYMENT_ORDER_ID_KEY, orderId);
        metadata.getAnnotations().put(KubernetesDeploymentStatusCollector.DEPLOYMENT_ORDER_ID_KEY, orderId);
    }

    private void updateAppliedDeploymentSnapshot(DeploymentOrderDO order, Deployment applied) {
        if (applied != null && applied.getMetadata() != null) {
            order.setWorkloadGeneration(applied.getMetadata().getGeneration());
            order.setWorkloadUid(applied.getMetadata().getUid());
            deploymentOrderMapper.updateById(order);
        }
    }

    private Deployment waitRolloutReady(KubernetesClient client, DeploymentOrderDO order,
                                        ContainerDeployConfigContext config, Deployment initialDeployment) {
        updateStage(order, DeploymentOrderStepKeyEnum.WAIT_ROLLOUT, "等待 Deployment rollout ready");
        long deadline = System.currentTimeMillis() + config.getRolloutTimeoutSeconds() * 1000L;
        Deployment last = initialDeployment;
        while (System.currentTimeMillis() <= deadline) {
            if (isCanceled(order.getId())) {
                throw new DeploymentCanceledException();
            }
            last = client.apps().deployments().inNamespace(order.getNamespace()).withName(order.getWorkloadName()).get();
            if (last != null && isRolloutReady(last)) {
                return last;
            }
            sleepQuietly();
        }
        throw exception(DEPLOYMENT_KUBERNETES_ROLLOUT_TIMEOUT);
    }

    private boolean isRolloutReady(Deployment deployment) {
        if (deployment.getSpec() == null || deployment.getStatus() == null || deployment.getMetadata() == null) {
            return false;
        }
        Integer replicas = deployment.getSpec().getReplicas();
        int desired = replicas == null ? 1 : replicas;
        Long observedGeneration = deployment.getStatus().getObservedGeneration();
        Long generation = deployment.getMetadata().getGeneration();
        Integer updatedReplicas = deployment.getStatus().getUpdatedReplicas();
        Integer availableReplicas = deployment.getStatus().getAvailableReplicas();
        return observedGeneration != null && generation != null && observedGeneration >= generation
                && Objects.equals(updatedReplicas, desired)
                && Objects.equals(availableReplicas, desired)
                && !hasProgressDeadlineExceeded(deployment.getStatus().getConditions());
    }

    private boolean hasProgressDeadlineExceeded(List<DeploymentCondition> conditions) {
        if (conditions == null) {
            return false;
        }
        return conditions.stream().anyMatch(condition -> "Progressing".equals(condition.getType())
                && "False".equals(condition.getStatus())
                && "ProgressDeadlineExceeded".equals(condition.getReason()));
    }

    private void fillPreviousSnapshot(DeploymentOrderDO order, Deployment deployment) {
        if (deployment == null) {
            deploymentOrderMapper.updateById(order);
            return;
        }
        Container container = findContainerQuietly(deployment, order.getContainerName());
        order.setPreviousImage(container == null ? null : container.getImage());
        order.setPreviousReplicas(deployment.getSpec() == null ? null : deployment.getSpec().getReplicas());
        order.setPreviousRevision(readRevision(deployment));
        order.setWorkloadUid(deployment.getMetadata() == null ? null : deployment.getMetadata().getUid());
        order.setWorkloadGeneration(deployment.getMetadata() == null ? null : deployment.getMetadata().getGeneration());
        deploymentOrderMapper.updateById(order);
    }

    private Container findContainerQuietly(Deployment deployment, String containerName) {
        try {
            return kubernetesDeploymentManifestSupport.findContainer(deployment, containerName);
        } catch (Exception ex) {
            return null;
        }
    }

    private DeploymentOrderExecutionResult markSuccess(KubernetesClient client, DeploymentOrderDO order,
                                                       Deployment deployment) {
        captureFinalSnapshotQuietly(client, order, deployment, null, null);
        order.setDeployStatus(DeploymentOrderStatusEnum.SUCCESS.getStatus());
        order.setCurrentStage(DeploymentOrderStepKeyEnum.CAPTURE_RESULT.getKey());
        order.setTargetRevision(readRevision(deployment));
        DeploymentOrderResultContext result = loadResultContext(order);
        result.setStage(DeploymentOrderStepKeyEnum.CAPTURE_RESULT.getKey());
        result.setSummary("容器部署成功：" + order.getWorkloadName());
        persistResultContext(order, result);
        order.setFinishedAt(LocalDateTime.now());
        order.setErrorMessage(null);
        deploymentOrderMapper.updateById(order);
        updateOrderLogSuccess(order, "容器部署成功：" + order.getWorkloadName());
        return buildExecutionResult(order, true, "容器部署成功：" + order.getWorkloadName(), null);
    }

    private void markFailed(DeploymentOrderDO order, String errorMessage) {
        DeploymentOrderResultContext result = loadResultContext(order);
        if (result.getFailure() == null || result.getFailure().isEmpty()) {
            result.setFailure(buildFailure(resolveFailureReason(errorMessage), errorMessage, null));
        }
        result.setSummary("容器部署失败");
        persistResultContext(order, result);
        order.setDeployStatus(DeploymentOrderStatusEnum.FAILED.getStatus());
        order.setFinishedAt(LocalDateTime.now());
        order.setErrorMessage(errorMessage);
        deploymentOrderMapper.updateById(order);
        updateOrderLogFailed(order, errorMessage);
    }

    private void updateStage(DeploymentOrderDO order, DeploymentOrderStepKeyEnum stage, String summary) {
        order.setCurrentStage(stage.getKey());
        DeploymentOrderResultContext result = loadResultContext(order);
        result.setStage(stage.getKey());
        result.setSummary(summary);
        persistResultContext(order, result);
        PipelineRunLogDO log = pipelineRunLogMapper.selectById(order.getPipelineRunLogId());
        if (log != null) {
            log.setSummary(summary);
            log.setResultJson(JsonUtils.toJsonString(buildNodeResult(order)));
            upsertDeploymentOrderAction(log, order);
            pipelineRunLogMapper.updateById(log);
        }
    }

    private void updateResourceApplyResult(DeploymentOrderDO order, int documentIndex, String operation,
                                           HasMetadata requested, HasMetadata applied, String status,
                                           String message) {
        DeploymentOrderResultContext result = loadResultContext(order);
        if (result.getApplyResults() == null) {
            result.setApplyResults(new ArrayList<>());
        }
        DeploymentOrderResultContext.ResourceApplyResult resourceResult = result.getApplyResults().stream()
                .filter(item -> Objects.equals(item.getDocumentIndex(), documentIndex))
                .findFirst()
                .orElseGet(() -> {
                    DeploymentOrderResultContext.ResourceApplyResult item =
                            new DeploymentOrderResultContext.ResourceApplyResult();
                    item.setDocumentIndex(documentIndex);
                    result.getApplyResults().add(item);
                    return item;
                });
        HasMetadata identitySource = applied == null ? requested : applied;
        resourceResult.setOperation(operation);
        resourceResult.setApiVersion(requested.getApiVersion());
        resourceResult.setKind(requested.getKind());
        resourceResult.setNamespace(identitySource.getMetadata() == null ? order.getNamespace()
                : identitySource.getMetadata().getNamespace());
        resourceResult.setName(identitySource.getMetadata() == null ? null : identitySource.getMetadata().getName());
        resourceResult.setStatus(status);
        resourceResult.setUid(identitySource.getMetadata() == null ? null : identitySource.getMetadata().getUid());
        resourceResult.setResourceVersion(identitySource.getMetadata() == null
                ? null : identitySource.getMetadata().getResourceVersion());
        resourceResult.setGeneration(identitySource.getMetadata() == null
                ? null : identitySource.getMetadata().getGeneration());
        resourceResult.setMessage(message);
        persistResultContext(order, result);
    }

    private DeploymentOrderResultContext loadResultContext(DeploymentOrderDO order) {
        if (StrUtil.isBlank(order.getResultJson())) {
            return new DeploymentOrderResultContext();
        }
        try {
            DeploymentOrderResultContext result = JsonUtils.parseObject(order.getResultJson(),
                    DeploymentOrderResultContext.class);
            if (result == null || !DeploymentOrderResultContext.SCHEMA_VERSION.equals(result.getSchemaVersion())) {
                return new DeploymentOrderResultContext();
            }
            if (result.getApplyResults() == null) {
                result.setApplyResults(new ArrayList<>());
            }
            return result;
        } catch (Exception ignored) {
            return new DeploymentOrderResultContext();
        }
    }

    private void persistResultContext(DeploymentOrderDO order, DeploymentOrderResultContext result) {
        order.setResultJson(JsonUtils.toJsonString(result));
        deploymentOrderMapper.updateById(order);
    }

    @SuppressWarnings("unchecked")
    private void captureFinalSnapshotQuietly(KubernetesClient client, DeploymentOrderDO order, Deployment deployment,
                                             String primaryReason, String primaryMessage) {
        try {
            Map<String, Object> snapshot = deployment == null
                    ? kubernetesDeploymentStatusCollector.collect(client, order)
                    : kubernetesDeploymentStatusCollector.collect(client, order, deployment);
            DeploymentOrderResultContext result = loadResultContext(order);
            result.setRollout(snapshot.get("deployment") instanceof Map<?, ?> value
                    ? new LinkedHashMap<>((Map<String, Object>) value) : new LinkedHashMap<>());
            result.setReplicaSet(snapshot.get("replicaSet") instanceof Map<?, ?> value
                    ? new LinkedHashMap<>((Map<String, Object>) value) : new LinkedHashMap<>());
            result.setPods(snapshot.get("pods") instanceof List<?> value
                    ? (List<Map<String, Object>>) value : new ArrayList<>());
            result.setEvents(snapshot.get("events") instanceof List<?> value
                    ? (List<Map<String, Object>>) value : new ArrayList<>());
            Map<String, Object> diagnosedFailure = snapshot.get("failure") instanceof Map<?, ?> value
                    ? new LinkedHashMap<>((Map<String, Object>) value) : null;
            if (StrUtil.isNotBlank(primaryReason) || StrUtil.isNotBlank(primaryMessage)) {
                result.setFailure(buildFailure(primaryReason, primaryMessage, diagnosedFailure));
            } else {
                result.setFailure(diagnosedFailure);
            }
            result.setCapturedAt(LocalDateTime.now());
            persistResultContext(order, result);
        } catch (Exception ignored) {
            // 最终快照是 best-effort，不能覆盖原始 apply/rollout 异常。
        }
    }

    private Map<String, Object> buildFailure(String reason, String message, Map<String, Object> diagnosis) {
        Map<String, Object> failure = new LinkedHashMap<>();
        failure.put("reason", StrUtil.blankToDefault(reason, "DEPLOYMENT_FAILED"));
        failure.put("message", sanitizeMessage(message));
        if (diagnosis != null && !diagnosis.isEmpty()) {
            failure.put("diagnosis", diagnosis);
        }
        return failure;
    }

    private String resolveFailureReason(Throwable ex) {
        return ex == null ? "DEPLOYMENT_FAILED" : resolveFailureReason(ex.getMessage());
    }

    private String resolveFailureReason(String message) {
        if (StrUtil.containsIgnoreCase(message, "timeout") || StrUtil.containsIgnoreCase(message, "超时")) {
            return "ROLLOUT_TIMEOUT";
        }
        if (StrUtil.containsIgnoreCase(message, "cancel") || StrUtil.containsIgnoreCase(message, "取消")) {
            return "CANCELED";
        }
        if (StrUtil.containsIgnoreCase(message, "apply") || StrUtil.containsIgnoreCase(message, "提交")) {
            return "RESOURCE_APPLY_FAILED";
        }
        return "DEPLOYMENT_FAILED";
    }

    private void updateOrderLogRunning(DeploymentOrderDO order, String summary) {
        PipelineRunLogDO log = pipelineRunLogMapper.selectById(order.getPipelineRunLogId());
        if (log != null) {
            log.setStatus(PipelineRunLogStatusEnum.RUNNING.getStatus());
            log.setStartedAt(log.getStartedAt() == null ? LocalDateTime.now() : log.getStartedAt());
            log.setSummary(summary);
            log.setResultJson(JsonUtils.toJsonString(buildNodeResult(order)));
            upsertDeploymentOrderAction(log, order);
            pipelineRunLogMapper.updateById(log);
        }
    }

    private void updateOrderLogSuccess(DeploymentOrderDO order, String summary) {
        PipelineRunLogDO log = pipelineRunLogMapper.selectById(order.getPipelineRunLogId());
        if (log != null) {
            log.setStatus(PipelineRunLogStatusEnum.SUCCESS.getStatus());
            log.setSummary(summary);
            log.setFinishedAt(LocalDateTime.now());
            log.setErrorMessage(null);
            log.setResultJson(JsonUtils.toJsonString(buildNodeResult(order)));
            upsertDeploymentOrderAction(log, order);
            pipelineRunLogMapper.updateById(log);
        }
    }

    private void updateOrderLogFailed(DeploymentOrderDO order, String errorMessage) {
        PipelineRunLogDO log = pipelineRunLogMapper.selectById(order.getPipelineRunLogId());
        if (log != null) {
            log.setStatus(PipelineRunLogStatusEnum.FAILED.getStatus());
            log.setSummary("容器部署失败");
            log.setFinishedAt(LocalDateTime.now());
            log.setErrorMessage(errorMessage);
            log.setResultJson(JsonUtils.toJsonString(buildNodeResult(order)));
            upsertDeploymentOrderAction(log, order);
            pipelineRunLogMapper.updateById(log);
        }
    }

    private void updateOrderLogCanceled(DeploymentOrderDO order, String summary) {
        PipelineRunLogDO log = pipelineRunLogMapper.selectById(order.getPipelineRunLogId());
        if (log != null) {
            log.setStatus(PipelineRunLogStatusEnum.CANCELED.getStatus());
            log.setSummary(summary);
            log.setFinishedAt(LocalDateTime.now());
            log.setResultJson(JsonUtils.toJsonString(buildNodeResult(order)));
            upsertDeploymentOrderAction(log, order);
            pipelineRunLogMapper.updateById(log);
        }
    }

    private PipelineRunLogDO createOrGetNodeLog(PipelineRunDO run, PipelineSpec.ExecutableStep step,
                                                ContainerDeployConfigContext config) {
        PipelineRunLogDO log = pipelineRunLogMapper.selectByPipelineRunIdAndNodeId(run.getId(), step.getStepId());
        if (log != null) {
            return log;
        }
        log = new PipelineRunLogDO();
        log.setPipelineRunId(run.getId());
        log.setTenantId(run.getTenantId());
        log.setStageId(step.getStageId());
        log.setStageName(step.getStageName());
        log.setJobId(step.getJobId());
        log.setJobName(step.getJobName());
        log.setNodeId(step.getStepId());
        log.setNodeType(step.getStep());
        log.setNodeName(StrUtil.blankToDefault(step.getName(), "容器部署"));
        log.setLogLevel(PipelineRunLogLevelEnum.NODE.getLevel());
        log.setStatus(PipelineRunLogStatusEnum.PENDING.getStatus());
        log.setSort(900);
        log.setAttempt(1);
        log.setRuntimeType("PLATFORM");
        log.setStartedAt(LocalDateTime.now());
        log.setSummary("等待容器部署");
        log.setContextJson(JsonUtils.toJsonString(config));
        pipelineRunLogMapper.insert(log);
        return log;
    }

    private ContainerDeployConfigContext buildConfig(PipelineRunDO run, PipelineSpec.ExecutableStep step,
                                                     ApplicationDO application, EnvironmentDO environment) {
        if (!EnvironmentInfraTypeEnum.K8S.getInfraType().equals(environment.getInfraType())) {
            throw exception(DEPLOYMENT_ENVIRONMENT_NOT_K8S);
        }
        KubernetesEnvironmentConfig infraConfig = parseKubernetesConfig(environment);
        String deployType = resolveDeployType(step);
        if (DEPLOY_TYPE_K8S_IMAGE_UPGRADE.equals(deployType)) {
            return buildImageUpgradeConfig(run, step, application, environment, infraConfig);
        }
        return buildRawManifestConfig(run, step, application, environment, infraConfig);
    }

    private ContainerDeployConfigContext buildRawManifestConfig(PipelineRunDO run, PipelineSpec.ExecutableStep step,
                                                                ApplicationDO application, EnvironmentDO environment,
                                                                KubernetesEnvironmentConfig infraConfig) {
        String deployMode = requiredParam(step, "deployMode");
        if (!DeploymentModeEnum.RAW_MANIFEST.getMode().equals(deployMode)) {
            throw exception(DEPLOYMENT_NODE_PARAM_INVALID, "deployMode 当前仅支持 RAW_MANIFEST");
        }
        String manifestYaml = requiredParam(step, "manifestYaml");
        String containerName = requiredParam(step, "containerName");
        String imageExpression = requiredParam(step, "image");
        String image = resolveImageExpression(imageExpression, run, application, environment);
        ContainerDeployConfigContext config = new ContainerDeployConfigContext();
        config.setInfraType(EnvironmentInfraTypeEnum.K8S.getInfraType());
        config.setDeployMode(deployMode);
        config.setWorkloadKind(WORKLOAD_KIND_DEPLOYMENT);
        config.setNamespace(infraConfig.getNamespace());
        config.setManifestYaml(manifestYaml);
        config.setContainerName(containerName);
        config.setImageExpression(imageExpression);
        config.setImage(image);
        config.setReplicas(integerParam(step, "replicas"));
        config.setRolloutTimeoutSeconds(integerParam(step, "rolloutTimeoutSeconds",
                DEFAULT_ROLLOUT_TIMEOUT_SECONDS));
        String renderedManifestYaml = renderManifestYaml(manifestYaml, run, application, environment, image,
                infraConfig.getNamespace());
        config.setRenderedManifestYaml(renderedManifestYaml);
        List<HasMetadata> resources = kubernetesDeploymentManifestSupport.parseSupportedResources(renderedManifestYaml);
        Deployment deployment = kubernetesDeploymentManifestSupport.findSingleDeployment(resources);
        kubernetesDeploymentManifestSupport.validateDeployment(deployment, containerName);
        config.setWorkloadName(kubernetesDeploymentManifestSupport.readDeploymentName(deployment));
        return config;
    }

    private ContainerDeployConfigContext buildImageUpgradeConfig(PipelineRunDO run, PipelineSpec.ExecutableStep step,
                                                                 ApplicationDO application, EnvironmentDO environment,
                                                                 KubernetesEnvironmentConfig infraConfig) {
        String workloadKind = requiredParam(step, "workloadKind");
        if (!WORKLOAD_KIND_DEPLOYMENT.equalsIgnoreCase(workloadKind)) {
            throw exception(DEPLOYMENT_NODE_PARAM_INVALID, "workloadKind 当前仅支持 Deployment");
        }
        String workloadName = requiredParam(step, "workloadName");
        String containerName = requiredParam(step, "containerName");
        String imageExpression = requiredParam(step, "image");
        String image = resolveImageExpression(imageExpression, run, application, environment);
        ContainerDeployConfigContext config = new ContainerDeployConfigContext();
        config.setInfraType(EnvironmentInfraTypeEnum.K8S.getInfraType());
        config.setDeployMode(DEPLOY_TYPE_K8S_IMAGE_UPGRADE);
        config.setWorkloadKind(WORKLOAD_KIND_DEPLOYMENT);
        config.setNamespace(infraConfig.getNamespace());
        config.setWorkloadName(workloadName);
        config.setContainerName(containerName);
        config.setImageExpression(imageExpression);
        config.setImage(image);
        config.setReplicas(integerParam(step, "replicas"));
        config.setRolloutTimeoutSeconds(integerParam(step, "rolloutTimeoutSeconds",
                DEFAULT_ROLLOUT_TIMEOUT_SECONDS));
        return config;
    }

    private String renderManifestYaml(String manifestYaml, PipelineRunDO run, ApplicationDO application,
                                      EnvironmentDO environment, String image, String namespace) {
        Map<String, String> variables = new LinkedHashMap<>();
        variables.put("IMAGE", StrUtil.blankToDefault(image, ""));
        variables.put("IMAGE_URI", StrUtil.blankToDefault(image, ""));
        variables.put("APP_KEY", StrUtil.blankToDefault(application.getAppKey(), ""));
        variables.put("COMMIT_SHA", StrUtil.blankToDefault(resolveDeployCommitSha(run), ""));
        variables.put("BRANCH_NAME", StrUtil.blankToDefault(run.getBranchName(), ""));
        variables.put("PIPELINE_RUN_ID", String.valueOf(run.getId()));
        variables.put("ENV_KEY", StrUtil.blankToDefault(environment.getEnvKey(), ""));
        variables.put("NAMESPACE", StrUtil.blankToDefault(namespace, ""));
        return kubernetesDeploymentManifestSupport.renderManifest(manifestYaml, variables);
    }

    private KubernetesEnvironmentConfig parseKubernetesConfig(EnvironmentDO environment) {
        KubernetesEnvironmentConfig config = JsonUtils.parseObject(environment.getInfraConfig(), KubernetesEnvironmentConfig.class);
        if (config == null || StrUtil.isBlank(config.getKubeconfig()) || StrUtil.isBlank(config.getNamespace())) {
            throw exception(DEPLOYMENT_ENVIRONMENT_NOT_K8S);
        }
        return config;
    }

    private ContainerDeployConfigContext parseConfig(DeploymentOrderDO order) {
        ContainerDeployConfigContext config = JsonUtils.parseObject(order.getConfigJson(), ContainerDeployConfigContext.class);
        if (config == null) {
            throw exception(DEPLOYMENT_NODE_PARAM_INVALID, "配置快照为空");
        }
        return config;
    }

    private String resolveImageExpression(String expression, PipelineRunDO run, ApplicationDO application,
                                          EnvironmentDO environment) {
        String deployCommitSha = resolveDeployCommitSha(run);
        return expression
                .replace("${APP_KEY}", StrUtil.blankToDefault(application.getAppKey(), ""))
                .replace("${COMMIT_SHA}", StrUtil.blankToDefault(deployCommitSha, ""))
                .replace("${BRANCH_NAME}", StrUtil.blankToDefault(run.getBranchName(), ""))
                .replace("${PIPELINE_RUN_ID}", String.valueOf(run.getId()))
                .replace("${ENV_KEY}", StrUtil.blankToDefault(environment.getEnvKey(), ""));
    }

    private String resolveDeployCommitSha(PipelineRunDO run) {
        PipelineRunLogDO codeMergeLog = pipelineRunLogMapper.selectByPipelineRunIdAndNodeType(
                run.getId(), PipelineNodeRegistryServiceImpl.TYPE_CODE_MERGE);
        Map<String, Object> result = codeMergeLog == null ? null : JsonUtils.parseMap(codeMergeLog.getResultJson());
        if (result != null && result.get("deployCommitSha") != null) {
            return String.valueOf(result.get("deployCommitSha"));
        }
        return run.getCommitSha();
    }

    private void upsertDeploymentOrderAction(PipelineRunLogDO log, DeploymentOrderDO order) {
        pipelineStepActionHelper.upsertActions(log, List.of(buildDeploymentOrderAction(log, order)));
    }

    private PipelineStepAction buildDeploymentOrderAction(PipelineRunLogDO log, DeploymentOrderDO order) {
        Map<String, Object> payload = new LinkedHashMap<>();
        payload.put("runId", order.getPipelineRunId());
        payload.put("stepId", StrUtil.blankToDefault(log.getStepId(), order.getNodeId()));
        payload.put("deploymentOrderId", order.getId());
        payload.put("deployStatus", order.getDeployStatus());
        payload.put("currentStage", order.getCurrentStage());
        payload.put("modal", "DEPLOYMENT_ORDER_DETAIL");
        return PipelineStepAction.builder()
                .key(PipelineStepActionKeyConstants.DEPLOYMENT_ORDER_OPEN_DETAIL)
                .label("查看部署单")
                .type(PipelineStepActionTypeEnum.API.getType())
                .style("PRIMARY")
                .permission("devops:pipeline:query")
                .enabled(true)
                .target(PipelineStepActionTarget.builder()
                        .apiPath("/devops/deployment-order/" + order.getId())
                        .method("GET")
                        .build())
                .payload(payload)
                .order(150)
                .build();
    }

    private Map<String, Object> buildNodeResult(DeploymentOrderDO order) {
        Map<String, Object> result = new LinkedHashMap<>();
        result.put("deploymentOrderId", order.getId());
        result.put("deployStatus", order.getDeployStatus());
        result.put("currentStage", order.getCurrentStage());
        result.put("namespace", order.getNamespace());
        result.put("workloadName", order.getWorkloadName());
        result.put("containerName", order.getContainerName());
        result.put("image", order.getImage());
        result.put("replicas", order.getReplicas());
        return result;
    }

    private DeploymentOrderExecutionResult buildExecutionResult(DeploymentOrderDO order, boolean success,
                                                                String summary, String errorMessage) {
        Map<String, Object> outputs = new LinkedHashMap<>();
        outputs.put("DEPLOYMENT_ORDER_ID", order.getId());
        outputs.put("DEPLOY_STATUS", order.getDeployStatus());
        outputs.put("NAMESPACE", order.getNamespace());
        outputs.put("WORKLOAD_NAME", order.getWorkloadName());
        outputs.put("CONTAINER_NAME", order.getContainerName());
        outputs.put("IMAGE", order.getImage());
        if (order.getTargetRevision() != null) {
            outputs.put("TARGET_REVISION", order.getTargetRevision());
        }
        return DeploymentOrderExecutionResult.builder()
                .deploymentOrderId(order.getId())
                .success(success)
                .summary(summary)
                .errorMessage(errorMessage)
                .outputs(outputs)
                .build();
    }

    private DeploymentOrderRespVO buildRespVO(DeploymentOrderDO order, boolean includeLiveStatus) {
        DeploymentOrderRespVO respVO = new DeploymentOrderRespVO();
        respVO.setId(order.getId());
        respVO.setPipelineRunId(order.getPipelineRunId());
        respVO.setPipelineRunLogId(order.getPipelineRunLogId());
        respVO.setNodeId(order.getNodeId());
        respVO.setNodeType(order.getNodeType());
        respVO.setAppId(order.getAppId());
        respVO.setApplicationEnvId(order.getApplicationEnvId());
        respVO.setEnvironmentId(order.getEnvironmentId());
        respVO.setInfraType(order.getInfraType());
        respVO.setDeployType(order.getDeployType());
        respVO.setDeployStatus(order.getDeployStatus());
        respVO.setAttempt(order.getAttempt());
        respVO.setCurrentStage(order.getCurrentStage());
        respVO.setTriggerType(order.getTriggerType());
        respVO.setTriggerUserId(order.getTriggerUserId());
        respVO.setTriggeredAt(order.getTriggeredAt());
        respVO.setStartedAt(order.getStartedAt());
        respVO.setFinishedAt(order.getFinishedAt());
        respVO.setNamespace(order.getNamespace());
        respVO.setWorkloadKind(order.getWorkloadKind());
        respVO.setWorkloadName(order.getWorkloadName());
        respVO.setContainerName(order.getContainerName());
        respVO.setImage(order.getImage());
        respVO.setReplicas(order.getReplicas());
        respVO.setPreviousImage(order.getPreviousImage());
        respVO.setPreviousReplicas(order.getPreviousReplicas());
        respVO.setPreviousRevision(order.getPreviousRevision());
        respVO.setTargetRevision(order.getTargetRevision());
        respVO.setErrorMessage(order.getErrorMessage());
        respVO.setConfig(JsonUtils.parseMap(order.getConfigJson()));
        respVO.setResult(JsonUtils.parseMap(order.getResultJson()));
        respVO.setLiveStatus(includeLiveStatus ? getLiveStatus(order) : null);
        return respVO;
    }

    private Map<String, Object> getLiveStatus(DeploymentOrderDO order) {
        EnvironmentDO environment = environmentMapper.selectById(order.getEnvironmentId());
        if (environment == null) {
            return Map.of("available", false, "message", "环境不存在");
        }
        try (KubernetesClient client = kubernetesClientFactory.create(parseKubernetesConfig(environment).getKubeconfig())) {
            return kubernetesDeploymentStatusCollector.collect(client, order);
        } catch (Exception ex) {
            return Map.of("available", false, "message", sanitizeMessage(ex.getMessage()));
        }
    }

    private DeploymentOrderDO validateDeploymentOrderExists(Long id) {
        DeploymentOrderDO order = deploymentOrderMapper.selectById(id);
        if (order == null) {
            throw exception(DEPLOYMENT_ORDER_NOT_EXISTS);
        }
        return order;
    }

    private ApplicationEnvDO validateApplicationEnvExists(Long id) {
        ApplicationEnvDO applicationEnv = applicationEnvMapper.selectById(id);
        if (applicationEnv == null) {
            throw exception(APPLICATION_ENV_NOT_EXISTS);
        }
        return applicationEnv;
    }

    private ApplicationDO validateApplicationExists(Long id) {
        ApplicationDO application = applicationMapper.selectById(id);
        if (application == null) {
            throw exception(APPLICATION_NOT_EXISTS);
        }
        return application;
    }

    private EnvironmentDO validateEnvironmentExists(Long id) {
        EnvironmentDO environment = environmentMapper.selectById(id);
        if (environment == null) {
            throw exception(ENVIRONMENT_NOT_EXISTS);
        }
        return environment;
    }

    private String requiredParam(PipelineSpec.ExecutableStep step, String name) {
        Object value = step.getWith() == null ? null : step.getWith().get(name);
        if (value == null || StrUtil.isBlank(String.valueOf(value))) {
            throw exception(DEPLOYMENT_NODE_PARAM_INVALID, name + " 不能为空");
        }
        return String.valueOf(value);
    }

    private Integer integerParam(PipelineSpec.ExecutableStep step, String name) {
        Object value = step.getWith() == null ? null : step.getWith().get(name);
        if (value == null || StrUtil.isBlank(String.valueOf(value))) {
            return null;
        }
        try {
            return Integer.parseInt(String.valueOf(value));
        } catch (NumberFormatException ex) {
            throw exception(DEPLOYMENT_NODE_PARAM_INVALID, name + " 必须是整数");
        }
    }

    private Integer integerParam(PipelineSpec.ExecutableStep step, String name, Integer defaultValue) {
        Integer value = integerParam(step, name);
        return value == null ? defaultValue : value;
    }

    private String resolveDeployType(PipelineSpec.ExecutableStep step) {
        if (PipelineNodeRegistryServiceImpl.TYPE_K8S_IMAGE_UPGRADE.equals(step.getStep())) {
            return DEPLOY_TYPE_K8S_IMAGE_UPGRADE;
        }
        return DEPLOY_TYPE_K8S_DEPLOYMENT;
    }

    private boolean isCanceled(Long orderId) {
        DeploymentOrderDO order = deploymentOrderMapper.selectById(orderId);
        return order != null && DeploymentOrderStatusEnum.CANCELED.getStatus().equals(order.getDeployStatus());
    }

    private String readRevision(Deployment deployment) {
        if (deployment == null || deployment.getMetadata() == null || deployment.getMetadata().getAnnotations() == null) {
            return null;
        }
        return deployment.getMetadata().getAnnotations()
                .get(KubernetesDeploymentStatusCollector.DEPLOYMENT_REVISION_ANNOTATION);
    }

    private String sanitizeMessage(String message) {
        return StrUtil.subPre(StrUtil.blankToDefault(message, ""), 1000);
    }

    private void sleepQuietly() {
        try {
            Thread.sleep(ROLLOUT_POLL_INTERVAL_MILLIS);
        } catch (InterruptedException ex) {
            Thread.currentThread().interrupt();
        }
    }

    private static class DeploymentCanceledException extends RuntimeException {
    }

    public Long getApplicationEnvIdByDeploymentOrderId(Long deploymentOrderId) {
        DeploymentOrderDO order = deploymentOrderMapper.selectById(deploymentOrderId);
        return order == null ? null : order.getApplicationEnvId();
    }

}
