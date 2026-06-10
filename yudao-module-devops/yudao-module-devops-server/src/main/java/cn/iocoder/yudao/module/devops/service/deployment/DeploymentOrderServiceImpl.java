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
import cn.iocoder.yudao.module.devops.dal.mysql.pipeline.PipelineRunMapper;
import cn.iocoder.yudao.module.devops.dal.mysql.pipeline.log.PipelineRunLogMapper;
import cn.iocoder.yudao.module.devops.enums.DeploymentModeEnum;
import cn.iocoder.yudao.module.devops.enums.DeploymentOrderStatusEnum;
import cn.iocoder.yudao.module.devops.enums.DeploymentOrderStepKeyEnum;
import cn.iocoder.yudao.module.devops.enums.EnvironmentInfraTypeEnum;
import cn.iocoder.yudao.module.devops.enums.PipelineNodeTypeEnum;
import cn.iocoder.yudao.module.devops.enums.PipelineRunLogLevelEnum;
import cn.iocoder.yudao.module.devops.enums.PipelineRunLogStatusEnum;
import cn.iocoder.yudao.module.devops.enums.PipelineRunStatusEnum;
import cn.iocoder.yudao.module.devops.framework.kubernetes.KubernetesClientFactory;
import cn.iocoder.yudao.module.devops.framework.kubernetes.KubernetesDeploymentManifestSupport;
import cn.iocoder.yudao.module.devops.framework.kubernetes.KubernetesEnvironmentConfig;
import cn.iocoder.yudao.module.devops.framework.pipeline.PipelineSpec;
import cn.iocoder.yudao.module.devops.service.deployment.context.ContainerDeployConfigContext;
import io.fabric8.kubernetes.api.model.Container;
import io.fabric8.kubernetes.api.model.ContainerStatus;
import io.fabric8.kubernetes.api.model.Pod;
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
import java.util.Collections;
import java.util.Comparator;
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
    private static final String WORKLOAD_KIND_DEPLOYMENT = "DEPLOYMENT";
    private static final int DEFAULT_ROLLOUT_TIMEOUT_SECONDS = 300;
    private static final long ROLLOUT_POLL_INTERVAL_MILLIS = 2000L;
    private static final String DEPLOYMENT_REVISION_ANNOTATION = "deployment.kubernetes.io/revision";

    @Resource
    private DeploymentOrderMapper deploymentOrderMapper;
    @Resource
    private PipelineRunMapper pipelineRunMapper;
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
        updateRunAndLogCanceled(order, "部署已取消");
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
        deploymentOrderMapper.updateById(order);
        updateRunAndLogRunning(order, "正在重试容器部署");
        executeDeployment(order);
    }

    @Override
    @CacheEvict(value = RedisKeyConstants.APPLICATION_RELEASE_CURRENT_RUN, key = "#run.applicationEnvId")
    public void startContainerDeploy(PipelineRunDO run, PipelineSpec.Node node, Long userId) {
        DeploymentOrderDO existing = deploymentOrderMapper.selectByPipelineRunIdAndNodeId(run.getId(), node.getId());
        if (existing != null && DeploymentOrderStatusEnum.SUCCESS.getStatus().equals(existing.getDeployStatus())) {
            return;
        }
        if (existing != null && DeploymentOrderStatusEnum.RUNNING.getStatus().equals(existing.getDeployStatus())) {
            return;
        }
        DeploymentOrderDO order = existing == null ? createDeploymentOrder(run, node, userId)
                : prepareExistingOrderForRetry(existing);
        updateRunAndLogRunning(order, "开始容器部署");
        executeDeployment(order);
    }

    private DeploymentOrderDO createDeploymentOrder(PipelineRunDO run, PipelineSpec.Node node, Long userId) {
        ApplicationEnvDO applicationEnv = validateApplicationEnvExists(run.getApplicationEnvId());
        ApplicationDO application = validateApplicationExists(run.getAppId());
        EnvironmentDO environment = validateEnvironmentExists(applicationEnv.getEnvId());
        ContainerDeployConfigContext config = buildConfig(run, node, application, environment);

        PipelineRunLogDO log = createOrGetNodeLog(run, node, config);
        DeploymentOrderDO order = new DeploymentOrderDO();
        order.setTenantId(run.getTenantId());
        order.setPipelineRunId(run.getId());
        order.setPipelineRunLogId(log.getId());
        order.setNodeId(node.getId());
        order.setNodeType(PipelineNodeTypeEnum.CONTAINER_DEPLOY.getType());
        order.setDefinitionId(run.getDefinitionId());
        order.setDefinitionVersionId(run.getDefinitionVersionId());
        order.setAppId(run.getAppId());
        order.setApplicationEnvId(run.getApplicationEnvId());
        order.setEnvironmentId(environment.getId());
        order.setInfraType(EnvironmentInfraTypeEnum.K8S.getInfraType());
        order.setDeployType(DEPLOY_TYPE_K8S_DEPLOYMENT);
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
        deploymentOrderMapper.updateById(order);
        return order;
    }

    private void executeDeployment(DeploymentOrderDO order) {
        try {
            order.setDeployStatus(DeploymentOrderStatusEnum.RUNNING.getStatus());
            order.setStartedAt(order.getStartedAt() == null ? LocalDateTime.now() : order.getStartedAt());
            deploymentOrderMapper.updateById(order);
            ContainerDeployConfigContext config = parseConfig(order);
            EnvironmentDO environment = validateEnvironmentExists(order.getEnvironmentId());
            KubernetesEnvironmentConfig infraConfig = parseKubernetesConfig(environment);
            try (KubernetesClient client = kubernetesClientFactory.create(infraConfig.getKubeconfig())) {
                updateStage(order, DeploymentOrderStepKeyEnum.CONNECT_CLUSTER, "已连接 Kubernetes 集群");
                Deployment previousDeployment = loadExistingDeployment(client, order);
                fillPreviousSnapshot(order, previousDeployment);
                Deployment deployment = kubernetesDeploymentManifestSupport.prepareDeployment(config);
                Deployment appliedDeployment = applyDeploymentManifest(client, order, deployment);
                Deployment readyDeployment = waitRolloutReady(client, order, config, appliedDeployment);
                markSuccess(order, readyDeployment);
            }
        } catch (DeploymentCanceledException ex) {
            // Cancellation has already persisted CANCELED state from cancelDeploymentOrder.
        } catch (Exception ex) {
            markFailed(order, sanitizeMessage(ex.getMessage()));
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

    private Deployment applyDeploymentManifest(KubernetesClient client, DeploymentOrderDO order, Deployment deployment) {
        updateStage(order, DeploymentOrderStepKeyEnum.APPLY_SPEC, "正在提交 Deployment YAML：" + order.getWorkloadName());
        try {
            if (deployment.getMetadata().getAnnotations() == null) {
                deployment.getMetadata().setAnnotations(new LinkedHashMap<>());
            }
            deployment.getMetadata().getAnnotations().put("gone.devops/pipeline-run-id", String.valueOf(order.getPipelineRunId()));
            deployment.getMetadata().getAnnotations().put("gone.devops/deployment-order-id", String.valueOf(order.getId()));
            deployment.getMetadata().getAnnotations().put("kubernetes.io/change-cause",
                    "gone devops deploy " + order.getId() + " image " + order.getImage());
            Deployment applied = client.apps().deployments().inNamespace(order.getNamespace()).resource(deployment)
                    .createOrReplace();
            if (applied != null && applied.getMetadata() != null) {
                order.setWorkloadGeneration(applied.getMetadata().getGeneration());
                order.setWorkloadUid(applied.getMetadata().getUid());
                deploymentOrderMapper.updateById(order);
            }
            return applied;
        } catch (KubernetesClientException ex) {
            throw exception(DEPLOYMENT_KUBERNETES_APPLY_FAIL, sanitizeMessage(ex.getMessage()));
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
        order.setResultJson(JsonUtils.toJsonString(buildRolloutSummary(last)));
        deploymentOrderMapper.updateById(order);
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

    private void markSuccess(DeploymentOrderDO order, Deployment deployment) {
        order.setDeployStatus(DeploymentOrderStatusEnum.SUCCESS.getStatus());
        order.setCurrentStage(DeploymentOrderStepKeyEnum.CAPTURE_RESULT.getKey());
        order.setTargetRevision(readRevision(deployment));
        order.setResultJson(JsonUtils.toJsonString(buildRolloutSummary(deployment)));
        order.setFinishedAt(LocalDateTime.now());
        order.setErrorMessage(null);
        deploymentOrderMapper.updateById(order);
        updateRunAndLogSuccess(order, "容器部署成功：" + order.getWorkloadName());
    }

    private void markFailed(DeploymentOrderDO order, String errorMessage) {
        order.setDeployStatus(DeploymentOrderStatusEnum.FAILED.getStatus());
        order.setFinishedAt(LocalDateTime.now());
        order.setErrorMessage(errorMessage);
        deploymentOrderMapper.updateById(order);
        updateRunAndLogFailed(order, errorMessage);
    }

    private void updateStage(DeploymentOrderDO order, DeploymentOrderStepKeyEnum stage, String summary) {
        order.setCurrentStage(stage.getKey());
        order.setResultJson(JsonUtils.toJsonString(Map.of("stage", stage.getKey(), "summary", summary)));
        deploymentOrderMapper.updateById(order);
        PipelineRunLogDO log = pipelineRunLogMapper.selectById(order.getPipelineRunLogId());
        if (log != null) {
            log.setSummary(summary);
            log.setResultJson(JsonUtils.toJsonString(buildNodeResult(order)));
            pipelineRunLogMapper.updateById(log);
        }
    }

    private void updateRunAndLogRunning(DeploymentOrderDO order, String summary) {
        PipelineRunLogDO log = pipelineRunLogMapper.selectById(order.getPipelineRunLogId());
        if (log != null) {
            log.setStatus(PipelineRunLogStatusEnum.RUNNING.getStatus());
            log.setStartedAt(log.getStartedAt() == null ? LocalDateTime.now() : log.getStartedAt());
            log.setSummary(summary);
            log.setResultJson(JsonUtils.toJsonString(buildNodeResult(order)));
            pipelineRunLogMapper.updateById(log);
        }
        PipelineRunDO run = pipelineRunMapper.selectById(order.getPipelineRunId());
        if (run != null) {
            run.setRunStatus(PipelineRunStatusEnum.RUNNING.getStatus());
            run.setStartedAt(run.getStartedAt() == null ? LocalDateTime.now() : run.getStartedAt());
            run.setFinishedAt(null);
            run.setErrorMessage(null);
            pipelineRunMapper.updateById(run);
        }
    }

    private void updateRunAndLogSuccess(DeploymentOrderDO order, String summary) {
        PipelineRunLogDO log = pipelineRunLogMapper.selectById(order.getPipelineRunLogId());
        if (log != null) {
            log.setStatus(PipelineRunLogStatusEnum.SUCCESS.getStatus());
            log.setSummary(summary);
            log.setFinishedAt(LocalDateTime.now());
            log.setErrorMessage(null);
            log.setResultJson(JsonUtils.toJsonString(buildNodeResult(order)));
            pipelineRunLogMapper.updateById(log);
        }
        PipelineRunDO run = pipelineRunMapper.selectById(order.getPipelineRunId());
        if (run != null) {
            run.setRunStatus(PipelineRunStatusEnum.SUCCESS.getStatus());
            run.setFinishedAt(LocalDateTime.now());
            run.setErrorMessage(null);
            pipelineRunMapper.updateById(run);
        }
    }

    private void updateRunAndLogFailed(DeploymentOrderDO order, String errorMessage) {
        PipelineRunLogDO log = pipelineRunLogMapper.selectById(order.getPipelineRunLogId());
        if (log != null) {
            log.setStatus(PipelineRunLogStatusEnum.FAILED.getStatus());
            log.setSummary("容器部署失败");
            log.setFinishedAt(LocalDateTime.now());
            log.setErrorMessage(errorMessage);
            log.setResultJson(JsonUtils.toJsonString(buildNodeResult(order)));
            pipelineRunLogMapper.updateById(log);
        }
        PipelineRunDO run = pipelineRunMapper.selectById(order.getPipelineRunId());
        if (run != null) {
            run.setRunStatus(PipelineRunStatusEnum.FAILED.getStatus());
            run.setFinishedAt(LocalDateTime.now());
            run.setErrorMessage(errorMessage);
            pipelineRunMapper.updateById(run);
        }
    }

    private void updateRunAndLogCanceled(DeploymentOrderDO order, String summary) {
        PipelineRunLogDO log = pipelineRunLogMapper.selectById(order.getPipelineRunLogId());
        if (log != null) {
            log.setStatus(PipelineRunLogStatusEnum.CANCELED.getStatus());
            log.setSummary(summary);
            log.setFinishedAt(LocalDateTime.now());
            log.setResultJson(JsonUtils.toJsonString(buildNodeResult(order)));
            pipelineRunLogMapper.updateById(log);
        }
        PipelineRunDO run = pipelineRunMapper.selectById(order.getPipelineRunId());
        if (run != null) {
            run.setRunStatus(PipelineRunStatusEnum.CANCELED.getStatus());
            run.setFinishedAt(LocalDateTime.now());
            pipelineRunMapper.updateById(run);
        }
    }

    private PipelineRunLogDO createOrGetNodeLog(PipelineRunDO run, PipelineSpec.Node node,
                                                ContainerDeployConfigContext config) {
        PipelineRunLogDO log = pipelineRunLogMapper.selectByPipelineRunIdAndNodeId(run.getId(), node.getId());
        if (log != null) {
            return log;
        }
        log = new PipelineRunLogDO();
        log.setPipelineRunId(run.getId());
        log.setTenantId(run.getTenantId());
        log.setNodeId(node.getId());
        log.setNodeType(PipelineNodeTypeEnum.CONTAINER_DEPLOY.getType());
        log.setNodeName(StrUtil.blankToDefault(node.getName(), "容器部署"));
        log.setLogLevel(PipelineRunLogLevelEnum.NODE.getLevel());
        log.setStatus(PipelineRunLogStatusEnum.PENDING.getStatus());
        log.setSort(900);
        log.setStartedAt(LocalDateTime.now());
        log.setSummary("等待容器部署");
        log.setContextJson(JsonUtils.toJsonString(config));
        pipelineRunLogMapper.insert(log);
        return log;
    }

    private ContainerDeployConfigContext buildConfig(PipelineRunDO run, PipelineSpec.Node node,
                                                     ApplicationDO application, EnvironmentDO environment) {
        if (!EnvironmentInfraTypeEnum.K8S.getInfraType().equals(environment.getInfraType())) {
            throw exception(DEPLOYMENT_ENVIRONMENT_NOT_K8S);
        }
        KubernetesEnvironmentConfig infraConfig = parseKubernetesConfig(environment);
        String deployMode = requiredParam(node, "deployMode");
        if (!DeploymentModeEnum.RAW_MANIFEST.getMode().equals(deployMode)) {
            throw exception(DEPLOYMENT_NODE_PARAM_INVALID, "deployMode 当前仅支持 RAW_MANIFEST");
        }
        String manifestYaml = requiredParam(node, "manifestYaml");
        String containerName = requiredParam(node, "containerName");
        String imageExpression = requiredParam(node, "image");
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
        config.setReplicas(integerParam(node, "replicas"));
        config.setRolloutTimeoutSeconds(integerParam(node, "rolloutTimeoutSeconds",
                DEFAULT_ROLLOUT_TIMEOUT_SECONDS));
        String renderedManifestYaml = renderManifestYaml(manifestYaml, run, application, environment, image,
                infraConfig.getNamespace());
        config.setRenderedManifestYaml(renderedManifestYaml);
        Deployment deployment = kubernetesDeploymentManifestSupport.parseDeployment(renderedManifestYaml);
        kubernetesDeploymentManifestSupport.validateDeployment(deployment, containerName);
        config.setWorkloadName(kubernetesDeploymentManifestSupport.readDeploymentName(deployment));
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
                run.getId(), PipelineNodeTypeEnum.CODE_MERGE.getType());
        Map<String, Object> result = codeMergeLog == null ? null : JsonUtils.parseMap(codeMergeLog.getResultJson());
        if (result != null && result.get("deployCommitSha") != null) {
            return String.valueOf(result.get("deployCommitSha"));
        }
        return run.getCommitSha();
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

    private Map<String, Object> buildRolloutSummary(Deployment deployment) {
        Map<String, Object> summary = new LinkedHashMap<>();
        if (deployment == null || deployment.getStatus() == null) {
            return summary;
        }
        summary.put("observedGeneration", deployment.getStatus().getObservedGeneration());
        summary.put("updatedReplicas", deployment.getStatus().getUpdatedReplicas());
        summary.put("availableReplicas", deployment.getStatus().getAvailableReplicas());
        summary.put("readyReplicas", deployment.getStatus().getReadyReplicas());
        summary.put("conditions", deployment.getStatus().getConditions());
        summary.put("revision", readRevision(deployment));
        return summary;
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
            Deployment deployment = client.apps().deployments().inNamespace(order.getNamespace())
                    .withName(order.getWorkloadName()).get();
            Map<String, Object> status = new LinkedHashMap<>(buildRolloutSummary(deployment));
            status.put("available", deployment != null);
            status.put("ready", deployment != null && isRolloutReady(deployment));
            status.put("pods", deployment == null ? Collections.emptyList() : buildPodSummaries(client, order, deployment));
            return status;
        } catch (Exception ex) {
            return Map.of("available", false, "message", sanitizeMessage(ex.getMessage()));
        }
    }

    private List<Map<String, Object>> buildPodSummaries(KubernetesClient client, DeploymentOrderDO order,
                                                        Deployment deployment) {
        Map<String, String> matchLabels = deployment.getSpec() == null
                || deployment.getSpec().getSelector() == null ? null : deployment.getSpec().getSelector().getMatchLabels();
        if (matchLabels == null || matchLabels.isEmpty()) {
            return Collections.emptyList();
        }
        List<Pod> pods = client.pods().inNamespace(order.getNamespace()).withLabels(matchLabels).list().getItems();
        if (pods == null || pods.isEmpty()) {
            return Collections.emptyList();
        }
        return pods.stream()
                .sorted(Comparator.comparing(pod -> pod.getMetadata() == null ? "" : pod.getMetadata().getName()))
                .map(pod -> buildPodSummary(order, pod))
                .toList();
    }

    private Map<String, Object> buildPodSummary(DeploymentOrderDO order, Pod pod) {
        Map<String, Object> summary = new LinkedHashMap<>();
        summary.put("name", pod.getMetadata() == null ? null : pod.getMetadata().getName());
        summary.put("namespace", pod.getMetadata() == null ? null : pod.getMetadata().getNamespace());
        summary.put("creationTimestamp", pod.getMetadata() == null ? null : pod.getMetadata().getCreationTimestamp());
        summary.put("phase", pod.getStatus() == null ? null : pod.getStatus().getPhase());
        summary.put("ready", isPodReady(pod));
        summary.put("readyContainers", countReadyContainers(pod));
        summary.put("totalContainers", countTotalContainers(pod));
        summary.put("restartCount", countRestartCount(pod));
        summary.put("podIp", pod.getStatus() == null ? null : pod.getStatus().getPodIP());
        summary.put("hostIp", pod.getStatus() == null ? null : pod.getStatus().getHostIP());
        summary.put("nodeName", pod.getSpec() == null ? null : pod.getSpec().getNodeName());
        summary.put("startTime", pod.getStatus() == null ? null : pod.getStatus().getStartTime());
        summary.put("targetContainer", buildTargetContainerSummary(order, pod));
        summary.put("containers", buildContainerSummaries(pod));
        return summary;
    }

    private boolean isPodReady(Pod pod) {
        if (pod.getStatus() == null || pod.getStatus().getConditions() == null) {
            return false;
        }
        return pod.getStatus().getConditions().stream()
                .anyMatch(condition -> "Ready".equals(condition.getType()) && "True".equals(condition.getStatus()));
    }

    private int countReadyContainers(Pod pod) {
        if (pod.getStatus() == null || pod.getStatus().getContainerStatuses() == null) {
            return 0;
        }
        return (int) pod.getStatus().getContainerStatuses().stream()
                .filter(status -> Boolean.TRUE.equals(status.getReady()))
                .count();
    }

    private int countTotalContainers(Pod pod) {
        if (pod.getSpec() != null && pod.getSpec().getContainers() != null) {
            return pod.getSpec().getContainers().size();
        }
        if (pod.getStatus() != null && pod.getStatus().getContainerStatuses() != null) {
            return pod.getStatus().getContainerStatuses().size();
        }
        return 0;
    }

    private int countRestartCount(Pod pod) {
        if (pod.getStatus() == null || pod.getStatus().getContainerStatuses() == null) {
            return 0;
        }
        return pod.getStatus().getContainerStatuses().stream()
                .map(ContainerStatus::getRestartCount)
                .filter(Objects::nonNull)
                .mapToInt(Integer::intValue)
                .sum();
    }

    private Map<String, Object> buildTargetContainerSummary(DeploymentOrderDO order, Pod pod) {
        if (pod.getStatus() == null || pod.getStatus().getContainerStatuses() == null) {
            return Collections.emptyMap();
        }
        return pod.getStatus().getContainerStatuses().stream()
                .filter(status -> order.getContainerName().equals(status.getName()))
                .findFirst()
                .map(this::buildContainerSummary)
                .orElse(Collections.emptyMap());
    }

    private List<Map<String, Object>> buildContainerSummaries(Pod pod) {
        if (pod.getStatus() == null || pod.getStatus().getContainerStatuses() == null) {
            return Collections.emptyList();
        }
        return pod.getStatus().getContainerStatuses().stream()
                .map(this::buildContainerSummary)
                .toList();
    }

    private Map<String, Object> buildContainerSummary(ContainerStatus status) {
        Map<String, Object> summary = new LinkedHashMap<>();
        summary.put("name", status.getName());
        summary.put("image", status.getImage());
        summary.put("imageId", status.getImageID());
        summary.put("ready", status.getReady());
        summary.put("restartCount", status.getRestartCount());
        summary.put("state", resolveContainerState(status));
        summary.put("reason", resolveContainerStateReason(status));
        return summary;
    }

    private String resolveContainerState(ContainerStatus status) {
        if (status.getState() == null) {
            return null;
        }
        if (status.getState().getRunning() != null) {
            return "RUNNING";
        }
        if (status.getState().getWaiting() != null) {
            return "WAITING";
        }
        if (status.getState().getTerminated() != null) {
            return "TERMINATED";
        }
        return null;
    }

    private String resolveContainerStateReason(ContainerStatus status) {
        if (status.getState() == null) {
            return null;
        }
        if (status.getState().getWaiting() != null) {
            return status.getState().getWaiting().getReason();
        }
        if (status.getState().getTerminated() != null) {
            return status.getState().getTerminated().getReason();
        }
        return null;
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

    private String requiredParam(PipelineSpec.Node node, String name) {
        Object value = node.getParams() == null ? null : node.getParams().get(name);
        if (value == null || StrUtil.isBlank(String.valueOf(value))) {
            throw exception(DEPLOYMENT_NODE_PARAM_INVALID, name + " 不能为空");
        }
        return String.valueOf(value);
    }

    private Integer integerParam(PipelineSpec.Node node, String name) {
        Object value = node.getParams() == null ? null : node.getParams().get(name);
        if (value == null || StrUtil.isBlank(String.valueOf(value))) {
            return null;
        }
        try {
            return Integer.parseInt(String.valueOf(value));
        } catch (NumberFormatException ex) {
            throw exception(DEPLOYMENT_NODE_PARAM_INVALID, name + " 必须是整数");
        }
    }

    private Integer integerParam(PipelineSpec.Node node, String name, Integer defaultValue) {
        Integer value = integerParam(node, name);
        return value == null ? defaultValue : value;
    }

    private boolean isCanceled(Long orderId) {
        DeploymentOrderDO order = deploymentOrderMapper.selectById(orderId);
        return order != null && DeploymentOrderStatusEnum.CANCELED.getStatus().equals(order.getDeployStatus());
    }

    private String readRevision(Deployment deployment) {
        if (deployment == null || deployment.getMetadata() == null || deployment.getMetadata().getAnnotations() == null) {
            return null;
        }
        return deployment.getMetadata().getAnnotations().get(DEPLOYMENT_REVISION_ANNOTATION);
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
