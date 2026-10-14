package cn.iocoder.yudao.module.devops.service.deployment;

import cn.iocoder.yudao.framework.common.exception.ServiceException;
import cn.iocoder.yudao.framework.common.util.json.JsonUtils;
import cn.iocoder.yudao.framework.test.core.ut.BaseMockitoUnitTest;
import cn.iocoder.yudao.module.devops.controller.admin.applicationorchestration.vo.ApplicationOrchestrationComponentVO;
import cn.iocoder.yudao.module.devops.controller.admin.applicationorchestration.vo.ApplicationOrchestrationConfigVO;
import cn.iocoder.yudao.module.devops.controller.admin.applicationorchestration.vo.ApplicationOrchestrationRespVO;
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
import cn.iocoder.yudao.module.devops.dal.mysql.pipeline.log.PipelineRunLogMapper;
import cn.iocoder.yudao.module.devops.enums.DeploymentOrderStatusEnum;
import cn.iocoder.yudao.module.devops.framework.kubernetes.KubernetesClientFactory;
import cn.iocoder.yudao.module.devops.framework.kubernetes.KubernetesDeploymentManifestSupport;
import cn.iocoder.yudao.module.devops.framework.pipeline.PipelineSpec;
import cn.iocoder.yudao.module.devops.service.applicationorchestration.ApplicationOrchestrationService;
import cn.iocoder.yudao.module.devops.service.applicationorchestration.ApplicationOrchestrationValidationServiceImpl;
import cn.iocoder.yudao.module.devops.service.deployment.context.ContainerDeployConfigContext;
import cn.iocoder.yudao.module.devops.service.deployment.context.DeploymentOrderResultContext;
import cn.iocoder.yudao.module.devops.service.deployment.context.KubernetesDeploymentHealthResult;
import cn.iocoder.yudao.module.devops.service.pipeline.execution.handler.PipelineStepActionHelper;
import cn.iocoder.yudao.module.devops.service.variablegroup.VariableGroupService;
import io.fabric8.kubernetes.api.model.ConfigMap;
import io.fabric8.kubernetes.api.model.HasMetadata;
import io.fabric8.kubernetes.api.model.Secret;
import io.fabric8.kubernetes.api.model.apps.Deployment;
import io.fabric8.kubernetes.api.model.apps.DeploymentList;
import io.fabric8.kubernetes.client.KubernetesClient;
import io.fabric8.kubernetes.client.KubernetesClientException;
import io.fabric8.kubernetes.client.dsl.AppsAPIGroupDSL;
import io.fabric8.kubernetes.client.dsl.MixedOperation;
import io.fabric8.kubernetes.client.dsl.NamespaceableResource;
import io.fabric8.kubernetes.client.dsl.NonNamespaceOperation;
import io.fabric8.kubernetes.client.dsl.Resource;
import io.fabric8.kubernetes.client.dsl.RollableScalableResource;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.Spy;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicReference;

import static cn.iocoder.yudao.module.devops.enums.ErrorCodeConstants.DEPLOYMENT_NODE_PARAM_INVALID;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

class OrchestrationDeploymentTest extends BaseMockitoUnitTest {

    @InjectMocks private DeploymentOrderServiceImpl service;
    @Mock private DeploymentOrderMapper deploymentOrderMapper;
    @Mock private PipelineRunLogMapper pipelineRunLogMapper;
    @Mock private ApplicationMapper applicationMapper;
    @Mock private ApplicationEnvMapper applicationEnvMapper;
    @Mock private EnvironmentMapper environmentMapper;
    @Mock private KubernetesClientFactory kubernetesClientFactory;
    @Mock private ApplicationOrchestrationService applicationOrchestrationService;
    @Spy private ApplicationOrchestrationValidationServiceImpl applicationOrchestrationValidationService;
    @Spy private KubernetesDeploymentManifestSupport kubernetesDeploymentManifestSupport;
    @Spy private KubernetesDeploymentStatusCollector kubernetesDeploymentStatusCollector;
    @Spy private PipelineStepActionHelper pipelineStepActionHelper;
    @Mock private VariableGroupService variableGroupService;

