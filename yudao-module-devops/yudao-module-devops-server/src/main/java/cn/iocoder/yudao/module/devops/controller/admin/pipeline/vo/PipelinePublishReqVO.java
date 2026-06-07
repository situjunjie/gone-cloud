package cn.iocoder.yudao.module.devops.controller.admin.pipeline.vo;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import lombok.Data;

@Schema(description = "管理后台 - DevOps 流水线发布 Request VO")
@Data
public class PipelinePublishReqVO {

    @Schema(description = "流水线定义编号", requiredMode = Schema.RequiredMode.REQUIRED, example = "1")
    @NotNull(message = "流水线定义编号不能为空")
    private Long definitionId;

    @Schema(description = "草稿版本编号", requiredMode = Schema.RequiredMode.REQUIRED, example = "10")
    @NotNull(message = "草稿版本编号不能为空")
    private Long draftVersionId;

    @Schema(description = "版本名称", example = "v1")
    @Size(max = 64, message = "版本名称长度不能超过 64 个字符")
    private String versionName;

}
