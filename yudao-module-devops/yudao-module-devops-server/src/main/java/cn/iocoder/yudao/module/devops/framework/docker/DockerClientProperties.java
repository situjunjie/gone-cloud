package cn.iocoder.yudao.module.devops.framework.docker;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;

import java.time.Duration;

/**
 * Docker 客户端配置。
 */
@Data
@ConfigurationProperties(prefix = "yudao.devops.docker")
public class DockerClientProperties {

    /**
     * Docker daemon 地址。为空时使用 docker-java 默认发现规则。
     *
     * <p>常用值：{@code unix:///var/run/docker.sock}、{@code tcp://127.0.0.1:2375}。
     */
    private String host;

    /**
     * 是否启用 TLS 校验。为空时使用 docker-java 默认发现规则。
     */
    private Boolean tlsVerify;

    /**
     * Docker TLS 证书目录。为空时使用 docker-java 默认发现规则。
     */
    private String certPath;

    /**
     * Docker CLI 配置目录。为空时使用 docker-java 默认发现规则。
     */
    private String configPath;

    /**
     * Docker API 版本。为空时由 daemon 协商或使用 docker-java 默认值。
     */
    private String apiVersion;

    /**
     * Registry 地址。
     */
    private String registryUrl;

    /**
     * Registry 用户名。
     */
    private String registryUsername;

    /**
     * Registry 密码。只用于创建客户端配置，不要写入日志。
     */
    private String registryPassword;

    /**
     * Registry 邮箱。
     */
    private String registryEmail;

    /**
     * HTTP 连接池最大连接数。
     */
    private Integer maxConnections = 100;

    /**
     * 连接超时时间。
     */
    private Duration connectionTimeout = Duration.ofSeconds(30);

    /**
     * 响应超时时间。
     */
    private Duration responseTimeout = Duration.ofSeconds(45);

}
