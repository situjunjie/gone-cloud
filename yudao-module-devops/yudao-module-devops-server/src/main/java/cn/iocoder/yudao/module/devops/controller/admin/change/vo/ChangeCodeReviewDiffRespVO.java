package cn.iocoder.yudao.module.devops.controller.admin.change.vo;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;

import java.util.List;

@Schema(description = "管理后台 - DevOps 变更代码审核 Diff Response VO")
@Data
public class ChangeCodeReviewDiffRespVO {

    @Schema(description = "变更编号", requiredMode = Schema.RequiredMode.REQUIRED, example = "1024")
    private Long changeId;

    @Schema(description = "应用编号", requiredMode = Schema.RequiredMode.REQUIRED, example = "1")
    private Long appId;

    @Schema(description = "变更分支名称", requiredMode = Schema.RequiredMode.REQUIRED, example = "feat/login-1717651234567")
    private String branchName;

    @Schema(description = "来源基线分支名称", example = "master")
    private String sourceBaseBranchName;

    @Schema(description = "本次比较基准 ref", requiredMode = Schema.RequiredMode.REQUIRED, example = "master")
    private String compareBaseRef;

    @Schema(description = "本次比较目标 ref", requiredMode = Schema.RequiredMode.REQUIRED, example = "a1b2c3d4")
    private String compareTargetRef;

    @Schema(description = "上次审核通过的提交 SHA")
    private String codeReviewPassedCommitSha;

    @Schema(description = "当前变更最新提交 SHA")
    private String latestCommitSha;

    @Schema(description = "Diff 文件列表", requiredMode = Schema.RequiredMode.REQUIRED)
    private List<FileDiff> files;

    @Schema(description = "管理后台 - DevOps 变更代码审核文件 Diff")
    @Data
    public static class FileDiff {

        @Schema(description = "展示路径", requiredMode = Schema.RequiredMode.REQUIRED, example = "src/App.java")
        private String path;

        @Schema(description = "旧路径", example = "src/OldApp.java")
        private String oldPath;

        @Schema(description = "新路径", example = "src/App.java")
        private String newPath;

        @Schema(description = "变更类型：ADDED、DELETED、RENAMED、MODIFIED", requiredMode = Schema.RequiredMode.REQUIRED)
        private String changeType;

        @Schema(description = "是否新增文件")
        private Boolean newFile;

        @Schema(description = "是否删除文件")
        private Boolean deletedFile;

        @Schema(description = "是否重命名文件")
        private Boolean renamedFile;

        @Schema(description = "Unified diff 内容")
        private String diff;

    }

}
