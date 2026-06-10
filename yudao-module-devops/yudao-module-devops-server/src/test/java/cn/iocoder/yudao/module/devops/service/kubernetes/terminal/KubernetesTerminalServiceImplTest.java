package cn.iocoder.yudao.module.devops.service.kubernetes.terminal;

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
import io.fabric8.kubernetes.client.dsl.ExecListenable;
import io.fabric8.kubernetes.client.dsl.ExecWatch;
import io.fabric8.kubernetes.client.dsl.MixedOperation;
import io.fabric8.kubernetes.client.dsl.NonNamespaceOperation;
import io.fabric8.kubernetes.client.dsl.PodResource;
import io.fabric8.kubernetes.client.dsl.TtyExecErrorChannelable;
import io.fabric8.kubernetes.client.dsl.TtyExecErrorable;
import io.fabric8.kubernetes.client.dsl.TtyExecOutputErrorable;
import org.junit.jupiter.api.Test;
import org.mockito.InjectMocks;
import org.mockito.Mock;

import static cn.iocoder.yudao.framework.test.core.util.AssertUtils.assertServiceException;
import static cn.iocoder.yudao.module.devops.enums.ErrorCodeConstants.ENVIRONMENT_INFRA_TYPE_NOT_SUPPORTED;
import static cn.iocoder.yudao.module.devops.enums.ErrorCodeConstants.KUBERNETES_POD_CONTAINER_NOT_EXISTS;
import static cn.iocoder.yudao.module.devops.enums.ErrorCodeConstants.KUBERNETES_POD_CONTAINER_REQUIRED;
import static cn.iocoder.yudao.module.devops.enums.ErrorCodeConstants.KUBERNETES_POD_NOT_RUNNING;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.argThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * {@link KubernetesTerminalServiceImpl} 的单元测试。
 */
@SuppressWarnings("unchecked")
public class KubernetesTerminalServiceImplTest extends BaseMockitoUnitTest {

    @InjectMocks
    private KubernetesTerminalServiceImpl kubernetesTerminalService;

    @Mock
    private EnvironmentMapper environmentMapper;
    @Mock
    private KubernetesClientFactory kubernetesClientFactory;

    @Test
    public void testOpenTerminal_singleContainerDefault() {
        // 准备参数
        KubernetesClient client = mock(KubernetesClient.class);
        PodResource podResource = mockPodLookup(client, buildRunningPod("pod-1", "app"));
        ExecWatch execWatch = mockExec(podResource, "app");
        when(environmentMapper.selectById(eq(300L))).thenReturn(buildKubernetesEnvironment());
        when(kubernetesClientFactory.create(eq("kubeconfig"))).thenReturn(client);

        // 调用
        KubernetesTerminalSession session = kubernetesTerminalService.openTerminal(300L, "prod", "pod-1", null);

        // 断言
        assertNotNull(session);
        verify(podResource).inContainer(eq("app"));
        assertNotNull(execWatch);
    }

    @Test
    public void testOpenTerminal_multiContainerRequireContainerName() {
        // 准备参数
        KubernetesClient client = mock(KubernetesClient.class);
        mockPodLookup(client, buildRunningPod("pod-1", "app", "sidecar"));
        when(environmentMapper.selectById(eq(300L))).thenReturn(buildKubernetesEnvironment());
        when(kubernetesClientFactory.create(eq("kubeconfig"))).thenReturn(client);

        // 调用 & 断言
        assertServiceException(() -> kubernetesTerminalService.openTerminal(300L, "prod", "pod-1", null),
                KUBERNETES_POD_CONTAINER_REQUIRED);
        verify(client).close();
    }

    @Test
    public void testOpenTerminal_containerNotExists() {
        // 准备参数
        KubernetesClient client = mock(KubernetesClient.class);
        mockPodLookup(client, buildRunningPod("pod-1", "app"));
        when(environmentMapper.selectById(eq(300L))).thenReturn(buildKubernetesEnvironment());
        when(kubernetesClientFactory.create(eq("kubeconfig"))).thenReturn(client);

        // 调用 & 断言
        assertServiceException(() -> kubernetesTerminalService.openTerminal(300L, "prod", "pod-1", "missing"),
                KUBERNETES_POD_CONTAINER_NOT_EXISTS, "missing");
        verify(client).close();
    }

    @Test
    public void testOpenTerminal_podNotRunning() {
        // 准备参数
        KubernetesClient client = mock(KubernetesClient.class);
        mockPodLookup(client, buildPod("pod-1", "Pending", "app"));
        when(environmentMapper.selectById(eq(300L))).thenReturn(buildKubernetesEnvironment());
        when(kubernetesClientFactory.create(eq("kubeconfig"))).thenReturn(client);

        // 调用 & 断言
        assertServiceException(() -> kubernetesTerminalService.openTerminal(300L, "prod", "pod-1", "app"),
                KUBERNETES_POD_NOT_RUNNING, "Pending");
        verify(client).close();
    }

    @Test
    public void testOpenTerminal_nonKubernetesEnvironment() {
        // 准备参数
        EnvironmentDO environment = buildKubernetesEnvironment();
        environment.setInfraType("HOST");
        when(environmentMapper.selectById(eq(300L))).thenReturn(environment);

        // 调用 & 断言
        assertServiceException(() -> kubernetesTerminalService.openTerminal(300L, "prod", "pod-1", "app"),
                ENVIRONMENT_INFRA_TYPE_NOT_SUPPORTED);
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

    private ExecWatch mockExec(PodResource podResource, String containerName) {
        ContainerResource containerResource = mock(ContainerResource.class);
        TtyExecOutputErrorable input = mock(TtyExecOutputErrorable.class);
        TtyExecErrorable output = mock(TtyExecErrorable.class);
        TtyExecErrorChannelable error = mock(TtyExecErrorChannelable.class);
        ExecListenable tty = mock(ExecListenable.class);
        ExecWatch execWatch = mock(ExecWatch.class);
        when(podResource.inContainer(eq(containerName))).thenReturn(containerResource);
        when(containerResource.redirectingInput()).thenReturn(input);
        when(input.redirectingOutput()).thenReturn(output);
        when(output.redirectingError()).thenReturn(error);
        when(error.withTTY()).thenReturn(tty);
        when(tty.exec(eq("/bin/sh"), eq("-c"), argThat(command -> command.contains("exec bash -il")
                && command.contains("exec ash -i") && command.contains("exec sh -i")))).thenReturn(execWatch);
        return execWatch;
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

    private Pod buildRunningPod(String name, String... containers) {
        return buildPod(name, "Running", containers);
    }

    private Pod buildPod(String name, String phase, String... containers) {
        PodBuilder builder = new PodBuilder()
                .withNewMetadata()
                .withName(name)
                .withNamespace("prod")
                .endMetadata()
                .withNewStatus()
                .withPhase(phase)
                .endStatus();
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
