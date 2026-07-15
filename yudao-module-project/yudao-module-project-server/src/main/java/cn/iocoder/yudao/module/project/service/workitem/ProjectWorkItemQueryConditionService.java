package cn.iocoder.yudao.module.project.service.workitem;

import cn.hutool.core.util.StrUtil;
import cn.iocoder.yudao.framework.common.util.date.LocalDateTimeUtils;
import cn.iocoder.yudao.module.project.controller.admin.workitem.vo.ProjectWorkItemPageReqVO;
import cn.iocoder.yudao.module.project.dal.dataobject.workitem.WorkItemFieldDO;
import cn.iocoder.yudao.module.project.dal.dataobject.workitem.WorkItemFieldOptionDO;
import cn.iocoder.yudao.module.project.dal.dataobject.workitem.WorkItemQueryViewDO;
import cn.iocoder.yudao.module.project.dal.mysql.workitem.WorkItemFieldMapper;
import cn.iocoder.yudao.module.project.dal.mysql.workitem.WorkItemFieldOptionMapper;
import cn.iocoder.yudao.module.project.enums.ProjectWorkItemQueryFieldEnum;
import cn.iocoder.yudao.module.project.enums.ProjectWorkItemQueryOperatorEnum;
import cn.iocoder.yudao.module.project.enums.WorkItemFieldScopeEnum;
import cn.iocoder.yudao.module.project.enums.WorkItemFieldTypeEnum;
import jakarta.annotation.Resource;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.time.format.DateTimeParseException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.EnumSet;
import java.util.List;
import java.util.Objects;
import java.util.Set;
import java.util.stream.Collectors;

import static cn.iocoder.yudao.framework.common.exception.util.ServiceExceptionUtil.exception;
import static cn.iocoder.yudao.framework.security.core.util.SecurityFrameworkUtils.getLoginUserId;
import static cn.iocoder.yudao.module.project.enums.ErrorCodeConstants.PROJECT_WORK_ITEM_QUERY_CONDITION_INVALID;
import static cn.iocoder.yudao.module.project.enums.ErrorCodeConstants.PROJECT_WORK_ITEM_QUERY_FIELD_NOT_SUPPORTED;

@Service
public class ProjectWorkItemQueryConditionService {

    static final int MAX_CONDITION_COUNT = 20;

    @Resource
    private WorkItemFieldMapper customFieldMapper;
    @Resource
    private WorkItemFieldOptionMapper customFieldOptionMapper;

    public void normalize(ProjectWorkItemPageReqVO pageReqVO) {
        normalize(pageReqVO, getLoginUserId());
    }

    void normalize(ProjectWorkItemPageReqVO pageReqVO, Long loginUserId) {
        boolean hasFlatConditions = isNotEmpty(pageReqVO.getConditions());
        boolean hasConditionGroups = isNotEmpty(pageReqVO.getConditionGroups());
        if (hasFlatConditions && hasConditionGroups) {
            throw exception(PROJECT_WORK_ITEM_QUERY_CONDITION_INVALID, "conditions,conditionGroups");
        }
        if (hasFlatConditions) {
            validateConditionCount(pageReqVO.getConditions().size(), "conditions");
            normalizeConditions(pageReqVO, pageReqVO.getConditions(), loginUserId);
            return;
        }
        if (!hasConditionGroups) {
            return;
        }
        int conditionCount = 0;
        for (ProjectWorkItemPageReqVO.ConditionGroup group : pageReqVO.getConditionGroups()) {
            if (group == null || !isNotEmpty(group.getConditions())) {
                throw exception(PROJECT_WORK_ITEM_QUERY_CONDITION_INVALID, "conditionGroups");
            }
            conditionCount += group.getConditions().size();
            validateConditionCount(conditionCount, "conditionGroups");
            normalizeConditions(pageReqVO, group.getConditions(), loginUserId);
        }
    }

    public void validateQueryViewCondition(WorkItemQueryViewDO.QueryCondition condition, Long loginUserId) {
        if (condition == null) {
            throw exception(PROJECT_WORK_ITEM_QUERY_CONDITION_INVALID, "condition");
        }
        normalize(toPageReqVO(condition), loginUserId);
    }

