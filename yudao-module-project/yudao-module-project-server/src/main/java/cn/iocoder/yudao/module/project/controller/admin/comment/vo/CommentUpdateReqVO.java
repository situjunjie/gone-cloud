package cn.iocoder.yudao.module.project.controller.admin.comment.vo;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import lombok.Data;

import java.util.LinkedHashSet;
import java.util.Set;

@Schema(description = "管理后台 - 项目评论更新 Request VO")
@Data
public class CommentUpdateReqVO {

    @Schema(description = "评论编号", requiredMode = Schema.RequiredMode.REQUIRED, example = "1024")
    @NotNull(message = "评论编号不能为空")
    private Long id;

    @Schema(description = "Markdown 正文", requiredMode = Schema.RequiredMode.REQUIRED, example = "验收口径已确认")
    @NotBlank(message = "评论正文不能为空")
    @Size(max = 10000, message = "评论正文不能超过 10000 个字符")
    private String content;

    @Schema(description = "被 @ 的用户编号集合", example = "[2, 3]")
    private Set<Long> mentionUserIds = new LinkedHashSet<>();

}
