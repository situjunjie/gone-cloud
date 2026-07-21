package cn.iocoder.yudao.module.project.controller.admin.project.vo;

import cn.iocoder.yudao.module.system.api.user.AdminUserApi;
import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;
import org.dromara.core.trans.anno.Trans;
import org.dromara.core.trans.constant.TransType;
import org.dromara.core.trans.vo.VO;

import java.time.LocalDateTime;
import java.util.List;

@Schema(description = "管理后台 - 项目成员 Response VO")
@Data
public class ProjectMemberRespVO implements VO {

    @Schema(description = "项目成员编号", requiredMode = Schema.RequiredMode.REQUIRED, example = "1024")
    private Long id;

    @Schema(description = "项目编号", requiredMode = Schema.RequiredMode.REQUIRED, example = "2048")
    private Long projectId;

    @Schema(description = "用户编号", requiredMode = Schema.RequiredMode.REQUIRED, example = "1")
    @Trans(type = TransType.AUTO_TRANS, key = AdminUserApi.PREFIX,
            fields = "nickname", ref = "userName")
    private Long userId;
    @Schema(description = "用户昵称", example = "芋道源码")
    private String userName;

    @Schema(description = "项目角色列表", requiredMode = Schema.RequiredMode.REQUIRED)
    private List<ProjectRoleRespVO> roles;

    @Schema(description = "创建时间", requiredMode = Schema.RequiredMode.REQUIRED)
    private LocalDateTime createTime;

}
