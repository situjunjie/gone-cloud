package cn.iocoder.yudao.module.project.controller.admin.project.vo;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import lombok.Data;

@Schema(description = "管理后台 - 项目角色更新 Request VO")
@Data
public class ProjectRoleUpdateReqVO {

    @Schema(description = "角色编号", requiredMode = Schema.RequiredMode.REQUIRED, example = "1024")
    @NotNull(message = "角色编号不能为空")
    private Long id;

    @Schema(description = "角色名称", requiredMode = Schema.RequiredMode.REQUIRED, example = "开发人员")
    @NotBlank(message = "角色名称不能为空")
    @Size(max = 128, message = "角色名称长度不能超过 128 个字符")
    private String name;

    @Schema(description = "排序", example = "10")
    private Integer sort;

    @Schema(description = "备注")
    @Size(max = 512, message = "备注长度不能超过 512 个字符")
    private String remark;

}
