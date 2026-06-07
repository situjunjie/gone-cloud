package cn.iocoder.yudao.module.devops.controller.admin.application.vo;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;

@Schema(description = "管理后台 - DevOps 应用发布提交分支 Response VO")
@Data
public class ApplicationReleaseSubmitBranchRespVO {

    @Schema(description = "变更编号", requiredMode = Schema.RequiredMode.REQUIRED, example = "1024")
    private Long changeId;

    @Schema(description = "应用环境关系编号", requiredMode = Schema.RequiredMode.REQUIRED, example = "2048")
    private Long applicationEnvId;

    @Schema(description = "变更环境关系编号", requiredMode = Schema.RequiredMode.REQUIRED, example = "4096")
    private Long changeEnvId;

    @Schema(description = "流水线运行编号", requiredMode = Schema.RequiredMode.REQUIRED, example = "8192")
    private Long pipelineRunId;

    @Schema(description = "流水线运行状态，参见 dev_pipeline_run_status", requiredMode = Schema.RequiredMode.REQUIRED, example = "1")
    private Integer runStatus;

}
