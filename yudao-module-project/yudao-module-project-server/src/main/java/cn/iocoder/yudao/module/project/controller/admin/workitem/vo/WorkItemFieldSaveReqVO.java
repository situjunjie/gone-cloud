package cn.iocoder.yudao.module.project.controller.admin.workitem.vo;

import cn.iocoder.yudao.framework.common.validation.InEnum;
import cn.iocoder.yudao.module.project.enums.ProjectWorkItemCategoryEnum;
import cn.iocoder.yudao.module.project.enums.WorkItemFieldTypeEnum;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import lombok.Data;

@Schema(description = "管理后台 - 工作项自定义字段保存 Request VO")
@Data
public class WorkItemFieldSaveReqVO {

    private Long id;

    @NotNull(message = "项目编号不能为空")
    private Long projectId;

    @NotBlank(message = "字段编码不能为空")
    @Pattern(regexp = "[a-z][a-z0-9_]{0,63}", message = "字段编码必须以小写字母开头且只能包含小写字母、数字和下划线")
    private String code;

    @NotBlank(message = "字段名称不能为空")
    @Size(max = 128, message = "字段名称长度不能超过 128 个字符")
    private String name;

    @NotBlank(message = "字段类型不能为空")
    @InEnum(WorkItemFieldTypeEnum.class)
    private String fieldType;

    @InEnum(value = ProjectWorkItemCategoryEnum.class, message = "适用工作项分类不正确")
    private String applicableCategory;

    @NotNull(message = "是否必填不能为空")
    private Boolean required;

    @NotNull(message = "是否启用不能为空")
    private Boolean enabled;

    @NotNull(message = "排序不能为空")
    private Integer sort;

    private String configJson;

}
