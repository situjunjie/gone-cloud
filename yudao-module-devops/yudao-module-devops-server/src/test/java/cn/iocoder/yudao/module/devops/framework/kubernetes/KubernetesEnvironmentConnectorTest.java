package cn.iocoder.yudao.module.devops.framework.kubernetes;

import cn.iocoder.yudao.framework.common.util.json.JsonUtils;
import cn.iocoder.yudao.framework.test.core.ut.BaseMockitoUnitTest;
import cn.iocoder.yudao.module.devops.controller.admin.environment.vo.EnvironmentKubernetesDeploymentRespVO;
import cn.iocoder.yudao.module.devops.controller.admin.environment.vo.EnvironmentKubernetesPodRespVO;
import cn.iocoder.yudao.module.devops.controller.admin.environment.vo.EnvironmentKubernetesServiceRespVO;
import cn.iocoder.yudao.module.devops.controller.admin.environment.vo.EnvironmentKubernetesConfigReqVO;
import cn.iocoder.yudao.module.devops.controller.admin.environment.vo.EnvironmentSaveReqVO;
import cn.iocoder.yudao.module.devops.dal.dataobject.environment.EnvironmentDO;
import cn.iocoder.yudao.module.devops.enums.EnvironmentInfraTypeEnum;
import io.fabric8.kubernetes.api.model.Pod;
import io.fabric8.kubernetes.api.model.PodBuilder;
import io.fabric8.kubernetes.api.model.Service;
import io.fabric8.kubernetes.api.model.ServiceBuilder;
import io.fabric8.kubernetes.api.model.apps.Deployment;
import io.fabric8.kubernetes.api.model.apps.DeploymentBuilder;
import io.fabric8.kubernetes.client.KubernetesClient;
import org.junit.jupiter.api.Test;
import org.mockito.InjectMocks;
import org.mockito.Mock;

import static cn.iocoder.yudao.framework.test.core.util.AssertUtils.assertServiceException;
import static cn.iocoder.yudao.module.devops.enums.ErrorCodeConstants.ENVIRONMENT_KUBECONFIG_REQUIRED;
import static cn.iocoder.yudao.module.devops.enums.ErrorCodeConstants.ENVIRONMENT_KUBERNETES_NAMESPACE_REQUIRED;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * {@link KubernetesEnvironmentConnector} 的单元测试。
 */
public class KubernetesEnvironmentConnectorTest extends BaseMockitoUnitTest {

    @InjectMocks
    private KubernetesEnvironmentConnector connector;

    @Mock
    private KubernetesClientFactory kubernetesClientFactory;
    @Mock
    private KubernetesClient kubernetesClient;

    @Test
    public void testBuildInfraConfig_createK8s_success() {
        // 准备参数
        String kubeconfig = "apiVersion: v1\nclusters: []";
        String namespace = "test";
        EnvironmentSaveReqVO reqVO = buildKubernetesReqVO(kubeconfig, namespace);
        when(kubernetesClientFactory.create(eq(kubeconfig))).thenReturn(kubernetesClient);

        // 调用
        String infraConfig = connector.buildInfraConfig(reqVO, null);

        // 断言
        KubernetesEnvironmentConfig config = JsonUtils.parseObject(infraConfig, KubernetesEnvironmentConfig.class);
        assertEquals(kubeconfig, config.getKubeconfig());
        assertEquals(namespace, config.getNamespace());
        verify(kubernetesClientFactory).create(eq(kubeconfig));
        verify(kubernetesClient).close();
    }

    @Test
    public void testBuildInfraConfig_createK8s_missingKubeconfig() {
        // 准备参数
        EnvironmentSaveReqVO reqVO = buildKubernetesReqVO(null, "test");

        // 调用并断言
        assertServiceException(() -> connector.buildInfraConfig(reqVO, null), ENVIRONMENT_KUBECONFIG_REQUIRED);
    }

    @Test
    public void testBuildInfraConfig_createK8s_missingNamespace() {
        // 准备参数
        EnvironmentSaveReqVO reqVO = buildKubernetesReqVO("apiVersion: v1\nclusters: []", null);

        // 调用并断言
        assertServiceException(() -> connector.buildInfraConfig(reqVO, null), ENVIRONMENT_KUBERNETES_NAMESPACE_REQUIRED);
    }

