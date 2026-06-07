package cn.iocoder.yudao.module.devops.controller.admin.pipeline.vo;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;

import java.util.ArrayList;
import java.util.List;

@Schema(description = "管理后台 - DevOps 流水线校验 Response VO")
@Data
public class PipelineValidationRespVO {

    @Schema(description = "是否通过", requiredMode = Schema.RequiredMode.REQUIRED, example = "true")
    private Boolean valid;

    @Schema(description = "错误列表")
    private List<PipelineValidationMessageRespVO> errors = new ArrayList<>();

    @Schema(description = "警告列表")
    private List<PipelineValidationMessageRespVO> warnings = new ArrayList<>();

    @Schema(description = "Jenkinsfile 文本")
    private String jenkinsfileText;

    @Schema(description = "Jenkinsfile SHA-256 校验和")
    private String jenkinsfileChecksum;

}
