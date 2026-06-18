package cn.iocoder.yudao.module.devops.controller.admin.host.vo;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;

@Schema(description = "管理后台 - DevOps 主机内存 Response VO")
@Data
public class EnvironmentHostMemoryRespVO {

    @Schema(description = "总内存 KB", example = "8175328")
    private Long totalKb;

    @Schema(description = "可用内存 KB", example = "4123456")
    private Long availableKb;

    @Schema(description = "已用内存 KB", example = "4051872")
    private Long usedKb;

    @Schema(description = "使用率百分比", example = "49.6")
    private Double usagePercent;

}
