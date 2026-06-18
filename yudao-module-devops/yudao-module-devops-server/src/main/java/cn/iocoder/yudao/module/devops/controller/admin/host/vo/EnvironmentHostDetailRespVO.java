package cn.iocoder.yudao.module.devops.controller.admin.host.vo;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;

import java.time.LocalDateTime;
import java.util.List;

@Schema(description = "管理后台 - DevOps 环境主机详情 Response VO")
@Data
public class EnvironmentHostDetailRespVO {

    @Schema(description = "主机基础信息", requiredMode = Schema.RequiredMode.REQUIRED)
    private EnvironmentHostRespVO host;

    @Schema(description = "是否连接成功", requiredMode = Schema.RequiredMode.REQUIRED)
    private Boolean connected;

    @Schema(description = "系统信息")
    private EnvironmentHostSystemRespVO system;

    @Schema(description = "CPU 信息")
    private EnvironmentHostCpuRespVO cpu;

    @Schema(description = "负载信息")
    private EnvironmentHostLoadRespVO load;

    @Schema(description = "内存信息")
    private EnvironmentHostMemoryRespVO memory;

    @Schema(description = "磁盘列表")
    private List<EnvironmentHostDiskRespVO> disks;

    @Schema(description = "活跃进程列表")
    private List<EnvironmentHostProcessRespVO> processes;

    @Schema(description = "采集时间")
    private LocalDateTime collectedAt;

    @Schema(description = "错误消息，connected=false 时返回")
    private String errorMessage;

}
