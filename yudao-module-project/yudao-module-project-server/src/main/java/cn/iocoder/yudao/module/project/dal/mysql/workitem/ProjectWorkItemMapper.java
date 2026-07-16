package cn.iocoder.yudao.module.project.dal.mysql.workitem;

import cn.hutool.core.util.StrUtil;
import cn.iocoder.yudao.framework.common.pojo.PageResult;
import cn.iocoder.yudao.framework.common.util.object.PageUtils;
import cn.iocoder.yudao.framework.mybatis.core.mapper.BaseMapperX;
import cn.iocoder.yudao.framework.mybatis.core.query.LambdaQueryWrapperX;
import cn.iocoder.yudao.module.project.controller.admin.workitem.vo.ProjectWorkItemPageReqVO;
import cn.iocoder.yudao.module.project.dal.dataobject.workitem.ProjectWorkItemDO;
import cn.iocoder.yudao.module.project.dal.dataobject.workitem.ProjectWorkItemGroupCountResult;
import cn.iocoder.yudao.module.project.dal.dataobject.workitem.ProjectWorkItemGroupField;
import cn.iocoder.yudao.module.project.enums.ProjectWorkItemQueryOperatorEnum;
import cn.iocoder.yudao.module.project.enums.ProjectWorkItemUpdateFieldEnum;
import cn.iocoder.yudao.module.project.enums.WorkItemFieldTypeEnum;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.conditions.update.LambdaUpdateWrapper;
import com.baomidou.mybatisplus.core.toolkit.Constants;
import com.baomidou.mybatisplus.core.toolkit.support.SFunction;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;

@Mapper
public interface ProjectWorkItemMapper extends BaseMapperX<ProjectWorkItemDO> {

    default PageResult<ProjectWorkItemDO> selectPage(ProjectWorkItemPageReqVO reqVO) {
        LambdaQueryWrapperX<ProjectWorkItemDO> query = buildQueryWrapper(reqVO);
        PageUtils.buildDefaultSortingField(reqVO, ProjectWorkItemDO::getId);
        return selectPage(reqVO, query);
    }

    default LambdaQueryWrapperX<ProjectWorkItemDO> buildQueryWrapper(ProjectWorkItemPageReqVO reqVO) {
        LambdaQueryWrapperX<ProjectWorkItemDO> query = new LambdaQueryWrapperX<ProjectWorkItemDO>()
                .eqIfPresent(ProjectWorkItemDO::getCategory, reqVO.getCategory())
                .eqIfPresent(ProjectWorkItemDO::getStatus, reqVO.getStatus())
                .eqIfPresent(ProjectWorkItemDO::getPriority, reqVO.getPriority())
                .eqIfPresent(ProjectWorkItemDO::getAssigneeUserId, reqVO.getAssigneeUserId())
                .eqIfPresent(ProjectWorkItemDO::getProjectId, reqVO.getProjectId())
                .eqIfPresent(ProjectWorkItemDO::getSprintId, reqVO.getSprintId())
                .eqIfPresent(ProjectWorkItemDO::getCreator, reqVO.getCreator())
                .betweenIfPresent(ProjectWorkItemDO::getPlannedEndTime, reqVO.getPlannedEndTime())
                .betweenIfPresent(ProjectWorkItemDO::getCreateTime, reqVO.getCreateTime());
        if (StrUtil.isNotBlank(reqVO.getKeyword())) {
            query.and(q -> q.like(ProjectWorkItemDO::getWorkItemNo, reqVO.getKeyword())
                    .or().like(ProjectWorkItemDO::getTitle, reqVO.getKeyword()));
        }
        applyConditions(query, reqVO.getConditions(), reqVO.getConditionGroups());
        return query;
    }

    List<ProjectWorkItemGroupCountResult> selectGroupCount(
            @Param(Constants.WRAPPER) LambdaQueryWrapperX<ProjectWorkItemDO> queryWrapper,
            @Param("groupField") ProjectWorkItemGroupField groupField);

