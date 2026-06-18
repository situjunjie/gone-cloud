package cn.iocoder.yudao.module.devops.controller.admin.environment.vo;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;

@Schema(description = "管理后台 - DevOps Docker 卷挂载 Response VO")
@Data
public class EnvironmentDockerVolumeMountRespVO {

    @Schema(description = "卷名称", example = "mysql-data")
    private String name;

    @Schema(description = "宿主机来源路径")
    private String source;

    @Schema(description = "容器目标路径")
    private String destination;

    @Schema(description = "Driver")
    private String driver;

    @Schema(description = "挂载模式", example = "rw")
    private String mode;

    @Schema(description = "是否可写")
    private Boolean rw;

    @Schema(description = "容器 ID")
    private String containerId;

    @Schema(description = "容器名称")
    private String containerName;

    @Schema(description = "Compose 服务名称")
    private String serviceName;

}
