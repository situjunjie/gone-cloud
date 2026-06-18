package cn.iocoder.yudao.module.devops.controller.admin.environment.vo;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;

@Schema(description = "管理后台 - DevOps Docker 容器端口 Response VO")
@Data
public class EnvironmentDockerContainerPortRespVO {

    @Schema(description = "监听 IP", example = "0.0.0.0")
    private String ip;

    @Schema(description = "容器内部端口", example = "8080")
    private Integer privatePort;

    @Schema(description = "宿主机端口", example = "18080")
    private Integer publicPort;

    @Schema(description = "协议", example = "tcp")
    private String type;

}
