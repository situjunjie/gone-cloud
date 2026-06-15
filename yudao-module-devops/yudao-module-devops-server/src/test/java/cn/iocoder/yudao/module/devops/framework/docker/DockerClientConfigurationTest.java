package cn.iocoder.yudao.module.devops.framework.docker;

import com.github.dockerjava.core.DockerClientConfig;
import org.junit.jupiter.api.Test;

import java.net.URI;

import static org.junit.jupiter.api.Assertions.assertEquals;

/**
 * {@link DockerClientConfiguration} 的单元测试。
 */
public class DockerClientConfigurationTest {

    @Test
    public void testDockerClientConfig_customProperties() {
        DockerClientProperties properties = new DockerClientProperties();
        properties.setHost("tcp://127.0.0.1:2375");
        properties.setTlsVerify(false);
        properties.setApiVersion("1.44");
        properties.setRegistryUrl("https://registry.example.com");
        properties.setRegistryUsername("devops");
        properties.setRegistryPassword("secret");
        properties.setRegistryEmail("devops@example.com");

        DockerClientConfig config = new DockerClientConfiguration().dockerClientConfig(properties);

        assertEquals(URI.create("tcp://127.0.0.1:2375"), config.getDockerHost());
        assertEquals("v1.44", config.getApiVersion().asWebPathPart());
        assertEquals("https://registry.example.com",
                config.effectiveAuthConfig("https://registry.example.com").getRegistryAddress());
        assertEquals("devops", config.effectiveAuthConfig("https://registry.example.com").getUsername());
        assertEquals("secret", config.effectiveAuthConfig("https://registry.example.com").getPassword());
        assertEquals("devops@example.com", config.effectiveAuthConfig("https://registry.example.com").getEmail());
    }

}
