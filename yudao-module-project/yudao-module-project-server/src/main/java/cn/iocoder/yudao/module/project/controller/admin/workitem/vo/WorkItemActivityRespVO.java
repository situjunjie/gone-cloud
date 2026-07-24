package cn.iocoder.yudao.module.project.controller.admin.workitem.vo;

import cn.iocoder.yudao.module.project.controller.admin.comment.vo.CommentRespVO;
import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;

import java.time.LocalDateTime;

@Schema(description = "管理后台 - 工作项动态 Response VO")
@Data
public class WorkItemActivityRespVO {

    @Schema(description = "动态类型", requiredMode = Schema.RequiredMode.REQUIRED, example = "COMMENT")
    private String type;
    @Schema(description = "来源记录编号，需和 type 共同作为唯一标识", requiredMode = Schema.RequiredMode.REQUIRED,
            example = "9001")
    private Long sourceId;
    @Schema(description = "操作者用户编号", requiredMode = Schema.RequiredMode.REQUIRED, example = "1")
    private Long operatorUserId;
    @Schema(description = "操作者昵称", example = "张三")
    private String operatorName;
    @Schema(description = "操作者头像")
    private String operatorAvatar;
    @Schema(description = "动态创建时间", requiredMode = Schema.RequiredMode.REQUIRED)
    private LocalDateTime createTime;
    @Schema(description = "评论载荷，仅 COMMENT 类型返回")
    private CommentRespVO comment;
    @Schema(description = "操作记录载荷，仅 OPERATION 类型返回")
    private WorkItemChangeLogRespVO operation;

}
