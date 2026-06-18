package cn.iocoder.yudao.module.devops.controller.admin.environment.vo;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;

import java.util.List;

@Schema(description = "管理后台 - DevOps Docker Compose 项目详情 Response VO")
@Data
public class EnvironmentDockerComposeProjectDetailRespVO {

    @Schema(description = "项目总览")
    private EnvironmentDockerComposeProjectRespVO summary;

    @Schema(description = "项目容器")
    private List<EnvironmentDockerContainerRespVO> containers;

    @Schema(description = "项目服务")
    private List<EnvironmentDockerComposeServiceRespVO> services;

    @Schema(description = "项目网络")
    private List<EnvironmentDockerNetworkRespVO> networks;

    @Schema(description = "项目卷")
    private List<EnvironmentDockerVolumeMountRespVO> volumes;

    @Schema(description = "项目镜像")
    private List<EnvironmentDockerImageRespVO> images;

}
