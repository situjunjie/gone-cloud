package cn.iocoder.yudao.module.devops.controller.admin.environment.vo;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;

@Schema(description = "管理后台 - DevOps 环境 Kubernetes Service Port Response VO")
@Data
public class EnvironmentKubernetesServicePortRespVO {

    @Schema(description = "端口名称", example = "http")
    private String name;

    @Schema(description = "协议", example = "TCP")
    private String protocol;

    @Schema(description = "服务端口", example = "80")
    private Integer port;

    @Schema(description = "目标端口", example = "8080")
    private String targetPort;

    @Schema(description = "NodePort 端口", example = "30080")
    private Integer nodePort;

}
