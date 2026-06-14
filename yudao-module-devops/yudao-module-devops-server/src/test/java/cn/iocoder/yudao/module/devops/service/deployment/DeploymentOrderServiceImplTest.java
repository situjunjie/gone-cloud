package cn.iocoder.yudao.module.devops.service.deployment;

import cn.iocoder.yudao.framework.common.util.json.JsonUtils;
import cn.iocoder.yudao.framework.test.core.ut.BaseMockitoUnitTest;
import cn.iocoder.yudao.module.devops.controller.admin.deployment.vo.DeploymentOrderRespVO;
import cn.iocoder.yudao.module.devops.dal.dataobject.application.ApplicationDO;
import cn.iocoder.yudao.module.devops.dal.dataobject.application.ApplicationEnvDO;
import cn.iocoder.yudao.module.devops.dal.dataobject.deployment.DeploymentOrderDO;
import cn.iocoder.yudao.module.devops.dal.dataobject.environment.EnvironmentDO;
import cn.iocoder.yudao.module.devops.dal.dataobject.pipeline.PipelineRunDO;
import cn.iocoder.yudao.module.devops.dal.dataobject.pipeline.log.PipelineRunLogDO;
import cn.iocoder.yudao.module.devops.dal.mysql.application.ApplicationEnvMapper;
import cn.iocoder.yudao.module.devops.dal.mysql.application.ApplicationMapper;
import cn.iocoder.yudao.module.devops.dal.mysql.deployment.DeploymentOrderMapper;
import cn.iocoder.yudao.module.devops.dal.mysql.environment.EnvironmentMapper;
import cn.iocoder.yudao.module.devops.dal.mysql.pipeline.PipelineRunMapper;
import cn.iocoder.yudao.module.devops.dal.mysql.pipeline.log.PipelineRunLogMapper;
import cn.iocoder.yudao.module.devops.enums.DeploymentOrderStatusEnum;
import cn.iocoder.yudao.module.devops.enums.DeploymentOrderStepKeyEnum;
import cn.iocoder.yudao.module.devops.enums.DeploymentModeEnum;
import cn.iocoder.yudao.module.devops.enums.EnvironmentInfraTypeEnum;
import cn.iocoder.yudao.module.devops.enums.PipelineRunLogStatusEnum;
import cn.iocoder.yudao.module.devops.enums.PipelineRunStatusEnum;
import cn.iocoder.yudao.module.devops.framework.kubernetes.KubernetesClientFactory;
import cn.iocoder.yudao.module.devops.framework.kubernetes.KubernetesDeploymentManifestSupport;
import cn.iocoder.yudao.module.devops.framework.kubernetes.KubernetesEnvironmentConfig;
import cn.iocoder.yudao.module.devops.framework.pipeline.PipelineSpec;
import cn.iocoder.yudao.module.devops.service.deployment.context.ContainerDeployConfigContext;
import cn.iocoder.yudao.module.devops.service.pipeline.PipelineNodeRegistryServiceImpl;
import io.fabric8.kubernetes.api.model.ContainerStateBuilder;
import io.fabric8.kubernetes.api.model.PodBuilder;
import io.fabric8.kubernetes.api.model.PodListBuilder;
import io.fabric8.kubernetes.api.model.apps.DeploymentBuilder;
import io.fabric8.kubernetes.api.model.apps.Deployment;
import io.fabric8.kubernetes.api.model.apps.DeploymentList;
import io.fabric8.kubernetes.client.KubernetesClient;
import io.fabric8.kubernetes.client.dsl.AppsAPIGroupDSL;
import io.fabric8.kubernetes.client.dsl.MixedOperation;
import io.fabric8.kubernetes.client.dsl.NonNamespaceOperation;
import io.fabric8.kubernetes.client.dsl.PodResource;
import io.fabric8.kubernetes.client.dsl.RollableScalableResource;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.Spy;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import static cn.iocoder.yudao.framework.test.core.util.AssertUtils.assertServiceException;
import static cn.iocoder.yudao.module.devops.enums.ErrorCodeConstants.DEPLOYMENT_ORDER_STATE_INVALID;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * {@link DeploymentOrderServiceImpl} 的单元测试。
 */
public class DeploymentOrderServiceImplTest extends BaseMockitoUnitTest {