    private final PipelineRunDO run = new PipelineRunDO();
    private final PipelineRunLogDO log = new PipelineRunLogDO();
    private final AtomicReference<DeploymentOrderDO> order = new AtomicReference<>();

    @Test
    void testSixResourcesOrderedAndRetryUsesSnapshot() {
        ApplicationOrchestrationRespVO source = source();
        prepareContext(source);
        KubernetesClient client = prepareCluster(false, false);
        prepareOrderPersistence();
        doReturn(new KubernetesDeploymentHealthResult(true, false, "READY", "ready", Map.of()))
                .when(kubernetesDeploymentStatusCollector).evaluateHealth(eq(client), any(), any(), eq(List.of()));
        doReturn(Map.of("health", Map.of("ready", true)))
                .when(kubernetesDeploymentStatusCollector).collect(eq(client), any(), any(Deployment.class));

        assertTrue(service.startContainerDeploy(run, step(), 7L).isSuccess());
        ArgumentCaptor<HasMetadata> submitted = ArgumentCaptor.forClass(HasMetadata.class);
        verify(client, times(6)).resource(submitted.capture());
        assertEquals(List.of("Secret", "ConfigMap", "PersistentVolumeClaim", "Service", "Deployment", "Ingress"),
                submitted.getAllValues().stream().map(HasMetadata::getKind).toList());
        assertTrue(submitted.getAllValues().stream().allMatch(resource -> "prod".equals(resource.getMetadata().getNamespace())));
        Secret secret = (Secret) submitted.getAllValues().get(0);
        assertEquals("sensitive-password", secret.getStringData().get("password"));
        Deployment deployment = (Deployment) submitted.getAllValues().get(4);
        assertEquals("registry/demo:commit", deployment.getSpec().getTemplate().getSpec().getContainers().get(0).getImage());
        assertEquals(2, deployment.getSpec().getReplicas());

        ContainerDeployConfigContext config = JsonUtils.parseObject(order.get().getConfigJson(), ContainerDeployConfigContext.class);
        assertEquals(42L, config.getOrchestrationId());
        assertEquals(source.getUpdateTime(), config.getOrchestrationUpdateTime());
        assertTrue(config.getManifestYaml().contains("sensitive-password"));
        assertTrue(config.getRenderedManifestYaml().contains("registry/demo:commit"));
        assertFalse(log.getContextJson().contains("sensitive-password"));
        String response = JsonUtils.toJsonString(service.getDeploymentOrder(100L));
        assertFalse(response.contains("sensitive-password"));
        assertTrue(order.get().getConfigJson().contains("sensitive-password"));

        String snapshot = order.get().getConfigJson();
        source.getConfig().setComponents(List.of(component("Deployment", "changed", 1)));
        order.get().setDeployStatus(DeploymentOrderStatusEnum.FAILED.getStatus());
        service.retryDeploymentOrder(100L, 7L);
        assertEquals(snapshot, order.get().getConfigJson());
        assertEquals(2, order.get().getAttempt());
        verify(applicationOrchestrationService, times(1)).getOrchestration(10L, "K8S_DEPLOYMENT");
        verify(client, times(12)).resource(any(HasMetadata.class));
    }

    @Test
    void testApplyFailurePreservesPartialResultsAndMasksPayload() {
        prepareContext(source());
        KubernetesClient client = prepareCluster(true, false);
        prepareOrderPersistence();
        doReturn(Map.of()).when(kubernetesDeploymentStatusCollector).collect(eq(client), any());
        assertFalse(service.startContainerDeploy(run, step(), 7L).isSuccess());
        DeploymentOrderResultContext result = JsonUtils.parseObject(order.get().getResultJson(), DeploymentOrderResultContext.class);
        assertEquals(2, result.getApplyResults().size());
        assertEquals("APPLIED", result.getApplyResults().get(0).getStatus());
        assertEquals("FAILED", result.getApplyResults().get(1).getStatus());
        assertFalse(order.get().getResultJson().contains("sensitive-password"));
        assertFalse(order.get().getErrorMessage().contains("sensitive-password"));
        verify(client, times(2)).resource(any(HasMetadata.class));
        verify(client).close();
    }

