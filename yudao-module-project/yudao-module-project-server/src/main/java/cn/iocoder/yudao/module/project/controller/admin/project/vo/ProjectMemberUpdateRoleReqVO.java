package cn.iocoder.yudao.module.project.controller.admin.project.vo;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.NotEmpty;
import lombok.Data;

import java.util.Set;

@Schema(description = "管理后台 - 项目成员角色修改 Request VO")
@Data
public class ProjectMemberUpdateRoleReqVO {

    @Schema(description = "项目成员编号", requiredMode = Schema.RequiredMode.REQUIRED, example = "1024")
    @NotNull(message = "项目成员编号不能为空")
    private Long id;

    @Schema(description = "角色编号集合", requiredMode = Schema.RequiredMode.REQUIRED)
    @NotEmpty(message = "项目成员至少需要一个角色")
    private Set<Long> roleIds;

}
