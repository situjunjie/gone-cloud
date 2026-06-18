package cn.iocoder.yudao.module.devops.framework.docker;

import lombok.Data;
import lombok.ToString;

/**
 * Docker 环境连接配置。
 */
@Data
public class DockerEnvironmentConfig {

    /**
     * Docker daemon 地址。
     */
    private String host;

    /**
     * 是否启用 TLS 校验。
     */
    private Boolean tlsVerify;

    /**
     * Docker API 版本，可空自动协商。
     */
    private String apiVersion;

    /**
     * CA 证书 PEM 内容。
     */
    @ToString.Exclude
    private String caCert;

    /**
     * 客户端证书 PEM 内容。
     */
    @ToString.Exclude
    private String clientCert;

    /**
     * 客户端私钥 PEM 内容。
     */
    @ToString.Exclude
    private String clientKey;

}