    private static final String LEGACY_CONTAINER_DEPLOY_NODE_TYPE = "CONTAINER_DEPLOY";

    @InjectMocks
    private DeploymentOrderServiceImpl deploymentOrderService;

    @Mock
    private DeploymentOrderMapper deploymentOrderMapper;
    @Mock
    private PipelineRunMapper pipelineRunMapper;
    @Mock
    private PipelineRunLogMapper pipelineRunLogMapper;
    @Mock
    private ApplicationMapper applicationMapper;
    @Mock
    private ApplicationEnvMapper applicationEnvMapper;
    @Mock
    private EnvironmentMapper environmentMapper;
    @Mock
    private KubernetesClientFactory kubernetesClientFactory;
    @Spy
    private KubernetesDeploymentManifestSupport kubernetesDeploymentManifestSupport;

    @Test
    public void testCancelDeploymentOrder_success() {
        // 准备参数
        DeploymentOrderDO order = buildOrder(DeploymentOrderStatusEnum.RUNNING.getStatus());
        PipelineRunLogDO log = buildDeployLog();
        PipelineRunDO run = buildRun();
        when(deploymentOrderMapper.selectById(eq(100L))).thenReturn(order);
        when(pipelineRunLogMapper.selectById(eq(900L))).thenReturn(log);
        when(pipelineRunMapper.selectById(eq(800L))).thenReturn(run);

        // 调用
        deploymentOrderService.cancelDeploymentOrder(100L, 7L);

        // 断言
        verify(deploymentOrderMapper).updateById(order);
        assertEquals(DeploymentOrderStatusEnum.CANCELED.getStatus(), order.getDeployStatus());
        assertNotNull(order.getFinishedAt());
        assertEquals(PipelineRunLogStatusEnum.CANCELED.getStatus(), log.getStatus());
        assertEquals("部署已取消", log.getSummary());
        assertNotNull(log.getFinishedAt());
        assertEquals(PipelineRunStatusEnum.CANCELED.getStatus(), run.getRunStatus());
        assertNotNull(run.getFinishedAt());
    }

    @Test
    public void testCancelDeploymentOrder_invalidStatus() {
        // 准备参数
        when(deploymentOrderMapper.selectById(eq(100L))).thenReturn(
                buildOrder(DeploymentOrderStatusEnum.SUCCESS.getStatus()));

        // 调用 & 断言
        assertServiceException(() -> deploymentOrderService.cancelDeploymentOrder(100L, 7L),
                DEPLOYMENT_ORDER_STATE_INVALID);
    }

