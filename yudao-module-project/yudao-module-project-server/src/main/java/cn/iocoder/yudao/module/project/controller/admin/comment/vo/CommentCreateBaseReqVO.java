package cn.iocoder.yudao.module.project.controller.admin.comment.vo;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.Data;

import java.util.LinkedHashSet;
import java.util.Set;

@Schema(description = "管理后台 - 项目评论创建基础 Request VO")
@Data
public class CommentCreateBaseReqVO {

    @Schema(description = "Markdown 正文", requiredMode = Schema.RequiredMode.REQUIRED, example = "请确认验收口径")
    @NotBlank(message = "评论正文不能为空")
    @Size(max = 10000, message = "评论正文不能超过 10000 个字符")
    private String content;

    @Schema(description = "回复引用的评论编号", example = "2048")
    private Long replyToCommentId;

    @Schema(description = "被 @ 的用户编号集合", example = "[2, 3]")
    private Set<Long> mentionUserIds = new LinkedHashSet<>();

}
