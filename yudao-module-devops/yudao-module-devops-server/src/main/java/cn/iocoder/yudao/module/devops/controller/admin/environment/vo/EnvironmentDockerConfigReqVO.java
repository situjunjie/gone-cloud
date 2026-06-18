package cn.iocoder.yudao.module.devops.controller.admin.environment.vo;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.Size;
import lombok.Data;

@Schema(description = "管理后台 - DevOps Docker 环境连接配置 Request VO")
@Data
public class EnvironmentDockerConfigReqVO {

    @Schema(description = "Docker daemon 地址", example = "tcp://192.168.1.10:2376")
    @Size(max = 256, message = "Docker daemon 地址长度不能超过 256 个字符")
    private String host;

    @Schema(description = "是否启用 TLS 校验", example = "true")
    private Boolean tlsVerify;

    @Schema(description = "Docker API 版本，可空自动协商", example = "1.45")
    @Size(max = 32, message = "Docker API 版本长度不能超过 32 个字符")
    private String apiVersion;

    @Schema(description = "CA 证书 PEM 内容，启用 TLS 时可传")
    private String caCert;

    @Schema(description = "客户端证书 PEM 内容，启用 TLS 时可传")
    private String clientCert;

    @Schema(description = "客户端私钥 PEM 内容，启用 TLS 时可传")
    private String clientKey;

}
