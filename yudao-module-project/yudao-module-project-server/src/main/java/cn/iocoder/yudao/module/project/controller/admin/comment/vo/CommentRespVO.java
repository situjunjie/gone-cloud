package cn.iocoder.yudao.module.project.controller.admin.comment.vo;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;

import java.time.LocalDateTime;
import java.util.List;

@Schema(description = "管理后台 - 项目评论 Response VO")
@Data
public class CommentRespVO {

    @Schema(description = "评论编号", requiredMode = Schema.RequiredMode.REQUIRED, example = "1024")
    private Long id;
    @Schema(description = "归属项目编号", requiredMode = Schema.RequiredMode.REQUIRED, example = "100")
    private Long projectId;
    @Schema(description = "目标类型", requiredMode = Schema.RequiredMode.REQUIRED, example = "WORK_ITEM")
    private String targetType;
    @Schema(description = "目标编号", requiredMode = Schema.RequiredMode.REQUIRED, example = "2048")
    private Long targetId;
    @Schema(description = "作者用户编号", requiredMode = Schema.RequiredMode.REQUIRED, example = "1")
    private Long authorUserId;
    @Schema(description = "作者昵称", example = "张三")
    private String authorName;
    @Schema(description = "作者头像")
    private String authorAvatar;
    @Schema(description = "Markdown 正文，已删除评论为 null")
    private String content;
    @Schema(description = "被 @ 的用户编号集合")
    private List<Long> mentionUserIds;
    @Schema(description = "回复引用摘要")
    private Reply replyTo;
    @Schema(description = "是否编辑过", requiredMode = Schema.RequiredMode.REQUIRED)
    private Boolean edited;
    @Schema(description = "是否已删除", requiredMode = Schema.RequiredMode.REQUIRED)
    private Boolean deleted;
    @Schema(description = "创建时间", requiredMode = Schema.RequiredMode.REQUIRED)
    private LocalDateTime createTime;
    @Schema(description = "更新时间", requiredMode = Schema.RequiredMode.REQUIRED)
    private LocalDateTime updateTime;

    @Data
    public static class Reply {
        @Schema(description = "被回复评论编号", requiredMode = Schema.RequiredMode.REQUIRED, example = "1000")
        private Long id;
        @Schema(description = "被回复评论作者编号", requiredMode = Schema.RequiredMode.REQUIRED, example = "2")
        private Long authorUserId;
        @Schema(description = "被回复评论作者昵称", example = "李四")
        private String authorName;
        @Schema(description = "被回复评论完整正文，已删除评论为 null")
        private String content;
        @Schema(description = "正文摘要，已删除评论为 null")
        private String contentPreview;
        @Schema(description = "被回复评论是否已删除", requiredMode = Schema.RequiredMode.REQUIRED)
        private Boolean deleted;
    }

}
