package cn.iocoder.yudao.module.project.controller.admin.workitem.vo;

import cn.iocoder.yudao.framework.common.pojo.PageParam;
import cn.iocoder.yudao.framework.common.validation.InEnum;
import cn.iocoder.yudao.module.project.enums.ProjectWorkItemCategoryEnum;
import cn.iocoder.yudao.module.project.enums.ProjectWorkItemPriorityEnum;
import cn.iocoder.yudao.module.project.enums.ProjectWorkItemStatusEnum;
import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;
import org.springframework.format.annotation.DateTimeFormat;

import java.time.LocalDateTime;

import static cn.iocoder.yudao.framework.common.util.date.DateUtils.FORMAT_YEAR_MONTH_DAY_HOUR_MINUTE_SECOND;

@Schema(description = "管理后台 - 项目工作项分页 Request VO")
@Data
public class ProjectWorkItemPageReqVO extends PageParam {

    @Schema(description = "关键字，匹配工作项编号或标题", example = "WI")
    private String keyword;

    @Schema(description = "工作项分类，参见 project_work_item_category", example = "REQUIREMENT")
    @InEnum(ProjectWorkItemCategoryEnum.class)
    private String category;

    @Schema(description = "状态，参见 project_work_item_status", example = "PENDING")
    @InEnum(ProjectWorkItemStatusEnum.class)
    private String status;

    @Schema(description = "优先级，参见 project_work_item_priority", example = "MEDIUM")
    @InEnum(ProjectWorkItemPriorityEnum.class)
    private String priority;

    @Schema(description = "负责人用户编号", example = "1")
    private Long assigneeUserId;

    @Schema(description = "创建者用户编号", example = "1")
    private String creator;

    @Schema(description = "项目编号", example = "1024")
    private Long projectId;

    @Schema(description = "迭代编号", example = "2048")
    private Long iterationId;

    @Schema(description = "计划完成时间")
    @DateTimeFormat(pattern = FORMAT_YEAR_MONTH_DAY_HOUR_MINUTE_SECOND)
    private LocalDateTime[] plannedEndTime;

    @Schema(description = "创建时间")
    @DateTimeFormat(pattern = FORMAT_YEAR_MONTH_DAY_HOUR_MINUTE_SECOND)
    private LocalDateTime[] createTime;

}