    @Test
    public void testStartContainerDeploy_createOrderAndMarkFailedWhenClientCreateFails() {
        // 准备参数
        PipelineRunDO run = buildRun();
        PipelineSpec.Node node = buildDeployNode();
        ApplicationDO application = buildApplication();
        ApplicationEnvDO applicationEnv = buildApplicationEnv();
        EnvironmentDO environment = buildKubernetesEnvironment();
        PipelineRunLogDO deployLog = buildDeployLog();
        PipelineRunLogDO codeMergeLog = new PipelineRunLogDO();
        codeMergeLog.setResultJson("{\"deployCommitSha\":\"merge-sha\"}");
        when(deploymentOrderMapper.selectByPipelineRunIdAndNodeId(eq(800L), eq("deploy"))).thenReturn(null);
        when(applicationEnvMapper.selectById(eq(200L))).thenReturn(applicationEnv);
        when(applicationMapper.selectById(eq(10L))).thenReturn(application);
        when(environmentMapper.selectById(eq(300L))).thenReturn(environment);
        when(pipelineRunLogMapper.selectByPipelineRunIdAndNodeId(eq(800L), eq("deploy"))).thenReturn(null);
        when(pipelineRunLogMapper.selectByPipelineRunIdAndNodeType(eq(800L), eq(PipelineNodeRegistryServiceImpl.TYPE_CODE_MERGE)))
                .thenReturn(codeMergeLog);
        doAnswer(invocation -> {
            PipelineRunLogDO log = invocation.getArgument(0);
            log.setId(900L);
            return 1;
        }).when(pipelineRunLogMapper).insert(any(PipelineRunLogDO.class));
        doAnswer(invocation -> {
            DeploymentOrderDO order = invocation.getArgument(0);
            order.setId(100L);
            return 1;
        }).when(deploymentOrderMapper).insert(any(DeploymentOrderDO.class));
        when(pipelineRunLogMapper.selectById(eq(900L))).thenReturn(deployLog);
        when(pipelineRunMapper.selectById(eq(800L))).thenReturn(run);
        when(kubernetesClientFactory.create(eq("kubeconfig"))).thenThrow(new RuntimeException("cluster unavailable"));

        // 调用
        deploymentOrderService.startContainerDeploy(run, node, 7L);

        // 断言
        ArgumentCaptor<DeploymentOrderDO> orderCaptor = ArgumentCaptor.forClass(DeploymentOrderDO.class);
        verify(deploymentOrderMapper).insert(orderCaptor.capture());
        DeploymentOrderDO order = orderCaptor.getValue();
        assertEquals(100L, order.getId());
        assertEquals(1L, order.getTenantId());
        assertEquals(800L, order.getPipelineRunId());
        assertEquals(900L, order.getPipelineRunLogId());
        assertEquals("deploy", order.getNodeId());
        assertEquals(LEGACY_CONTAINER_DEPLOY_NODE_TYPE, order.getNodeType());
        assertEquals(200L, order.getApplicationEnvId());
        assertEquals(300L, order.getEnvironmentId());
        assertEquals(EnvironmentInfraTypeEnum.K8S.getInfraType(), order.getInfraType());
        assertEquals("prod", order.getNamespace());
        assertEquals("gone-api", order.getWorkloadName());
        assertEquals("app", order.getContainerName());
        assertEquals("registry.example.com/gone-api:merge-sha-prod-800-release/prod", order.getImage());
        assertEquals(3, order.getReplicas());
        assertEquals(DeploymentOrderStatusEnum.FAILED.getStatus(), order.getDeployStatus());
        assertEquals("cluster unavailable", order.getErrorMessage());

        ContainerDeployConfigContext config = JsonUtils.parseObject(order.getConfigJson(), ContainerDeployConfigContext.class);
        assertEquals("prod", config.getNamespace());
        assertEquals(DeploymentModeEnum.RAW_MANIFEST.getMode(), config.getDeployMode());
        assertEquals("registry.example.com/gone-api:${COMMIT_SHA}-${ENV_KEY}-${PIPELINE_RUN_ID}-${BRANCH_NAME}",
                config.getImageExpression());
        assertEquals(order.getImage(), config.getImage());
        assertEquals(120, config.getRolloutTimeoutSeconds());
        assertNotNull(config.getManifestYaml());
        assertNotNull(config.getRenderedManifestYaml());

        assertEquals(PipelineRunLogStatusEnum.FAILED.getStatus(), deployLog.getStatus());
        assertEquals("容器部署失败", deployLog.getSummary());
        assertEquals("cluster unavailable", deployLog.getErrorMessage());
        assertEquals(PipelineRunStatusEnum.FAILED.getStatus(), run.getRunStatus());
        assertEquals("cluster unavailable", run.getErrorMessage());
    }

