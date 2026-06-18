package cn.iocoder.yudao.module.devops.controller.admin.host.vo;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;

@Schema(description = "管理后台 - DevOps 主机负载 Response VO")
@Data
public class EnvironmentHostLoadRespVO {

    @Schema(description = "1 分钟负载", example = "0.15")
    private Double load1;

    @Schema(description = "5 分钟负载", example = "0.10")
    private Double load5;

    @Schema(description = "15 分钟负载", example = "0.08")
    private Double load15;

    @Schema(description = "运行/总进程数", example = "1/128")
    private String runningProcessSummary;

}
