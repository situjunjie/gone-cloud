package cn.iocoder.yudao.module.devops.controller.admin.application.vo;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;

@Schema(description = "管理后台 - DevOps 应用发布流水线连线 Response VO")
@Data
public class ApplicationReleasePipelineEdgeRespVO {

    @Schema(description = "来源节点编号", requiredMode = Schema.RequiredMode.REQUIRED, example = "checkout")
    private String source;

    @Schema(description = "目标节点编号", requiredMode = Schema.RequiredMode.REQUIRED, example = "unit_test")
    private String target;

}