    @Test
    void testMissingDraftFailsBeforeClusterWrite() {
        ApplicationOrchestrationRespVO source = new ApplicationOrchestrationRespVO();
        prepareContext(source);
        assertServiceException(() -> service.startContainerDeploy(run, step(), 7L), DEPLOYMENT_NODE_PARAM_INVALID);
        verifyNoInteractions(kubernetesClientFactory);
        verify(deploymentOrderMapper, never()).insert(any(DeploymentOrderDO.class));
    }

    @Test
    void testNoEnabledComponentsFailsBeforeClusterWrite() {
        ApplicationOrchestrationRespVO source = source();
        source.getConfig().getComponents().forEach(component -> component.setEnabled(false));
        prepareContext(source);
        assertServiceException(() -> service.startContainerDeploy(run, step(), 7L), DEPLOYMENT_NODE_PARAM_INVALID);
        verifyNoInteractions(kubernetesClientFactory);
    }

    @Test
    void testDependencyOnDisabledComponentRejected() {
        ApplicationOrchestrationRespVO source = source();
        source.getConfig().getComponents().stream().filter(component -> "Secret".equals(component.getKind()))
                .forEach(component -> component.setEnabled(false));
        source.getConfig().getComponents().stream().filter(component -> "Deployment".equals(component.getKind()))
                .forEach(component -> component.setDependsOn(List.of("credentials")));
        prepareContext(source);
        assertServiceException(() -> service.startContainerDeploy(run, step(), 7L), DEPLOYMENT_NODE_PARAM_INVALID);
        verifyNoInteractions(kubernetesClientFactory);
    }

    @Test
    void testMultipleDeploymentsRejected() {
        ApplicationOrchestrationRespVO source = source();
        source.getConfig().getComponents().add(component("Deployment", "worker", 8));
        prepareContext(source);
        assertServiceException(() -> service.startContainerDeploy(run, step(), 7L), DEPLOYMENT_NODE_PARAM_INVALID);
        verifyNoInteractions(kubernetesClientFactory);
    }

    @Test
    void testNoDeploymentRejected() {
        ApplicationOrchestrationRespVO source = source();
        source.getConfig().getComponents().stream().filter(component -> "Deployment".equals(component.getKind()))
                .forEach(component -> component.setEnabled(false));
        prepareContext(source);
        assertServiceException(() -> service.startContainerDeploy(run, step(), 7L), DEPLOYMENT_NODE_PARAM_INVALID);
        verifyNoInteractions(kubernetesClientFactory);
    }

    @Test
    void testMalformedSecretDoesNotLeakInError() {
        ApplicationOrchestrationRespVO source = source();
        source.getConfig().getComponents().stream().filter(component -> "Secret".equals(component.getKind()))
                .forEach(component -> component.setYaml("kind: Secret\nstringData: [sensitive-password"));
        prepareContext(source);
        ServiceException error = assertThrows(ServiceException.class, () -> service.startContainerDeploy(run, step(), 7L));
        assertFalse(error.getMessage().contains("sensitive-password"));
        verifyNoInteractions(kubernetesClientFactory);
    }

    @Test
    void testCancelStopsRemainingResourceSubmission() {
        prepareContext(source());
        KubernetesClient client = prepareCluster(false, true);
        prepareOrderPersistence();
        assertFalse(service.startContainerDeploy(run, step(), 7L).isSuccess());
        verify(client, times(1)).resource(any(HasMetadata.class));
        assertEquals(DeploymentOrderStatusEnum.CANCELED.getStatus(), order.get().getDeployStatus());
    }

    @Test
    void testMissingTargetContainerFailsBeforeClusterWrite() {
        prepareContext(source());
        PipelineSpec.ExecutableStep step = step();
        step.setWith(Map.of("deployMode", "ORCHESTRATION", "containerName", "missing", "image", "registry/demo:sha"));
        ServiceException error = assertThrows(ServiceException.class, () -> service.startContainerDeploy(run, step, 7L));
        assertEquals(cn.iocoder.yudao.module.devops.enums.ErrorCodeConstants.DEPLOYMENT_MANIFEST_CONTAINER_NOT_EXISTS.getCode(),
                error.getCode());
        verifyNoInteractions(kubernetesClientFactory);
    }

