package cn.iocoder.yudao.module.project.controller.admin.workitem.vo;

import cn.iocoder.yudao.module.project.api.sprint.ProjectSprintApi;
import cn.iocoder.yudao.module.project.api.workitem.ProjectWorkItemApi;
import cn.iocoder.yudao.module.system.api.user.AdminUserApi;
import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;
import org.dromara.core.trans.anno.Trans;
import org.dromara.core.trans.constant.TransType;
import org.dromara.core.trans.vo.VO;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;

@Schema(description = "管理后台 - 项目工作项 Response VO")
@Data
public class ProjectWorkItemRespVO implements VO {

    @Schema(description = "工作项编号", requiredMode = Schema.RequiredMode.REQUIRED, example = "1024")
    private Long id;

    @Schema(description = "工作项编号展示值", requiredMode = Schema.RequiredMode.REQUIRED, example = "WI-000001")
    private String workItemNo;

    @Schema(description = "工作项标题", requiredMode = Schema.RequiredMode.REQUIRED, example = "支持项目工作项")
    private String title;

    @Schema(description = "工作项描述")
    private String description;

    @Schema(description = "工作项分类，参见 project_work_item_category", requiredMode = Schema.RequiredMode.REQUIRED,
            example = "REQUIREMENT")
    private String category;

    @Schema(description = "优先级，参见 project_work_item_priority", requiredMode = Schema.RequiredMode.REQUIRED,
            example = "MEDIUM")
    private String priority;

    @Schema(description = "状态，参见 project_work_item_status", requiredMode = Schema.RequiredMode.REQUIRED,
            example = "PENDING")
    private String status;

    @Schema(description = "负责人用户编号", requiredMode = Schema.RequiredMode.REQUIRED, example = "1")
    @Trans(type = TransType.AUTO_TRANS, key = AdminUserApi.PREFIX,
            fields = "nickname", ref = "assigneeUserName")
    private Long assigneeUserId;
    @Schema(description = "负责人用户昵称", example = "芋道源码")
    private String assigneeUserName;

    @Schema(description = "项目编号", requiredMode = Schema.RequiredMode.REQUIRED, example = "1024")
    private Long projectId;

    @Schema(description = "迭代编号", example = "2048")
    @Trans(type = TransType.AUTO_TRANS, key = ProjectSprintApi.PREFIX,
            fields = "title", ref = "sprintTitle")
    private Long sprintId;
    @Schema(description = "迭代标题", example = "第 1 迭代")
    private String sprintTitle;

    @Schema(description = "计划开始时间")
    private LocalDateTime plannedStartTime;

    @Schema(description = "计划完成时间")
    private LocalDateTime plannedEndTime;

    @Schema(description = "预估工时合计", example = "8.00")
    private BigDecimal estimatedHours;

    @Schema(description = "实际工时合计", example = "5.50")
    private BigDecimal actualHours;

    @Schema(description = "父工作项编号", example = "512")
    @Trans(type = TransType.AUTO_TRANS, key = ProjectWorkItemApi.PREFIX,
            fields = "title", ref = "parentWorkItemTitle")
    private Long parentWorkItemId;
    @Schema(description = "父工作项标题", example = "支持项目工作项")
    private String parentWorkItemTitle;

    @Schema(description = "备注")
    private String remark;

    @Schema(description = "创建者用户编号", requiredMode = Schema.RequiredMode.REQUIRED, example = "1")
    @Trans(type = TransType.AUTO_TRANS, key = AdminUserApi.PREFIX,
            fields = "nickname", ref = "creatorUserName")
    private String creator;
    @Schema(description = "创建者用户昵称", example = "芋道源码")
    private String creatorUserName;

    @Schema(description = "创建时间", requiredMode = Schema.RequiredMode.REQUIRED)
    private LocalDateTime createTime;

    @Schema(description = "更新时间", requiredMode = Schema.RequiredMode.REQUIRED)
    private LocalDateTime updateTime;

    @Schema(description = "自定义字段值")
    private List<WorkItemCustomFieldValueRespVO> customFields;

}
