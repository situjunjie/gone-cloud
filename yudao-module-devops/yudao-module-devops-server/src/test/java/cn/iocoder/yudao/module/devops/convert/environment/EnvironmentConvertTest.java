package cn.iocoder.yudao.module.devops.convert.environment;

import cn.iocoder.yudao.framework.common.util.json.JsonUtils;
import cn.iocoder.yudao.module.devops.controller.admin.environment.vo.EnvironmentRespVO;
import cn.iocoder.yudao.module.devops.dal.dataobject.environment.EnvironmentDO;
import cn.iocoder.yudao.module.devops.enums.EnvironmentInfraTypeEnum;
import cn.iocoder.yudao.module.devops.framework.docker.DockerEnvironmentConfig;
import cn.iocoder.yudao.module.devops.framework.kubernetes.KubernetesEnvironmentConfig;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * {@link EnvironmentConvert} 的单元测试。
 */
public class EnvironmentConvertTest {

    @Test
    public void testConvert_k8sNamespaceSummary() {
        // 准备参数
        KubernetesEnvironmentConfig config = new KubernetesEnvironmentConfig();
        config.setKubeconfig("apiVersion: v1\nclusters: []");
        config.setNamespace("test");
        EnvironmentDO environment = new EnvironmentDO();
        environment.setInfraType(EnvironmentInfraTypeEnum.K8S.getInfraType());
        environment.setInfraConfig(JsonUtils.toJsonString(config));

        // 调用
        EnvironmentRespVO respVO = EnvironmentConvert.INSTANCE.convert(environment);

        // 断言
        assertTrue(respVO.getInfraConfigConfigured());
        assertEquals("test", respVO.getKubernetesNamespace());
    }

    @Test
    public void testConvert_dockerSummary() {
        // 准备参数
        DockerEnvironmentConfig config = new DockerEnvironmentConfig();
        config.setHost("tcp://192.168.1.10:2376");
        config.setTlsVerify(true);
        config.setCaCert("secret-ca");
        config.setClientCert("secret-cert");
        config.setClientKey("secret-key");
        EnvironmentDO environment = new EnvironmentDO();
        environment.setInfraType(EnvironmentInfraTypeEnum.DOCKER.getInfraType());
        environment.setInfraConfig(JsonUtils.toJsonString(config));

        // 调用
        EnvironmentRespVO respVO = EnvironmentConvert.INSTANCE.convert(environment);

        // 断言
        assertTrue(respVO.getInfraConfigConfigured());
        assertEquals("tcp://192.168.1.10:2376", respVO.getDockerHost());
        assertTrue(respVO.getDockerTlsEnabled());
    }

}