    @Test
    @SuppressWarnings("unchecked")
    public void testStartContainerDeploy_applyManifestSuccess() {
        // 准备参数
        PipelineRunDO run = buildRun();
        PipelineSpec.Node node = buildDeployNode();
        ApplicationDO application = buildApplication();
        ApplicationEnvDO applicationEnv = buildApplicationEnv();
        EnvironmentDO environment = buildKubernetesEnvironment();
        PipelineRunLogDO deployLog = buildDeployLog();
        PipelineRunLogDO codeMergeLog = new PipelineRunLogDO();
        codeMergeLog.setResultJson("{\"deployCommitSha\":\"merge-sha\"}");
        KubernetesClient client = mock(KubernetesClient.class);
        AppsAPIGroupDSL appsAPIGroup = mock(AppsAPIGroupDSL.class);
        MixedOperation<Deployment, DeploymentList, RollableScalableResource<Deployment>> deploymentOperation = mock(MixedOperation.class);
        NonNamespaceOperation<Deployment, DeploymentList, RollableScalableResource<Deployment>> namespacedDeploymentOperation =
                mock(NonNamespaceOperation.class);
        RollableScalableResource<Deployment> deploymentResource = mock(RollableScalableResource.class);
        when(deploymentOrderMapper.selectByPipelineRunIdAndNodeId(eq(800L), eq("deploy"))).thenReturn(null);
        when(applicationEnvMapper.selectById(eq(200L))).thenReturn(applicationEnv);
        when(applicationMapper.selectById(eq(10L))).thenReturn(application);
        when(environmentMapper.selectById(eq(300L))).thenReturn(environment);
        when(pipelineRunLogMapper.selectByPipelineRunIdAndNodeId(eq(800L), eq("deploy"))).thenReturn(null);
        when(pipelineRunLogMapper.selectByPipelineRunIdAndNodeType(eq(800L), eq(PipelineNodeRegistryServiceImpl.TYPE_CODE_MERGE)))
                .thenReturn(codeMergeLog);
        doAnswer(invocation -> {
            PipelineRunLogDO log = invocation.getArgument(0);
            log.setId(900L);
            return 1;
        }).when(pipelineRunLogMapper).insert(any(PipelineRunLogDO.class));
        doAnswer(invocation -> {
            DeploymentOrderDO order = invocation.getArgument(0);
            order.setId(100L);
            return 1;
        }).when(deploymentOrderMapper).insert(any(DeploymentOrderDO.class));
        when(pipelineRunLogMapper.selectById(eq(900L))).thenReturn(deployLog);
        when(pipelineRunMapper.selectById(eq(800L))).thenReturn(run);
        when(kubernetesClientFactory.create(eq("kubeconfig"))).thenReturn(client);
        when(client.apps()).thenReturn(appsAPIGroup);
        when(appsAPIGroup.deployments()).thenReturn(deploymentOperation);
        when(deploymentOperation.inNamespace(eq("prod"))).thenReturn(namespacedDeploymentOperation);
        when(namespacedDeploymentOperation.withName(eq("gone-api"))).thenReturn(deploymentResource);
        when(namespacedDeploymentOperation.resource(any(Deployment.class))).thenReturn(deploymentResource);
        Deployment appliedDeployment = readyDeployment("gone-api", 3, 10L, "uid-1", "9");
        Deployment readyDeployment = readyDeployment("gone-api", 3, 10L, "uid-1", "9");
        when(deploymentResource.createOrReplace()).thenReturn(appliedDeployment);
        when(deploymentResource.get()).thenReturn(null, readyDeployment);

        // 调用
        deploymentOrderService.startContainerDeploy(run, node, 7L);

        // 断言
        ArgumentCaptor<Deployment> deploymentCaptor = ArgumentCaptor.forClass(Deployment.class);
        verify(namespacedDeploymentOperation).resource(deploymentCaptor.capture());
        Deployment submitted = deploymentCaptor.getValue();
        assertEquals("prod", submitted.getMetadata().getNamespace());
        assertEquals(3, submitted.getSpec().getReplicas());
        assertEquals("registry.example.com/gone-api:merge-sha-prod-800-release/prod",
                submitted.getSpec().getTemplate().getSpec().getContainers().get(0).getImage());

        ArgumentCaptor<DeploymentOrderDO> orderCaptor = ArgumentCaptor.forClass(DeploymentOrderDO.class);
        verify(deploymentOrderMapper).insert(orderCaptor.capture());
        DeploymentOrderDO order = orderCaptor.getValue();
        assertEquals(DeploymentOrderStatusEnum.SUCCESS.getStatus(), order.getDeployStatus());
        assertEquals("gone-api", order.getWorkloadName());
        assertEquals("9", order.getTargetRevision());
        ContainerDeployConfigContext config = JsonUtils.parseObject(order.getConfigJson(), ContainerDeployConfigContext.class);
        assertEquals(DeploymentModeEnum.RAW_MANIFEST.getMode(), config.getDeployMode());
        assertEquals("gone-api", config.getWorkloadName());
        assertEquals(3, config.getReplicas());
        assertTrue(config.getRenderedManifestYaml().contains("prod"));
        assertTrue(config.getRenderedManifestYaml().contains("registry.example.com/gone-api:merge-sha-prod-800-release/prod"));
        assertEquals(PipelineRunLogStatusEnum.SUCCESS.getStatus(), deployLog.getStatus());
        assertEquals(PipelineRunStatusEnum.SUCCESS.getStatus(), run.getRunStatus());
        verify(client).close();
    }

