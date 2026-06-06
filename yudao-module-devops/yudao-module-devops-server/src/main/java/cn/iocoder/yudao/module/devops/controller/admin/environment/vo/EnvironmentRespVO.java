package cn.iocoder.yudao.module.devops.controller.admin.environment.vo;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;

import java.time.LocalDateTime;

@Schema(description = "管理后台 - DevOps 环境 Response VO")
@Data
public class EnvironmentRespVO {

    @Schema(description = "环境编号", requiredMode = Schema.RequiredMode.REQUIRED, example = "1024")
    private Long id;

    @Schema(description = "环境标识", requiredMode = Schema.RequiredMode.REQUIRED, example = "test")
    private String envKey;

    @Schema(description = "环境名称", requiredMode = Schema.RequiredMode.REQUIRED, example = "测试环境")
    private String envName;

    @Schema(description = "环境阶段，参见 dev_env_stage", requiredMode = Schema.RequiredMode.REQUIRED, example = "TEST")
    private String envStage;

    @Schema(description = "基础设施类型，参见 dev_infra_type", requiredMode = Schema.RequiredMode.REQUIRED, example = "HOST")
    private String infraType;

    @Schema(description = "是否已配置基础设施连接信息", requiredMode = Schema.RequiredMode.REQUIRED)
    private Boolean infraConfigConfigured;

    @Schema(description = "Kubernetes 部署目标 Namespace，infraType=K8S 时返回", example = "test")
    private String kubernetesNamespace;

    @Schema(description = "环境描述")
    private String description;

    @Schema(description = "状态，参见 common_status", requiredMode = Schema.RequiredMode.REQUIRED, example = "0")
    private Integer status;

    @Schema(description = "备注")
    private String remark;

    @Schema(description = "创建时间", requiredMode = Schema.RequiredMode.REQUIRED)
    private LocalDateTime createTime;

}
