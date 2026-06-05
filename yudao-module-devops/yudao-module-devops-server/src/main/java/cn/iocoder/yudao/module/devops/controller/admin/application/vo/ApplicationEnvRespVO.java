package cn.iocoder.yudao.module.devops.controller.admin.application.vo;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;

import java.time.LocalDateTime;

@Schema(description = "管理后台 - DevOps 应用环境 Response VO")
@Data
public class ApplicationEnvRespVO {

    @Schema(description = "应用环境关系编号", requiredMode = Schema.RequiredMode.REQUIRED, example = "1024")
    private Long id;

    @Schema(description = "应用编号", requiredMode = Schema.RequiredMode.REQUIRED, example = "1")
    private Long appId;

    @Schema(description = "环境编号", requiredMode = Schema.RequiredMode.REQUIRED, example = "2")
    private Long envId;

    @Schema(description = "显示顺序", requiredMode = Schema.RequiredMode.REQUIRED, example = "10")
    private Integer displayOrder;

    @Schema(description = "部署分支名称模式", requiredMode = Schema.RequiredMode.REQUIRED, example = "feature/*")
    private String deployBranchNamePattern;

    @Schema(description = "流水线定义编号", example = "2048")
    private Long pipelineDefinitionId;

    @Schema(description = "是否需要审批", requiredMode = Schema.RequiredMode.REQUIRED, example = "false")
    private Boolean approvalRequired;

    @Schema(description = "审批配置 JSON")
    private String approvalConfigJson;

    @Schema(description = "当前快照编号", example = "4096")
    private Long currentSnapshotId;

    @Schema(description = "状态，参见 common_status", requiredMode = Schema.RequiredMode.REQUIRED, example = "0")
    private Integer status;

    @Schema(description = "备注")
    private String remark;

    @Schema(description = "创建时间", requiredMode = Schema.RequiredMode.REQUIRED)
    private LocalDateTime createTime;

}
