package cn.iocoder.yudao.module.devops.controller.admin.environment.vo;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;

@Schema(description = "管理后台 - DevOps 环境 Docker 大盘 Response VO")
@Data
public class EnvironmentDockerDashboardRespVO {

    @Schema(description = "环境编号", requiredMode = Schema.RequiredMode.REQUIRED, example = "1024")
    private Long environmentId;

    @Schema(description = "环境标识", requiredMode = Schema.RequiredMode.REQUIRED, example = "dev")
    private String envKey;

    @Schema(description = "环境名称", requiredMode = Schema.RequiredMode.REQUIRED, example = "开发环境")
    private String envName;

    @Schema(description = "环境阶段，参见 dev_env_stage", requiredMode = Schema.RequiredMode.REQUIRED, example = "DEV")
    private String envStage;

    @Schema(description = "Docker daemon 地址", requiredMode = Schema.RequiredMode.REQUIRED, example = "tcp://192.168.1.10:2376")
    private String dockerHost;

    @Schema(description = "Docker 服务端版本", example = "26.1.4")
    private String serverVersion;

    @Schema(description = "Docker API 版本", example = "1.45")
    private String apiVersion;

    @Schema(description = "操作系统", example = "Docker Desktop")
    private String operatingSystem;

    @Schema(description = "OS 类型", example = "linux")
    private String osType;

    @Schema(description = "CPU 架构", example = "x86_64")
    private String architecture;

    @Schema(description = "容器总数", example = "12")
    private Integer containerCount;

    @Schema(description = "运行中容器数", example = "8")
    private Integer runningContainerCount;

    @Schema(description = "暂停容器数", example = "1")
    private Integer pausedContainerCount;

    @Schema(description = "停止容器数", example = "3")
    private Integer stoppedContainerCount;

    @Schema(description = "镜像数量", example = "30")
    private Integer imageCount;

    @Schema(description = "Docker root dir", example = "/var/lib/docker")
    private String dockerRootDir;

}
