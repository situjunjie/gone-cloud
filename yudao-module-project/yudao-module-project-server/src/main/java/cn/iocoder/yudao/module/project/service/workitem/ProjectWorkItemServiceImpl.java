package cn.iocoder.yudao.module.project.service.workitem;

import cn.hutool.core.util.IdUtil;
import cn.hutool.core.util.ObjUtil;
import cn.hutool.core.util.StrUtil;
import cn.iocoder.yudao.framework.common.pojo.PageResult;
import cn.iocoder.yudao.framework.common.util.date.LocalDateTimeUtils;
import cn.iocoder.yudao.module.project.controller.admin.workitem.vo.ProjectWorkItemPageReqVO;
import cn.iocoder.yudao.module.project.controller.admin.workitem.vo.ProjectWorkItemSaveReqVO;
import cn.iocoder.yudao.module.project.controller.admin.workitem.vo.ProjectWorkItemUpdateFieldReqVO;
import cn.iocoder.yudao.module.project.convert.workitem.ProjectWorkItemConvert;
import cn.iocoder.yudao.module.project.dal.dataobject.workitem.ProjectWorkItemDO;
import cn.iocoder.yudao.module.project.dal.mysql.workitem.ProjectWorkItemMapper;
import cn.iocoder.yudao.module.project.dal.mysql.workitem.WorkItemFieldMapper;
import cn.iocoder.yudao.module.project.dal.mysql.workitem.WorkItemFieldOptionMapper;
import cn.iocoder.yudao.module.project.dal.dataobject.workitem.WorkItemFieldDO;
import cn.iocoder.yudao.module.project.dal.dataobject.workitem.WorkItemFieldOptionDO;
import cn.iocoder.yudao.module.project.enums.ProjectWorkItemCategoryEnum;
import cn.iocoder.yudao.module.project.enums.ProjectWorkItemPriorityEnum;
import cn.iocoder.yudao.module.project.enums.ProjectWorkItemQueryFieldEnum;
import cn.iocoder.yudao.module.project.enums.ProjectWorkItemQueryOperatorEnum;
import cn.iocoder.yudao.module.project.enums.ProjectWorkItemStatusEnum;
import cn.iocoder.yudao.module.project.enums.ProjectWorkItemUpdateFieldEnum;
import cn.iocoder.yudao.module.project.enums.WorkItemFieldTypeEnum;
import cn.iocoder.yudao.module.project.service.project.ProjectService;
import cn.iocoder.yudao.module.system.api.user.AdminUserApi;
import com.fasterxml.jackson.databind.JsonNode;
import jakarta.annotation.Resource;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.validation.annotation.Validated;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.time.format.DateTimeParseException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.EnumMap;
import java.util.EnumSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.stream.Collectors;

import static cn.iocoder.yudao.framework.common.exception.util.ServiceExceptionUtil.exception;
import static cn.iocoder.yudao.framework.security.core.util.SecurityFrameworkUtils.getLoginUserId;
import static cn.iocoder.yudao.module.project.enums.ErrorCodeConstants.*;

@Service
@Validated
public class ProjectWorkItemServiceImpl implements ProjectWorkItemService {

    private static final String WORK_ITEM_NO_PREFIX = "WI-";
    private static final String WORK_ITEM_NO_TEMP_PREFIX = "WI-TMP-";

    @Resource
    private ProjectWorkItemMapper workItemMapper;
    @Resource
    private ProjectService projectService;
    @Resource
    private AdminUserApi adminUserApi;
    @Resource
    private WorkItemCustomFieldValueService customFieldValueService;
    @Resource
    private WorkItemFieldMapper customFieldMapper;
    @Resource
    private WorkItemFieldOptionMapper customFieldOptionMapper;

