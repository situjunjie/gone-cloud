package cn.iocoder.yudao.module.devops.controller.admin.environment.vo;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;

@Schema(description = "管理后台 - DevOps 环境 Kubernetes 大盘 Response VO")
@Data
public class EnvironmentKubernetesDashboardRespVO {

    @Schema(description = "环境编号", requiredMode = Schema.RequiredMode.REQUIRED, example = "1024")
    private Long environmentId;

    @Schema(description = "环境标识", requiredMode = Schema.RequiredMode.REQUIRED, example = "dev")
    private String envKey;

    @Schema(description = "环境名称", requiredMode = Schema.RequiredMode.REQUIRED, example = "开发环境")
    private String envName;

    @Schema(description = "环境阶段，参见 dev_env_stage", requiredMode = Schema.RequiredMode.REQUIRED, example = "DEV")
    private String envStage;

    @Schema(description = "Kubernetes Namespace", requiredMode = Schema.RequiredMode.REQUIRED, example = "default")
    private String namespace;

    @Schema(description = "Service 数量", requiredMode = Schema.RequiredMode.REQUIRED, example = "3")
    private Integer serviceCount;

    @Schema(description = "Deployment 数量", requiredMode = Schema.RequiredMode.REQUIRED, example = "5")
    private Integer deploymentCount;

    @Schema(description = "Pod 数量", requiredMode = Schema.RequiredMode.REQUIRED, example = "12")
    private Integer podCount;

    @Schema(description = "Running Pod 数量", requiredMode = Schema.RequiredMode.REQUIRED, example = "10")
    private Integer runningPodCount;

    @Schema(description = "异常 Pod 数量", requiredMode = Schema.RequiredMode.REQUIRED, example = "2")
    private Integer abnormalPodCount;

}
