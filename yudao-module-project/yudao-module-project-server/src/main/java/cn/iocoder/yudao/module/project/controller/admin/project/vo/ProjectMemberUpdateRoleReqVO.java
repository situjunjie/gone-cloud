package cn.iocoder.yudao.module.project.controller.admin.project.vo;

import cn.iocoder.yudao.framework.common.validation.InEnum;
import cn.iocoder.yudao.module.project.enums.ProjectMemberRoleEnum;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotNull;
import lombok.Data;

@Schema(description = "管理后台 - 项目成员角色修改 Request VO")
@Data
public class ProjectMemberUpdateRoleReqVO {

    @Schema(description = "项目成员编号", requiredMode = Schema.RequiredMode.REQUIRED, example = "1024")
    @NotNull(message = "项目成员编号不能为空")
    private Long id;

    @Schema(description = "项目角色，参见 project_member_role", requiredMode = Schema.RequiredMode.REQUIRED, example = "MEMBER")
    @NotNull(message = "项目角色不能为空")
    @InEnum(ProjectMemberRoleEnum.class)
    private String role;

}
