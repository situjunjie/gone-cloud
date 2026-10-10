package cn.iocoder.yudao.module.devops.framework.pipeline.runtime;

import cn.iocoder.yudao.framework.test.core.ut.BaseMockitoUnitTest;
import cn.iocoder.yudao.module.devops.dal.dataobject.environment.EnvironmentDO;
import cn.iocoder.yudao.module.devops.dal.dataobject.pipeline.PipelineRunDO;
import cn.iocoder.yudao.module.devops.enums.PipelineRunStatusEnum;
import cn.iocoder.yudao.module.devops.framework.config.DevopsConfig;
import cn.iocoder.yudao.module.devops.framework.kubernetes.KubernetesClientFactory;
import cn.iocoder.yudao.module.devops.framework.pipeline.PipelineSpec;
import cn.iocoder.yudao.module.devops.service.environment.EnvironmentService;
import io.fabric8.kubernetes.api.model.Container;
import io.fabric8.kubernetes.api.model.Pod;
import io.fabric8.kubernetes.api.model.PodBuilder;
import io.fabric8.kubernetes.api.model.PodListBuilder;
import io.fabric8.kubernetes.api.model.VolumeMount;
import io.fabric8.kubernetes.api.model.Volume;
import io.fabric8.kubernetes.api.model.PersistentVolumeClaim;
import io.fabric8.kubernetes.api.model.PersistentVolumeClaimBuilder;
import io.fabric8.kubernetes.client.KubernetesClient;
import io.fabric8.kubernetes.client.dsl.MixedOperation;
import io.fabric8.kubernetes.client.dsl.Resource;
import io.fabric8.kubernetes.api.model.PodList;
import io.fabric8.kubernetes.api.model.PersistentVolumeClaimList;
import io.fabric8.kubernetes.client.dsl.PodResource;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.springframework.test.util.ReflectionTestUtils;

import java.nio.file.Path;
import java.util.List;
import java.util.Map;
import java.util.concurrent.TimeUnit;
import java.util.function.UnaryOperator;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Answers.RETURNS_DEEP_STUBS;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

class K8sPipelineJobRuntimeManagerTest extends BaseMockitoUnitTest {

    private static final String NAMESPACE = "devops-build";
    private static final String POD_NAME = "pipeline-800-build";

    @InjectMocks
    private K8sPipelineJobRuntimeManager manager;
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

