package cn.iocoder.yudao.module.devops.controller.admin.environment.vo;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;

import java.util.List;

@Schema(description = "管理后台 - DevOps Docker Compose 项目 Response VO")
@Data
public class EnvironmentDockerComposeProjectRespVO {

    @Schema(description = "Compose 项目名称", example = "gone-cloud")
    private String projectName;

    @Schema(description = "项目状态", example = "RUNNING")
    private String status;

    @Schema(description = "服务数量", example = "3")
    private Integer serviceCount;

    @Schema(description = "容器总数", example = "5")
    private Integer containerCount;

    @Schema(description = "运行中容器数", example = "4")
    private Integer runningContainerCount;

    @Schema(description = "停止容器数", example = "1")
    private Integer stoppedContainerCount;

    @Schema(description = "异常容器数", example = "0")
    private Integer abnormalContainerCount;

    @Schema(description = "镜像数量", example = "3")
    private Integer imageCount;

    @Schema(description = "网络数量", example = "2")
    private Integer networkCount;

    @Schema(description = "服务名称")
    private List<String> services;

    @Schema(description = "镜像")
    private List<String> images;

    @Schema(description = "网络名称")
    private List<String> networks;

}
