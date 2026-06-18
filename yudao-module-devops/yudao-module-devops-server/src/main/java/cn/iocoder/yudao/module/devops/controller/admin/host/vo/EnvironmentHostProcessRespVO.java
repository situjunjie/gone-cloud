package cn.iocoder.yudao.module.devops.controller.admin.host.vo;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;

@Schema(description = "管理后台 - DevOps 主机进程 Response VO")
@Data
public class EnvironmentHostProcessRespVO {

    @Schema(description = "进程 ID", example = "1234")
    private Long pid;

    @Schema(description = "父进程 ID", example = "1")
    private Long ppid;

    @Schema(description = "用户", example = "root")
    private String user;

    @Schema(description = "状态", example = "S")
    private String stat;

    @Schema(description = "CPU 使用率百分比", example = "2.3")
    private Double cpuPercent;

    @Schema(description = "内存使用率百分比", example = "1.2")
    private Double memoryPercent;

    @Schema(description = "运行时长", example = "01:23:45")
    private String elapsed;

    @Schema(description = "命令", example = "java")
    private String command;

    @Schema(description = "命令参数")
    private String args;

}