    private void validateConditionCount(int conditionCount, String field) {
        if (conditionCount > MAX_CONDITION_COUNT) {
            throw exception(PROJECT_WORK_ITEM_QUERY_CONDITION_INVALID, field);
        }
    }

    private void normalizeConditions(ProjectWorkItemPageReqVO pageReqVO,
                                     List<ProjectWorkItemPageReqVO.Condition> conditions, Long loginUserId) {
        for (ProjectWorkItemPageReqVO.Condition condition : conditions) {
            if (condition == null) {
                throw exception(PROJECT_WORK_ITEM_QUERY_CONDITION_INVALID, "conditions");
            }
            ProjectWorkItemQueryFieldEnum field = ProjectWorkItemQueryFieldEnum.of(condition.getField());
            if (field == null) {
                normalizeCustomQueryCondition(pageReqVO, condition, loginUserId);
                continue;
            }
            ProjectWorkItemQueryOperatorEnum operator = ProjectWorkItemQueryOperatorEnum.of(condition.getOperator());
            if (operator == null || !field.supports(operator)) {
                throw exception(PROJECT_WORK_ITEM_QUERY_CONDITION_INVALID, condition.getField());
            }
            condition.setFieldEnum(field);
            condition.setOperatorEnum(operator);
            normalizeQueryConditionValue(condition, field, operator, loginUserId);
        }
    }

    private void normalizeCustomQueryCondition(ProjectWorkItemPageReqVO pageReqVO,
                                               ProjectWorkItemPageReqVO.Condition condition, Long loginUserId) {
        if (StrUtil.isBlank(condition.getField()) || !condition.getField().startsWith("custom:")
                || pageReqVO.getProjectId() == null) {
            throw exception(PROJECT_WORK_ITEM_QUERY_FIELD_NOT_SUPPORTED, condition.getField());
        }
        Long fieldId;
        try {
            fieldId = Long.valueOf(condition.getField().substring("custom:".length()));
        } catch (NumberFormatException e) {
            throw exception(PROJECT_WORK_ITEM_QUERY_FIELD_NOT_SUPPORTED, condition.getField());
        }
        WorkItemFieldDO field = customFieldMapper.selectById(fieldId);
        if (field == null || !WorkItemFieldScopeEnum.isEffectiveForProject(
                field.getScope(), field.getProjectId(), pageReqVO.getProjectId())) {
            throw exception(PROJECT_WORK_ITEM_QUERY_FIELD_NOT_SUPPORTED, condition.getField());
        }
        ProjectWorkItemQueryOperatorEnum operator = ProjectWorkItemQueryOperatorEnum.of(condition.getOperator());
        WorkItemFieldTypeEnum type = WorkItemFieldTypeEnum.of(field.getFieldType());
        if (operator == null || !supportsCustomOperator(type, operator)) {
            throw exception(PROJECT_WORK_ITEM_QUERY_CONDITION_INVALID, condition.getField());
        }
        condition.setCustomField(field);
        condition.setOperatorEnum(operator);
        if (operator == ProjectWorkItemQueryOperatorEnum.IS_EMPTY
                || operator == ProjectWorkItemQueryOperatorEnum.IS_NOT_EMPTY) {
            return;
        }
        if (operator == ProjectWorkItemQueryOperatorEnum.IN || operator == ProjectWorkItemQueryOperatorEnum.NOT_IN
                || operator == ProjectWorkItemQueryOperatorEnum.CONTAINS
                || operator == ProjectWorkItemQueryOperatorEnum.NOT_CONTAINS) {
            List<String> rawValues = type == WorkItemFieldTypeEnum.TEXT && StrUtil.isNotBlank(condition.getValue())
                    ? List.of(condition.getValue()) : getRawQueryValues(condition);
            if (rawValues.isEmpty()) {
                throw exception(PROJECT_WORK_ITEM_QUERY_CONDITION_INVALID, condition.getField());
            }
            List<Object> parsed = rawValues.stream()
                    .map(value -> parseCustomQueryValue(field, type, value, loginUserId)).toList();
            validateCustomOptionValues(field, type, parsed);
            condition.setParsedValues(parsed);
            if (type == WorkItemFieldTypeEnum.TEXT) {
                condition.setParsedValue(parsed.get(0));
            }
            return;
        }
        if (operator == ProjectWorkItemQueryOperatorEnum.BETWEEN) {
            List<String> rawValues = getRawQueryValues(condition);
            String start = rawValues.size() >= 2 ? rawValues.get(0) : condition.getValue();
            String end = rawValues.size() >= 2 ? rawValues.get(1) : condition.getValueEnd();
            condition.setParsedValue(parseCustomQueryValue(field, type, start, loginUserId));
            condition.setParsedValueEnd(parseCustomQueryValue(field, type, end, loginUserId));
            return;
        }
        Object parsed = parseCustomQueryValue(field, type, condition.getValue(), loginUserId);
        validateCustomOptionValues(field, type, List.of(parsed));
        condition.setParsedValue(parsed);
    }

