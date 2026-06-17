package cn.iocoder.yudao.module.devops.service.kubernetes.log;

import cn.iocoder.yudao.framework.common.util.json.JsonUtils;
import cn.iocoder.yudao.framework.test.core.ut.BaseMockitoUnitTest;
import cn.iocoder.yudao.module.devops.dal.dataobject.environment.EnvironmentDO;
import cn.iocoder.yudao.module.devops.dal.mysql.environment.EnvironmentMapper;
import cn.iocoder.yudao.module.devops.enums.EnvironmentInfraTypeEnum;
import cn.iocoder.yudao.module.devops.framework.kubernetes.KubernetesClientFactory;
import cn.iocoder.yudao.module.devops.framework.kubernetes.KubernetesEnvironmentConfig;
import io.fabric8.kubernetes.api.model.Pod;
import io.fabric8.kubernetes.api.model.PodBuilder;
import io.fabric8.kubernetes.api.model.PodList;
import io.fabric8.kubernetes.client.KubernetesClient;
import io.fabric8.kubernetes.client.dsl.ContainerResource;
import io.fabric8.kubernetes.client.dsl.LogWatch;
import io.fabric8.kubernetes.client.dsl.MixedOperation;
import io.fabric8.kubernetes.client.dsl.NonNamespaceOperation;
import io.fabric8.kubernetes.client.dsl.PodResource;
import io.fabric8.kubernetes.client.dsl.PrettyLoggable;
import org.junit.jupiter.api.Test;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

import java.io.ByteArrayInputStream;
import java.nio.charset.StandardCharsets;
import java.util.concurrent.Executor;

import static cn.iocoder.yudao.framework.test.core.util.AssertUtils.assertServiceException;
import static cn.iocoder.yudao.module.devops.enums.ErrorCodeConstants.ENVIRONMENT_KUBERNETES_NAMESPACE_NOT_MATCH;
import static cn.iocoder.yudao.module.devops.enums.ErrorCodeConstants.KUBERNETES_POD_CONTAINER_REQUIRED;
import static cn.iocoder.yudao.module.devops.enums.ErrorCodeConstants.KUBERNETES_POD_NOT_EXISTS;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * {@link KubernetesPodLogServiceImpl} 的单元测试。
 */
@SuppressWarnings("unchecked")
public class KubernetesPodLogServiceImplTest extends BaseMockitoUnitTest {

    @InjectMocks
    private KubernetesPodLogServiceImpl kubernetesPodLogService;

    @Mock
    private EnvironmentMapper environmentMapper;
    @Mock
    private KubernetesClientFactory kubernetesClientFactory;

    @Test
    public void testStreamPodLogs_singleContainerDefault() {
        // 准备参数
        KubernetesClient client = mock(KubernetesClient.class);
        PodResource podResource = mockPodLookup(client, buildPod("pod-1", "app"));
        ContainerResource containerResource = mock(ContainerResource.class);
        PrettyLoggable loggable = mock(PrettyLoggable.class);
        LogWatch logWatch = mock(LogWatch.class);
        when(podResource.inContainer(eq("app"))).thenReturn(containerResource);
        when(containerResource.tailingLines(eq(10))).thenReturn(loggable);
        when(loggable.watchLog()).thenReturn(logWatch);
        when(logWatch.getOutput()).thenReturn(new ByteArrayInputStream("hello\nworld\n".getBytes(StandardCharsets.UTF_8)));
        when(environmentMapper.selectById(eq(300L))).thenReturn(buildKubernetesEnvironment());
        when(kubernetesClientFactory.create(eq("kubeconfig"))).thenReturn(client);
        ReflectionTestUtils.setField(kubernetesPodLogService, "pipelineLogStreamExecutor", (Executor) Runnable::run);

        // 调用
        SseEmitter emitter = kubernetesPodLogService.streamPodLogs(300L, null, "pod-1", null, 10);

        // 断言
        assertNotNull(emitter);
        verify(podResource).inContainer(eq("app"));
        verify(containerResource).tailingLines(eq(10));
        verify(logWatch).close();
        verify(client).close();
    }

    @Test
    public void testStreamPodLogs_multiContainerRequireContainerName() {
        // 准备参数
        KubernetesClient client = mock(KubernetesClient.class);
        mockPodLookup(client, buildPod("pod-1", "app", "sidecar"));
        when(environmentMapper.selectById(eq(300L))).thenReturn(buildKubernetesEnvironment());
        when(kubernetesClientFactory.create(eq("kubeconfig"))).thenReturn(client);

        // 调用 & 断言
        assertServiceException(() -> kubernetesPodLogService.streamPodLogs(300L, null, "pod-1", null, 10),
                KUBERNETES_POD_CONTAINER_REQUIRED);
        verify(client).close();
    }

    @Test
    public void testStreamPodLogs_podNotExists() {
        // 准备参数
        KubernetesClient client = mock(KubernetesClient.class);
        mockPodLookup(client, null);
        when(environmentMapper.selectById(eq(300L))).thenReturn(buildKubernetesEnvironment());
        when(kubernetesClientFactory.create(eq("kubeconfig"))).thenReturn(client);

        // 调用 & 断言
        assertServiceException(() -> kubernetesPodLogService.streamPodLogs(300L, null, "pod-1", "app", 10),
                KUBERNETES_POD_NOT_EXISTS, "pod-1");
        verify(client).close();
    }

    @Test
    public void testStreamPodLogs_namespaceNotMatch() {
        // 准备参数
        when(environmentMapper.selectById(eq(300L))).thenReturn(buildKubernetesEnvironment());

        // 调用 & 断言
        assertServiceException(() -> kubernetesPodLogService.streamPodLogs(300L, "other", "pod-1", "app", 10),
                ENVIRONMENT_KUBERNETES_NAMESPACE_NOT_MATCH, "prod");
    }

    private PodResource mockPodLookup(KubernetesClient client, Pod pod) {
        MixedOperation<Pod, PodList, PodResource> podOperation = mock(MixedOperation.class);
        NonNamespaceOperation<Pod, PodList, PodResource> namespacedPodOperation = mock(NonNamespaceOperation.class);
        PodResource podResource = mock(PodResource.class);
        when(client.pods()).thenReturn(podOperation);
        when(podOperation.inNamespace(eq("prod"))).thenReturn(namespacedPodOperation);
        when(namespacedPodOperation.withName(eq("pod-1"))).thenReturn(podResource);
        when(podResource.get()).thenReturn(pod);
        return podResource;
    }

    private EnvironmentDO buildKubernetesEnvironment() {
        EnvironmentDO environment = new EnvironmentDO();
        environment.setId(300L);
        environment.setInfraType(EnvironmentInfraTypeEnum.K8S.getInfraType());
        KubernetesEnvironmentConfig config = new KubernetesEnvironmentConfig();
        config.setKubeconfig("kubeconfig");
        config.setNamespace("prod");
        environment.setInfraConfig(JsonUtils.toJsonString(config));
        return environment;
    }

    private Pod buildPod(String name, String... containers) {
        PodBuilder builder = new PodBuilder()
                .withNewMetadata()
                .withName(name)
                .withNamespace("prod")
                .endMetadata();
        for (String container : containers) {
            builder.editOrNewSpec()
                    .addNewContainer()
                    .withName(container)
                    .withImage("registry.example.com/" + container + ":latest")
                    .endContainer()
                    .endSpec();
        }
        return builder.build();
    }

}