    private DevopsConfig config;

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
        config = new DevopsConfig();
        config.getBuild().getCluster().setEnvironmentId(42L);
        config.getBuild().getCluster().getWorkspaceStorage().setType("EMPTY_DIR");
        ReflectionTestUtils.setField(manager, "devopsConfig", config);
    }

    @Test
    void createRuntimeBoundsNamesAndBuildsKanikoWithSharedWorkspace() {
        configureEnvironment();
        PodResource podResource = readyPodResource();
        PipelineSpec.ExecutableJob job = job();
        job.setJobId("Build_" + "very-long-job-".repeat(12));
        PipelineSpec.ExecutableStep step = new PipelineSpec.ExecutableStep();
        step.setStep("KanikoBuild");
        job.setSteps(List.of(step));

        PipelineJobRuntime runtime = manager.createRuntime(run(), job, workspace());

        ArgumentCaptor<Pod> captor = ArgumentCaptor.forClass(Pod.class);
        verify(client.pods().inNamespace(NAMESPACE)).resource(captor.capture());
        Pod pod = captor.getValue();
        assertTrue(pod.getMetadata().getName().length() <= 63);
        assertTrue(pod.getMetadata().getName().matches("[a-z0-9]([-a-z0-9]*[a-z0-9])?"));
        assertTrue(pod.getMetadata().getLabels().get("pipeline-job-id").length() <= 63);
        Container builder = container(pod, "builder");
        Container kaniko = container(pod, "kaniko");
        assertEquals("builder:test", builder.getImage());
        assertEquals(config.getBuild().getCluster().getKanikoImage(), kaniko.getImage());
        assertEquals("/busybox/sh", kaniko.getCommand().get(0));
        assertEquals(builder.getVolumeMounts().get(0), kaniko.getVolumeMounts().get(0));
        assertEquals("/workspace", kaniko.getWorkingDir());
        assertNotNull(pod.getSpec().getVolumes().get(0).getEmptyDir());
        // 平台侧工作目录保持为本地 run workspace，供日志落盘使用；容器内路径固定为 /workspace
        assertEquals(Path.of("/tmp/test-run"), runtime.getWorkspace());
        assertEquals(pod.getMetadata().getName(), runtime.getRuntimeId());
        verify(podResource, never()).delete();
        verify(client).close();
    }

    @Test
    void createRuntimeWithoutKanikoUsesOnlyBuilder() {
        configureEnvironment();
        readyPodResource();

        manager.createRuntime(run(), job(), workspace());

        ArgumentCaptor<Pod> captor = ArgumentCaptor.forClass(Pod.class);
        verify(client.pods().inNamespace(NAMESPACE)).resource(captor.capture());
        assertEquals(List.of("builder"), captor.getValue().getSpec().getContainers().stream()
                .map(Container::getName).toList());
    }

    @Test
    void createRuntimeRollsBackPodWhenReadinessFails() {
        configureEnvironment();
        PodResource podResource = client.pods().inNamespace(NAMESPACE).withName("unused");
        when(client.pods().inNamespace(NAMESPACE).withName(anyString())).thenReturn(podResource);
        when(podResource.waitUntilReady(anyLong(), eq(TimeUnit.SECONDS)))
                .thenThrow(new IllegalStateException("simulated readiness timeout"));

        assertThrows(IllegalStateException.class, () -> manager.createRuntime(run(), job(), workspace()));

        verify(podResource).delete();
        verify(client).close();
    }

    @Test
    @SuppressWarnings("unchecked")
    void destroyRuntimeRetainsFailedJobEvenWhenPodIsRunning() {
        when(kubernetesClientFactory.create("test-kubeconfig")).thenReturn(client);
        config.getBuild().getCluster().getPodCleanup().setStrategy("RETAIN");
        PodResource podResource = client.pods().inNamespace(NAMESPACE).withName(POD_NAME);
        Pod pod = runningPod();
        when(podResource.get()).thenReturn(pod);
        long before = System.currentTimeMillis();

        manager.destroyRuntime(runtime(), true);

        ArgumentCaptor<UnaryOperator<Pod>> captor = ArgumentCaptor.forClass(UnaryOperator.class);
        verify(podResource).edit(captor.capture());
        Pod retained = captor.getValue().apply(pod);
        long deadline = Long.parseLong(retained.getMetadata().getAnnotations().get("devops.cleanup-after"));
        assertTrue(deadline >= before + TimeUnit.MINUTES.toMillis(30));
        assertEquals("Running", retained.getStatus().getPhase());
        verify(podResource, never()).delete();
    }

    @Test
    void destroySuccessfulJobDeletesPodEvenUnderRetainStrategy() {
        when(kubernetesClientFactory.create("test-kubeconfig")).thenReturn(client);
        config.getBuild().getCluster().getPodCleanup().setStrategy("RETAIN");

        manager.destroyRuntime(runtime(), false);

        verify(client.pods().inNamespace(NAMESPACE).withName(POD_NAME)).delete();
    }

    @Test
    void cleanupRunDeletesDynamicPvcAfterLastPodDisappears() {
        configureEnvironment();
        config.getBuild().getCluster().getWorkspaceStorage().setType("DYNAMIC_PVC");
        when(client.persistentVolumeClaims().inNamespace(NAMESPACE).withName("pipeline-run-800").get())
                .thenReturn(ownedClaim());
        when(client.pods().inNamespace(NAMESPACE).withLabel("app", "pipeline-build")
                .withLabel("pipeline-run-id", "800").list())
                .thenReturn(new PodListBuilder().withItems(runningPod()).build(), new PodListBuilder().build());

        manager.cleanupRun(run());

        verify(client.pods().inNamespace(NAMESPACE).withName(POD_NAME)).delete();
        verify(client.persistentVolumeClaims().inNamespace(NAMESPACE).withName("pipeline-run-800")).delete();
    }

    @Test
    void cleanupRunKeepsDynamicPvcWhileFailedPodIsRetained() {
        configureEnvironment();
        config.getBuild().getCluster().getWorkspaceStorage().setType("DYNAMIC_PVC");
        when(client.persistentVolumeClaims().inNamespace(NAMESPACE).withName("pipeline-run-800").get())
                .thenReturn(ownedClaim());
        Pod pod = new PodBuilder(runningPod()).editMetadata().addToAnnotations("devops.cleanup-after",
                String.valueOf(System.currentTimeMillis() + TimeUnit.HOURS.toMillis(1))).endMetadata().build();
        when(client.pods().inNamespace(NAMESPACE).withLabel("app", "pipeline-build")
                .withLabel("pipeline-run-id", "800").list()).thenReturn(new PodListBuilder().withItems(pod).build());

        manager.cleanupRun(run());

        verify(client.pods().inNamespace(NAMESPACE).withName(POD_NAME), never()).delete();
        verify(client.persistentVolumeClaims().inNamespace(NAMESPACE).withName("pipeline-run-800"), never()).delete();
    }

    @Test
    @SuppressWarnings("unchecked")
    void cleanupRunMarksPvcForRetryWhenPodDeletionIsAsynchronous() {
        configureEnvironment();
        when(client.persistentVolumeClaims().inNamespace(NAMESPACE).withName("pipeline-run-800").get())
                .thenReturn(ownedClaim());
        when(client.pods().inNamespace(NAMESPACE).withLabel("app", "pipeline-build")
                .withLabel("pipeline-run-id", "800").list()).thenReturn(new PodListBuilder().withItems(runningPod()).build());

        manager.cleanupRun(run());

        ArgumentCaptor<UnaryOperator<PersistentVolumeClaim>> captor = ArgumentCaptor.forClass(UnaryOperator.class);
        verify(client.persistentVolumeClaims().inNamespace(NAMESPACE).withName("pipeline-run-800")).edit(captor.capture());
        PersistentVolumeClaim marked = captor.getValue().apply(ownedClaim());
        assertTrue(Long.parseLong(marked.getMetadata().getAnnotations().get("devops.cleanup-after")) <= System.currentTimeMillis());
        verify(client.persistentVolumeClaims().inNamespace(NAMESPACE).withName("pipeline-run-800"), never()).delete();
    }

    @Test
    void cleanupRunDoesNotTouchPvcOwnedByAnotherApplication() {
        configureEnvironment();
        PersistentVolumeClaim foreign = new PersistentVolumeClaimBuilder(ownedClaim()).editMetadata()
                .addToLabels("app", "other-application").endMetadata().build();
        when(client.persistentVolumeClaims().inNamespace(NAMESPACE).withName("pipeline-run-800").get()).thenReturn(foreign);
        when(client.pods().inNamespace(NAMESPACE).withLabel("app", "pipeline-build")
                .withLabel("pipeline-run-id", "800").list()).thenReturn(new PodListBuilder().build());

        manager.cleanupRun(run());

        verify(client.persistentVolumeClaims().inNamespace(NAMESPACE).withName("pipeline-run-800"), never()).delete();
        verify(client.persistentVolumeClaims().inNamespace(NAMESPACE).withName("pipeline-run-800"), never()).edit(any(UnaryOperator.class));
    }

    @Test
    void cleanupRunLeavesRunningAndSuspendedRunsUntouched() {
        PipelineRunDO run = run();
        run.setRunStatus(PipelineRunStatusEnum.WAITING_INPUT.getStatus());
        manager.cleanupRun(run);
        run.setRunStatus(PipelineRunStatusEnum.RUNNING.getStatus());
        manager.cleanupRun(run);
        verifyNoInteractions(environmentService, kubernetesClientFactory, client);
    }

    @Test
    void cacheMountsAreIsolatedByTenantAndDefinition() {
        configureEnvironment();
        readyPodResource();
        config.getBuild().getCluster().getWorkspaceStorage().setCachePvcName("pipeline-caches");
        PipelineRunDO run = run();
        PipelineWorkspace workspace1 = workspace();
        workspace1.setCacheMounts(List.of(PipelineCacheMount.builder().id("maven").containerPath("/root/.m2")
                .canonicalPath("/root/.m2").build()));
        manager.createRuntime(run, job(), workspace1);

        PipelineWorkspace workspace2 = workspace();
        workspace2.setCacheMounts(List.of(PipelineCacheMount.builder().id("maven").containerPath("/root/.m2")
                .canonicalPath("/root/.m2").build()));
        run.setTenantId(2L);
        manager.createRuntime(run, job(), workspace2);

        PipelineWorkspace workspace3 = workspace();
        workspace3.setCacheMounts(List.of(PipelineCacheMount.builder().id("maven").containerPath("/root/.m2")
                .canonicalPath("/root/.m2").build()));
        workspace3.setCacheKey("definition-31");
        manager.createRuntime(run, job(), workspace3);

        ArgumentCaptor<Pod> captor = ArgumentCaptor.forClass(Pod.class);
        verify(client.pods().inNamespace(NAMESPACE), times(3)).resource(captor.capture());

        // 验证所有 Pod 都挂载 cache 到 /cache-root，不使用 subPath
        List<VolumeMount> cacheMounts = captor.getAllValues().stream()
                .map(pod -> container(pod, "builder").getVolumeMounts()
                        .stream().filter(mount -> "cache".equals(mount.getName())).findFirst().orElseThrow())
                .toList();
        assertTrue(cacheMounts.stream().allMatch(mount -> "/cache-root".equals(mount.getMountPath())));
        assertTrue(cacheMounts.stream().allMatch(mount -> mount.getSubPath() == null || mount.getSubPath().isEmpty()));

        // 验证隔离通过 workspace metadata 中的 cacheMappings 实现
        List<PipelineWorkspace> workspaces = List.of(workspace1, workspace2, workspace3);
        List<String> sources = workspaces.stream()
                .map(ws -> (List<Map<String, String>>) ws.getMetadata().get("cacheMappings"))
                .map(mappings -> mappings.get(0).get("source"))
                .toList();
        assertEquals(3, sources.stream().distinct().count(), "Cache source paths should be isolated");
    }

    @Test
    void defaultKubernetesJobAllowsMissingRunsOn() {
        configureEnvironment();
        readyPodResource();
        PipelineSpec.ExecutableJob job = job();
        job.setRunsOn(null);
        PipelineJobRuntime runtime = manager.createRuntime(run(), job, workspace());
        assertEquals("kubernetes/default", runtime.getExecutorGroup());
        assertEquals(config.getBuild().getCluster().getDefaultImage(), runtime.getExecutorImage());
    }

    @Test
    void restoreRejectsPodOwnedByDifferentJob() {
        configureEnvironment();
        when(podResource.get()).thenReturn(new PodBuilder(runningPod()).editMetadata()
                .addToLabels("pipeline-job-id", "other-job").endMetadata().build());
        assertThrows(IllegalStateException.class, () -> manager.restoreRuntime(run(), job(), workspace(), POD_NAME));
    }

    @Test
    void sharedPvcCacheReusesWorkspaceVolume() {
        configureEnvironment();
        readyPodResource();
        config.getBuild().getCluster().getWorkspaceStorage().setType("SHARED_PVC");
        config.getBuild().getCluster().getWorkspaceStorage().setSharedPvcName("pipeline-shared-workspace");
        config.getBuild().getCluster().getWorkspaceStorage().setCachePvcName(null);
        PipelineWorkspace workspace = workspace();
        workspace.setCacheMounts(List.of(PipelineCacheMount.builder().id("maven").containerPath("/root/.m2")
                .canonicalPath("/root/.m2").build()));

        manager.createRuntime(run(), job(), workspace);

        ArgumentCaptor<Pod> captor = ArgumentCaptor.forClass(Pod.class);
        verify(pods).resource(captor.capture());
        Pod pod = captor.getValue();
        // 同一个 PVC 只能声明一个 Volume，否则 kubelet 卷协调失败
        List<Volume> pvcVolumes = pod.getSpec().getVolumes().stream()
                .filter(volume -> volume.getPersistentVolumeClaim() != null)
                .toList();
        assertEquals(1, pvcVolumes.size());
        assertEquals("pipeline-shared-workspace", pvcVolumes.get(0).getPersistentVolumeClaim().getClaimName());
        List<VolumeMount> mounts = container(pod, "builder").getVolumeMounts().stream()
                .filter(mount -> pvcVolumes.get(0).getName().equals(mount.getName()))
                .toList();
        assertEquals(2, mounts.size());
        assertEquals(List.of("/workspace", "/cache-root"),
                mounts.stream().map(VolumeMount::getMountPath).toList());
        // 工作空间用 subPath 直接挂到 /workspace，避免容器内软链绕过 pwd -P 校验
        VolumeMount workspaceMount = mounts.stream()
                .filter(mount -> "/workspace".equals(mount.getMountPath())).findFirst().orElseThrow();
        assertEquals("runs/800", workspaceMount.getSubPath());
    }

    @Test
    void emptyDirCacheDoesNotRequireUnconfiguredSharedPvc() {
        configureEnvironment();
        readyPodResource();
        PipelineWorkspace workspace = workspace();
        workspace.setCacheMounts(List.of(PipelineCacheMount.builder().id("maven").containerPath("/root/.m2").build()));
        manager.createRuntime(run(), job(), workspace);
        ArgumentCaptor<Pod> captor = ArgumentCaptor.forClass(Pod.class);
        verify(pods).resource(captor.capture());
        assertTrue(captor.getValue().getSpec().getVolumes().stream().noneMatch(volume -> "cache".equals(volume.getName())));
    }

    private PersistentVolumeClaim ownedClaim() {
        return new PersistentVolumeClaimBuilder().withNewMetadata().withName("pipeline-run-800")
                .addToLabels("app", "pipeline-build").addToLabels("pipeline-run-id", "800").endMetadata().build();
    }

    private void configureEnvironment() {
        EnvironmentDO environment = new EnvironmentDO();
        environment.setId(42L);
        environment.setInfraType("K8S");
        environment.setInfraConfig("{\"kubeconfig\":\"test-kubeconfig\"}");
        when(environmentService.getEnvironment(42L)).thenReturn(environment);
        when(kubernetesClientFactory.create("test-kubeconfig")).thenReturn(client);
    }

    private PodResource readyPodResource() {
        PodResource resource = mock(PodResource.class);
        when(client.pods().inNamespace(NAMESPACE).withName(anyString())).thenReturn(resource);
        when(resource.waitUntilReady(anyLong(), eq(TimeUnit.SECONDS))).thenReturn(runningPod());
        return resource;
    }

    private Container container(Pod pod, String name) {
        return pod.getSpec().getContainers().stream().filter(c -> name.equals(c.getName())).findFirst().orElseThrow();
    }

    private Pod runningPod() {
        return new PodBuilder().withNewMetadata().withName(POD_NAME)
                .addToLabels("pipeline-run-id", "800").endMetadata()
                .withNewStatus().withPhase("Running").endStatus().build();
    }

    private PipelineJobRuntime runtime() {
        return PipelineJobRuntime.builder().runtimeType("KUBERNETES").runtimeId(POD_NAME)
                .metadata(Map.of("namespace", NAMESPACE, "kubeconfig", "test-kubeconfig")).build();
    }

    private PipelineRunDO run() {
        PipelineRunDO run = new PipelineRunDO();
        run.setId(800L);
        run.setTenantId(1L);
        run.setRunStatus(PipelineRunStatusEnum.SUCCESS.getStatus());
        return run;
    }

    private PipelineSpec.ExecutableJob job() {
        PipelineSpec.ExecutableJob job = new PipelineSpec.ExecutableJob();
        job.setJobId("build");
        PipelineSpec.RunsOn runsOn = new PipelineSpec.RunsOn();
        runsOn.setGroup("kubernetes/default");
        runsOn.setContainer("builder:test");
        job.setRunsOn(runsOn);
        return job;
    }

    private PipelineWorkspace workspace() {
        return PipelineWorkspace.builder().runWorkspace(Path.of("/tmp/test-run"))
                .cacheKey("definition-30").cacheMounts(List.of()).build();
    }
}