    private void applyConditions(LambdaQueryWrapper<ProjectWorkItemDO> query,
                                 List<ProjectWorkItemPageReqVO.Condition> conditions,
                                 List<ProjectWorkItemPageReqVO.ConditionGroup> conditionGroups) {
        if (conditionGroups != null && !conditionGroups.isEmpty()) {
            query.and(groupsQuery -> {
                for (int index = 0; index < conditionGroups.size(); index++) {
                    ProjectWorkItemPageReqVO.ConditionGroup group = conditionGroups.get(index);
                    if (index == 0) {
                        groupsQuery.nested(groupQuery -> applyConditionGroup(groupQuery, group));
                    } else {
                        groupsQuery.or(groupQuery -> applyConditionGroup(groupQuery, group));
                    }
                }
            });
            return;
        }
        if (conditions == null || conditions.isEmpty()) {
            return;
        }
        for (ProjectWorkItemPageReqVO.Condition condition : conditions) {
            applyCondition(query, condition);
        }
    }

    private void applyConditionGroup(LambdaQueryWrapper<ProjectWorkItemDO> query,
                                     ProjectWorkItemPageReqVO.ConditionGroup group) {
        for (ProjectWorkItemPageReqVO.Condition condition : group.getConditions()) {
            applyCondition(query, condition);
        }
    }

    private void applyCondition(LambdaQueryWrapper<ProjectWorkItemDO> query,
                                ProjectWorkItemPageReqVO.Condition condition) {
        if (condition.getCustomField() != null) {
            applyCustomCondition(query, condition);
            return;
        }
        SFunction<ProjectWorkItemDO, ?> column = condition.getFieldEnum().getColumn();
        ProjectWorkItemQueryOperatorEnum operator = condition.getOperatorEnum();
        switch (operator) {
            case EQ -> query.eq(column, condition.getParsedValue());
            case NE -> query.ne(column, condition.getParsedValue());
            case GT -> query.gt(column, condition.getParsedValue());
            case GE -> query.ge(column, condition.getParsedValue());
            case LT -> query.lt(column, condition.getParsedValue());
            case LE -> query.le(column, condition.getParsedValue());
            case BETWEEN -> query.between(column, condition.getParsedValue(), condition.getParsedValueEnd());
            case IN -> query.in(column, condition.getParsedValues());
            case NOT_IN -> query.notIn(column, condition.getParsedValues());
            case CONTAINS -> applyContainsCondition(query, condition, column);
            case NOT_CONTAINS -> applyNotContainsCondition(query, condition, column);
            case IS_EMPTY -> query.isNull(column);
            case IS_NOT_EMPTY -> query.isNotNull(column);
        }
    }

