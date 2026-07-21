package cn.iocoder.yudao.module.project.controller.admin.project.vo;

import cn.iocoder.yudao.framework.common.validation.InEnum;
import cn.iocoder.yudao.module.project.enums.ProjectVisibilityEnum;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import lombok.Data;

@Schema(description = "管理后台 - 项目创建/修改 Request VO")
@Data
public class ProjectSaveReqVO {

    @Schema(description = "项目编号，修改时必填", example = "1024")
    private Long id;

    @Schema(description = "项目封面地址")
    @Size(max = 512, message = "项目封面地址长度不能超过 512 个字符")
    private String coverUrl;

    @Schema(description = "项目名称", requiredMode = Schema.RequiredMode.REQUIRED, example = "研发管理平台")
    @NotBlank(message = "项目名称不能为空")
    @Size(max = 128, message = "项目名称长度不能超过 128 个字符")
    private String name;

    @Schema(description = "公开性，当前仅支持 PRIVATE", requiredMode = Schema.RequiredMode.REQUIRED, example = "PRIVATE")
    @NotBlank(message = "公开性不能为空")
    @InEnum(ProjectVisibilityEnum.class)
    private String visibility;

    @Schema(description = "项目编号展示值", requiredMode = Schema.RequiredMode.REQUIRED, example = "RD-PLATFORM")
    @NotBlank(message = "项目编号不能为空")
    @Size(max = 64, message = "项目编号长度不能超过 64 个字符")
    private String code;

    @Schema(description = "项目描述")
    @Size(max = 1024, message = "项目描述长度不能超过 1024 个字符")
    private String description;

    @Schema(description = "项目公告")
    private String announcement;

    @Schema(description = "负责人用户编号", requiredMode = Schema.RequiredMode.REQUIRED, example = "1")
    @NotNull(message = "负责人用户编号不能为空")
    private Long ownerUserId;

    @Schema(description = "排序", example = "0")
    private Integer sort;

}