    @Test
    void testForwardDependencyFailsBeforeClusterWrite() {
        ApplicationOrchestrationRespVO source = source();
        source.getConfig().getComponents().stream().filter(component -> "Secret".equals(component.getKind()))
                .forEach(component -> component.setDependsOn(List.of("demo")));
        prepareContext(source);
        assertServiceException(() -> service.startContainerDeploy(run, step(), 7L), DEPLOYMENT_NODE_PARAM_INVALID);
        verifyNoInteractions(kubernetesClientFactory);
    }

    @Test
    void testUnsupportedKindFailsBeforeClusterWrite() {
        ApplicationOrchestrationRespVO source = source();
        ApplicationOrchestrationComponentVO component = component("Namespace", "other", 8);
        source.getConfig().getComponents().add(component);
        prepareContext(source);
        assertServiceException(() -> service.startContainerDeploy(run, step(), 7L), DEPLOYMENT_NODE_PARAM_INVALID);
        verifyNoInteractions(kubernetesClientFactory);
    }

    @Test
    void testDuplicateResourceRejected() {
        ApplicationOrchestrationRespVO source = source();
        ApplicationOrchestrationComponentVO duplicate = component("Secret", "other-credentials", 8);
        duplicate.setYaml(component("Secret", "credentials", 1).getYaml());
        source.getConfig().getComponents().add(duplicate);
        prepareContext(source);
        assertServiceException(() -> service.startContainerDeploy(run, step(), 7L), DEPLOYMENT_NODE_PARAM_INVALID);
        verifyNoInteractions(kubernetesClientFactory);
    }

    @Test
    void testVariableGroupInjectedAndSensitiveValueMasked() {
        ApplicationOrchestrationRespVO source = source();
        source.getConfig().getComponents().stream().filter(component -> "ConfigMap".equals(component.getKind()))
                .forEach(component -> component.setYaml("apiVersion: v1\nkind: ConfigMap\nmetadata:\n  name: configuration\n"
                        + "data:\n  host: ${DB_HOST}\n  password: ${DB_PASSWORD}\n"));
        prepareContext(source);
        when(variableGroupService.getEffectiveVariables(10L, 200L))
                .thenReturn(Map.of("DB_HOST", "db.prod", "DB_PASSWORD", "vg-sensitive-value"));
        KubernetesClient client = prepareCluster(false, false);
        prepareHealthyCluster(client);
        prepareOrderPersistence();

        assertTrue(service.startContainerDeploy(run, step(), 7L).isSuccess());

        ArgumentCaptor<HasMetadata> submitted = ArgumentCaptor.forClass(HasMetadata.class);
        verify(client, times(6)).resource(submitted.capture());
        ConfigMap configMap = (ConfigMap) submitted.getAllValues().stream()
                .filter(resource -> resource instanceof ConfigMap).findFirst().orElseThrow();
        assertEquals("db.prod", configMap.getData().get("host"));
        assertEquals("vg-sensitive-value", configMap.getData().get("password"));

        ContainerDeployConfigContext config = JsonUtils.parseObject(order.get().getConfigJson(), ContainerDeployConfigContext.class);
        assertEquals(Map.of("DB_HOST", "db.prod", "DB_PASSWORD", "vg-sensitive-value"),
                config.getVariableGroupVariables());
        assertTrue(config.getRenderedManifestYaml().contains("vg-sensitive-value"));

        assertFalse(log.getContextJson().contains("vg-sensitive-value"));
        assertTrue(log.getContextJson().contains("******"));
        String response = JsonUtils.toJsonString(service.getDeploymentOrder(100L));
        assertFalse(response.contains("vg-sensitive-value"));
        assertTrue(response.contains("******"));
        assertTrue(response.contains("db.prod"));
        assertTrue(order.get().getConfigJson().contains("vg-sensitive-value"));
    }

