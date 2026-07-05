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
    ITERATION_ID("iterationId", ProjectWorkItemDO::getIterationId, Long.class, QueryType.SELECT, false),
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
                ProjectWorkItemQueryOperatorEnum.CONTAINS, ProjectWorkItemQueryOperatorEnum.NOT_CONTAINS)),
        SELECT(EnumSet.of(ProjectWorkItemQueryOperatorEnum.EQ, ProjectWorkItemQueryOperatorEnum.NE,
                ProjectWorkItemQueryOperatorEnum.IN, ProjectWorkItemQueryOperatorEnum.NOT_IN,
                ProjectWorkItemQueryOperatorEnum.CONTAINS, ProjectWorkItemQueryOperatorEnum.NOT_CONTAINS)),
        COMPARABLE(EnumSet.of(ProjectWorkItemQueryOperatorEnum.EQ, ProjectWorkItemQueryOperatorEnum.NE,
                ProjectWorkItemQueryOperatorEnum.GT, ProjectWorkItemQueryOperatorEnum.GE,
                ProjectWorkItemQueryOperatorEnum.LT, ProjectWorkItemQueryOperatorEnum.LE,
                ProjectWorkItemQueryOperatorEnum.BETWEEN));

        private final Set<ProjectWorkItemQueryOperatorEnum> operators;

    }

}
