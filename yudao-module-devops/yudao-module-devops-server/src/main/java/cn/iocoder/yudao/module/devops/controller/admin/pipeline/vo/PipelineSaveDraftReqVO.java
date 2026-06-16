package cn.iocoder.yudao.module.devops.controller.admin.pipeline.vo;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import lombok.Data;

@Schema(description = "管理后台 - DevOps 流水线保存草稿 Request VO")
@Data
public class PipelineSaveDraftReqVO {

    @Schema(description = "应用环境关系编号", requiredMode = Schema.RequiredMode.REQUIRED, example = "1")
    @NotNull(message = "应用环境关系编号不能为空")
    private Long applicationEnvId;

    @Schema(description = "流水线名称", requiredMode = Schema.RequiredMode.REQUIRED, example = "测试环境流水线")
    @NotBlank(message = "流水线名称不能为空")
    @Size(max = 128, message = "流水线名称长度不能超过 128 个字符")
    private String name;

    @Schema(description = "画布 JSON", requiredMode = Schema.RequiredMode.REQUIRED)
    @NotBlank(message = "画布 JSON 不能为空")
    private String diagramJson;

    @Schema(description = "流水线 YAML", requiredMode = Schema.RequiredMode.REQUIRED)
    @NotBlank(message = "流水线 YAML 不能为空")
    private String specJson;

    @Schema(description = "备注")
    @Size(max = 512, message = "备注长度不能超过 512 个字符")
    private String remark;

}
