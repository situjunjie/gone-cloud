package cn.iocoder.yudao.module.project.controller.admin.workitem.vo;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import lombok.Data;

@Schema(description = "管理后台 - 工作项自定义字段选项保存 Request VO")
@Data
public class WorkItemFieldOptionSaveReqVO {

    private Long id;

    @NotNull(message = "字段编号不能为空")
    private Long fieldId;

    @NotBlank(message = "选项编码不能为空")
    @Pattern(regexp = "[a-z][a-z0-9_]{0,63}", message = "选项编码必须以小写字母开头且只能包含小写字母、数字和下划线")
    private String code;

    @NotBlank(message = "选项名称不能为空")
    @Size(max = 128, message = "选项名称长度不能超过 128 个字符")
    private String name;

    @Size(max = 32, message = "选项颜色长度不能超过 32 个字符")
    private String color;

    @NotNull(message = "是否启用不能为空")
    private Boolean enabled;

    @NotNull(message = "排序不能为空")
    private Integer sort;

}
