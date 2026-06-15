package cn.iocoder.yudao.module.devops.framework.docker;

import cn.hutool.core.util.StrUtil;
import com.github.dockerjava.api.DockerClient;
import com.github.dockerjava.core.DefaultDockerClientConfig;
import com.github.dockerjava.core.DockerClientConfig;
import com.github.dockerjava.core.DockerClientImpl;
import com.github.dockerjava.httpclient5.ApacheDockerHttpClient;
import com.github.dockerjava.transport.DockerHttpClient;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * Docker 客户端配置。
 */
@Configuration(proxyBeanMethods = false)
@EnableConfigurationProperties(DockerClientProperties.class)
public class DockerClientConfiguration {

    @Bean
    public DockerClientConfig dockerClientConfig(DockerClientProperties properties) {
        DefaultDockerClientConfig.Builder builder = DefaultDockerClientConfig.createDefaultConfigBuilder();
        if (StrUtil.isNotBlank(properties.getHost())) {
            builder.withDockerHost(properties.getHost());
        }
        if (properties.getTlsVerify() != null) {
            builder.withDockerTlsVerify(properties.getTlsVerify());
        }
        if (StrUtil.isNotBlank(properties.getCertPath())) {
            builder.withDockerCertPath(properties.getCertPath());
        }
        if (StrUtil.isNotBlank(properties.getConfigPath())) {
            builder.withDockerConfig(properties.getConfigPath());
        }
        if (StrUtil.isNotBlank(properties.getApiVersion())) {
            builder.withApiVersion(properties.getApiVersion());
        }
        if (StrUtil.isNotBlank(properties.getRegistryUrl())) {
            builder.withRegistryUrl(properties.getRegistryUrl());
        }
        if (StrUtil.isNotBlank(properties.getRegistryUsername())) {
            builder.withRegistryUsername(properties.getRegistryUsername());
        }
        if (StrUtil.isNotBlank(properties.getRegistryPassword())) {
            builder.withRegistryPassword(properties.getRegistryPassword());
        }
        if (StrUtil.isNotBlank(properties.getRegistryEmail())) {
            builder.withRegistryEmail(properties.getRegistryEmail());
        }
        return builder.build();
    }

    @Bean(destroyMethod = "close")
    public DockerHttpClient dockerHttpClient(DockerClientConfig dockerClientConfig,
                                             DockerClientProperties properties) {
        return new ApacheDockerHttpClient.Builder()
                .dockerHost(dockerClientConfig.getDockerHost())
                .sslConfig(dockerClientConfig.getSSLConfig())
                .maxConnections(properties.getMaxConnections())
                .connectionTimeout(properties.getConnectionTimeout())
                .responseTimeout(properties.getResponseTimeout())
                .build();
    }

    @Bean(destroyMethod = "close")
    public DockerClient dockerClient(DockerClientConfig dockerClientConfig, DockerHttpClient dockerHttpClient) {
        return DockerClientImpl.getInstance(dockerClientConfig, dockerHttpClient);
    }

}