    @Test
    void testBuiltinVariableWinsOverVariableGroup() {
        prepareContext(source());
        when(variableGroupService.getEffectiveVariables(10L, 200L))
                .thenReturn(Map.of("IMAGE", "evil/image:tag"));
        KubernetesClient client = prepareCluster(false, false);
        prepareHealthyCluster(client);
        prepareOrderPersistence();

        assertTrue(service.startContainerDeploy(run, step(), 7L).isSuccess());

        ArgumentCaptor<HasMetadata> submitted = ArgumentCaptor.forClass(HasMetadata.class);
        verify(client, times(6)).resource(submitted.capture());
        Deployment deployment = (Deployment) submitted.getAllValues().stream()
                .filter(resource -> resource instanceof Deployment).findFirst().orElseThrow();
        assertEquals("registry/demo:commit",
                deployment.getSpec().getTemplate().getSpec().getContainers().get(0).getImage());
    }

    private void assertServiceException(Runnable action, cn.iocoder.yudao.framework.common.exception.ErrorCode code) {
        assertEquals(code.getCode(), assertThrows(ServiceException.class, action::run).getCode());
    }

    private void prepareContext(ApplicationOrchestrationRespVO source) {
        run.setId(800L); run.setAppId(10L); run.setApplicationEnvId(200L); run.setTenantId(1L); run.setCommitSha("commit");
        ApplicationDO application = new ApplicationDO(); application.setId(10L); application.setAppKey("demo");
        ApplicationEnvDO appEnv = new ApplicationEnvDO(); appEnv.setEnvId(300L); appEnv.setAppId(10L);
        EnvironmentDO environment = new EnvironmentDO(); environment.setId(300L); environment.setInfraType("K8S");
        environment.setInfraConfig("{\"namespace\":\"prod\",\"kubeconfig\":\"kubeconfig\"}");
        when(applicationMapper.selectById(10L)).thenReturn(application);
        when(applicationEnvMapper.selectById(200L)).thenReturn(appEnv);
        when(environmentMapper.selectById(300L)).thenReturn(environment);
        when(applicationOrchestrationService.getOrchestration(10L, "K8S_DEPLOYMENT")).thenReturn(source);
    }

    private void prepareOrderPersistence() {
        log.setId(900L);
        doAnswer(invocation -> { DeploymentOrderDO value = invocation.getArgument(0); value.setId(100L); order.set(value); return 1; })
                .when(deploymentOrderMapper).insert(any(DeploymentOrderDO.class));
        doAnswer(invocation -> { PipelineRunLogDO value = invocation.getArgument(0); value.setId(900L); log.setContextJson(value.getContextJson()); return 1; })
                .when(pipelineRunLogMapper).insert(any(PipelineRunLogDO.class));
        when(deploymentOrderMapper.selectById(100L)).thenAnswer(invocation -> order.get());
        when(pipelineRunLogMapper.selectById(900L)).thenReturn(log);
    }

    @SuppressWarnings("unchecked")
    private void prepareHealthyCluster(KubernetesClient client) {
        doReturn(new KubernetesDeploymentHealthResult(true, false, "READY", "ready", Map.of()))
                .when(kubernetesDeploymentStatusCollector).evaluateHealth(eq(client), any(), any(), eq(List.of()));
        doReturn(Map.of("health", Map.of("ready", true)))
                .when(kubernetesDeploymentStatusCollector).collect(eq(client), any(), any(Deployment.class));
    }

