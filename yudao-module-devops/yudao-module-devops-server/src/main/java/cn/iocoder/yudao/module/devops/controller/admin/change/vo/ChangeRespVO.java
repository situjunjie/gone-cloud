package cn.iocoder.yudao.module.devops.controller.admin.change.vo;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;

import java.time.LocalDateTime;
import java.util.List;

@Schema(description = "管理后台 - DevOps 变更 Response VO")
@Data
public class ChangeRespVO {

    @Schema(description = "变更编号", requiredMode = Schema.RequiredMode.REQUIRED, example = "1024")
    private Long id;

    @Schema(description = "应用编号", requiredMode = Schema.RequiredMode.REQUIRED, example = "1")
    private Long appId;

    @Schema(description = "变更标识", requiredMode = Schema.RequiredMode.REQUIRED, example = "GONE-1")
    private String changeKey;

    @Schema(description = "变更标题", requiredMode = Schema.RequiredMode.REQUIRED, example = "发布网关")
    private String title;

    @Schema(description = "变更描述")
    private String description;

    @Schema(description = "变更分支名称", requiredMode = Schema.RequiredMode.REQUIRED, example = "feature/gone-1")
    private String branchName;

    @Schema(description = "来源基线分支名称", requiredMode = Schema.RequiredMode.REQUIRED, example = "master")
    private String sourceBaseBranchName;

    @Schema(description = "负责人用户编号", requiredMode = Schema.RequiredMode.REQUIRED, example = "1")
    private Long ownerUserId;

    @Schema(description = "测试者用户编号", example = "1")
    private Long testerUserId;

    @Schema(description = "测试是否通过，0 未测试/未通过，1 已通过", requiredMode = Schema.RequiredMode.REQUIRED, example = "0")
    private Integer testPassed;

    @Schema(description = "测试通过的提交 SHA")
    private String testPassedCommitSha;

    @Schema(description = "代码审核者用户编号", example = "1")
    private Long codeReviewerUserId;

    @Schema(description = "代码审核状态，参见 dev_change_code_review_status", requiredMode = Schema.RequiredMode.REQUIRED, example = "0")
    private Integer codeReviewStatus;

    @Schema(description = "代码审核通过的提交 SHA")
    private String codeReviewPassedCommitSha;

    @Schema(description = "状态，参见 dev_change_status", requiredMode = Schema.RequiredMode.REQUIRED, example = "0")
    private Integer status;

    @Schema(description = "最新提交 SHA")
    private String latestCommitSha;

    @Schema(description = "最新提交信息")
    private String latestCommitMessage;

    @Schema(description = "最新提交时间")
    private LocalDateTime latestCommitAt;

    @Schema(description = "发布时间")
    private LocalDateTime releasedAt;

    @Schema(description = "合并回主干时间")
    private LocalDateTime mergedToMasterAt;

    @Schema(description = "废弃时间")
    private LocalDateTime discardedAt;

    @Schema(description = "废弃原因")
    private String discardReason;

    @Schema(description = "备注")
    private String remark;

    @Schema(description = "变更环境列表")
    private List<ChangeEnvRespVO> envs;

    @Schema(description = "创建时间", requiredMode = Schema.RequiredMode.REQUIRED)
    private LocalDateTime createTime;

}
