package cn.iocoder.yudao.module.devops.controller.admin.application.vo;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;

@Schema(description = "管理后台 - DevOps 应用发布变更快照 Response VO")
@Data
public class ApplicationReleaseChangeSnapshotRespVO {

    @Schema(description = "变更编号", requiredMode = Schema.RequiredMode.REQUIRED, example = "1024")
    private Long changeId;

    @Schema(description = "本次发布提交时的变更分支 commit SHA", example = "abc123")
    private String commitSha;

}
