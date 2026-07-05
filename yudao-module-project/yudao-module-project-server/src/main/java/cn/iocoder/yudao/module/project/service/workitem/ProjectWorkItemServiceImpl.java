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
import cn.iocoder.yudao.module.project.enums.ProjectWorkItemCategoryEnum;
import cn.iocoder.yudao.module.project.enums.ProjectWorkItemPriorityEnum;
import cn.iocoder.yudao.module.project.enums.ProjectWorkItemQueryFieldEnum;
import cn.iocoder.yudao.module.project.enums.ProjectWorkItemQueryOperatorEnum;
import cn.iocoder.yudao.module.project.enums.ProjectWorkItemStatusEnum;
import cn.iocoder.yudao.module.project.enums.ProjectWorkItemUpdateFieldEnum;
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
import java.util.List;
import java.util.Objects;
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
        return workItem.getId();
    }

    @Override
    public void updateWorkItem(ProjectWorkItemSaveReqVO updateReqVO) {
        ProjectWorkItemDO oldWorkItem = validateWorkItemExists(updateReqVO.getId());
        validateWorkItemForCreateOrUpdate(oldWorkItem, updateReqVO);

        ProjectWorkItemDO updateObj = ProjectWorkItemConvert.INSTANCE.convert(updateReqVO);
        updateObj.setWorkItemNo(null);
        updateObj.setStatus(null);
        workItemMapper.updateById(updateObj);
    }

    @Override
    public void updateWorkItemField(ProjectWorkItemUpdateFieldReqVO updateReqVO) {
        ProjectWorkItemDO oldWorkItem = validateWorkItemExists(updateReqVO.getId());
        ProjectWorkItemUpdateFieldEnum field = ProjectWorkItemUpdateFieldEnum.of(updateReqVO.getField());
        if (field == null) {
            throw exception(PROJECT_WORK_ITEM_FIELD_NOT_SUPPORTED, updateReqVO.getField());
        }
        Object value = parseFieldValue(field, updateReqVO.getValue());
        validateWorkItemField(oldWorkItem, field, value);
        if (isFieldValueNotChanged(oldWorkItem, field, value)) {
            return;
        }
        workItemMapper.updateFieldById(updateReqVO.getId(), field.getColumn(), value, LocalDateTime.now());
    }

    @Override
    public void deleteWorkItem(Long id) {
        validateWorkItemExists(id);
        validateNoChildWorkItems(id);
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
        for (ProjectWorkItemPageReqVO.Condition condition : pageReqVO.getConditions()) {
            ProjectWorkItemQueryFieldEnum field = ProjectWorkItemQueryFieldEnum.of(condition.getField());
            if (field == null) {
                throw exception(PROJECT_WORK_ITEM_QUERY_FIELD_NOT_SUPPORTED, condition.getField());
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

    private void normalizeQueryConditionValue(ProjectWorkItemPageReqVO.Condition condition,
                                              ProjectWorkItemQueryFieldEnum field,
                                              ProjectWorkItemQueryOperatorEnum operator) {
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

    private void validateWorkItemField(ProjectWorkItemDO oldWorkItem, ProjectWorkItemUpdateFieldEnum field, Object value) {
        switch (field) {
            case TITLE -> validateTitle((String) value);
            case DESCRIPTION -> validateTextMaxLength(field, (String) value, 5000);
            case CATEGORY -> validateInEnum(field, (String) value, ProjectWorkItemCategoryEnum.ARRAYS);
            case PRIORITY -> validateInEnum(field, (String) value, ProjectWorkItemPriorityEnum.ARRAYS);
            case STATUS -> validateInEnum(field, (String) value, ProjectWorkItemStatusEnum.ARRAYS);
            case ASSIGNEE_USER_ID -> adminUserApi.validateUser((Long) value);
            case PROJECT_ID -> validateProjectIdField(oldWorkItem, (Long) value);
            case PLANNED_START_TIME, PLANNED_END_TIME -> validatePlannedTime(oldWorkItem, field, value);
            case ESTIMATED_WORKLOAD -> validateEstimatedWorkload((BigDecimal) value);
            case PARENT_WORK_ITEM_ID -> validateParentWorkItemIdField(oldWorkItem, (Long) value);
            case REMARK -> validateTextMaxLength(field, (String) value, 512);
            case ITERATION_ID -> {
                // No iteration aggregate exists in the current project module; keep parity with full update.
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

    private void validateProjectIdField(ProjectWorkItemDO oldWorkItem, Long projectId) {
        projectService.validateProjectUsable(projectId);
        if (ObjUtil.equal(oldWorkItem.getProjectId(), projectId)) {
            return;
        }
        validateNoChildWorkItems(oldWorkItem.getId());
        validateParentWorkItem(oldWorkItem.getId(), projectId, oldWorkItem.getParentWorkItemId());
    }

    private void validateParentWorkItemIdField(ProjectWorkItemDO oldWorkItem, Long parentWorkItemId) {
        if (!Objects.equals(oldWorkItem.getParentWorkItemId(), parentWorkItemId)) {
            validateNoChildWorkItems(oldWorkItem.getId());
        }
        validateParentWorkItem(oldWorkItem.getId(), oldWorkItem.getProjectId(), parentWorkItemId);
    }

    private void validatePlannedTime(ProjectWorkItemDO oldWorkItem, ProjectWorkItemUpdateFieldEnum field, Object value) {
        LocalDateTime plannedStartTime = field == ProjectWorkItemUpdateFieldEnum.PLANNED_START_TIME
                ? (LocalDateTime) value : oldWorkItem.getPlannedStartTime();
        LocalDateTime plannedEndTime = field == ProjectWorkItemUpdateFieldEnum.PLANNED_END_TIME
                ? (LocalDateTime) value : oldWorkItem.getPlannedEndTime();
        validatePlannedTime(plannedStartTime, plannedEndTime);
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
            case ITERATION_ID -> Objects.equals(oldWorkItem.getIterationId(), value);
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

}
