package cn.iocoder.yudao.module.project.controller.admin.workitem.vo;

import com.fasterxml.jackson.databind.JsonNode;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import lombok.Data;

@Schema(description = "管理后台 - 项目工作项单字段更新 Request VO")
@Data
public class ProjectWorkItemUpdateFieldReqVO {

    @Schema(description = "工作项编号", requiredMode = Schema.RequiredMode.REQUIRED, example = "1024")
    @NotNull(message = "工作项编号不能为空")
    private Long id;

    @Schema(description = "字段名", requiredMode = Schema.RequiredMode.REQUIRED, example = "priority")
    @NotBlank(message = "字段名不能为空")
    private String field;

    @Schema(description = "字段值，支持 JSON null 清空可空字段", requiredMode = Schema.RequiredMode.REQUIRED,
            example = "\"HIGH\"")
    @NotNull(message = "字段值不能缺失")
    private JsonNode value;

}
