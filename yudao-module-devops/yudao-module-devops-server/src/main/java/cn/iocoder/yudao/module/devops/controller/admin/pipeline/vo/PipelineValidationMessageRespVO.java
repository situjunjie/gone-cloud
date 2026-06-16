package cn.iocoder.yudao.module.devops.controller.admin.pipeline.vo;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;

@Schema(description = "管理后台 - DevOps 流水线校验消息 Response VO")
@Data
public class PipelineValidationMessageRespVO {

    @Schema(description = "字段", example = "stages.smoke_stage.jobs.smoke_job.steps.command_step.with.run")
    private String field;

    @Schema(description = "错误定位编号，通常为 stepId 或 jobId", example = "command_step")
    private String nodeId;

    @Schema(description = "错误码", requiredMode = Schema.RequiredMode.REQUIRED, example = "PARAM_REQUIRED")
    private String code;

    @Schema(description = "消息", requiredMode = Schema.RequiredMode.REQUIRED, example = "命令步骤必须配置 run")
    private String message;

}
