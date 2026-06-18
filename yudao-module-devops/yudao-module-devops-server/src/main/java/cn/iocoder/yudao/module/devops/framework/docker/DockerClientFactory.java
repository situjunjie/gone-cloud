package cn.iocoder.yudao.module.devops.framework.docker;

import cn.hutool.core.util.StrUtil;
import com.github.dockerjava.api.DockerClient;
import com.github.dockerjava.core.DefaultDockerClientConfig;
import com.github.dockerjava.core.DockerClientConfig;
import com.github.dockerjava.core.DockerClientImpl;
import com.github.dockerjava.httpclient5.ApacheDockerHttpClient;
import com.github.dockerjava.transport.DockerHttpClient;
import jakarta.annotation.Resource;
import org.springframework.stereotype.Component;

/**
 * Docker 客户端工厂。
 *
 * <p>当前提供默认 Docker daemon 客户端。后续如果需要按构建主机、租户或 Registry 凭据创建临时客户端，
 * 可以在这里扩展，不要把 docker-java 初始化细节散落到业务服务里。
 */
@Component
public class DockerClientFactory {

    @Resource
    private DockerClient dockerClient;
    @Resource
    private DockerClientProperties properties;

    /**
     * 获取默认 Docker 客户端。
     *
     * @return Docker 客户端
     */
    public DockerClient getDefaultClient() {
        return dockerClient;
    }

    /**
     * 根据 Docker 环境配置创建短生命周期客户端。
     *
     * @param config Docker 环境配置
     * @return Docker 客户端
     */
    public DockerClient createClient(DockerEnvironmentConfig config) {
        DockerClientConfig clientConfig = buildClientConfig(config);
        DockerHttpClient httpClient = new ApacheDockerHttpClient.Builder()
                .dockerHost(clientConfig.getDockerHost())
                .sslConfig(clientConfig.getSSLConfig())
                .maxConnections(properties.getMaxConnections())
                .connectionTimeout(properties.getConnectionTimeout())
                .responseTimeout(properties.getResponseTimeout())
                .build();
        return DockerClientImpl.getInstance(clientConfig, httpClient);
    }

    private DockerClientConfig buildClientConfig(DockerEnvironmentConfig config) {
        DefaultDockerClientConfig.Builder builder = DefaultDockerClientConfig.createDefaultConfigBuilder();
        if (config != null && StrUtil.isNotBlank(config.getHost())) {
            builder.withDockerHost(config.getHost());
        }
        if (config != null && config.getTlsVerify() != null) {
            builder.withDockerTlsVerify(config.getTlsVerify());
        }
        if (config != null && StrUtil.isNotBlank(config.getApiVersion())) {
            builder.withApiVersion(config.getApiVersion());
        }
        if (config != null && (StrUtil.isNotBlank(config.getCaCert())
                || StrUtil.isNotBlank(config.getClientCert()) || StrUtil.isNotBlank(config.getClientKey()))) {
            builder.withCustomSslConfig(new DockerPemSslConfig(config));
        }
        return builder.build();
    }

}
