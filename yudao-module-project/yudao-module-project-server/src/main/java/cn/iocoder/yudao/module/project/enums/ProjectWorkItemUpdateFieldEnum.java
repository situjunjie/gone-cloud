package cn.iocoder.yudao.module.project.enums;

import cn.iocoder.yudao.module.project.dal.dataobject.workitem.ProjectWorkItemDO;
import com.baomidou.mybatisplus.core.toolkit.support.SFunction;
import lombok.AllArgsConstructor;
import lombok.Getter;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.Arrays;

@Getter
@AllArgsConstructor
public enum ProjectWorkItemUpdateFieldEnum {

    TITLE("title", ProjectWorkItemDO::getTitle, String.class, false),
    DESCRIPTION("description", ProjectWorkItemDO::getDescription, String.class, true),
    CATEGORY("category", ProjectWorkItemDO::getCategory, String.class, false),
    PRIORITY("priority", ProjectWorkItemDO::getPriority, String.class, false),
    STATUS("status", ProjectWorkItemDO::getStatus, String.class, false),
    ASSIGNEE_USER_ID("assigneeUserId", ProjectWorkItemDO::getAssigneeUserId, Long.class, false),
    PROJECT_ID("projectId", ProjectWorkItemDO::getProjectId, Long.class, false),
    SPRINT_ID("sprintId", ProjectWorkItemDO::getSprintId, Long.class, true),
    PLANNED_START_TIME("plannedStartTime", ProjectWorkItemDO::getPlannedStartTime, LocalDateTime.class, true),
    PLANNED_END_TIME("plannedEndTime", ProjectWorkItemDO::getPlannedEndTime, LocalDateTime.class, true),
    ESTIMATED_WORKLOAD("estimatedWorkload", ProjectWorkItemDO::getEstimatedWorkload, BigDecimal.class, true),
    PARENT_WORK_ITEM_ID("parentWorkItemId", ProjectWorkItemDO::getParentWorkItemId, Long.class, true),
    REMARK("remark", ProjectWorkItemDO::getRemark, String.class, true);

    private final String field;
    private final SFunction<ProjectWorkItemDO, ?> column;
    private final Class<?> valueType;
    private final boolean nullable;

    public static ProjectWorkItemUpdateFieldEnum of(String field) {
        return Arrays.stream(values())
                .filter(item -> item.getField().equals(field))
                .findFirst()
                .orElse(null);
    }

}
