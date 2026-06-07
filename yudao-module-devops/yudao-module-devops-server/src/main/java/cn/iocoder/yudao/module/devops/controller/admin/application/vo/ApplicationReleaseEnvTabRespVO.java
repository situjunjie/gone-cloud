package cn.iocoder.yudao.module.devops.controller.admin.application.vo;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;

@Schema(description = "管理后台 - DevOps 应用发布环境 Tab Response VO")
@Data
public class ApplicationReleaseEnvTabRespVO {

    @Schema(description = "应用环境关系编号", requiredMode = Schema.RequiredMode.REQUIRED, example = "1024")
    private Long applicationEnvId;

    @Schema(description = "应用编号", requiredMode = Schema.RequiredMode.REQUIRED, example = "1")
    private Long appId;

    @Schema(description = "环境编号", requiredMode = Schema.RequiredMode.REQUIRED, example = "2")
    private Long envId;

    @Schema(description = "环境标识", requiredMode = Schema.RequiredMode.REQUIRED, example = "test")
    private String envKey;

    @Schema(description = "环境名称", requiredMode = Schema.RequiredMode.REQUIRED, example = "测试环境")
    private String envName;

    @Schema(description = "环境阶段，参见 dev_env_stage", requiredMode = Schema.RequiredMode.REQUIRED, example = "TEST")
    private String envStage;

    @Schema(description = "基础设施类型，参见 dev_infra_type", requiredMode = Schema.RequiredMode.REQUIRED, example = "K8S")
    private String infraType;

    @Schema(description = "显示顺序", requiredMode = Schema.RequiredMode.REQUIRED, example = "10")
    private Integer displayOrder;

    @Schema(description = "部署分支名称模式", requiredMode = Schema.RequiredMode.REQUIRED, example = "feat/*")
    private String deployBranchNamePattern;

    @Schema(description = "流水线定义编号", example = "2048")
    private Long pipelineDefinitionId;

    @Schema(description = "是否存在已发布流水线版本", requiredMode = Schema.RequiredMode.REQUIRED, example = "true")
    private Boolean hasPublishedPipeline;

    @Schema(description = "应用环境关系状态，参见 common_status", requiredMode = Schema.RequiredMode.REQUIRED, example = "0")
    private Integer status;

}
