package cn.iocoder.yudao.module.devops.controller.admin.host.vo;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;

@Schema(description = "管理后台 - DevOps 主机 CPU Response VO")
@Data
public class EnvironmentHostCpuRespVO {

    @Schema(description = "CPU 使用率百分比", example = "23.5")
    private Double usagePercent;

}
