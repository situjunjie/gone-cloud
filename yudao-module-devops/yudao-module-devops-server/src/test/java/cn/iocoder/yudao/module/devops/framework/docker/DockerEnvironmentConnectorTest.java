package cn.iocoder.yudao.module.devops.framework.docker;

import cn.iocoder.yudao.framework.common.util.json.JsonUtils;
import cn.iocoder.yudao.framework.common.pojo.PageResult;
import cn.iocoder.yudao.framework.test.core.ut.BaseMockitoUnitTest;
import cn.iocoder.yudao.module.devops.controller.admin.environment.vo.EnvironmentDockerComposeProjectRespVO;
import cn.iocoder.yudao.module.devops.controller.admin.environment.vo.EnvironmentDockerConfigReqVO;
import cn.iocoder.yudao.module.devops.controller.admin.environment.vo.EnvironmentDockerContainerRespVO;
import cn.iocoder.yudao.module.devops.controller.admin.environment.vo.EnvironmentDockerImagePageReqVO;
import cn.iocoder.yudao.module.devops.controller.admin.environment.vo.EnvironmentDockerImageRespVO;
import cn.iocoder.yudao.module.devops.controller.admin.environment.vo.EnvironmentSaveReqVO;
import cn.iocoder.yudao.module.devops.dal.dataobject.environment.EnvironmentDO;
import cn.iocoder.yudao.module.devops.enums.EnvironmentInfraTypeEnum;
import com.github.dockerjava.api.DockerClient;
import com.github.dockerjava.api.command.ListContainersCmd;
import com.github.dockerjava.api.command.ListImagesCmd;
import com.github.dockerjava.api.model.Container;
import com.github.dockerjava.api.model.ContainerPort;
import com.github.dockerjava.api.model.Image;
import org.junit.jupiter.api.Test;
import org.mockito.InjectMocks;
import org.mockito.Mock;

import java.util.List;
import java.util.Map;

import static cn.iocoder.yudao.framework.test.core.util.AssertUtils.assertServiceException;
import static cn.iocoder.yudao.module.devops.enums.ErrorCodeConstants.ENVIRONMENT_DOCKER_HOST_REQUIRED;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * {@link DockerEnvironmentConnector} 的单元测试。
 */
public class DockerEnvironmentConnectorTest extends BaseMockitoUnitTest {

    @InjectMocks
    private DockerEnvironmentConnector connector;

    @Mock
    private DockerClientFactory dockerClientFactory;

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

    @Test
    public void testConvertImage_usedByComposeContainer() {
        // 准备参数
        Image image = mock(Image.class);
        when(image.getId()).thenReturn("sha256:abcdef1234567890");
        when(image.getRepoTags()).thenReturn(new String[]{"gone/server:latest"});
        when(image.getRepoDigests()).thenReturn(new String[]{"gone/server@sha256:abc"});
        when(image.getCreated()).thenReturn(1780000000L);
        when(image.getSize()).thenReturn(1024L);

        Container container = mock(Container.class);
        when(container.getId()).thenReturn("1234567890abcdef");
        when(container.getNames()).thenReturn(new String[]{"/gone-server"});
        when(container.getImage()).thenReturn("gone/server:latest");
        when(container.getImageId()).thenReturn("sha256:abcdef1234567890");
        when(container.getLabels()).thenReturn(Map.of(
                "com.docker.compose.project", "gone",
                "com.docker.compose.service", "server"));

        // 调用
        EnvironmentDockerImageRespVO respVO = connector.convertImage(image, List.of(container));

        // 断言
        assertEquals("abcdef123456", respVO.getShortId());
        assertEquals(1, respVO.getUsedContainerCount());
        assertFalse(respVO.getUnused());
        assertFalse(respVO.getDangling());
        assertEquals(List.of("gone"), respVO.getComposeProjects());
        assertEquals(List.of("gone-server"), respVO.getUsedContainerNames());
    }

    @Test
    public void testBuildComposeProjectSummary_partial() {
        // 准备参数
        Container running = mock(Container.class);
        when(running.getImage()).thenReturn("gone/server:latest");
        when(running.getState()).thenReturn("running");
        when(running.getLabels()).thenReturn(Map.of(
                "com.docker.compose.project", "gone",
                "com.docker.compose.service", "server"));
        Container stopped = mock(Container.class);
        when(stopped.getImage()).thenReturn("gone/mysql:8");
        when(stopped.getState()).thenReturn("exited");
        when(stopped.getLabels()).thenReturn(Map.of(
                "com.docker.compose.project", "gone",
                "com.docker.compose.service", "mysql"));

        // 调用
        EnvironmentDockerComposeProjectRespVO respVO = connector.buildComposeProjectSummaryForTest(
                "gone", List.of(running, stopped));

        // 断言
        assertEquals("gone", respVO.getProjectName());
        assertEquals("PARTIAL", respVO.getStatus());
        assertEquals(2, respVO.getContainerCount());
        assertEquals(1, respVO.getRunningContainerCount());
        assertEquals(2, respVO.getServiceCount());
        assertEquals(List.of("mysql", "server"), respVO.getServices());
    }