    @Override
    @Transactional(rollbackFor = Exception.class)
    public Long createWorkItem(ProjectWorkItemSaveReqVO createReqVO) {
        validateWorkItemForCreateOrUpdate(null, createReqVO);

        ProjectWorkItemDO workItem = ProjectWorkItemConvert.INSTANCE.convert(createReqVO);
        workItem.setStatus(ProjectWorkItemStatusEnum.PENDING.getValue());
        workItem.setWorkItemNo(WORK_ITEM_NO_TEMP_PREFIX + IdUtil.fastSimpleUUID());
        workItemMapper.insert(workItem);

        String workItemNo = generateWorkItemNo(workItem.getId());
        workItemMapper.updateWorkItemNoById(workItem.getId(), workItemNo, LocalDateTime.now());
        customFieldValueService.saveSnapshot(workItem.getId(), workItem.getProjectId(), workItem.getCategory(),
                createReqVO.getCustomFieldValues(), true);
        return workItem.getId();
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void updateWorkItem(ProjectWorkItemSaveReqVO updateReqVO) {
        ProjectWorkItemDO oldWorkItem = validateWorkItemExists(updateReqVO.getId());
        validateWorkItemForCreateOrUpdate(oldWorkItem, updateReqVO);
        if (ObjUtil.notEqual(oldWorkItem.getProjectId(), updateReqVO.getProjectId())
                && customFieldValueService.hasValue(oldWorkItem.getId())) {
            throw exception(WORK_ITEM_CUSTOM_FIELD_PROJECT_CHANGE_DENIED);
        }
        if (ObjUtil.notEqual(oldWorkItem.getCategory(), updateReqVO.getCategory())) {
            customFieldValueService.validateCategoryChange(oldWorkItem.getId(), updateReqVO.getCategory());
        }

        ProjectWorkItemDO updateObj = ProjectWorkItemConvert.INSTANCE.convert(updateReqVO);
        updateObj.setWorkItemNo(null);
        updateObj.setStatus(null);
        workItemMapper.updateById(updateObj);
        if (updateReqVO.getCustomFieldValues() != null) {
            customFieldValueService.saveSnapshot(oldWorkItem.getId(), updateReqVO.getProjectId(),
                    updateReqVO.getCategory(), updateReqVO.getCustomFieldValues(), true);
        }
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void updateWorkItemField(ProjectWorkItemUpdateFieldReqVO updateReqVO) {
        ProjectWorkItemDO oldWorkItem = validateWorkItemExists(updateReqVO.getId());
        List<ProjectWorkItemUpdateFieldReqVO.FieldValue> customFields = updateReqVO.getFields().stream()
                .filter(item -> item != null && item.getField() != null && item.getField().startsWith("custom:"))
                .toList();
        List<ProjectWorkItemUpdateFieldReqVO.FieldValue> builtInFields = updateReqVO.getFields().stream()
                .filter(item -> item == null || item.getField() == null || !item.getField().startsWith("custom:"))
                .toList();
        Map<ProjectWorkItemUpdateFieldEnum, Object> fieldValues = builtInFields.isEmpty()
                ? new EnumMap<>(ProjectWorkItemUpdateFieldEnum.class) : parseUpdateFieldValues(builtInFields);
        ProjectWorkItemDO updatedWorkItem = copyWorkItem(oldWorkItem);
        fieldValues.forEach((field, value) -> applyFieldValue(updatedWorkItem, field, value));

        validateWorkItemFields(oldWorkItem, updatedWorkItem, fieldValues.keySet());
        fieldValues.entrySet().removeIf(entry -> isFieldValueNotChanged(oldWorkItem, entry.getKey(), entry.getValue()));
        if (!fieldValues.isEmpty()) {
            workItemMapper.updateFieldsById(updateReqVO.getId(), fieldValues, LocalDateTime.now());
        }
        Set<Long> customFieldIds = new java.util.HashSet<>();
        for (ProjectWorkItemUpdateFieldReqVO.FieldValue item : customFields) {
            Long fieldId;
            try {
                fieldId = Long.valueOf(item.getField().substring("custom:".length()));
            } catch (NumberFormatException e) {
                throw exception(PROJECT_WORK_ITEM_FIELD_NOT_SUPPORTED, item.getField());
            }
            if (!customFieldIds.add(fieldId)) {
                throw exception(PROJECT_WORK_ITEM_FIELD_VALUE_INVALID, item.getField());
            }
            customFieldValueService.updateValue(oldWorkItem.getId(), updatedWorkItem.getProjectId(),
                    updatedWorkItem.getCategory(), fieldId, item.getValue());
        }
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void deleteWorkItem(Long id) {
        validateWorkItemExists(id);
        validateNoChildWorkItems(id);
        customFieldValueService.deleteByWorkItemId(id);
        workItemMapper.deleteById(id);
    }

    @Override
    public ProjectWorkItemDO getWorkItem(Long id) {
        return workItemMapper.selectById(id);
    }

    @Override
    public PageResult<ProjectWorkItemDO> getWorkItemPage(ProjectWorkItemPageReqVO pageReqVO) {
        normalizeQueryConditions(pageReqVO);
        return workItemMapper.selectPage(pageReqVO);
    }

    @Override
    public ProjectWorkItemDO validateWorkItemExists(Long id) {
        if (id == null) {
            throw exception(PROJECT_WORK_ITEM_NOT_EXISTS);
        }
        ProjectWorkItemDO workItem = workItemMapper.selectById(id);
        if (workItem == null) {
            throw exception(PROJECT_WORK_ITEM_NOT_EXISTS);
        }
        return workItem;
    }

    private void validateWorkItemForCreateOrUpdate(ProjectWorkItemDO oldWorkItem, ProjectWorkItemSaveReqVO reqVO) {
        projectService.validateProjectUsable(reqVO.getProjectId());
        adminUserApi.validateUser(reqVO.getAssigneeUserId());
        validatePlannedTime(reqVO);
        validateParentWorkItem(oldWorkItem == null ? null : oldWorkItem.getId(), reqVO);
        if (oldWorkItem != null && ObjUtil.notEqual(oldWorkItem.getProjectId(), reqVO.getProjectId())) {
            validateNoChildWorkItems(oldWorkItem.getId());
        }
    }

    private void normalizeQueryConditions(ProjectWorkItemPageReqVO pageReqVO) {
        if (pageReqVO.getConditions() == null) {
            return;
        }
        if (pageReqVO.getConditions().size() > 20) {
            throw exception(PROJECT_WORK_ITEM_QUERY_CONDITION_INVALID, "conditions");
        }
        for (ProjectWorkItemPageReqVO.Condition condition : pageReqVO.getConditions()) {
            ProjectWorkItemQueryFieldEnum field = ProjectWorkItemQueryFieldEnum.of(condition.getField());
            if (field == null) {
                normalizeCustomQueryCondition(pageReqVO, condition);
                continue;
            }
            ProjectWorkItemQueryOperatorEnum operator = ProjectWorkItemQueryOperatorEnum.of(condition.getOperator());
            if (operator == null || !field.supports(operator)) {
                throw exception(PROJECT_WORK_ITEM_QUERY_CONDITION_INVALID, condition.getField());
            }
            condition.setFieldEnum(field);
            condition.setOperatorEnum(operator);
            normalizeQueryConditionValue(condition, field, operator);
        }
    }

    private void normalizeCustomQueryCondition(ProjectWorkItemPageReqVO pageReqVO,
                                               ProjectWorkItemPageReqVO.Condition condition) {
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
        if (field == null || !Objects.equals(field.getProjectId(), pageReqVO.getProjectId())) {
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
            List<Object> parsed = rawValues.stream().map(value -> parseCustomQueryValue(field, type, value)).toList();
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
            condition.setParsedValue(parseCustomQueryValue(field, type, start));
            condition.setParsedValueEnd(parseCustomQueryValue(field, type, end));
            return;
        }
        Object parsed = parseCustomQueryValue(field, type, condition.getValue());
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
            case BOOLEAN -> operator == ProjectWorkItemQueryOperatorEnum.EQ || operator == ProjectWorkItemQueryOperatorEnum.NE;
        };
    }

    private Object parseCustomQueryValue(WorkItemFieldDO field, WorkItemFieldTypeEnum type, String rawValue) {
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
                case USER -> Long.valueOf(StrUtil.equalsAnyIgnoreCase(rawValue, "me", "currentUser", "self")
                        ? Objects.requireNonNull(getLoginUserId()).toString() : rawValue);
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
                                              ProjectWorkItemQueryOperatorEnum operator) {
        if (operator == ProjectWorkItemQueryOperatorEnum.IS_EMPTY
                || operator == ProjectWorkItemQueryOperatorEnum.IS_NOT_EMPTY) {
            return;
        }
        if (operator == ProjectWorkItemQueryOperatorEnum.IN
                || operator == ProjectWorkItemQueryOperatorEnum.NOT_IN
                || (field.isText() && (operator == ProjectWorkItemQueryOperatorEnum.CONTAINS
                || operator == ProjectWorkItemQueryOperatorEnum.NOT_CONTAINS))) {
            if (field.isText()) {
                condition.setParsedValue(parseQueryValue(field, condition.getValue()));
                return;
            }
            condition.setParsedValues(parseQueryValues(field, condition));
            return;
        }
        if (operator == ProjectWorkItemQueryOperatorEnum.CONTAINS
                || operator == ProjectWorkItemQueryOperatorEnum.NOT_CONTAINS) {
            condition.setParsedValues(parseQueryValues(field, condition));
            return;
        }
        if (operator == ProjectWorkItemQueryOperatorEnum.BETWEEN) {
            List<String> values = getRawQueryValues(condition);
            if (values.size() >= 2) {
                condition.setParsedValue(parseQueryValue(field, values.get(0)));
                condition.setParsedValueEnd(parseQueryValue(field, values.get(1)));
                return;
            }
            condition.setParsedValue(parseQueryValue(field, condition.getValue()));
            condition.setParsedValueEnd(parseQueryValue(field, condition.getValueEnd()));
            return;
        }
        condition.setParsedValue(parseQueryValue(field, condition.getValue()));
    }

    private List<Object> parseQueryValues(ProjectWorkItemQueryFieldEnum field,
                                          ProjectWorkItemPageReqVO.Condition condition) {
        List<String> rawValues = getRawQueryValues(condition);
        if (rawValues.isEmpty()) {
            throw exception(PROJECT_WORK_ITEM_QUERY_CONDITION_INVALID, condition.getField());
        }
        return rawValues.stream()
                .map(value -> parseQueryValue(field, value))
                .collect(Collectors.toList());
    }

    private List<String> getRawQueryValues(ProjectWorkItemPageReqVO.Condition condition) {
        if (condition.getValues() != null && !condition.getValues().isEmpty()) {
            return condition.getValues().stream()
                    .filter(StrUtil::isNotBlank)
                    .collect(Collectors.toList());
        }
        if (StrUtil.isBlank(condition.getValue())) {
            return Collections.emptyList();
        }
        if (condition.getValue().contains(",")) {
            return Arrays.stream(condition.getValue().split(","))
                    .map(String::trim)
                    .filter(StrUtil::isNotBlank)
                    .collect(Collectors.toList());
        }
        List<String> values = new ArrayList<>(1);
        values.add(condition.getValue());
        return values;
    }

    private Object parseQueryValue(ProjectWorkItemQueryFieldEnum field, String rawValue) {
        if (StrUtil.isBlank(rawValue)) {
            throw exception(PROJECT_WORK_ITEM_QUERY_CONDITION_INVALID, field.getField());
        }
        String value = parseCurrentUserToken(field, rawValue);
        try {
            if (field.getValueType() == String.class) {
                return value;
            }
            if (field.getValueType() == Long.class) {
                return Long.valueOf(value);
            }
            if (field.getValueType() == LocalDateTime.class) {
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

    private String parseCurrentUserToken(ProjectWorkItemQueryFieldEnum field, String rawValue) {
        if (!field.isUserField()) {
            return rawValue;
        }
        if (!StrUtil.equalsAnyIgnoreCase(rawValue, "me", "currentUser", "self")) {
            return rawValue;
        }
        Long loginUserId = getLoginUserId();
        if (loginUserId == null) {
            throw exception(PROJECT_WORK_ITEM_QUERY_CONDITION_INVALID, field.getField());
        }
        return loginUserId.toString();
    }

    private Object parseFieldValue(ProjectWorkItemUpdateFieldEnum field, JsonNode value) {
        if (value == null) {
            throw exception(PROJECT_WORK_ITEM_FIELD_VALUE_INVALID, field.getField());
        }
        if (value.isNull()) {
            if (!field.isNullable()) {
                throw exception(PROJECT_WORK_ITEM_FIELD_VALUE_INVALID, field.getField());
            }
            return null;
        }
        try {
            if (field.getValueType() == String.class) {
                return parseStringField(field, value);
            }
            if (field.getValueType() == Long.class) {
                return parseLongField(field, value);
            }
            if (field.getValueType() == LocalDateTime.class) {
                return parseLocalDateTimeField(field, value);
            }
            if (field.getValueType() == BigDecimal.class) {
                return parseBigDecimalField(field, value);
            }
        } catch (NumberFormatException | DateTimeParseException e) {
            throw exception(PROJECT_WORK_ITEM_FIELD_VALUE_INVALID, field.getField());
        }
        throw exception(PROJECT_WORK_ITEM_FIELD_VALUE_INVALID, field.getField());
    }

    private String parseStringField(ProjectWorkItemUpdateFieldEnum field, JsonNode value) {
        if (!value.isTextual()) {
            throw exception(PROJECT_WORK_ITEM_FIELD_VALUE_INVALID, field.getField());
        }
        return value.asText();
    }

    private Long parseLongField(ProjectWorkItemUpdateFieldEnum field, JsonNode value) {
        if (value.isIntegralNumber() && value.canConvertToLong()) {
            return value.asLong();
        }
        if (value.isTextual()) {
            return Long.valueOf(value.asText());
        }
        throw exception(PROJECT_WORK_ITEM_FIELD_VALUE_INVALID, field.getField());
    }

    private LocalDateTime parseLocalDateTimeField(ProjectWorkItemUpdateFieldEnum field, JsonNode value) {
        if (!value.isTextual() || StrUtil.isBlank(value.asText())) {
            throw exception(PROJECT_WORK_ITEM_FIELD_VALUE_INVALID, field.getField());
        }
        return LocalDateTimeUtils.parse(value.asText());
    }

    private BigDecimal parseBigDecimalField(ProjectWorkItemUpdateFieldEnum field, JsonNode value) {
        if (value.isNumber()) {
            return value.decimalValue();
        }
        if (value.isTextual()) {
            return new BigDecimal(value.asText());
        }
        throw exception(PROJECT_WORK_ITEM_FIELD_VALUE_INVALID, field.getField());
    }

    private Map<ProjectWorkItemUpdateFieldEnum, Object> parseUpdateFieldValues(
            List<ProjectWorkItemUpdateFieldReqVO.FieldValue> fields) {
        if (fields == null || fields.isEmpty()) {
            throw exception(PROJECT_WORK_ITEM_FIELD_VALUE_INVALID, "fields");
        }
        Map<ProjectWorkItemUpdateFieldEnum, Object> fieldValues = new EnumMap<>(ProjectWorkItemUpdateFieldEnum.class);
        for (ProjectWorkItemUpdateFieldReqVO.FieldValue item : fields) {
            if (item == null) {
                throw exception(PROJECT_WORK_ITEM_FIELD_VALUE_INVALID, "fields");
            }
            ProjectWorkItemUpdateFieldEnum field = ProjectWorkItemUpdateFieldEnum.of(item.getField());
            if (field == null) {
                throw exception(PROJECT_WORK_ITEM_FIELD_NOT_SUPPORTED, item.getField());
            }
            if (fieldValues.containsKey(field)) {
                throw exception(PROJECT_WORK_ITEM_FIELD_VALUE_INVALID, item.getField());
            }
            fieldValues.put(field, parseFieldValue(field, item.getValue()));
        }
        return fieldValues;
    }

    private void validateWorkItemFields(ProjectWorkItemDO oldWorkItem, ProjectWorkItemDO updatedWorkItem,
                                        Set<ProjectWorkItemUpdateFieldEnum> fields) {
        for (ProjectWorkItemUpdateFieldEnum field : fields) {
            validateWorkItemField(updatedWorkItem, field);
        }
        if (fields.contains(ProjectWorkItemUpdateFieldEnum.PROJECT_ID)) {
            validateProjectIdField(oldWorkItem, updatedWorkItem);
        }
        if (fields.contains(ProjectWorkItemUpdateFieldEnum.CATEGORY)
                && ObjUtil.notEqual(oldWorkItem.getCategory(), updatedWorkItem.getCategory())) {
            customFieldValueService.validateCategoryChange(oldWorkItem.getId(), updatedWorkItem.getCategory());
        }
        if (fields.contains(ProjectWorkItemUpdateFieldEnum.PROJECT_ID)
                || fields.contains(ProjectWorkItemUpdateFieldEnum.PARENT_WORK_ITEM_ID)) {
            validateParentWorkItemIdField(oldWorkItem, updatedWorkItem);
        }
        if (fields.contains(ProjectWorkItemUpdateFieldEnum.PLANNED_START_TIME)
                || fields.contains(ProjectWorkItemUpdateFieldEnum.PLANNED_END_TIME)) {
            validatePlannedTime(updatedWorkItem.getPlannedStartTime(), updatedWorkItem.getPlannedEndTime());
        }
    }

    private void validateWorkItemField(ProjectWorkItemDO workItem, ProjectWorkItemUpdateFieldEnum field) {
        switch (field) {
            case TITLE -> validateTitle(workItem.getTitle());
            case DESCRIPTION -> validateTextMaxLength(field, workItem.getDescription(), 5000);
            case CATEGORY -> validateInEnum(field, workItem.getCategory(), ProjectWorkItemCategoryEnum.ARRAYS);
            case PRIORITY -> validateInEnum(field, workItem.getPriority(), ProjectWorkItemPriorityEnum.ARRAYS);
            case STATUS -> validateInEnum(field, workItem.getStatus(), ProjectWorkItemStatusEnum.ARRAYS);
            case ASSIGNEE_USER_ID -> adminUserApi.validateUser(workItem.getAssigneeUserId());
            case ESTIMATED_WORKLOAD -> validateEstimatedWorkload(workItem.getEstimatedWorkload());
            case REMARK -> validateTextMaxLength(field, workItem.getRemark(), 512);
            case PROJECT_ID, PLANNED_START_TIME, PLANNED_END_TIME, PARENT_WORK_ITEM_ID -> {
                // Cross-field validation is handled after all submitted values are applied.
            }
            case SPRINT_ID -> {
                // Sprint aggregate validation is deferred until sprint service logic is introduced.
            }
            default -> throw exception(PROJECT_WORK_ITEM_FIELD_NOT_SUPPORTED, field.getField());
        }
    }

    private void validateTitle(String title) {
        if (StrUtil.isBlank(title) || title.length() > 128) {
            throw exception(PROJECT_WORK_ITEM_FIELD_VALUE_INVALID, ProjectWorkItemUpdateFieldEnum.TITLE.getField());
        }
    }

    private void validateTextMaxLength(ProjectWorkItemUpdateFieldEnum field, String value, int maxLength) {
        if (value != null && value.length() > maxLength) {
            throw exception(PROJECT_WORK_ITEM_FIELD_VALUE_INVALID, field.getField());
        }
    }

    private void validateInEnum(ProjectWorkItemUpdateFieldEnum field, String value, String[] arrays) {
        if (StrUtil.isBlank(value) || !Arrays.asList(arrays).contains(value)) {
            throw exception(PROJECT_WORK_ITEM_FIELD_VALUE_INVALID, field.getField());
        }
    }

    private void validateProjectIdField(ProjectWorkItemDO oldWorkItem, ProjectWorkItemDO updatedWorkItem) {
        projectService.validateProjectUsable(updatedWorkItem.getProjectId());
        if (ObjUtil.equal(oldWorkItem.getProjectId(), updatedWorkItem.getProjectId())) {
            return;
        }
        if (customFieldValueService.hasValue(oldWorkItem.getId())) {
            throw exception(WORK_ITEM_CUSTOM_FIELD_PROJECT_CHANGE_DENIED);
        }
        validateNoChildWorkItems(oldWorkItem.getId());
    }

    private void validateParentWorkItemIdField(ProjectWorkItemDO oldWorkItem, ProjectWorkItemDO updatedWorkItem) {
        if (!Objects.equals(oldWorkItem.getParentWorkItemId(), updatedWorkItem.getParentWorkItemId())) {
            validateNoChildWorkItems(oldWorkItem.getId());
        }
        validateParentWorkItem(oldWorkItem.getId(), updatedWorkItem.getProjectId(), updatedWorkItem.getParentWorkItemId());
    }

    private void validateEstimatedWorkload(BigDecimal estimatedWorkload) {
        if (estimatedWorkload != null && estimatedWorkload.compareTo(BigDecimal.ZERO) < 0) {
            throw exception(PROJECT_WORK_ITEM_FIELD_VALUE_INVALID,
                    ProjectWorkItemUpdateFieldEnum.ESTIMATED_WORKLOAD.getField());
        }
    }

    private void validatePlannedTime(ProjectWorkItemSaveReqVO reqVO) {
        validatePlannedTime(reqVO.getPlannedStartTime(), reqVO.getPlannedEndTime());
    }

    private void validatePlannedTime(LocalDateTime plannedStartTime, LocalDateTime plannedEndTime) {
        if (plannedStartTime == null || plannedEndTime == null) {
            return;
        }
        if (plannedEndTime.isBefore(plannedStartTime)) {
            throw exception(PROJECT_WORK_ITEM_PLANNED_TIME_INVALID);
        }
    }

    private void validateParentWorkItem(Long currentWorkItemId, ProjectWorkItemSaveReqVO reqVO) {
        validateParentWorkItem(currentWorkItemId, reqVO.getProjectId(), reqVO.getParentWorkItemId());
    }

    private void validateParentWorkItem(Long currentWorkItemId, Long projectId, Long parentWorkItemId) {
        if (parentWorkItemId == null) {
            return;
        }
        if (Objects.equals(currentWorkItemId, parentWorkItemId)) {
            throw exception(PROJECT_WORK_ITEM_PARENT_INVALID);
        }
        ProjectWorkItemDO parentWorkItem = validateWorkItemExists(parentWorkItemId);
        if (!Objects.equals(parentWorkItem.getProjectId(), projectId)) {
            throw exception(PROJECT_WORK_ITEM_PARENT_INVALID);
        }
        validateParentChain(currentWorkItemId, parentWorkItem);
    }

    private void validateNoChildWorkItems(Long id) {
        if (workItemMapper.selectCountByParentWorkItemId(id) > 0) {
            throw exception(PROJECT_WORK_ITEM_HAS_CHILDREN);
        }
    }

    private void validateParentChain(Long currentWorkItemId, ProjectWorkItemDO parentWorkItem) {
        if (currentWorkItemId == null) {
            return;
        }
        ProjectWorkItemDO cursor = parentWorkItem;
        while (cursor != null && cursor.getParentWorkItemId() != null) {
            if (Objects.equals(cursor.getParentWorkItemId(), currentWorkItemId)) {
                throw exception(PROJECT_WORK_ITEM_PARENT_INVALID);
            }
            cursor = workItemMapper.selectById(cursor.getParentWorkItemId());
        }
    }

    private String generateWorkItemNo(Long id) {
        return WORK_ITEM_NO_PREFIX + String.format("%06d", id);
    }

    private boolean isFieldValueNotChanged(ProjectWorkItemDO oldWorkItem,
                                           ProjectWorkItemUpdateFieldEnum field, Object value) {
        return switch (field) {
            case TITLE -> Objects.equals(oldWorkItem.getTitle(), value);
            case DESCRIPTION -> Objects.equals(oldWorkItem.getDescription(), value);
            case CATEGORY -> Objects.equals(oldWorkItem.getCategory(), value);
            case PRIORITY -> Objects.equals(oldWorkItem.getPriority(), value);
            case STATUS -> Objects.equals(oldWorkItem.getStatus(), value);
            case ASSIGNEE_USER_ID -> Objects.equals(oldWorkItem.getAssigneeUserId(), value);
            case PROJECT_ID -> Objects.equals(oldWorkItem.getProjectId(), value);
            case SPRINT_ID -> Objects.equals(oldWorkItem.getSprintId(), value);
            case PLANNED_START_TIME -> Objects.equals(oldWorkItem.getPlannedStartTime(), value);
            case PLANNED_END_TIME -> Objects.equals(oldWorkItem.getPlannedEndTime(), value);
            case ESTIMATED_WORKLOAD -> isBigDecimalValueNotChanged(oldWorkItem.getEstimatedWorkload(), (BigDecimal) value);
            case PARENT_WORK_ITEM_ID -> Objects.equals(oldWorkItem.getParentWorkItemId(), value);
            case REMARK -> Objects.equals(oldWorkItem.getRemark(), value);
        };
    }

    private boolean isBigDecimalValueNotChanged(BigDecimal oldValue, BigDecimal newValue) {
        if (oldValue == null || newValue == null) {
            return oldValue == newValue;
        }
        return oldValue.compareTo(newValue) == 0;
    }

    private ProjectWorkItemDO copyWorkItem(ProjectWorkItemDO source) {
        ProjectWorkItemDO target = new ProjectWorkItemDO();
        target.setId(source.getId());
        target.setWorkItemNo(source.getWorkItemNo());
        target.setTitle(source.getTitle());
        target.setDescription(source.getDescription());
        target.setCategory(source.getCategory());
        target.setPriority(source.getPriority());
        target.setStatus(source.getStatus());
        target.setAssigneeUserId(source.getAssigneeUserId());
        target.setProjectId(source.getProjectId());
        target.setSprintId(source.getSprintId());
        target.setPlannedStartTime(source.getPlannedStartTime());
        target.setPlannedEndTime(source.getPlannedEndTime());
        target.setEstimatedWorkload(source.getEstimatedWorkload());
        target.setParentWorkItemId(source.getParentWorkItemId());
        target.setRemark(source.getRemark());
        return target;
    }

    private void applyFieldValue(ProjectWorkItemDO workItem, ProjectWorkItemUpdateFieldEnum field, Object value) {
        switch (field) {
            case TITLE -> workItem.setTitle((String) value);
            case DESCRIPTION -> workItem.setDescription((String) value);
            case CATEGORY -> workItem.setCategory((String) value);
            case PRIORITY -> workItem.setPriority((String) value);
            case STATUS -> workItem.setStatus((String) value);
            case ASSIGNEE_USER_ID -> workItem.setAssigneeUserId((Long) value);
            case PROJECT_ID -> workItem.setProjectId((Long) value);
            case SPRINT_ID -> workItem.setSprintId((Long) value);
            case PLANNED_START_TIME -> workItem.setPlannedStartTime((LocalDateTime) value);
            case PLANNED_END_TIME -> workItem.setPlannedEndTime((LocalDateTime) value);
            case ESTIMATED_WORKLOAD -> workItem.setEstimatedWorkload((BigDecimal) value);
            case PARENT_WORK_ITEM_ID -> workItem.setParentWorkItemId((Long) value);
            case REMARK -> workItem.setRemark((String) value);
        }
    }

}
