package cn.iocoder.yudao.module.project.controller.admin.project.vo;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

@Schema(description = "管理后台 - 固定项目权限 Response VO")
@Data
@NoArgsConstructor
@AllArgsConstructor
public class ProjectPermissionRespVO {

    @Schema(description = "权限编码", requiredMode = Schema.RequiredMode.REQUIRED, example = "PROJECT_QUERY")
    private String code;

    @Schema(description = "权限名称", requiredMode = Schema.RequiredMode.REQUIRED, example = "查看项目资料")
    private String name;

}
