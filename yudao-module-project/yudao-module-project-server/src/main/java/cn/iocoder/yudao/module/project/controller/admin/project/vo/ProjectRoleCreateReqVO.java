package cn.iocoder.yudao.module.project.controller.admin.project.vo;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import lombok.Data;

@Schema(description = "管理后台 - 项目角色创建 Request VO")
@Data
public class ProjectRoleCreateReqVO {

    @Schema(description = "项目编号；项目角色必填，全局模板忽略", example = "1024")
    private Long projectId;

    @Schema(description = "角色编码", requiredMode = Schema.RequiredMode.REQUIRED, example = "DEVELOPER")
    @NotBlank(message = "角色编码不能为空")
    @Pattern(regexp = "[A-Z][A-Z0-9_]{0,63}", message = "角色编码只允许大写字母、数字和下划线")
    private String code;

    @Schema(description = "角色名称", requiredMode = Schema.RequiredMode.REQUIRED, example = "开发人员")
    @NotBlank(message = "角色名称不能为空")
    @Size(max = 128, message = "角色名称长度不能超过 128 个字符")
    private String name;

    @Schema(description = "排序", example = "10")
    private Integer sort;

    @Schema(description = "备注", example = "可维护工作项")
    @Size(max = 512, message = "备注长度不能超过 512 个字符")
    private String remark;

}
