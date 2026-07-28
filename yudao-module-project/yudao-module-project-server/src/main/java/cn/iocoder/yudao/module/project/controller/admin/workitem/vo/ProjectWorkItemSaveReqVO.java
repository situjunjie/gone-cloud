package cn.iocoder.yudao.module.project.controller.admin.workitem.vo;

import cn.iocoder.yudao.framework.common.validation.InEnum;
import cn.iocoder.yudao.module.project.enums.ProjectWorkItemCategoryEnum;
import cn.iocoder.yudao.module.project.enums.ProjectWorkItemPriorityEnum;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import lombok.Data;

import java.time.LocalDateTime;
import java.util.List;

@Schema(description = "管理后台 - 项目工作项创建/修改 Request VO")
@Data
public class ProjectWorkItemSaveReqVO {

    @Schema(description = "工作项编号，修改时必填", example = "1024")
    private Long id;

    @Schema(description = "工作项标题", requiredMode = Schema.RequiredMode.REQUIRED, example = "支持项目工作项")
    @NotBlank(message = "工作项标题不能为空")
    @Size(max = 128, message = "工作项标题长度不能超过 128 个字符")
    private String title;

    @Schema(description = "工作项描述")
    @Size(max = 5000, message = "工作项描述长度不能超过 5000 个字符")
    private String description;

    @Schema(description = "工作项分类，参见 project_work_item_category", requiredMode = Schema.RequiredMode.REQUIRED,
            example = "REQUIREMENT")
    @NotBlank(message = "工作项分类不能为空")
    @InEnum(ProjectWorkItemCategoryEnum.class)
    private String category;

    @Schema(description = "优先级，参见 project_work_item_priority", requiredMode = Schema.RequiredMode.REQUIRED,
            example = "MEDIUM")
    @NotBlank(message = "优先级不能为空")
    @InEnum(ProjectWorkItemPriorityEnum.class)
    private String priority;

    @Schema(description = "负责人用户编号", requiredMode = Schema.RequiredMode.REQUIRED, example = "1")
    @NotNull(message = "负责人用户编号不能为空")
    private Long assigneeUserId;

    @Schema(description = "项目编号", requiredMode = Schema.RequiredMode.REQUIRED, example = "1024")
    @NotNull(message = "项目编号不能为空")
    private Long projectId;

    @Schema(description = "迭代编号", example = "2048")
    private Long sprintId;

    @Schema(description = "计划开始时间")
    private LocalDateTime plannedStartTime;

    @Schema(description = "计划完成时间")
    private LocalDateTime plannedEndTime;

    @Schema(description = "父工作项编号", example = "512")
    private Long parentWorkItemId;

    @Schema(description = "备注")
    @Size(max = 512, message = "备注长度不能超过 512 个字符")
    private String remark;

    @Schema(description = "自定义字段值完整快照")
    private List<WorkItemCustomFieldValueReqVO> customFieldValues;

}
