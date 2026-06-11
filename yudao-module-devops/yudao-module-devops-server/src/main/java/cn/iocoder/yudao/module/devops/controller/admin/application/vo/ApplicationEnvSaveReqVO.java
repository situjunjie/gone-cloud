package cn.iocoder.yudao.module.devops.controller.admin.application.vo;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotNull;
import lombok.Data;

@Schema(description = "管理后台 - DevOps 应用环境创建/修改 Request VO")
@Data
public class ApplicationEnvSaveReqVO {

    @Schema(description = "环境编号", requiredMode = Schema.RequiredMode.REQUIRED, example = "1024")
    @NotNull(message = "环境编号不能为空")
    private Long envId;

    @Schema(description = "显示顺序", requiredMode = Schema.RequiredMode.REQUIRED, example = "10")
    @NotNull(message = "显示顺序不能为空")
    private Integer displayOrder;

    @Schema(description = "流水线定义编号", example = "2048")
    private Long pipelineDefinitionId;

    @Schema(description = "状态，参见 common_status", requiredMode = Schema.RequiredMode.REQUIRED, example = "0")
    @NotNull(message = "状态不能为空")
    private Integer status;

    @Schema(description = "备注")
    private String remark;

}