    @Test
    public void testBuildInfraConfig_updateK8s_keepOldConfig() {
        // 准备参数
        EnvironmentSaveReqVO reqVO = buildKubernetesReqVO(null, null);
        EnvironmentDO oldEnvironment = new EnvironmentDO();
        oldEnvironment.setInfraType(EnvironmentInfraTypeEnum.K8S.getInfraType());
        oldEnvironment.setInfraConfig("{\"kubeconfig\":\"old\",\"namespace\":\"old-test\"}");

        // 调用
        String infraConfig = connector.buildInfraConfig(reqVO, oldEnvironment);

        // 断言
        KubernetesEnvironmentConfig config = JsonUtils.parseObject(infraConfig, KubernetesEnvironmentConfig.class);
        assertEquals("old", config.getKubeconfig());
        assertEquals("old-test", config.getNamespace());
    }

    @Test
    public void testBuildInfraConfig_updateK8s_updateNamespaceOnly() {
        // 准备参数
        EnvironmentSaveReqVO reqVO = buildKubernetesReqVO(null, "prod");
        EnvironmentDO oldEnvironment = new EnvironmentDO();
        oldEnvironment.setInfraType(EnvironmentInfraTypeEnum.K8S.getInfraType());
        oldEnvironment.setInfraConfig("{\"kubeconfig\":\"old\",\"namespace\":\"test\"}");

        // 调用
        String infraConfig = connector.buildInfraConfig(reqVO, oldEnvironment);

        // 断言
        KubernetesEnvironmentConfig config = JsonUtils.parseObject(infraConfig, KubernetesEnvironmentConfig.class);
        assertEquals("old", config.getKubeconfig());
        assertEquals("prod", config.getNamespace());
    }

    @Test
    public void testBuildInfraConfig_updateFromHostToK8s_missingKubeconfig() {
        // 准备参数
        EnvironmentSaveReqVO reqVO = buildKubernetesReqVO(null, "test");
        EnvironmentDO oldEnvironment = new EnvironmentDO();
        oldEnvironment.setInfraType(EnvironmentInfraTypeEnum.HOST.getInfraType());
        oldEnvironment.setInfraConfig("{\"hostGroupId\":1}");

        // 调用并断言
        assertServiceException(() -> connector.buildInfraConfig(reqVO, oldEnvironment), ENVIRONMENT_KUBECONFIG_REQUIRED);
    }

    @Test
    public void testConvertPod_runningMultiContainer() {
        // 准备参数
        Pod pod = new PodBuilder()
                .withNewMetadata()
                .withName("server-1")
                .withNamespace("prod")
                .withCreationTimestamp("2026-06-10T10:00:00Z")
                .endMetadata()
                .withNewSpec()
                .withNodeName("node-1")
                .addNewContainer().withName("app").withImage("registry/app:1").endContainer()
                .addNewContainer().withName("sidecar").withImage("registry/sidecar:1").endContainer()
                .endSpec()
                .withNewStatus()
                .withPhase("Running")
                .withPodIP("10.244.1.10")
                .addNewContainerStatus().withName("app").withReady(true).withRestartCount(1).endContainerStatus()
                .addNewContainerStatus().withName("sidecar").withReady(false).withRestartCount(2).endContainerStatus()
                .endStatus()
                .build();

        // 调用
        EnvironmentKubernetesPodRespVO respVO = connector.convertPod(pod);

        // 断言
        assertEquals("server-1", respVO.getName());
        assertEquals("prod", respVO.getNamespace());
        assertEquals("Running", respVO.getPhase());
        assertEquals(1, respVO.getReadyContainerCount());
        assertEquals(2, respVO.getTotalContainerCount());
        assertEquals(3, respVO.getRestartCount());
        assertEquals("node-1", respVO.getNodeName());
        assertEquals("10.244.1.10", respVO.getPodIp());
        assertEquals(2, respVO.getContainerNames().size());
        assertTrue(respVO.getContainerNames().contains("app"));
        assertTrue(respVO.getTerminalEnabled());
    }