    @Test
    public void testRetryDeploymentOrder_failedOrder() {
        // 准备参数
        DeploymentOrderDO order = buildOrder(DeploymentOrderStatusEnum.FAILED.getStatus());
        order.setAttempt(1);
        order.setConfigJson(JsonUtils.toJsonString(buildDeployConfig()));
        PipelineRunLogDO log = buildDeployLog();
        PipelineRunDO run = buildRun();
        when(deploymentOrderMapper.selectById(eq(100L))).thenReturn(order);
        when(environmentMapper.selectById(eq(300L))).thenReturn(buildKubernetesEnvironment());
        when(pipelineRunLogMapper.selectById(eq(900L))).thenReturn(log);
        when(pipelineRunMapper.selectById(eq(800L))).thenReturn(run);
        when(kubernetesClientFactory.create(eq("kubeconfig"))).thenThrow(new RuntimeException("retry failed"));

        // 调用
        deploymentOrderService.retryDeploymentOrder(100L, 7L);

        // 断言
        assertEquals(2, order.getAttempt());
        assertEquals(DeploymentOrderStatusEnum.FAILED.getStatus(), order.getDeployStatus());
        assertEquals(DeploymentOrderStepKeyEnum.PREPARE_CONTEXT.getKey(), order.getCurrentStage());
        assertEquals("retry failed", order.getErrorMessage());
        assertEquals(PipelineRunLogStatusEnum.FAILED.getStatus(), log.getStatus());
        assertEquals(PipelineRunStatusEnum.FAILED.getStatus(), run.getRunStatus());
    }

    @Test
    @SuppressWarnings("unchecked")
    public void testGetDeploymentOrder_includeLivePods() {
        // 准备参数
        DeploymentOrderDO order = buildOrder(DeploymentOrderStatusEnum.SUCCESS.getStatus());
        order.setResultJson("{\"updatedReplicas\":1}");
        EnvironmentDO environment = buildKubernetesEnvironment();
        KubernetesClient client = mock(KubernetesClient.class);
        AppsAPIGroupDSL appsAPIGroup = mock(AppsAPIGroupDSL.class);
        MixedOperation<Deployment, DeploymentList, RollableScalableResource<Deployment>> deploymentOperation = mock(MixedOperation.class);
        NonNamespaceOperation<Deployment, DeploymentList, RollableScalableResource<Deployment>> namespacedDeploymentOperation =
                mock(NonNamespaceOperation.class);
        RollableScalableResource<Deployment> deploymentResource = mock(RollableScalableResource.class);
        MixedOperation<io.fabric8.kubernetes.api.model.Pod, io.fabric8.kubernetes.api.model.PodList, PodResource> podOperation =
                mock(MixedOperation.class);
        NonNamespaceOperation<io.fabric8.kubernetes.api.model.Pod, io.fabric8.kubernetes.api.model.PodList, PodResource> namespacedPodOperation =
                mock(NonNamespaceOperation.class);
        when(deploymentOrderMapper.selectById(eq(100L))).thenReturn(order);
        when(environmentMapper.selectById(eq(300L))).thenReturn(environment);
        when(kubernetesClientFactory.create(eq("kubeconfig"))).thenReturn(client);
        when(client.apps()).thenReturn(appsAPIGroup);
        when(appsAPIGroup.deployments()).thenReturn(deploymentOperation);
        when(deploymentOperation.inNamespace(eq("prod"))).thenReturn(namespacedDeploymentOperation);
        when(namespacedDeploymentOperation.withName(eq("gone-api"))).thenReturn(deploymentResource);
        when(deploymentResource.get())
                .thenReturn(new DeploymentBuilder()
                        .withNewMetadata()
                        .withGeneration(2L)
                        .addToAnnotations("deployment.kubernetes.io/revision", "8")
                        .endMetadata()
                        .withNewSpec()
                        .withReplicas(1)
                        .withNewSelector()
                        .addToMatchLabels("app", "gone-api")
                        .endSelector()
                        .endSpec()
                        .withNewStatus()
                        .withObservedGeneration(2L)
                        .withUpdatedReplicas(1)
                        .withAvailableReplicas(1)
                        .withReadyReplicas(1)
                        .endStatus()
                        .build());
        when(client.pods()).thenReturn(podOperation);
        when(podOperation.inNamespace(eq("prod"))).thenReturn(namespacedPodOperation);
        when(namespacedPodOperation.withLabels(eq(Map.of("app", "gone-api")))).thenReturn(namespacedPodOperation);
        when(namespacedPodOperation.list())
                .thenReturn(new PodListBuilder().withItems(new PodBuilder()
                        .withNewMetadata()
                        .withName("gone-api-7d98f")
                        .withNamespace("prod")
                        .withCreationTimestamp("2026-06-10T15:54:59Z")
                        .endMetadata()
                        .withNewSpec()
                        .withNodeName("node-1")
                        .addNewContainer()
                        .withName("app")
                        .withImage("registry.example.com/gone-api:sha")
                        .endContainer()
                        .endSpec()
                        .withNewStatus()
                        .withPhase("Running")
                        .withPodIP("10.0.0.8")
                        .withHostIP("192.168.1.8")
                        .withStartTime("2026-06-10T15:55:01Z")
                        .addNewCondition()
                        .withType("Ready")
                        .withStatus("True")
                        .endCondition()
                        .addNewContainerStatus()
                        .withName("app")
                        .withImage("registry.example.com/gone-api:sha")
                        .withImageID("docker-pullable://registry.example.com/gone-api@sha256:abc")
                        .withReady(true)
                        .withRestartCount(1)
                        .withState(new ContainerStateBuilder().withNewRunning().endRunning().build())
                        .endContainerStatus()
                        .endStatus()
                        .build()).build());

        // 调用
        DeploymentOrderRespVO respVO = deploymentOrderService.getDeploymentOrder(100L);

        // 断言
        List<Map<String, Object>> pods = (List<Map<String, Object>>) respVO.getLiveStatus().get("pods");
        assertEquals(1, pods.size());
        assertEquals("gone-api-7d98f", pods.get(0).get("name"));
        assertEquals("Running", pods.get(0).get("phase"));
        assertEquals(true, pods.get(0).get("ready"));
        assertEquals(1, pods.get(0).get("readyContainers"));
        assertEquals(1, pods.get(0).get("totalContainers"));
        assertEquals(1, pods.get(0).get("restartCount"));
        assertEquals("10.0.0.8", pods.get(0).get("podIp"));
        Map<String, Object> targetContainer = (Map<String, Object>) pods.get(0).get("targetContainer");
        assertEquals("app", targetContainer.get("name"));
        assertEquals("registry.example.com/gone-api:sha", targetContainer.get("image"));
        assertEquals("RUNNING", targetContainer.get("state"));
        verify(client).close();
    }

