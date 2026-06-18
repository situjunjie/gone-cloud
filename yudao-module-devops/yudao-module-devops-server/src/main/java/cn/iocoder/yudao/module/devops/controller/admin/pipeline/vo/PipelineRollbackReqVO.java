package cn.iocoder.yudao.module.devops.controller.admin.pipeline.vo;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import lombok.Data;

@Schema(description = "管理后台 - DevOps 流水线版本回退 Request VO")
@Data
public class PipelineRollbackReqVO {

    @Schema(description = "流水线定义编号", requiredMode = Schema.RequiredMode.REQUIRED, example = "1")
    @NotNull(message = "流水线定义编号不能为空")
    private Long definitionId;

    @Schema(description = "目标历史版本编号", requiredMode = Schema.RequiredMode.REQUIRED, example = "6")
    @NotNull(message = "目标历史版本编号不能为空")
    private Long targetVersionId;

    @Schema(description = "版本名称", example = "v11")
    @Size(max = 64, message = "版本名称长度不能超过 64 个字符")
    private String versionName;

    @Schema(description = "回退原因", requiredMode = Schema.RequiredMode.REQUIRED)
    @NotBlank(message = "回退原因不能为空")
    @Size(max = 512, message = "回退原因长度不能超过 512 个字符")
    private String rollbackReason;

}
