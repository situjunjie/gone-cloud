package cn.iocoder.yudao.module.devops.controller.admin.change.vo;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;

import java.time.LocalDateTime;

@Schema(description = "管理后台 - DevOps 变更环境 Response VO")
@Data
public class ChangeEnvRespVO {

    @Schema(description = "变更环境关系编号", requiredMode = Schema.RequiredMode.REQUIRED, example = "1024")
    private Long id;

    @Schema(description = "变更编号", requiredMode = Schema.RequiredMode.REQUIRED, example = "1")
    private Long changeId;

    @Schema(description = "应用环境关系编号", requiredMode = Schema.RequiredMode.REQUIRED, example = "2")
    private Long applicationEnvId;

    @Schema(description = "挂载状态，参见 dev_change_env_mount_status", requiredMode = Schema.RequiredMode.REQUIRED, example = "0")
    private Integer mountStatus;

    @Schema(description = "挂载时间")
    private LocalDateTime mountedAt;

    @Schema(description = "挂载人用户编号")
    private Long mountedBy;

    @Schema(description = "移除时间")
    private LocalDateTime unmountedAt;

    @Schema(description = "移除人用户编号")
    private Long unmountedBy;

    @Schema(description = "移除原因")
    private String unmountedReason;

    @Schema(description = "最近合并状态，参见 dev_pipeline_merge_status", requiredMode = Schema.RequiredMode.REQUIRED, example = "0")
    private Integer lastMergeStatus;

    @Schema(description = "最近构建状态，参见 dev_pipeline_stage_status", requiredMode = Schema.RequiredMode.REQUIRED, example = "0")
    private Integer lastBuildStatus;

    @Schema(description = "最近测试状态，参见 dev_pipeline_stage_status", requiredMode = Schema.RequiredMode.REQUIRED, example = "0")
    private Integer lastTestStatus;

    @Schema(description = "最近部署状态，参见 dev_pipeline_stage_status", requiredMode = Schema.RequiredMode.REQUIRED, example = "0")
    private Integer lastDeployStatus;

    @Schema(description = "审批状态，参见 dev_approval_status", requiredMode = Schema.RequiredMode.REQUIRED, example = "0")
    private Integer approvalStatus;

    @Schema(description = "是否包含在当前快照", requiredMode = Schema.RequiredMode.REQUIRED, example = "false")
    private Boolean includedInCurrentSnapshot;

    @Schema(description = "创建时间", requiredMode = Schema.RequiredMode.REQUIRED)
    private LocalDateTime createTime;

}
