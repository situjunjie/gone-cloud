package cn.iocoder.yudao.module.project.controller.admin.project.vo;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.Set;

@Schema(description = "管理后台 - 当前用户项目权限 Response VO")
@Data
@NoArgsConstructor
@AllArgsConstructor
public class ProjectMyPermissionRespVO {

    @Schema(description = "是否为项目内置管理员", requiredMode = Schema.RequiredMode.REQUIRED)
    private boolean admin;

    @Schema(description = "有效项目权限编码集合", requiredMode = Schema.RequiredMode.REQUIRED)
    private Set<String> permissionCodes;

}
