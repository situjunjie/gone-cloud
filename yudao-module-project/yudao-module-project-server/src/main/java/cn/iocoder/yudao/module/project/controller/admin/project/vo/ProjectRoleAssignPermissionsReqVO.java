package cn.iocoder.yudao.module.project.controller.admin.project.vo;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotNull;
import lombok.Data;

import java.util.Set;

@Schema(description = "管理后台 - 项目角色授权 Request VO")
@Data
public class ProjectRoleAssignPermissionsReqVO {

    @Schema(description = "角色编号", requiredMode = Schema.RequiredMode.REQUIRED, example = "1024")
    @NotNull(message = "角色编号不能为空")
    private Long roleId;

    @Schema(description = "项目权限编码集合", requiredMode = Schema.RequiredMode.REQUIRED)
    @NotNull(message = "项目权限编码集合不能为空")
    private Set<String> permissionCodes;

}
