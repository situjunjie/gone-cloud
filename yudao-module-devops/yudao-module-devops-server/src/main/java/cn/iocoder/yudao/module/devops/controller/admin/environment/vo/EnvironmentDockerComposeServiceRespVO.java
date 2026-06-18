package cn.iocoder.yudao.module.devops.controller.admin.environment.vo;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;

import java.util.List;

@Schema(description = "管理后台 - DevOps Docker Compose 服务 Response VO")
@Data
public class EnvironmentDockerComposeServiceRespVO {

    @Schema(description = "服务名称", example = "api")
    private String serviceName;

    @Schema(description = "容器数量", example = "2")
    private Integer containerCount;

    @Schema(description = "运行中容器数", example = "2")
    private Integer runningContainerCount;

    @Schema(description = "服务镜像")
    private List<String> images;

    @Schema(description = "容器名称")
    private List<String> containerNames;

}