    private boolean supportsCustomOperator(WorkItemFieldTypeEnum type, ProjectWorkItemQueryOperatorEnum operator) {
        if (type == null) {
            return false;
        }
        if (operator == ProjectWorkItemQueryOperatorEnum.IS_EMPTY
                || operator == ProjectWorkItemQueryOperatorEnum.IS_NOT_EMPTY) {
            return true;
        }
        return switch (type) {
            case TEXT -> EnumSet.of(ProjectWorkItemQueryOperatorEnum.EQ, ProjectWorkItemQueryOperatorEnum.NE,
                    ProjectWorkItemQueryOperatorEnum.CONTAINS, ProjectWorkItemQueryOperatorEnum.NOT_CONTAINS)
                    .contains(operator);
            case NUMBER, DATE_TIME -> EnumSet.of(ProjectWorkItemQueryOperatorEnum.EQ, ProjectWorkItemQueryOperatorEnum.NE,
                    ProjectWorkItemQueryOperatorEnum.GT, ProjectWorkItemQueryOperatorEnum.GE,
                    ProjectWorkItemQueryOperatorEnum.LT, ProjectWorkItemQueryOperatorEnum.LE,
                    ProjectWorkItemQueryOperatorEnum.BETWEEN).contains(operator);
            case SINGLE_SELECT, MULTI_SELECT, USER -> EnumSet.of(ProjectWorkItemQueryOperatorEnum.EQ,
                    ProjectWorkItemQueryOperatorEnum.NE, ProjectWorkItemQueryOperatorEnum.IN,
                    ProjectWorkItemQueryOperatorEnum.NOT_IN, ProjectWorkItemQueryOperatorEnum.CONTAINS,
                    ProjectWorkItemQueryOperatorEnum.NOT_CONTAINS).contains(operator);
            case BOOLEAN -> operator == ProjectWorkItemQueryOperatorEnum.EQ
                    || operator == ProjectWorkItemQueryOperatorEnum.NE;
        };
    }

    private Object parseCustomQueryValue(WorkItemFieldDO field, WorkItemFieldTypeEnum type,
                                         String rawValue, Long loginUserId) {
        if (StrUtil.isBlank(rawValue)) {
            throw exception(PROJECT_WORK_ITEM_QUERY_CONDITION_INVALID, field.getCode());
        }
        try {
            return switch (type) {
                case TEXT -> rawValue;
                case NUMBER -> new BigDecimal(rawValue);
                case DATE_TIME -> LocalDateTimeUtils.parse(rawValue);
                case BOOLEAN -> {
                    if (!StrUtil.equalsAnyIgnoreCase(rawValue, "true", "false")) {
                        throw exception(PROJECT_WORK_ITEM_QUERY_CONDITION_INVALID, field.getCode());
                    }
                    yield Boolean.valueOf(rawValue);
                }
                case USER -> Long.valueOf(resolveCurrentUserToken(rawValue, loginUserId, field.getCode()));
                case SINGLE_SELECT, MULTI_SELECT -> Long.valueOf(rawValue);
            };
        } catch (NumberFormatException | DateTimeParseException | NullPointerException e) {
            throw exception(PROJECT_WORK_ITEM_QUERY_CONDITION_INVALID, field.getCode());
        }
    }