    private DeploymentOrderDO buildOrder(String status) {
        DeploymentOrderDO order = new DeploymentOrderDO();
        order.setId(100L);
        order.setPipelineRunId(800L);
        order.setPipelineRunLogId(900L);
        order.setNodeId("deploy");
        order.setNodeType(LEGACY_CONTAINER_DEPLOY_NODE_TYPE);
        order.setApplicationEnvId(200L);
        order.setEnvironmentId(300L);
        order.setDeployStatus(status);
        order.setCurrentStage(DeploymentOrderStepKeyEnum.PREPARE_CONTEXT.getKey());
        order.setNamespace("prod");
        order.setWorkloadName("gone-api");
        order.setContainerName("app");
        order.setImage("registry.example.com/gone-api:old");
        return order;
    }

    private PipelineRunDO buildRun() {
        PipelineRunDO run = new PipelineRunDO();
        run.setId(800L);
        run.setTenantId(1L);
        run.setDefinitionId(600L);
        run.setDefinitionVersionId(700L);
        run.setAppId(10L);
        run.setApplicationEnvId(200L);
        run.setBranchName("release/prod");
        run.setCommitSha("run-sha");
        run.setRunStatus(PipelineRunStatusEnum.RUNNING.getStatus());
        run.setTriggerType("MANUAL");
        run.setTriggerUserId(6L);
        return run;
    }

    private PipelineRunLogDO buildDeployLog() {
        PipelineRunLogDO log = new PipelineRunLogDO();
        log.setId(900L);
        log.setPipelineRunId(800L);
        log.setNodeId("deploy");
        log.setNodeType(LEGACY_CONTAINER_DEPLOY_NODE_TYPE);
        log.setStatus(PipelineRunLogStatusEnum.RUNNING.getStatus());
        return log;
    }

