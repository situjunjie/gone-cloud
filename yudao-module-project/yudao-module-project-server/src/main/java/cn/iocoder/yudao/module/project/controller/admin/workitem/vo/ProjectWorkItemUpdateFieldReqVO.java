package cn.iocoder.yudao.module.project.controller.admin.workitem.vo;

import com.fasterxml.jackson.databind.JsonNode;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import lombok.Data;

import java.util.List;

@Schema(description = "管理后台 - 项目工作项字段更新 Request VO")
@Data
public class ProjectWorkItemUpdateFieldReqVO {

    @Schema(description = "工作项编号", requiredMode = Schema.RequiredMode.REQUIRED, example = "1024")
    @NotNull(message = "工作项编号不能为空")
    private Long id;

    @Schema(description = "字段更新列表", requiredMode = Schema.RequiredMode.REQUIRED)
    @NotEmpty(message = "字段更新列表不能为空")
    @Valid
    private List<FieldValue> fields;

    @Schema(description = "字段更新项")
    @Data
    public static class FieldValue {

        @Schema(description = "字段名", requiredMode = Schema.RequiredMode.REQUIRED, example = "priority")
        @NotBlank(message = "字段名不能为空")
        private String field;

        @Schema(description = "字段值，支持 JSON null 清空可空字段", requiredMode = Schema.RequiredMode.REQUIRED,
                example = "\"HIGH\"")
        @NotNull(message = "字段值不能缺失")
        private JsonNode value;

    }

}