    @Test
    public void testListImages_pageAndCache() {
        // 准备参数
        EnvironmentDO environment = buildEnvironmentDO();
        DockerClient client = mockDockerClient(List.of(
                buildImage("sha256:3", "repo/c:latest", 300L),
                buildImage("sha256:2", "repo/b:latest", 200L),
                buildImage("sha256:1", "repo/a:latest", 100L)
        ), List.of());
        when(dockerClientFactory.createClient(org.mockito.ArgumentMatchers.any())).thenReturn(client);

        EnvironmentDockerImagePageReqVO reqVO = new EnvironmentDockerImagePageReqVO();
        reqVO.setId(1L);
        reqVO.setPageNo(2);
        reqVO.setPageSize(2);

        // 调用
        PageResult<EnvironmentDockerImageRespVO> pageResult = connector.listImages(reqVO, environment);
        PageResult<EnvironmentDockerImageRespVO> cachedPageResult = connector.listImages(reqVO, environment);

        // 断言
        assertEquals(3L, pageResult.getTotal());
        assertEquals(1, pageResult.getList().size());
        assertEquals("repo/a:latest", pageResult.getList().get(0).getRepoTags().get(0));
        assertEquals(3L, cachedPageResult.getTotal());
        verify(dockerClientFactory, times(1)).createClient(org.mockito.ArgumentMatchers.any());
    }

    @Test
    public void testListImages_refreshCacheReloads() {
        // 准备参数
        EnvironmentDO environment = buildEnvironmentDO();
        DockerClient firstClient = mockDockerClient(List.of(buildImage("sha256:1", "repo/a:latest", 100L)), List.of());
        DockerClient secondClient = mockDockerClient(List.of(buildImage("sha256:2", "repo/b:latest", 200L)), List.of());
        when(dockerClientFactory.createClient(org.mockito.ArgumentMatchers.any())).thenReturn(firstClient, secondClient);

        EnvironmentDockerImagePageReqVO reqVO = new EnvironmentDockerImagePageReqVO();
        reqVO.setId(1L);
        reqVO.setPageNo(1);
        reqVO.setPageSize(10);
        connector.listImages(reqVO, environment);
        reqVO.setRefreshCache(true);

        // 调用
        PageResult<EnvironmentDockerImageRespVO> pageResult = connector.listImages(reqVO, environment);

        // 断言
        assertEquals(1L, pageResult.getTotal());
        assertEquals("repo/b:latest", pageResult.getList().get(0).getRepoTags().get(0));
        verify(dockerClientFactory, times(2)).createClient(org.mockito.ArgumentMatchers.any());
    }

    @Test
    public void testListImages_configChangedReloads() {
        // 准备参数
        EnvironmentDO environment = buildEnvironmentDO();
        DockerClient firstClient = mockDockerClient(List.of(buildImage("sha256:1", "repo/a:latest", 100L)), List.of());
        DockerClient secondClient = mockDockerClient(List.of(buildImage("sha256:2", "repo/b:latest", 200L)), List.of());
        when(dockerClientFactory.createClient(org.mockito.ArgumentMatchers.any())).thenReturn(firstClient, secondClient);

        EnvironmentDockerImagePageReqVO reqVO = new EnvironmentDockerImagePageReqVO();
        reqVO.setId(1L);
        reqVO.setPageNo(1);
        reqVO.setPageSize(10);
        connector.listImages(reqVO, environment);
        environment.setInfraConfig(JsonUtils.toJsonString(buildDockerConfig("tcp://192.168.1.11:2376")));

        // 调用
        PageResult<EnvironmentDockerImageRespVO> pageResult = connector.listImages(reqVO, environment);

        // 断言
        assertEquals(1L, pageResult.getTotal());
        assertEquals("repo/b:latest", pageResult.getList().get(0).getRepoTags().get(0));
        verify(dockerClientFactory, times(2)).createClient(org.mockito.ArgumentMatchers.any());
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

    private EnvironmentDO buildEnvironmentDO() {
        EnvironmentDO environment = new EnvironmentDO();
        environment.setId(1L);
        environment.setInfraType(EnvironmentInfraTypeEnum.DOCKER.getInfraType());
        environment.setInfraConfig(JsonUtils.toJsonString(buildDockerConfig("tcp://192.168.1.10:2376")));
        return environment;
    }

    private DockerEnvironmentConfig buildDockerConfig(String host) {
        DockerEnvironmentConfig config = new DockerEnvironmentConfig();
        config.setHost(host);
        return config;
    }

    private DockerClient mockDockerClient(List<Image> images, List<Container> containers) {
        DockerClient client = mock(DockerClient.class);
        ListImagesCmd listImagesCmd = mock(ListImagesCmd.class);
        when(client.listImagesCmd()).thenReturn(listImagesCmd);
        when(listImagesCmd.withShowAll(true)).thenReturn(listImagesCmd);
        when(listImagesCmd.exec()).thenReturn(images);
        ListContainersCmd listContainersCmd = mock(ListContainersCmd.class);
        when(client.listContainersCmd()).thenReturn(listContainersCmd);
        when(listContainersCmd.withShowAll(true)).thenReturn(listContainersCmd);
        when(listContainersCmd.exec()).thenReturn(containers);
        return client;
    }

    private Image buildImage(String id, String repoTag, Long created) {
        Image image = mock(Image.class);
        when(image.getId()).thenReturn(id);
        when(image.getRepoTags()).thenReturn(new String[]{repoTag});
        when(image.getRepoDigests()).thenReturn(new String[0]);
        when(image.getCreated()).thenReturn(created);
        when(image.getSize()).thenReturn(1024L);
        return image;
    }

}
