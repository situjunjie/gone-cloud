package cn.iocoder.yudao.module.project.controller.admin.workitem.vo;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import lombok.Data;
import lombok.EqualsAndHashCode;

@Schema(description = "管理后台 - 项目工作项单字段分组统计 Request VO")
@Data
@EqualsAndHashCode(callSuper = true)
public class ProjectWorkItemGroupCountReqVO extends ProjectWorkItemPageReqVO {

    @Schema(description = "分组字段编码，固定字段或 custom:<fieldId>", requiredMode = Schema.RequiredMode.REQUIRED,
            example = "status")
    @NotBlank(message = "分组字段不能为空")
    private String groupField;

}