    @SuppressWarnings("unchecked")
    private KubernetesClient prepareCluster(boolean failSecond, boolean cancelAfterFirst) {
        KubernetesClient client = mock(KubernetesClient.class);
        AppsAPIGroupDSL apps = mock(AppsAPIGroupDSL.class);
        MixedOperation<Deployment, DeploymentList, RollableScalableResource<Deployment>> deployments = mock(MixedOperation.class);
        NonNamespaceOperation<Deployment, DeploymentList, RollableScalableResource<Deployment>> namespace = mock(NonNamespaceOperation.class);
        RollableScalableResource<Deployment> workload = mock(RollableScalableResource.class);
        NamespaceableResource<HasMetadata> resource = mock(NamespaceableResource.class);
        Resource<HasMetadata> namespaced = mock(Resource.class);
        AtomicReference<HasMetadata> current = new AtomicReference<>();
        when(kubernetesClientFactory.create("kubeconfig")).thenReturn(client);
        when(client.apps()).thenReturn(apps); when(apps.deployments()).thenReturn(deployments);
        when(deployments.inNamespace("prod")).thenReturn(namespace);
        when(namespace.withName("demo")).thenReturn(workload);
        Deployment ready = (Deployment) kubernetesDeploymentManifestSupport.parseSupportedResources(component("Deployment", "demo", 1).getYaml()).get(0);
        when(workload.get()).thenReturn(ready);
        when(client.resource(any(HasMetadata.class))).thenAnswer(invocation -> { current.set(invocation.getArgument(0)); return resource; });
        when(resource.inNamespace("prod")).thenReturn(namespaced);
        when(namespaced.createOrReplace()).thenAnswer(invocation -> {
            if (failSecond && "ConfigMap".equals(current.get().getKind())) {
                throw new KubernetesClientException("rejected payload sensitive-password");
            }
            if (cancelAfterFirst) {
                order.get().setDeployStatus(DeploymentOrderStatusEnum.CANCELED.getStatus());
            }
            return current.get();
        });
        return client;
    }

    private PipelineSpec.ExecutableStep step() {
        PipelineSpec.ExecutableStep step = new PipelineSpec.ExecutableStep(); step.setStepId("deploy"); step.setStep("K8sDeploy");
        step.setWith(Map.of("deployMode", "ORCHESTRATION", "image", "registry/demo:${COMMIT_SHA}",
                "containerName", "app", "replicas", 2, "rolloutTimeoutSeconds", 30));
        return step;
    }

    private ApplicationOrchestrationRespVO source() {
        ApplicationOrchestrationRespVO source = new ApplicationOrchestrationRespVO(); source.setId(42L);
        source.setUpdateTime(LocalDateTime.of(2026, 10, 12, 10, 0));
        ApplicationOrchestrationConfigVO config = new ApplicationOrchestrationConfigVO();
        // 故意不按数组顺序存放，验证 applyOrder 驱动执行；禁用组件不参与部署。
        config.setComponents(new ArrayList<>(List.of(component("Ingress", "entry", 6), component("Deployment", "demo", 5),
                component("Service", "service", 4), component("Secret", "credentials", 1),
                component("PersistentVolumeClaim", "storage", 3), component("ConfigMap", "configuration", 2))));
        ApplicationOrchestrationComponentVO disabled = component("Deployment", "disabled", 7); disabled.setEnabled(false);
        config.getComponents().add(disabled); source.setConfig(config); return source;
    }

    private ApplicationOrchestrationComponentVO component(String kind, String name, int order) {
        ApplicationOrchestrationComponentVO component = new ApplicationOrchestrationComponentVO();
        component.setId(name); component.setName(name); component.setKind(kind); component.setTemplateKey("BLANK");
        component.setEnabled(true); component.setApplyOrder(order);
        String version = "Deployment".equals(kind) ? "apps/v1" : "Ingress".equals(kind) ? "networking.k8s.io/v1" : "v1";
        String yaml = "apiVersion: " + version + "\nkind: " + kind + "\nmetadata:\n  name: " + name + "\n";
        if ("Secret".equals(kind)) yaml += "stringData:\n  password: sensitive-password\n";
        if ("Service".equals(kind)) yaml += "spec:\n  type: ExternalName\n  externalName: example.com\n";
        if ("Deployment".equals(kind)) yaml += "spec:\n  replicas: 1\n  selector:\n    matchLabels:\n      app: demo\n  template:\n    metadata:\n      labels:\n        app: demo\n    spec:\n      containers:\n        - name: app\n          image: ${IMAGE}\n";
        component.setYaml(yaml); return component;
    }
}
