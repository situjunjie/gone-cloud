package cn.iocoder.yudao.module.devops.controller.admin.application.vo;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;

@Schema(description = "管理后台 - DevOps 应用上传镜像包部署 Response VO")
@Data
public class ApplicationReleaseUploadImageRespVO {

    @Schema(description = "应用环境关系编号", requiredMode = Schema.RequiredMode.REQUIRED, example = "2048")
    private Long applicationEnvId;

    @Schema(description = "流水线运行编号", example = "8192")
    private Long pipelineRunId;

    @Schema(description = "流水线运行状态，参见 dev_pipeline_run_status", example = "1")
    private Integer runStatus;

}
