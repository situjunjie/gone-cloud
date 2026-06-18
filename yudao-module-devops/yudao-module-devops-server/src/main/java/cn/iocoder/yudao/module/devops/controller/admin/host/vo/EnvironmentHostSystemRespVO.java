package cn.iocoder.yudao.module.devops.controller.admin.host.vo;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;

@Schema(description = "管理后台 - DevOps 主机系统信息 Response VO")
@Data
public class EnvironmentHostSystemRespVO {

    @Schema(description = "主机名", example = "app-01")
    private String hostname;

    @Schema(description = "内核信息", example = "Linux 6.1.0 x86_64")
    private String kernel;

    @Schema(description = "操作系统名称", example = "Ubuntu")
    private String osName;

    @Schema(description = "操作系统版本", example = "22.04.4 LTS")
    private String osVersion;

}
