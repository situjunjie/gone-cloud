package cn.iocoder.yudao.module.devops.controller.admin.pipeline.vo;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;

@Schema(description = "管理后台 - DevOps 流水线校验消息 Response VO")
@Data
public class PipelineValidationMessageRespVO {

    @Schema(description = "字段", example = "nodes")
    private String field;

    @Schema(description = "节点编号", example = "unit_test")
    private String nodeId;

    @Schema(description = "错误码", requiredMode = Schema.RequiredMode.REQUIRED, example = "NODE_PARAM_REQUIRED")
    private String code;

    @Schema(description = "消息", requiredMode = Schema.RequiredMode.REQUIRED, example = "节点参数不能为空")
    private String message;

}
