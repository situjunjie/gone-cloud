package cn.iocoder.yudao.module.project.controller.admin.comment.vo;

import cn.iocoder.yudao.framework.common.validation.InEnum;
import cn.iocoder.yudao.module.project.enums.CommentTargetTypeEnum;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import lombok.Data;
import lombok.EqualsAndHashCode;

@Schema(description = "管理后台 - 项目评论创建 Request VO")
@Data
@EqualsAndHashCode(callSuper = true)
public class CommentCreateReqVO extends CommentCreateBaseReqVO {

    @Schema(description = "目标类型", requiredMode = Schema.RequiredMode.REQUIRED, example = "WORK_ITEM")
    @NotBlank(message = "评论目标类型不能为空")
    @InEnum(value = CommentTargetTypeEnum.class, message = "评论目标类型不正确")
    private String targetType;

    @Schema(description = "目标编号", requiredMode = Schema.RequiredMode.REQUIRED, example = "1024")
    @NotNull(message = "评论目标编号不能为空")
    private Long targetId;

}
