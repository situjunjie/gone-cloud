package cn.iocoder.yudao.module.devops.controller.admin.host.vo;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;

import java.util.List;

@Schema(description = "管理后台 - DevOps HOST 环境大盘 Response VO")
@Data
public class EnvironmentHostDashboardRespVO {

    @Schema(description = "环境编号", requiredMode = Schema.RequiredMode.REQUIRED, example = "100")
    private Long envId;

    @Schema(description = "环境标识", requiredMode = Schema.RequiredMode.REQUIRED, example = "prod-host")
    private String envKey;

    @Schema(description = "环境名称", requiredMode = Schema.RequiredMode.REQUIRED, example = "生产主机组")
    private String envName;

    @Schema(description = "环境阶段", requiredMode = Schema.RequiredMode.REQUIRED, example = "PROD")
    private String envStage;

    @Schema(description = "主机总数", requiredMode = Schema.RequiredMode.REQUIRED)
    private Integer hostCount;

    @Schema(description = "最近检测成功数", requiredMode = Schema.RequiredMode.REQUIRED)
    private Integer onlineCount;

    @Schema(description = "最近检测失败数", requiredMode = Schema.RequiredMode.REQUIRED)
    private Integer offlineCount;

    @Schema(description = "未检测数", requiredMode = Schema.RequiredMode.REQUIRED)
    private Integer uncheckedCount;

    @Schema(description = "主机摘要列表", requiredMode = Schema.RequiredMode.REQUIRED)
    private List<EnvironmentHostRespVO> hosts;

}