    @Test
    public void testConvertPod_notRunningTerminalDisabled() {
        // 准备参数
        Pod pod = new PodBuilder()
                .withNewMetadata()
                .withName("server-1")
                .withNamespace("prod")
                .endMetadata()
                .withNewStatus()
                .withPhase("Pending")
                .endStatus()
                .build();

        // 调用
        EnvironmentKubernetesPodRespVO respVO = connector.convertPod(pod);

        // 断言
        assertEquals("Pending", respVO.getPhase());
        assertFalse(respVO.getTerminalEnabled());
    }

    @Test
    public void testConvertDeployment() {
        // 准备参数
        Deployment deployment = new DeploymentBuilder()
                .withNewMetadata()
                .withName("server")
                .withNamespace("prod")
                .withCreationTimestamp("2026-06-10T10:00:00Z")
                .endMetadata()
                .withNewSpec()
                .withReplicas(3)
                .withNewTemplate()
                .withNewSpec()
                .addNewContainer().withName("app").withImage("registry/app:1").endContainer()
                .addNewContainer().withName("worker").withImage("registry/worker:1").endContainer()
                .endSpec()
                .endTemplate()
                .endSpec()
                .withNewStatus()
                .withReadyReplicas(2)
                .withAvailableReplicas(2)
                .withUpdatedReplicas(3)
                .endStatus()
                .build();

        // 调用
        EnvironmentKubernetesDeploymentRespVO respVO = connector.convertDeployment(deployment);

        // 断言
        assertEquals("server", respVO.getName());
        assertEquals("prod", respVO.getNamespace());
        assertEquals(3, respVO.getReplicas());
        assertEquals(2, respVO.getReadyReplicas());
        assertEquals(2, respVO.getAvailableReplicas());
        assertEquals(3, respVO.getUpdatedReplicas());
        assertEquals(2, respVO.getImages().size());
        assertTrue(respVO.getImages().contains("registry/app:1"));
    }

    @Test
    public void testConvertService() {
        // 准备参数
        Service service = new ServiceBuilder()
                .withNewMetadata()
                .withName("server")
                .withNamespace("prod")
                .withCreationTimestamp("2026-06-10T10:00:00Z")
                .endMetadata()
                .withNewSpec()
                .withType("NodePort")
                .withClusterIP("10.96.1.10")
                .addToExternalIPs("1.2.3.4")
                .addToSelector("app", "server")
                .addNewPort()
                .withName("http")
                .withProtocol("TCP")
                .withPort(80)
                .withNewTargetPort(8080)
                .withNodePort(30080)
                .endPort()
                .endSpec()
                .build();

        // 调用
        EnvironmentKubernetesServiceRespVO respVO = connector.convertService(service);

        // 断言
        assertEquals("server", respVO.getName());
        assertEquals("prod", respVO.getNamespace());
        assertEquals("NodePort", respVO.getType());
        assertEquals("10.96.1.10", respVO.getClusterIp());
        assertEquals("1.2.3.4", respVO.getExternalIps().get(0));
        assertEquals("server", respVO.getSelector().get("app"));
        assertEquals(1, respVO.getPorts().size());
        assertEquals("http", respVO.getPorts().get(0).getName());
        assertEquals(80, respVO.getPorts().get(0).getPort());
        assertEquals("8080", respVO.getPorts().get(0).getTargetPort());
        assertEquals(30080, respVO.getPorts().get(0).getNodePort());
    }

    private EnvironmentSaveReqVO buildKubernetesReqVO(String kubeconfig, String namespace) {
        EnvironmentKubernetesConfigReqVO kubernetesConfig = new EnvironmentKubernetesConfigReqVO();
        kubernetesConfig.setKubeconfig(kubeconfig);
        kubernetesConfig.setNamespace(namespace);

        EnvironmentSaveReqVO reqVO = new EnvironmentSaveReqVO();
        reqVO.setInfraType(EnvironmentInfraTypeEnum.K8S.getInfraType());
        reqVO.setKubernetesConfig(kubernetesConfig);
        return reqVO;
    }

}
