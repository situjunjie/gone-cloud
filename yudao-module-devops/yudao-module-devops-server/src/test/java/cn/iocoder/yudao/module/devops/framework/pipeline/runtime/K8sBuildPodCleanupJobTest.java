package cn.iocoder.yudao.module.devops.framework.pipeline.runtime;

import cn.iocoder.yudao.framework.tenant.core.context.TenantContextHolder;
import cn.iocoder.yudao.framework.test.core.ut.BaseMockitoUnitTest;
import cn.iocoder.yudao.module.devops.dal.dataobject.environment.EnvironmentDO;
import cn.iocoder.yudao.module.devops.framework.config.DevopsConfig;
import cn.iocoder.yudao.module.devops.framework.kubernetes.KubernetesClientFactory;
import cn.iocoder.yudao.module.devops.service.environment.EnvironmentService;
import io.fabric8.kubernetes.api.model.*;
import io.fabric8.kubernetes.client.KubernetesClient;
import io.fabric8.kubernetes.client.dsl.MixedOperation;
import io.fabric8.kubernetes.client.dsl.Resource;
import io.fabric8.kubernetes.api.model.PodList;
import io.fabric8.kubernetes.api.model.PersistentVolumeClaimList;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.springframework.test.util.ReflectionTestUtils;

import java.util.concurrent.TimeUnit;

import static org.junit.jupiter.api.Assertions.assertNull;
import static org.mockito.Answers.RETURNS_DEEP_STUBS;
import static org.mockito.Mockito.*;

class K8sBuildPodCleanupJobTest extends BaseMockitoUnitTest {

    private static final String NAMESPACE = "devops-build";
    @InjectMocks
    private K8sBuildPodCleanupJob cleanup;
    @Mock
    private KubernetesClientFactory kubernetesClientFactory;
    @Mock
    private EnvironmentService environmentService;
    @Mock(answer = RETURNS_DEEP_STUBS)
    private KubernetesClient client;

    @Mock(answer = org.mockito.Answers.RETURNS_SELF)
    private MixedOperation<Pod, PodList, io.fabric8.kubernetes.client.dsl.PodResource> pods;
    @Mock(answer = org.mockito.Answers.RETURNS_SELF)
    private MixedOperation<PersistentVolumeClaim, PersistentVolumeClaimList, Resource<PersistentVolumeClaim>> claims;
    @Mock
    private io.fabric8.kubernetes.client.dsl.PodResource podResource;
    @Mock
    private Resource<PersistentVolumeClaim> claimResource;

    @BeforeEach
    void setUp() {
        lenient().when(client.pods()).thenReturn(pods);
        lenient().when(client.persistentVolumeClaims()).thenReturn(claims);
        lenient().when(pods.inNamespace(anyString())).thenReturn(pods);
        lenient().when(claims.inNamespace(anyString())).thenReturn(claims);
        lenient().when(pods.withLabel(anyString(), anyString())).thenReturn(pods);
        lenient().when(claims.withLabel(anyString(), anyString())).thenReturn(claims);
        lenient().when(pods.withName(anyString())).thenReturn(podResource);
        lenient().when(pods.resource(any(Pod.class))).thenReturn(podResource);
        lenient().when(claims.withName(anyString())).thenReturn(claimResource);
        DevopsConfig config = new DevopsConfig();
        config.getBuild().getCluster().setEnvironmentId(42L);
        ReflectionTestUtils.setField(cleanup, "devopsConfig", config);
        EnvironmentDO environment = new EnvironmentDO();
        environment.setInfraConfig("{\"kubeconfig\":\"test-kubeconfig\"}");
        when(environmentService.getEnvironment(42L)).thenReturn(environment);
        when(kubernetesClientFactory.create("test-kubeconfig")).thenReturn(client);
        when(client.pods().inNamespace(NAMESPACE).withLabel("app", "pipeline-build").list())
                .thenReturn(new PodListBuilder().build());
    }

    @Test
    void immediateStrategyReapsMarkedPvcEvenWhenNoPodsRemain() {
        when(client.persistentVolumeClaims().inNamespace(NAMESPACE).withLabel("app", "pipeline-build").list())
                .thenReturn(new PersistentVolumeClaimListBuilder().withItems(claim("800", "0")).build());
        when(client.pods().inNamespace(NAMESPACE).withLabel("app", "pipeline-build")
                .withLabel("pipeline-run-id", "800").list()).thenReturn(new PodListBuilder().build());

        cleanup.cleanupMarkedPods();

        verify(client.persistentVolumeClaims().inNamespace(NAMESPACE).withName("pipeline-run-800")).delete();
    }

    @Test
    void retainedOrTerminatingPodPreventsPvcDeletion() {
        when(client.persistentVolumeClaims().inNamespace(NAMESPACE).withLabel("app", "pipeline-build").list())
                .thenReturn(new PersistentVolumeClaimListBuilder().withItems(claim("800", "0")).build());
        Pod pod = new PodBuilder().withNewMetadata().withName("retained-pod").endMetadata()
                .withNewStatus().withPhase("Running").endStatus().build();
        when(client.pods().inNamespace(NAMESPACE).withLabel("app", "pipeline-build")
                .withLabel("pipeline-run-id", "800").list()).thenReturn(new PodListBuilder().withItems(pod).build());

        cleanup.cleanupMarkedPods();

        verify(client.persistentVolumeClaims().inNamespace(NAMESPACE).withName("pipeline-run-800"), never()).delete();
    }

    @Test
    void ignoresActiveSharedForeignAndFutureClaims() {
        PersistentVolumeClaim active = claim("800", null);
        PersistentVolumeClaim shared = new PersistentVolumeClaimBuilder(claim("801", "0"))
                .editMetadata().withName("shared-workspace").endMetadata().build();
        PersistentVolumeClaim foreign = new PersistentVolumeClaimBuilder(claim("802", "0"))
                .editMetadata().addToLabels("app", "other-application").endMetadata().build();
        PersistentVolumeClaim future = claim("803", String.valueOf(System.currentTimeMillis() + TimeUnit.HOURS.toMillis(1)));
        when(client.persistentVolumeClaims().inNamespace(NAMESPACE).withLabel("app", "pipeline-build").list())
                .thenReturn(new PersistentVolumeClaimListBuilder().withItems(active, shared, foreign, future).build());

        cleanup.cleanupMarkedPods();

        verify(client.persistentVolumeClaims().inNamespace(NAMESPACE), never()).withName(anyString());
    }

    @Test
    void cleanupRunsWithoutSchedulerTenantContext() {
        // 调度线程没有租户上下文，环境查询必须忽略租户，否则会抛 TenantContextHolder 不存在租户编号
        when(client.persistentVolumeClaims().inNamespace(NAMESPACE).withLabel("app", "pipeline-build").list())
                .thenReturn(new PersistentVolumeClaimListBuilder().build());

        cleanup.cleanupMarkedPods();

        verify(environmentService).getEnvironment(42L);
        assertNull(TenantContextHolder.getTenantId());
    }

    private PersistentVolumeClaim claim(String runId, String deadline) {
        PersistentVolumeClaimBuilder claim = new PersistentVolumeClaimBuilder().withNewMetadata()
                .withName("pipeline-run-" + runId).addToLabels("app", "pipeline-build")
                .addToLabels("pipeline-run-id", runId).endMetadata();
        if (deadline != null) {
            claim.editMetadata().addToAnnotations("devops.cleanup-after", deadline).endMetadata();
        }
        return claim.build();
    }
}