    private void validateCustomOptionValues(WorkItemFieldDO field, WorkItemFieldTypeEnum type, List<Object> values) {
        if (!type.isSelect()) {
            return;
        }
        Set<Long> optionIds = values.stream().map(value -> (Long) value).collect(Collectors.toSet());
        Set<Long> found = customFieldOptionMapper.selectListByIds(optionIds).stream()
                .filter(option -> Objects.equals(option.getFieldId(), field.getId()))
                .map(WorkItemFieldOptionDO::getId).collect(Collectors.toSet());
        if (!found.equals(optionIds)) {
            throw exception(PROJECT_WORK_ITEM_QUERY_CONDITION_INVALID, field.getCode());
        }
    }

    private void normalizeQueryConditionValue(ProjectWorkItemPageReqVO.Condition condition,
                                              ProjectWorkItemQueryFieldEnum field,
                                              ProjectWorkItemQueryOperatorEnum operator, Long loginUserId) {
        if (operator == ProjectWorkItemQueryOperatorEnum.IS_EMPTY
                || operator == ProjectWorkItemQueryOperatorEnum.IS_NOT_EMPTY) {
            return;
        }
        if (operator == ProjectWorkItemQueryOperatorEnum.IN
                || operator == ProjectWorkItemQueryOperatorEnum.NOT_IN
                || (field.isText() && (operator == ProjectWorkItemQueryOperatorEnum.CONTAINS
                || operator == ProjectWorkItemQueryOperatorEnum.NOT_CONTAINS))) {
            if (field.isText()) {
                condition.setParsedValue(parseQueryValue(field, condition.getValue(), loginUserId));
                return;
            }
            condition.setParsedValues(parseQueryValues(field, condition, loginUserId));
            return;
        }
        if (operator == ProjectWorkItemQueryOperatorEnum.CONTAINS
                || operator == ProjectWorkItemQueryOperatorEnum.NOT_CONTAINS) {
            condition.setParsedValues(parseQueryValues(field, condition, loginUserId));
            return;
        }
        if (operator == ProjectWorkItemQueryOperatorEnum.BETWEEN) {
            List<String> values = getRawQueryValues(condition);
            if (values.size() >= 2) {
                condition.setParsedValue(parseQueryValue(field, values.get(0), loginUserId));
                condition.setParsedValueEnd(parseQueryValue(field, values.get(1), loginUserId));
                return;
            }
            condition.setParsedValue(parseQueryValue(field, condition.getValue(), loginUserId));
            condition.setParsedValueEnd(parseQueryValue(field, condition.getValueEnd(), loginUserId));
            return;
        }
        condition.setParsedValue(parseQueryValue(field, condition.getValue(), loginUserId));
    }

    private List<Object> parseQueryValues(ProjectWorkItemQueryFieldEnum field,
                                          ProjectWorkItemPageReqVO.Condition condition, Long loginUserId) {
        List<String> rawValues = getRawQueryValues(condition);
        if (rawValues.isEmpty()) {
            throw exception(PROJECT_WORK_ITEM_QUERY_CONDITION_INVALID, condition.getField());
        }
        return rawValues.stream().map(value -> parseQueryValue(field, value, loginUserId)).collect(Collectors.toList());
    }

    private List<String> getRawQueryValues(ProjectWorkItemPageReqVO.Condition condition) {
        if (condition.getValues() != null && !condition.getValues().isEmpty()) {
            return condition.getValues().stream().filter(StrUtil::isNotBlank).collect(Collectors.toList());
        }
        if (StrUtil.isBlank(condition.getValue())) {
            return Collections.emptyList();
        }
        if (condition.getValue().contains(",")) {
            return Arrays.stream(condition.getValue().split(","))
                    .map(String::trim).filter(StrUtil::isNotBlank).collect(Collectors.toList());
        }
        return List.of(condition.getValue());
    }

