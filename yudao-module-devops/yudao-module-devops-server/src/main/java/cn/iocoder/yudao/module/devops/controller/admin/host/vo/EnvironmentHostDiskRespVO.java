package cn.iocoder.yudao.module.devops.controller.admin.host.vo;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;

@Schema(description = "管理后台 - DevOps 主机磁盘 Response VO")
@Data
public class EnvironmentHostDiskRespVO {

    @Schema(description = "文件系统", example = "/dev/vda1")
    private String filesystem;

    @Schema(description = "总容量 KB", example = "52403200")
    private Long totalKb;

    @Schema(description = "已用 KB", example = "20480000")
    private Long usedKb;

    @Schema(description = "可用 KB", example = "31923200")
    private Long availableKb;

    @Schema(description = "使用率百分比", example = "39.1")
    private Double usagePercent;

    @Schema(description = "挂载点", example = "/")
    private String mountPoint;

}