    private void applyCustomCondition(LambdaQueryWrapper<ProjectWorkItemDO> query,
                                      ProjectWorkItemPageReqVO.Condition condition) {
        Long fieldId = condition.getCustomField().getId();
        ProjectWorkItemQueryOperatorEnum operator = condition.getOperatorEnum();
        if (operator == ProjectWorkItemQueryOperatorEnum.IS_EMPTY) {
            query.apply("NOT EXISTS (SELECT 1 FROM proj_work_item_field_value cfv "
                    + "WHERE cfv.work_item_id = proj_work_item.id AND cfv.tenant_id = proj_work_item.tenant_id "
                    + "AND cfv.deleted = 0 AND cfv.field_id = {0})", fieldId);
            return;
        }
        if (operator == ProjectWorkItemQueryOperatorEnum.IS_NOT_EMPTY) {
            query.apply("EXISTS (SELECT 1 FROM proj_work_item_field_value cfv "
                    + "WHERE cfv.work_item_id = proj_work_item.id AND cfv.tenant_id = proj_work_item.tenant_id "
                    + "AND cfv.deleted = 0 AND cfv.field_id = {0})", fieldId);
            return;
        }
        WorkItemFieldTypeEnum type = WorkItemFieldTypeEnum.of(condition.getCustomField().getFieldType());
        if (type.isSelect()) {
            applyCustomOptionCondition(query, condition, fieldId);
            return;
        }
        String column = switch (type) {
            case TEXT -> "text_value";
            case NUMBER -> "number_value";
            case DATE_TIME -> "datetime_value";
            case BOOLEAN -> "boolean_value";
            case USER -> "user_value";
            case SINGLE_SELECT, MULTI_SELECT -> throw new IllegalStateException("unexpected select field");
        };
        if (operator == ProjectWorkItemQueryOperatorEnum.IN || operator == ProjectWorkItemQueryOperatorEnum.NOT_IN
                || (type == WorkItemFieldTypeEnum.USER
                && (operator == ProjectWorkItemQueryOperatorEnum.CONTAINS
                || operator == ProjectWorkItemQueryOperatorEnum.NOT_CONTAINS))) {
            applyCustomScalarInCondition(query, condition, fieldId, column);
            return;
        }
        String comparison = switch (operator) {
            case EQ -> "= {1}";
            case NE -> "<> {1}";
            case GT -> "> {1}";
            case GE -> ">= {1}";
            case LT -> "< {1}";
            case LE -> "<= {1}";
            case CONTAINS -> "LIKE CONCAT('%', {1}, '%')";
            case NOT_CONTAINS -> "NOT LIKE CONCAT('%', {1}, '%')";
            case BETWEEN -> "BETWEEN {1} AND {2}";
            default -> throw new IllegalStateException("unsupported custom scalar operator: " + operator);
        };
        String sql = "EXISTS (SELECT 1 FROM proj_work_item_field_value cfv "
                + "WHERE cfv.work_item_id = proj_work_item.id AND cfv.tenant_id = proj_work_item.tenant_id "
                + "AND cfv.deleted = 0 AND cfv.field_id = {0} AND cfv." + column + " " + comparison + ")";
        if (operator == ProjectWorkItemQueryOperatorEnum.BETWEEN) {
            query.apply(sql, fieldId, condition.getParsedValue(), condition.getParsedValueEnd());
        } else {
            query.apply(sql, fieldId, condition.getParsedValue());
        }
    }

    private void applyCustomScalarInCondition(LambdaQueryWrapper<ProjectWorkItemDO> query,
                                               ProjectWorkItemPageReqVO.Condition condition,
                                               Long fieldId, String column) {
        List<Object> values = condition.getParsedValues();
        String placeholders = java.util.stream.IntStream.range(0, values.size())
                .mapToObj(index -> "{" + (index + 1) + "}").collect(java.util.stream.Collectors.joining(","));
        Object[] args = new Object[values.size() + 1];
        args[0] = fieldId;
        for (int i = 0; i < values.size(); i++) {
            args[i + 1] = values.get(i);
        }
        boolean negative = condition.getOperatorEnum() == ProjectWorkItemQueryOperatorEnum.NOT_IN
                || condition.getOperatorEnum() == ProjectWorkItemQueryOperatorEnum.NOT_CONTAINS;
        query.apply("EXISTS (SELECT 1 FROM proj_work_item_field_value cfv "
                + "WHERE cfv.work_item_id = proj_work_item.id AND cfv.tenant_id = proj_work_item.tenant_id "
                + "AND cfv.deleted = 0 AND cfv.field_id = {0} AND cfv." + column
                + (negative ? " NOT IN (" : " IN (") + placeholders + "))", args);
    }

