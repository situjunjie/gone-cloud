package cn.iocoder.yudao.module.project.controller.admin.workitem.vo;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;

import java.util.List;

@Schema(description = "管理后台 - 工作项自定义字段值 Response VO")
@Data
public class WorkItemCustomFieldValueRespVO {

    private Long fieldId;
    private String scope;
    private String code;
    private String name;
    private String fieldType;
    private Boolean required;
    private Boolean enabled;
    private Object value;
    private List<WorkItemFieldRespVO.Option> selectedOptions;

}
