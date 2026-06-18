package cn.iocoder.yudao.module.devops.framework.docker;

import cn.iocoder.yudao.framework.common.util.json.JsonUtils;
import cn.iocoder.yudao.framework.test.core.ut.BaseMockitoUnitTest;
import cn.iocoder.yudao.module.devops.controller.admin.environment.vo.EnvironmentDockerConfigReqVO;
import cn.iocoder.yudao.module.devops.controller.admin.environment.vo.EnvironmentDockerContainerRespVO;
import cn.iocoder.yudao.module.devops.controller.admin.environment.vo.EnvironmentSaveReqVO;
import cn.iocoder.yudao.module.devops.dal.dataobject.environment.EnvironmentDO;
import cn.iocoder.yudao.module.devops.enums.EnvironmentInfraTypeEnum;
import com.github.dockerjava.api.model.Container;
import com.github.dockerjava.api.model.ContainerPort;
import org.junit.jupiter.api.Test;
import org.mockito.InjectMocks;

import java.util.Map;

import static cn.iocoder.yudao.framework.test.core.util.AssertUtils.assertServiceException;
import static cn.iocoder.yudao.module.devops.enums.ErrorCodeConstants.ENVIRONMENT_DOCKER_HOST_REQUIRED;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/**
 * {@link DockerEnvironmentConnector} 的单元测试。
 */
public class DockerEnvironmentConnectorTest extends BaseMockitoUnitTest {

    @InjectMocks
    private DockerEnvironmentConnector connector;

    @Test
    public void testBuildInfraConfig_createDocker_success() {
        // 准备参数
        EnvironmentSaveReqVO reqVO = buildDockerReqVO("tcp://192.168.1.10:2376", true,
                "ca", "cert", "key");

        // 调用
        String infraConfig = connector.buildInfraConfig(reqVO, null);

        // 断言
        DockerEnvironmentConfig config = JsonUtils.parseObject(infraConfig, DockerEnvironmentConfig.class);
        assertEquals("tcp://192.168.1.10:2376", config.getHost());
        assertTrue(config.getTlsVerify());
        assertEquals("ca", config.getCaCert());
        assertEquals("cert", config.getClientCert());
        assertEquals("key", config.getClientKey());
    }

    @Test
    public void testBuildInfraConfig_missingHost() {
        // 准备参数
        EnvironmentSaveReqVO reqVO = buildDockerReqVO(null, false, null, null, null);

        // 调用并断言
        assertServiceException(() -> connector.buildInfraConfig(reqVO, null), ENVIRONMENT_DOCKER_HOST_REQUIRED);
    }

    @Test
    public void testBuildInfraConfig_updateKeepOldSecret() {
        // 准备参数
        EnvironmentSaveReqVO reqVO = buildDockerReqVO("tcp://192.168.1.11:2376", null, null, null, null);
        DockerEnvironmentConfig oldConfig = new DockerEnvironmentConfig();
        oldConfig.setHost("tcp://192.168.1.10:2376");
        oldConfig.setTlsVerify(true);
        oldConfig.setCaCert("old-ca");
        oldConfig.setClientCert("old-cert");
        oldConfig.setClientKey("old-key");
        EnvironmentDO oldEnvironment = new EnvironmentDO();
        oldEnvironment.setInfraType(EnvironmentInfraTypeEnum.DOCKER.getInfraType());
        oldEnvironment.setInfraConfig(JsonUtils.toJsonString(oldConfig));

        // 调用
        String infraConfig = connector.buildInfraConfig(reqVO, oldEnvironment);

        // 断言
        DockerEnvironmentConfig config = JsonUtils.parseObject(infraConfig, DockerEnvironmentConfig.class);
        assertEquals("tcp://192.168.1.11:2376", config.getHost());
        assertTrue(config.getTlsVerify());
        assertEquals("old-ca", config.getCaCert());
        assertEquals("old-cert", config.getClientCert());
        assertEquals("old-key", config.getClientKey());
    }

    @Test
    public void testConvertContainer_running() {
        // 准备参数
        Container container = mock(Container.class);
        when(container.getId()).thenReturn("1234567890abcdef");
        when(container.getNames()).thenReturn(new String[]{"/gone-server", "/alias"});
        when(container.getImage()).thenReturn("gone/server:latest");
        when(container.getImageId()).thenReturn("sha256:image");
        when(container.getCommand()).thenReturn("java -jar app.jar");
        when(container.getState()).thenReturn("running");
        when(container.getStatus()).thenReturn("Up 3 minutes");
        when(container.getCreated()).thenReturn(1780000000L);
        when(container.getLabels()).thenReturn(Map.of("app", "gone"));
        ContainerPort port = new ContainerPort()
                .withIp("0.0.0.0")
                .withPrivatePort(8080)
                .withPublicPort(18080)
                .withType("tcp");
        when(container.getPorts()).thenReturn(new ContainerPort[]{port});

        // 调用
        EnvironmentDockerContainerRespVO respVO = connector.convertContainer(container);

        // 断言
        assertEquals("1234567890abcdef", respVO.getId());
        assertEquals("1234567890ab", respVO.getShortId());
        assertEquals("gone-server", respVO.getName());
        assertEquals(2, respVO.getNames().size());
        assertEquals("gone/server:latest", respVO.getImage());
        assertEquals("running", respVO.getState());
        assertTrue(respVO.getTerminalEnabled());
        assertEquals("gone", respVO.getLabels().get("app"));
        assertEquals(1, respVO.getPorts().size());
        assertEquals(18080, respVO.getPorts().get(0).getPublicPort());
    }

    @Test
    public void testConvertContainer_notRunningTerminalDisabled() {
        // 准备参数
        Container container = mock(Container.class);
        when(container.getId()).thenReturn("1234567890abcdef");
        when(container.getNames()).thenReturn(new String[]{"/gone-server"});
        when(container.getState()).thenReturn("exited");

        // 调用
        EnvironmentDockerContainerRespVO respVO = connector.convertContainer(container);

        // 断言
        assertFalse(respVO.getTerminalEnabled());
    }

    private EnvironmentSaveReqVO buildDockerReqVO(String host, Boolean tlsVerify, String caCert, String clientCert,
                                                  String clientKey) {
        EnvironmentDockerConfigReqVO dockerConfig = new EnvironmentDockerConfigReqVO();
        dockerConfig.setHost(host);
        dockerConfig.setTlsVerify(tlsVerify);
        dockerConfig.setCaCert(caCert);
        dockerConfig.setClientCert(clientCert);
        dockerConfig.setClientKey(clientKey);

        EnvironmentSaveReqVO reqVO = new EnvironmentSaveReqVO();
        reqVO.setInfraType(EnvironmentInfraTypeEnum.DOCKER.getInfraType());
        reqVO.setDockerConfig(dockerConfig);
        return reqVO;
    }

}
