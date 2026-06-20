package cn.iocoder.yudao.module.devops.service.artifactregistry;

import cn.iocoder.yudao.module.devops.dal.dataobject.artifactregistry.ArtifactRegistryDO;
import cn.iocoder.yudao.module.devops.enums.ArtifactRegistryAuthTypeEnum;
import cn.iocoder.yudao.module.devops.enums.ArtifactRegistryProviderTypeEnum;
import cn.iocoder.yudao.module.devops.enums.ArtifactRepositoryFormatEnum;
import cn.iocoder.yudao.module.devops.enums.ArtifactRepositoryTypeEnum;
import cn.iocoder.yudao.module.devops.service.artifactregistry.dto.ArtifactDockerSearchReqDTO;
import cn.iocoder.yudao.module.devops.service.artifactregistry.dto.ArtifactDockerSearchResultDTO;
import cn.iocoder.yudao.module.devops.service.artifactregistry.dto.ArtifactMavenSearchReqDTO;
import cn.iocoder.yudao.module.devops.service.artifactregistry.dto.ArtifactMavenSearchResultDTO;
import cn.iocoder.yudao.module.devops.service.artifactregistry.dto.ArtifactRepositoryDTO;
import com.sun.net.httpserver.HttpServer;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.concurrent.atomic.AtomicReference;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * {@link NexusArtifactRegistryClient} 的单元测试。
 */
public class NexusArtifactRegistryClientTest {

    private HttpServer httpServer;
    private ArtifactRegistryDO registry;
    private final NexusArtifactRegistryClient client = new NexusArtifactRegistryClient();

    @BeforeEach
    public void setUp() throws IOException {
        httpServer = HttpServer.create(new InetSocketAddress(0), 0);
        httpServer.start();
        registry = new ArtifactRegistryDO();
        registry.setProviderType(ArtifactRegistryProviderTypeEnum.NEXUS3.getProviderType());
        registry.setServerUrl("http://127.0.0.1:" + httpServer.getAddress().getPort());
        registry.setAuthType(ArtifactRegistryAuthTypeEnum.USERNAME_PASSWORD.getAuthType());
        registry.setUsername("admin");
        registry.setPassword("secret");
    }

    @AfterEach
    public void tearDown() {
        if (httpServer != null) {
            httpServer.stop(0);
        }
    }

    @Test
    public void testListRepositories_ignoreInvalidRepositoryRows() {
        // 准备参数
        registerJson("/service/rest/v1/repositories", """
                [
                  {"name":"maven-releases","format":"maven2","type":"hosted","url":"http://nexus/repository/maven-releases/","online":true},
                  {"name":"missing-type","format":"maven2","url":"http://nexus/repository/missing-type/","online":true},
                  {"name":"unknown-format","format":"raw","type":"hosted","url":"http://nexus/repository/raw/","online":true}
                ]
                """);

        // 调用
        List<ArtifactRepositoryDTO> repositories = client.listRepositories(registry);

        // 断言
        assertEquals(1, repositories.size());
        assertEquals("maven-releases", repositories.get(0).getRepositoryName());
        assertEquals(ArtifactRepositoryFormatEnum.MAVEN2.getFormat(), repositories.get(0).getFormat());
        assertEquals(ArtifactRepositoryTypeEnum.HOSTED.getRepositoryType(), repositories.get(0).getRepositoryType());
    }

