package cn.iocoder.yudao.module.devops.framework.kubernetes;

import cn.iocoder.yudao.framework.common.util.json.JsonUtils;
import cn.iocoder.yudao.framework.test.core.ut.BaseMockitoUnitTest;
import cn.iocoder.yudao.module.devops.controller.admin.environment.vo.EnvironmentKubernetesConfigReqVO;
import cn.iocoder.yudao.module.devops.controller.admin.environment.vo.EnvironmentSaveReqVO;
import cn.iocoder.yudao.module.devops.dal.dataobject.environment.EnvironmentDO;
import cn.iocoder.yudao.module.devops.enums.EnvironmentInfraTypeEnum;
import io.fabric8.kubernetes.client.KubernetesClient;
import org.junit.jupiter.api.Test;
import org.mockito.InjectMocks;
import org.mockito.Mock;

import static cn.iocoder.yudao.framework.test.core.util.AssertUtils.assertServiceException;
import static cn.iocoder.yudao.module.devops.enums.ErrorCodeConstants.ENVIRONMENT_KUBECONFIG_REQUIRED;
import static cn.iocoder.yudao.module.devops.enums.ErrorCodeConstants.ENVIRONMENT_KUBERNETES_NAMESPACE_REQUIRED;
import static org.junit.jupiter.api.Assertions.assertEquals;
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