    private void applyCustomOptionCondition(LambdaQueryWrapper<ProjectWorkItemDO> query,
                                            ProjectWorkItemPageReqVO.Condition condition, Long fieldId) {
        List<Object> values = condition.getParsedValues() == null
                ? List.of(condition.getParsedValue()) : condition.getParsedValues();
        String placeholders = java.util.stream.IntStream.range(0, values.size())
                .mapToObj(index -> "{" + (index + 1) + "}").collect(java.util.stream.Collectors.joining(","));
        Object[] args = new Object[values.size() + 1];
        args[0] = fieldId;
        for (int i = 0; i < values.size(); i++) {
            args[i + 1] = values.get(i);
        }
        String match = "EXISTS (SELECT 1 FROM proj_work_item_field_value_option cfvo "
                + "WHERE cfvo.work_item_id = proj_work_item.id AND cfvo.tenant_id = proj_work_item.tenant_id "
                + "AND cfvo.deleted = 0 AND cfvo.field_id = {0} AND cfvo.option_id IN (" + placeholders + "))";
        boolean negative = condition.getOperatorEnum() == ProjectWorkItemQueryOperatorEnum.NE
                || condition.getOperatorEnum() == ProjectWorkItemQueryOperatorEnum.NOT_IN
                || condition.getOperatorEnum() == ProjectWorkItemQueryOperatorEnum.NOT_CONTAINS;
        if (!negative) {
            query.apply(match, args);
            return;
        }
        query.apply("EXISTS (SELECT 1 FROM proj_work_item_field_value cfv WHERE cfv.work_item_id = proj_work_item.id "
                + "AND cfv.tenant_id = proj_work_item.tenant_id AND cfv.deleted = 0 AND cfv.field_id = {0}) AND NOT "
                + match, args);
    }

    private void applyContainsCondition(LambdaQueryWrapper<ProjectWorkItemDO> query,
                                        ProjectWorkItemPageReqVO.Condition condition,
                                        SFunction<ProjectWorkItemDO, ?> column) {
        if (condition.getFieldEnum().isText()) {
            query.like(column, condition.getParsedValue());
            return;
        }
        query.in(column, condition.getParsedValues());
    }

    private void applyNotContainsCondition(LambdaQueryWrapper<ProjectWorkItemDO> query,
                                           ProjectWorkItemPageReqVO.Condition condition,
                                           SFunction<ProjectWorkItemDO, ?> column) {
        if (condition.getFieldEnum().isText()) {
            query.notLike(column, condition.getParsedValue());
            return;
        }
        query.notIn(column, condition.getParsedValues());
    }

    default Long selectCountByParentWorkItemId(Long parentWorkItemId) {
        return selectCount(ProjectWorkItemDO::getParentWorkItemId, parentWorkItemId);
    }

    default int updateWorkItemNoById(Long id, String workItemNo, LocalDateTime updateTime) {
        return update(null, new LambdaUpdateWrapper<ProjectWorkItemDO>()
                .eq(ProjectWorkItemDO::getId, id)
                .set(ProjectWorkItemDO::getWorkItemNo, workItemNo)
                .set(ProjectWorkItemDO::getUpdateTime, updateTime));
    }

    default int updateFieldById(Long id, SFunction<ProjectWorkItemDO, ?> column, Object value, LocalDateTime updateTime) {
        return update(null, new LambdaUpdateWrapper<ProjectWorkItemDO>()
                .eq(ProjectWorkItemDO::getId, id)
                .set(column, value)
                .set(ProjectWorkItemDO::getUpdateTime, updateTime));
    }

    default int updateFieldsById(Long id, Map<ProjectWorkItemUpdateFieldEnum, Object> fieldValues,
                                 LocalDateTime updateTime) {
        LambdaUpdateWrapper<ProjectWorkItemDO> updateWrapper = new LambdaUpdateWrapper<ProjectWorkItemDO>()
                .eq(ProjectWorkItemDO::getId, id);
        fieldValues.forEach((field, value) -> updateWrapper.set(field.getColumn(), value));
        updateWrapper.set(ProjectWorkItemDO::getUpdateTime, updateTime);
        return update(null, updateWrapper);
    }

}