    @Test
    public void testSearchMaven_ignoreNullItems() {
        // 准备参数
        AtomicReference<String> queryRef = new AtomicReference<>();
        registerJson("/service/rest/v1/search", """
                {
                  "continuationToken":"next",
                  "items":[
                    null,
                    {
                      "repository":"gigi-docker",
                      "format":"docker",
                      "name":"gigi-docker/yudao-gateway",
                      "version":"uat-1",
                      "assets":[{"path":"v2/gigi-docker/yudao-gateway/manifests/uat-1","downloadUrl":"http://nexus/repository/gigi-docker/v2/gigi-docker/yudao-gateway/manifests/uat-1"}]
                    },
                    {
                      "repository":"maven-releases",
                      "format":"maven2",
                      "group":"cn.iocoder.cloud",
                      "name":"yudao-module-devops-api",
                      "version":"1.0.0",
                      "assets":[{
                        "path":"cn/iocoder/cloud/yudao-module-devops-api/1.0.0/yudao-module-devops-api-1.0.0-sources.jar",
                        "downloadUrl":"http://nexus/repository/maven-releases/artifact.jar",
                        "lastModified":"2026-06-20T08:30:00.000+00:00",
                        "maven2":{"groupId":"cn.iocoder.cloud","artifactId":"yudao-module-devops-api","version":"1.0.0","classifier":"sources","extension":"jar"}
                      }]
                    }
                  ]
                }
                """, queryRef);
        ArtifactMavenSearchReqDTO reqDTO = new ArtifactMavenSearchReqDTO();
        reqDTO.setRepositoryName("maven-releases");
        reqDTO.setGroupId("cn.iocoder.cloud");
        reqDTO.setLimit(20);

        // 调用
        ArtifactMavenSearchResultDTO result = client.searchMaven(registry, reqDTO);

        // 断言
        assertFalse(queryRef.get().contains("limit="));
        assertEquals("next", result.getContinuationToken());
        assertEquals(1, result.getItems().size());
        assertEquals("maven-releases", result.getItems().get(0).getRepository());
        assertEquals("cn.iocoder.cloud", result.getItems().get(0).getGroupId());
        assertEquals("yudao-module-devops-api", result.getItems().get(0).getArtifactId());
        assertEquals("sources", result.getItems().get(0).getClassifier());
        assertEquals("jar", result.getItems().get(0).getExtension());
    }

    @Test
    public void testSearchDocker_mixedResult() {
        // 准备参数
        AtomicReference<String> queryRef = new AtomicReference<>();
        registerJson("/service/rest/v1/search", """
                {
                  "continuationToken":"next",
                  "items":[
                    {
                      "repository":"maven-snapshots",
                      "format":"maven2",
                      "group":"cn.iocoder.cloud",
                      "name":"yudao-module-devops-api",
                      "version":"1.0.0",
                      "assets":[{"path":"cn/iocoder/cloud/yudao-module-devops-api/1.0.0/yudao-module-devops-api-1.0.0.jar"}]
                    },
                    {
                      "repository":"gigi-docker",
                      "format":"docker",
                      "name":"gigi-docker/yudao-gateway",
                      "version":"uat-1",
                      "assets":[{
                        "path":"v2/gigi-docker/yudao-gateway/manifests/uat-1",
                        "downloadUrl":"http://nexus/repository/gigi-docker/v2/gigi-docker/yudao-gateway/manifests/uat-1",
                        "lastModified":"2026-06-20T08:30:00.000+00:00"
                      }]
                    }
                  ]
                }
                """, queryRef);
        ArtifactDockerSearchReqDTO reqDTO = new ArtifactDockerSearchReqDTO();
        reqDTO.setRepositoryName("gigi-docker");
        reqDTO.setKeyword("yudao");
        reqDTO.setImageName("gigi-docker/yudao-gateway");
        reqDTO.setTag("uat-1");

        // 调用
        ArtifactDockerSearchResultDTO result = client.searchDocker(registry, reqDTO);

        // 断言
        assertTrue(queryRef.get().contains("repository=gigi-docker"));
        assertTrue(queryRef.get().contains("q=yudao"));
        assertTrue(queryRef.get().contains("docker.imageName=gigi-docker/yudao-gateway"));
        assertTrue(queryRef.get().contains("docker.imageTag=uat-1"));
        assertEquals("next", result.getContinuationToken());
        assertEquals(1, result.getItems().size());
        assertEquals("gigi-docker", result.getItems().get(0).getRepository());
        assertEquals("gigi-docker/yudao-gateway", result.getItems().get(0).getImageName());
        assertEquals("uat-1", result.getItems().get(0).getTag());
        assertEquals("v2/gigi-docker/yudao-gateway/manifests/uat-1", result.getItems().get(0).getPath());
    }

    private void registerJson(String path, String body) {
        registerJson(path, body, null);
    }

    private void registerJson(String path, String body, AtomicReference<String> queryRef) {
        httpServer.createContext(path, exchange -> {
            if (queryRef != null) {
                queryRef.set(exchange.getRequestURI().getRawQuery());
            }
            byte[] bytes = body.getBytes(StandardCharsets.UTF_8);
            exchange.getResponseHeaders().add("Content-Type", "application/json");
            exchange.sendResponseHeaders(200, bytes.length);
            exchange.getResponseBody().write(bytes);
            exchange.close();
        });
    }

}
