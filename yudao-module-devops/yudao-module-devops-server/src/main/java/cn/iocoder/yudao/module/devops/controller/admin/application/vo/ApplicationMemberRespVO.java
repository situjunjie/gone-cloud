package cn.iocoder.yudao.module.devops.controller.admin.application.vo;

import cn.iocoder.yudao.module.system.api.user.AdminUserApi;
import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;
import org.dromara.core.trans.anno.Trans;
import org.dromara.core.trans.constant.TransType;
import org.dromara.core.trans.vo.VO;

import java.time.LocalDateTime;

@Schema(description = "管理后台 - DevOps 应用成员 Response VO")
@Data
public class ApplicationMemberRespVO implements VO {

    @Schema(description = "成员行编号；拥有者未物化时为空", example = "1024")
    private Long id;

    @Schema(description = "应用编号", requiredMode = Schema.RequiredMode.REQUIRED, example = "1024")
    private Long appId;

    @Schema(description = "用户编号", requiredMode = Schema.RequiredMode.REQUIRED, example = "1")
    @Trans(type = TransType.AUTO_TRANS, key = AdminUserApi.PREFIX,
            fields = {"nickname", "avatar"}, refs = {"userName", "userAvatar"})
    private Long userId;
    @Schema(description = "用户昵称", example = "芋道源码")
    private String userName;
    @Schema(description = "用户头像")
    private String userAvatar;

    @Schema(description = "成员类型，参见 ApplicationMemberTypeEnum", requiredMode = Schema.RequiredMode.REQUIRED,
            example = "OWNER")
    private String memberType;

    @Schema(description = "加入时间")
    private LocalDateTime createTime;

}