    private Object parseQueryValue(ProjectWorkItemQueryFieldEnum field, String rawValue, Long loginUserId) {
        if (StrUtil.isBlank(rawValue)) {
            throw exception(PROJECT_WORK_ITEM_QUERY_CONDITION_INVALID, field.getField());
        }
        String value = field.isUserField()
                ? resolveCurrentUserToken(rawValue, loginUserId, field.getField()) : rawValue;
        try {
            if (field.getValueType() == String.class) {
                return value;
            }
            if (field.getValueType() == Long.class) {
                return Long.valueOf(value);
            }
            if (field.getValueType() == java.time.LocalDateTime.class) {
                return LocalDateTimeUtils.parse(value);
            }
            if (field.getValueType() == BigDecimal.class) {
                return new BigDecimal(value);
            }
        } catch (NumberFormatException | DateTimeParseException e) {
            throw exception(PROJECT_WORK_ITEM_QUERY_CONDITION_INVALID, field.getField());
        }
        throw exception(PROJECT_WORK_ITEM_QUERY_CONDITION_INVALID, field.getField());
    }

    private String resolveCurrentUserToken(String rawValue, Long loginUserId, String field) {
        if (!StrUtil.equalsAnyIgnoreCase(rawValue, "me", "currentUser", "self")) {
            return rawValue;
        }
        if (loginUserId == null) {
            throw exception(PROJECT_WORK_ITEM_QUERY_CONDITION_INVALID, field);
        }
        return loginUserId.toString();
    }

    private ProjectWorkItemPageReqVO toPageReqVO(WorkItemQueryViewDO.QueryCondition condition) {
        ProjectWorkItemPageReqVO pageReqVO = new ProjectWorkItemPageReqVO();
        pageReqVO.setKeyword(condition.getKeyword());
        pageReqVO.setCategory(condition.getCategory());
        pageReqVO.setStatus(condition.getStatus());
        pageReqVO.setPriority(condition.getPriority());
        pageReqVO.setAssigneeUserId(condition.getAssigneeUserId());
        pageReqVO.setCreator(condition.getCreator());
        pageReqVO.setProjectId(condition.getProjectId());
        pageReqVO.setSprintId(condition.getSprintId());
        pageReqVO.setPlannedEndTime(condition.getPlannedEndTime());
        pageReqVO.setCreateTime(condition.getCreateTime());
        pageReqVO.setConditions(toPageConditions(condition.getConditions()));
        if (condition.getConditionGroups() != null) {
            pageReqVO.setConditionGroups(condition.getConditionGroups().stream().map(this::toPageConditionGroup).toList());
        }
        return pageReqVO;
    }

    private ProjectWorkItemPageReqVO.ConditionGroup toPageConditionGroup(WorkItemQueryViewDO.ConditionGroup source) {
        if (source == null) {
            return null;
        }
        ProjectWorkItemPageReqVO.ConditionGroup target = new ProjectWorkItemPageReqVO.ConditionGroup();
        target.setConditions(toPageConditions(source.getConditions()));
        return target;
    }

    private List<ProjectWorkItemPageReqVO.Condition> toPageConditions(
            List<WorkItemQueryViewDO.FieldCondition> conditions) {
        if (conditions == null) {
            return null;
        }
        return conditions.stream().map(this::toPageCondition).toList();
    }

    private ProjectWorkItemPageReqVO.Condition toPageCondition(WorkItemQueryViewDO.FieldCondition source) {
        if (source == null) {
            return null;
        }
        ProjectWorkItemPageReqVO.Condition target = new ProjectWorkItemPageReqVO.Condition();
        target.setField(source.getField());
        target.setOperator(source.getOperator());
        target.setValue(source.getValue());
        target.setValueEnd(source.getValueEnd());
        target.setValues(source.getValues() == null ? null : new ArrayList<>(source.getValues()));
        return target;
    }

    private boolean isNotEmpty(List<?> list) {
        return list != null && !list.isEmpty();
    }

}
