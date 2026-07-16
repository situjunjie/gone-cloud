package cn.iocoder.yudao.module.project.enums;

import cn.iocoder.yudao.module.project.dal.dataobject.workitem.ProjectWorkItemDO;
import com.baomidou.mybatisplus.core.toolkit.support.SFunction;
import lombok.AllArgsConstructor;
import lombok.Getter;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.Arrays;
import java.util.EnumSet;
import java.util.Set;

@Getter
@AllArgsConstructor
public enum ProjectWorkItemQueryFieldEnum {

    WORK_ITEM_NO("workItemNo", ProjectWorkItemDO::getWorkItemNo, String.class, QueryType.TEXT, false),
    TITLE("title", ProjectWorkItemDO::getTitle, String.class, QueryType.TEXT, false),
    DESCRIPTION("description", ProjectWorkItemDO::getDescription, String.class, QueryType.TEXT, false),
    CATEGORY("category", ProjectWorkItemDO::getCategory, String.class, QueryType.SELECT, false),
    PRIORITY("priority", ProjectWorkItemDO::getPriority, String.class, QueryType.SELECT, false),
    STATUS("status", ProjectWorkItemDO::getStatus, String.class, QueryType.SELECT, false),
    ASSIGNEE_USER_ID("assigneeUserId", ProjectWorkItemDO::getAssigneeUserId, Long.class, QueryType.SELECT, true),
    OWNER_USER_ID("ownerUserId", ProjectWorkItemDO::getAssigneeUserId, Long.class, QueryType.SELECT, true),
    CREATOR("creator", ProjectWorkItemDO::getCreator, String.class, QueryType.SELECT, true),
    CREATOR_USER_ID("creatorUserId", ProjectWorkItemDO::getCreator, String.class, QueryType.SELECT, true),
    UPDATER("updater", ProjectWorkItemDO::getUpdater, String.class, QueryType.SELECT, true),
    PROJECT_ID("projectId", ProjectWorkItemDO::getProjectId, Long.class, QueryType.SELECT, false),
    SPRINT_ID("sprintId", ProjectWorkItemDO::getSprintId, Long.class, QueryType.SELECT, false),
    PLANNED_START_TIME("plannedStartTime", ProjectWorkItemDO::getPlannedStartTime, LocalDateTime.class, QueryType.COMPARABLE, false),
    PLANNED_END_TIME("plannedEndTime", ProjectWorkItemDO::getPlannedEndTime, LocalDateTime.class, QueryType.COMPARABLE, false),
    ESTIMATED_WORKLOAD("estimatedWorkload", ProjectWorkItemDO::getEstimatedWorkload, BigDecimal.class, QueryType.COMPARABLE, false),
    PARENT_WORK_ITEM_ID("parentWorkItemId", ProjectWorkItemDO::getParentWorkItemId, Long.class, QueryType.SELECT, false),
    REMARK("remark", ProjectWorkItemDO::getRemark, String.class, QueryType.TEXT, false),
    CREATE_TIME("createTime", ProjectWorkItemDO::getCreateTime, LocalDateTime.class, QueryType.COMPARABLE, false),
    UPDATE_TIME("updateTime", ProjectWorkItemDO::getUpdateTime, LocalDateTime.class, QueryType.COMPARABLE, false);

    private final String field;
    private final SFunction<ProjectWorkItemDO, ?> column;
    private final Class<?> valueType;
    private final QueryType queryType;
    private final boolean userField;

    public boolean supports(ProjectWorkItemQueryOperatorEnum operator) {
        return queryType.getOperators().contains(operator);
    }

    public boolean isText() {
        return queryType == QueryType.TEXT;
    }

    /**
     * 返回服务端维护的物理列名，用于动态分组 SQL。
     */
    public String getPhysicalColumn() {
        return switch (this) {
            case WORK_ITEM_NO -> "work_item_no";
            case TITLE -> "title";
            case DESCRIPTION -> "description";
            case CATEGORY -> "category";
            case PRIORITY -> "priority";
            case STATUS -> "status";
            case ASSIGNEE_USER_ID, OWNER_USER_ID -> "assignee_user_id";
            case CREATOR, CREATOR_USER_ID -> "creator";
            case UPDATER -> "updater";
            case PROJECT_ID -> "project_id";
            case SPRINT_ID -> "sprint_id";
            case PLANNED_START_TIME -> "planned_start_time";
            case PLANNED_END_TIME -> "planned_end_time";
            case ESTIMATED_WORKLOAD -> "estimated_workload";
            case PARENT_WORK_ITEM_ID -> "parent_work_item_id";
            case REMARK -> "remark";
            case CREATE_TIME -> "create_time";
            case UPDATE_TIME -> "update_time";
        };
    }

    public static ProjectWorkItemQueryFieldEnum of(String field) {
        return Arrays.stream(values())
                .filter(item -> item.getField().equals(field))
                .findFirst()
                .orElse(null);
    }

    @Getter
    @AllArgsConstructor
    public enum QueryType {

        TEXT(EnumSet.of(ProjectWorkItemQueryOperatorEnum.EQ, ProjectWorkItemQueryOperatorEnum.NE,
                ProjectWorkItemQueryOperatorEnum.CONTAINS, ProjectWorkItemQueryOperatorEnum.NOT_CONTAINS,
                ProjectWorkItemQueryOperatorEnum.IS_EMPTY, ProjectWorkItemQueryOperatorEnum.IS_NOT_EMPTY)),
        SELECT(EnumSet.of(ProjectWorkItemQueryOperatorEnum.EQ, ProjectWorkItemQueryOperatorEnum.NE,
                ProjectWorkItemQueryOperatorEnum.IN, ProjectWorkItemQueryOperatorEnum.NOT_IN,
                ProjectWorkItemQueryOperatorEnum.CONTAINS, ProjectWorkItemQueryOperatorEnum.NOT_CONTAINS,
                ProjectWorkItemQueryOperatorEnum.IS_EMPTY, ProjectWorkItemQueryOperatorEnum.IS_NOT_EMPTY)),
        COMPARABLE(EnumSet.of(ProjectWorkItemQueryOperatorEnum.EQ, ProjectWorkItemQueryOperatorEnum.NE,
                ProjectWorkItemQueryOperatorEnum.GT, ProjectWorkItemQueryOperatorEnum.GE,
                ProjectWorkItemQueryOperatorEnum.LT, ProjectWorkItemQueryOperatorEnum.LE,
                ProjectWorkItemQueryOperatorEnum.BETWEEN, ProjectWorkItemQueryOperatorEnum.IS_EMPTY,
                ProjectWorkItemQueryOperatorEnum.IS_NOT_EMPTY));

        private final Set<ProjectWorkItemQueryOperatorEnum> operators;

    }

}
