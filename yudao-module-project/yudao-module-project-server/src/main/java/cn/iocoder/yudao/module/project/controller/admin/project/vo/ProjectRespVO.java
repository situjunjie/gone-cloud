package cn.iocoder.yudao.module.project.controller.admin.project.vo;

import cn.iocoder.yudao.module.system.api.user.AdminUserApi;
import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;
import org.dromara.core.trans.anno.Trans;
import org.dromara.core.trans.constant.TransType;
import org.dromara.core.trans.vo.VO;

import java.time.LocalDateTime;

@Schema(description = "管理后台 - 项目 Response VO")
@Data
public class ProjectRespVO implements VO {

    @Schema(description = "项目编号", requiredMode = Schema.RequiredMode.REQUIRED, example = "1024")
    private Long id;

    @Schema(description = "项目封面地址")
    private String coverUrl;

    @Schema(description = "项目名称", requiredMode = Schema.RequiredMode.REQUIRED, example = "研发管理平台")
    private String name;

    @Schema(description = "公开性，参见 project_visibility", requiredMode = Schema.RequiredMode.REQUIRED, example = "PUBLIC")
    private String visibility;

    @Schema(description = "项目编号展示值", requiredMode = Schema.RequiredMode.REQUIRED, example = "RD-PLATFORM")
    private String code;

    @Schema(description = "项目描述")
    private String description;

    @Schema(description = "负责人用户编号", requiredMode = Schema.RequiredMode.REQUIRED, example = "1")
    @Trans(type = TransType.AUTO_TRANS, key = AdminUserApi.PREFIX,
            fields = {"nickname", "avatar"}, refs = {"ownerUserName", "ownerUserAvatar"})
    private Long ownerUserId;
    @Schema(description = "负责人用户昵称", example = "芋道源码")
    private String ownerUserName;
    @Schema(description = "负责人用户头像", example = "https://www.iocoder.cn/1.png")
    private String ownerUserAvatar;

    @Schema(description = "状态，参见 project_status", requiredMode = Schema.RequiredMode.REQUIRED, example = "ACTIVE")
    private String status;

    @Schema(description = "排序", example = "0")
    private Integer sort;

    @Schema(description = "成员数量", example = "5")
    private Integer memberCount;

    @Schema(description = "创建者用户编号", requiredMode = Schema.RequiredMode.REQUIRED, example = "1")
    @Trans(type = TransType.AUTO_TRANS, key = AdminUserApi.PREFIX,
            fields = {"nickname", "avatar"}, refs = {"creatorUserName", "creatorUserAvatar"})
    private String creatorUserId;
    @Schema(description = "创建者用户昵称", example = "芋道源码")
    private String creatorUserName;
    @Schema(description = "创建者用户头像", example = "https://www.iocoder.cn/1.png")
    private String creatorUserAvatar;

    @Schema(description = "创建时间", requiredMode = Schema.RequiredMode.REQUIRED)
    private LocalDateTime createTime;

    @Schema(description = "更新时间", requiredMode = Schema.RequiredMode.REQUIRED)
    private LocalDateTime updateTime;

}
