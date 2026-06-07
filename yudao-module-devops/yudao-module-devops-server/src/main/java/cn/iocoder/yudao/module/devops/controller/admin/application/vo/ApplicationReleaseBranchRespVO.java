package cn.iocoder.yudao.module.devops.controller.admin.application.vo;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;

import java.time.LocalDateTime;

@Schema(description = "管理后台 - DevOps 应用发布分支 Response VO")
@Data
public class ApplicationReleaseBranchRespVO {

    @Schema(description = "变更编号", requiredMode = Schema.RequiredMode.REQUIRED, example = "1024")
    private Long changeId;

    @Schema(description = "变更标识", requiredMode = Schema.RequiredMode.REQUIRED, example = "gone-cloud-1717651234567")
    private String changeKey;

    @Schema(description = "变更标题", requiredMode = Schema.RequiredMode.REQUIRED, example = "登录页优化")
    private String title;

    @Schema(description = "变更分支名称", requiredMode = Schema.RequiredMode.REQUIRED, example = "feat/login-page-1717651234567")
    private String branchName;

    @Schema(description = "来源基线分支名称", requiredMode = Schema.RequiredMode.REQUIRED, example = "master")
    private String sourceBaseBranchName;

    @Schema(description = "负责人用户编号", requiredMode = Schema.RequiredMode.REQUIRED, example = "1")
    private Long ownerUserId;

    @Schema(description = "最新提交 SHA")
    private String latestCommitSha;

    @Schema(description = "最新提交信息")
    private String latestCommitMessage;

    @Schema(description = "最新提交时间")
    private LocalDateTime latestCommitAt;

    @Schema(description = "变更创建时间")
    private LocalDateTime createTime;

    @Schema(description = "变更环境关系编号", example = "2048")
    private Long changeEnvId;

    @Schema(description = "挂载状态，参见 dev_change_env_mount_status", example = "0")
    private Integer mountStatus;

    @Schema(description = "挂载时间")
    private LocalDateTime mountedAt;

    @Schema(description = "挂载人用户编号")
    private Long mountedBy;

    @Schema(description = "最近流水线运行编号")
    private Long lastPipelineRunId;

    @Schema(description = "最近合并状态，参见 dev_pipeline_merge_status", example = "0")
    private Integer lastMergeStatus;

    @Schema(description = "最近构建状态，参见 dev_pipeline_stage_status", example = "0")
    private Integer lastBuildStatus;

    @Schema(description = "最近测试状态，参见 dev_pipeline_stage_status", example = "0")
    private Integer lastTestStatus;

    @Schema(description = "最近部署状态，参见 dev_pipeline_stage_status", example = "0")
    private Integer lastDeployStatus;

    @Schema(description = "最近错误信息")
    private String lastErrorMessage;

    @Schema(description = "审批状态，参见 dev_approval_status", example = "0")
    private Integer approvalStatus;

    @Schema(description = "是否包含在当前快照", example = "false")
    private Boolean includedInCurrentSnapshot;

}
