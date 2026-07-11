package cn.iocoder.yudao.module.project.controller.admin.workitem.vo;

import com.fasterxml.jackson.databind.JsonNode;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotNull;
import lombok.Data;

@Schema(description = "管理后台 - 工作项自定义字段值 Request VO")
@Data
public class WorkItemCustomFieldValueReqVO {

    @NotNull(message = "自定义字段编号不能为空")
    private Long fieldId;

    @NotNull(message = "自定义字段值不能缺失")
    private JsonNode value;

}