    private PipelineSpec.Node buildDeployNode() {
        PipelineSpec.Node node = new PipelineSpec.Node();
        node.setId("deploy");
        node.setType(LEGACY_CONTAINER_DEPLOY_NODE_TYPE);
        node.setName("容器部署");
        Map<String, Object> params = new LinkedHashMap<>();
        params.put("infraType", EnvironmentInfraTypeEnum.K8S.getInfraType());
        params.put("deployMode", DeploymentModeEnum.RAW_MANIFEST.getMode());
        params.put("manifestYaml", deploymentManifestYaml("gone-api", "app"));
        params.put("containerName", "app");
        params.put("image", "registry.example.com/gone-api:${COMMIT_SHA}-${ENV_KEY}-${PIPELINE_RUN_ID}-${BRANCH_NAME}");
        params.put("replicas", 3);
        params.put("rolloutTimeoutSeconds", 120);
        node.setParams(params);
        return node;
    }

    private ApplicationDO buildApplication() {
        ApplicationDO application = new ApplicationDO();
        application.setId(10L);
        application.setAppKey("gone-api");
        return application;
    }

    private ApplicationEnvDO buildApplicationEnv() {
        ApplicationEnvDO applicationEnv = new ApplicationEnvDO();
        applicationEnv.setId(200L);
        applicationEnv.setAppId(10L);
        applicationEnv.setEnvId(300L);
        return applicationEnv;
    }

    private EnvironmentDO buildKubernetesEnvironment() {
        EnvironmentDO environment = new EnvironmentDO();
        environment.setId(300L);
        environment.setEnvKey("prod");
        environment.setInfraType(EnvironmentInfraTypeEnum.K8S.getInfraType());
        KubernetesEnvironmentConfig config = new KubernetesEnvironmentConfig();
        config.setKubeconfig("kubeconfig");
        config.setNamespace("prod");
        environment.setInfraConfig(JsonUtils.toJsonString(config));
        return environment;
    }

    private ContainerDeployConfigContext buildDeployConfig() {
        ContainerDeployConfigContext config = new ContainerDeployConfigContext();
        config.setInfraType(EnvironmentInfraTypeEnum.K8S.getInfraType());
        config.setDeployMode(DeploymentModeEnum.RAW_MANIFEST.getMode());
        config.setWorkloadKind("DEPLOYMENT");
        config.setNamespace("prod");
        config.setWorkloadName("gone-api");
        config.setManifestYaml(deploymentManifestYaml("gone-api", "app"));
        config.setRenderedManifestYaml(deploymentManifestYaml("gone-api", "app")
                .replace("${NAMESPACE}", "prod")
                .replace("${IMAGE}", "registry.example.com/gone-api:run-sha"));
        config.setContainerName("app");
        config.setImageExpression("registry.example.com/gone-api:run-sha");
        config.setImage("registry.example.com/gone-api:run-sha");
        config.setReplicas(2);
        config.setRolloutTimeoutSeconds(120);
        return config;
    }

    private Deployment readyDeployment(String name, int replicas, Long generation, String uid, String revision) {
        return new DeploymentBuilder()
                .withNewMetadata()
                .withName(name)
                .withNamespace("prod")
                .withGeneration(generation)
                .withUid(uid)
                .addToAnnotations("deployment.kubernetes.io/revision", revision)
                .endMetadata()
                .withNewSpec()
                .withReplicas(replicas)
                .endSpec()
                .withNewStatus()
                .withObservedGeneration(generation)
                .withUpdatedReplicas(replicas)
                .withAvailableReplicas(replicas)
                .withReadyReplicas(replicas)
                .endStatus()
                .build();
    }

    private String deploymentManifestYaml(String name, String containerName) {
        return """
                apiVersion: apps/v1
                kind: Deployment
                metadata:
                  name: %s
                  namespace: ${NAMESPACE}
                spec:
                  replicas: 1
                  selector:
                    matchLabels:
                      app: %s
                  template:
                    metadata:
                      labels:
                        app: %s
                    spec:
                      containers:
                        - name: %s
                          image: ${IMAGE}
                """.formatted(name, name, name, containerName);
    }

}
