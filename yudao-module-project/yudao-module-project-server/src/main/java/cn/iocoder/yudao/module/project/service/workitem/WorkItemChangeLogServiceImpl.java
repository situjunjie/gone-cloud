package cn.iocoder.yudao.module.project.service.workitem;

import cn.iocoder.yudao.framework.common.pojo.PageResult;
import cn.iocoder.yudao.framework.common.util.json.JsonUtils;
import cn.iocoder.yudao.module.project.controller.admin.workitem.vo.WorkItemChangeLogPageReqVO;
import cn.iocoder.yudao.module.project.controller.admin.workitem.vo.WorkItemChangeLogRespVO;
import cn.iocoder.yudao.module.project.controller.admin.workitem.vo.WorkItemCustomFieldValueRespVO;
import cn.iocoder.yudao.module.project.dal.dataobject.workitem.ProjectWorkItemDO;
import cn.iocoder.yudao.module.project.enums.ProjectWorkItemUpdateFieldEnum;
import cn.iocoder.yudao.module.project.enums.ProjectWorkItemCategoryEnum;
import cn.iocoder.yudao.module.project.enums.ProjectWorkItemPriorityEnum;
import cn.iocoder.yudao.module.project.enums.ProjectWorkItemStatusEnum;
import cn.iocoder.yudao.module.project.enums.WorkItemFieldTypeEnum;
import cn.iocoder.yudao.module.system.api.logger.OperateLogApi;
import cn.iocoder.yudao.module.system.api.logger.dto.OperateLogPageReqDTO;
import cn.iocoder.yudao.module.system.api.logger.dto.OperateLogRespDTO;
import cn.iocoder.yudao.module.system.api.user.AdminUserApi;
import cn.iocoder.yudao.module.system.api.user.dto.AdminUserRespDTO;
import com.mzt.logapi.context.LogRecordContext;
import jakarta.annotation.Resource;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.validation.annotation.Validated;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.TreeSet;
import java.util.stream.Collectors;

import static cn.iocoder.yudao.module.project.enums.LogRecordConstants.PROJECT_WORK_ITEM_TYPE;

@Service
@Validated
@Slf4j
public class WorkItemChangeLogServiceImpl implements WorkItemChangeLogService {

    private static final int MAX_ACTION_LENGTH = 1900;
    private static final int MAX_VALUE_LENGTH = 256;
    private static final int MAX_CHANGE_COUNT = 20;

    @Resource
    private WorkItemCustomFieldValueService customFieldValueService;
    @Resource
    private OperateLogApi operateLogApi;
    @Resource
    private AdminUserApi adminUserApi;

    @Override
    public void initContext() {
        LogRecordContext.putVariable("workItemChange", WorkItemChangeContext.empty());
    }

    @Override
    public Snapshot capture(ProjectWorkItemDO workItem, boolean includeCustomFields) {
        Map<Long, WorkItemCustomFieldValueRespVO> customFields = Map.of();
        if (includeCustomFields) {
            try {
                customFields = customFieldValueService.getValueMap(List.of(workItem.getId()))
                        .getOrDefault(workItem.getId(), List.of()).stream()
                        .collect(Collectors.toMap(WorkItemCustomFieldValueRespVO::getFieldId, item -> item,
                                (left, right) -> right, LinkedHashMap::new));
            } catch (RuntimeException ex) {
                log.warn("[capture][workItemId({}) 采集自定义字段变更快照失败，降级为仅记录固定字段]",
                        workItem.getId(), ex);
                return new Snapshot(workItem, Map.of(), false);
            }
        }
        return new Snapshot(workItem, customFields, true);
    }

    @Override
    public void record(Snapshot before, Snapshot after) {
        try {
            doRecord(before, after);
        } catch (RuntimeException ex) {
            LogRecordContext.putVariable("workItemChange", WorkItemChangeContext.empty());
            Long workItemId = after == null || after.workItem() == null ? null : after.workItem().getId();
            log.warn("[record][workItemId({}) 生成工作项修改动态失败，本次更新不记录动态]", workItemId, ex);
        }
    }

    private void doRecord(Snapshot before, Snapshot after) {
        Map<Long, String> userNames = resolveChangedUserNames(before, after);
        List<ChangeValue> changes = new ArrayList<>();
        for (ProjectWorkItemUpdateFieldEnum field : ProjectWorkItemUpdateFieldEnum.values()) {
            Object oldValue = getBuiltInValue(before.workItem(), field);
            Object newValue = getBuiltInValue(after.workItem(), field);
            if (!sameValue(oldValue, newValue)) {
                changes.add(new ChangeValue(field.getField(), getFieldName(field), oldValue, newValue,
                        formatValue(field, oldValue, userNames), formatValue(field, newValue, userNames)));
            }
        }

        if (before.customFieldsAvailable() && after.customFieldsAvailable()) {
            Set<Long> customIds = new TreeSet<>();
            customIds.addAll(before.customFields().keySet());
            customIds.addAll(after.customFields().keySet());
            for (Long fieldId : customIds) {
                WorkItemCustomFieldValueRespVO oldValue = before.customFields().get(fieldId);
                WorkItemCustomFieldValueRespVO newValue = after.customFields().get(fieldId);
                Object oldRaw = customRawValue(oldValue);
                Object newRaw = customRawValue(newValue);
                if (!sameValue(oldRaw, newRaw)) {
                    String fieldName = newValue != null ? newValue.getName() : oldValue.getName();
                    changes.add(new ChangeValue("custom:" + fieldId, fieldName, oldRaw, newRaw,
                            customDisplayValue(oldValue, userNames), customDisplayValue(newValue, userNames)));
                }
            }
        }

        String title = after.workItem().getTitle();
        if (changes.isEmpty()) {
            LogRecordContext.putVariable("workItemChange", WorkItemChangeContext.empty());
            return;
        }
        String summary = buildSummary(title, changes);
        String extra = buildExtra(changes);
        LogRecordContext.putVariable("workItemChange", new WorkItemChangeContext(true, summary, extra));
    }

    @Override
    public PageResult<WorkItemChangeLogRespVO> getPage(WorkItemChangeLogPageReqVO reqVO) {
        return getPage(reqVO, false);
    }

    @Override
    public PageResult<WorkItemChangeLogRespVO> getTimelinePage(WorkItemChangeLogPageReqVO reqVO) {
        return getPage(reqVO, true);
    }

    private PageResult<WorkItemChangeLogRespVO> getPage(WorkItemChangeLogPageReqVO reqVO,
                                                         boolean timelineOrder) {
        OperateLogPageReqDTO pageReqDTO = new OperateLogPageReqDTO();
        pageReqDTO.setPageNo(reqVO.getPageNo());
        pageReqDTO.setPageSize(reqVO.getPageSize());
        pageReqDTO.setType(PROJECT_WORK_ITEM_TYPE);
        pageReqDTO.setBizId(reqVO.getWorkItemId());
        PageResult<OperateLogRespDTO> page = (timelineOrder
                ? operateLogApi.getOperateLogTimelinePage(pageReqDTO)
                : operateLogApi.getOperateLogPage(pageReqDTO)).getCheckedData();
        List<WorkItemChangeLogRespVO> list = page.getList().stream().map(this::toResp).toList();
        return new PageResult<>(list, page.getTotal());
    }

    private WorkItemChangeLogRespVO toResp(OperateLogRespDTO log) {
        WorkItemChangeLogRespVO resp = new WorkItemChangeLogRespVO();
        resp.setId(log.getId());
        resp.setUserId(log.getUserId());
        resp.setUserName(log.getUserName());
        resp.setCreateTime(log.getCreateTime());
        resp.setSubType(log.getSubType());
        resp.setAction(log.getAction());
        ChangeExtra extra = JsonUtils.parseObjectQuietly(log.getExtra(), ChangeExtra.class);
        resp.setChanges(extra == null || !Objects.equals(extra.schemaVersion, 1) || extra.changes == null
                ? List.of() : extra.changes);
        return resp;
    }

    private String buildSummary(String title, List<ChangeValue> changes) {
        StringBuilder summary = new StringBuilder("更新了工作项【")
                .append(truncate(title, 128)).append("】：");
        for (int i = 0; i < changes.size(); i++) {
            if (i > 0) {
                summary.append("；");
            }
            ChangeValue change = changes.get(i);
            if (isValueHidden(change)) {
                summary.append("【").append(change.name()).append("】发生了修改");
                continue;
            }
            summary.append("【").append(change.name()).append("】从【")
                    .append(change.oldDisplay()).append("】修改为【")
                    .append(change.newDisplay()).append("】");
        }
        return truncate(summary.toString(), MAX_ACTION_LENGTH);
    }

    private String buildExtra(List<ChangeValue> source) {
        List<WorkItemChangeLogRespVO.Change> changes = source.stream().limit(MAX_CHANGE_COUNT)
                .map(this::toChangeResp).toList();
        Map<String, Object> payload = new LinkedHashMap<>();
        payload.put("schemaVersion", 1);
        payload.put("truncated", source.size() > MAX_CHANGE_COUNT);
        payload.put("changes", changes);
        String json = JsonUtils.toJsonString(payload);
        if (json.length() <= MAX_ACTION_LENGTH) {
            return json;
        }
        payload.put("truncated", true);
        payload.put("changes", changes.stream().limit(5).toList());
        json = JsonUtils.toJsonString(payload);
        if (json.length() <= MAX_ACTION_LENGTH) {
            return json;
        }
        payload.put("changes", List.of());
        return JsonUtils.toJsonString(payload);
    }

    private WorkItemChangeLogRespVO.Change toChangeResp(ChangeValue change) {
        WorkItemChangeLogRespVO.Change resp = new WorkItemChangeLogRespVO.Change();
        resp.setField(change.field());
        resp.setName(change.name());
        if (isValueHidden(change)) {
            return resp;
        }
        resp.setOldValue(truncate(stringValue(change.oldValue()), MAX_VALUE_LENGTH));
        resp.setNewValue(truncate(stringValue(change.newValue()), MAX_VALUE_LENGTH));
        resp.setOldDisplay(truncate(change.oldDisplay(), MAX_VALUE_LENGTH));
        resp.setNewDisplay(truncate(change.newDisplay(), MAX_VALUE_LENGTH));
        return resp;
    }

    private boolean isValueHidden(ChangeValue change) {
        return ProjectWorkItemUpdateFieldEnum.DESCRIPTION.getField().equals(change.field());
    }

    private Object getBuiltInValue(ProjectWorkItemDO workItem, ProjectWorkItemUpdateFieldEnum field) {
        return switch (field) {
            case TITLE -> workItem.getTitle();
            case DESCRIPTION -> workItem.getDescription();
            case CATEGORY -> workItem.getCategory();
            case PRIORITY -> workItem.getPriority();
            case STATUS -> workItem.getStatus();
            case ASSIGNEE_USER_ID -> workItem.getAssigneeUserId();
            case PROJECT_ID -> workItem.getProjectId();
            case SPRINT_ID -> workItem.getSprintId();
            case PLANNED_START_TIME -> workItem.getPlannedStartTime();
            case PLANNED_END_TIME -> workItem.getPlannedEndTime();
            case PARENT_WORK_ITEM_ID -> workItem.getParentWorkItemId();
            case REMARK -> workItem.getRemark();
        };
    }

    private String getFieldName(ProjectWorkItemUpdateFieldEnum field) {
        return switch (field) {
            case TITLE -> "标题";
            case DESCRIPTION -> "描述";
            case CATEGORY -> "分类";
            case PRIORITY -> "优先级";
            case STATUS -> "状态";
            case ASSIGNEE_USER_ID -> "负责人";
            case PROJECT_ID -> "项目";
            case SPRINT_ID -> "迭代";
            case PLANNED_START_TIME -> "计划开始时间";
            case PLANNED_END_TIME -> "计划完成时间";
            case PARENT_WORK_ITEM_ID -> "父工作项";
            case REMARK -> "备注";
        };
    }

    private String formatValue(ProjectWorkItemUpdateFieldEnum field, Object value, Map<Long, String> userNames) {
        if (value == null) {
            return "空";
        }
        if (field == ProjectWorkItemUpdateFieldEnum.CATEGORY) {
            return enumName(ProjectWorkItemCategoryEnum.values(), value);
        }
        if (field == ProjectWorkItemUpdateFieldEnum.PRIORITY) {
            return enumName(ProjectWorkItemPriorityEnum.values(), value);
        }
        if (field == ProjectWorkItemUpdateFieldEnum.STATUS) {
            return enumName(ProjectWorkItemStatusEnum.values(), value);
        }
        if (field == ProjectWorkItemUpdateFieldEnum.ASSIGNEE_USER_ID && value instanceof Long userId) {
            return userNames.getOrDefault(userId, stringValue(userId));
        }
        return truncate(stringValue(value), MAX_VALUE_LENGTH);
    }

    private Map<Long, String> resolveChangedUserNames(Snapshot before, Snapshot after) {
        Set<Long> userIds = new HashSet<>();
        Long oldAssignee = before.workItem().getAssigneeUserId();
        Long newAssignee = after.workItem().getAssigneeUserId();
        if (!Objects.equals(oldAssignee, newAssignee)) {
            addIfNotNull(userIds, oldAssignee);
            addIfNotNull(userIds, newAssignee);
        }
        if (before.customFieldsAvailable() && after.customFieldsAvailable()) {
            Set<Long> customIds = new HashSet<>(before.customFields().keySet());
            customIds.addAll(after.customFields().keySet());
            for (Long fieldId : customIds) {
                WorkItemCustomFieldValueRespVO oldValue = before.customFields().get(fieldId);
                WorkItemCustomFieldValueRespVO newValue = after.customFields().get(fieldId);
                WorkItemCustomFieldValueRespVO metadata = newValue != null ? newValue : oldValue;
                if (metadata == null || !WorkItemFieldTypeEnum.USER.getValue().equals(metadata.getFieldType())) {
                    continue;
                }
                Object oldRaw = customRawValue(oldValue);
                Object newRaw = customRawValue(newValue);
                if (!sameValue(oldRaw, newRaw)) {
                    addIfLong(userIds, oldRaw);
                    addIfLong(userIds, newRaw);
                }
            }
        }
        if (userIds.isEmpty()) {
            return Map.of();
        }
        try {
            return adminUserApi.getUserMap(userIds).values().stream()
                    .collect(Collectors.toMap(AdminUserRespDTO::getId, AdminUserRespDTO::getNickname));
        } catch (RuntimeException ex) {
            log.warn("[resolveChangedUserNames][workItemId({}) 解析用户昵称失败，降级显示用户编号]",
                    after.workItem().getId(), ex);
            return Map.of();
        }
    }

    private void addIfNotNull(Set<Long> values, Long value) {
        if (value != null) {
            values.add(value);
        }
    }

    private void addIfLong(Set<Long> values, Object value) {
        if (value instanceof Long longValue) {
            values.add(longValue);
        }
    }

    private String enumName(Object[] values, Object value) {
        for (Object item : values) {
            if (item instanceof ProjectWorkItemCategoryEnum category && Objects.equals(category.getValue(), value)) {
                return category.getName();
            }
            if (item instanceof ProjectWorkItemPriorityEnum priority && Objects.equals(priority.getValue(), value)) {
                return priority.getName();
            }
            if (item instanceof ProjectWorkItemStatusEnum status && Objects.equals(status.getValue(), value)) {
                return status.getName();
            }
        }
        return stringValue(value);
    }

    private Object customRawValue(WorkItemCustomFieldValueRespVO value) {
        if (value == null) {
            return null;
        }
        if (value.getSelectedOptions() != null && !value.getSelectedOptions().isEmpty()) {
            return value.getSelectedOptions().stream().map(option -> option.getId()).sorted().toList();
        }
        return value.getValue();
    }

    private String customDisplayValue(WorkItemCustomFieldValueRespVO value, Map<Long, String> userNames) {
        if (value == null) {
            return "空";
        }
        if (value.getSelectedOptions() != null && !value.getSelectedOptions().isEmpty()) {
            return value.getSelectedOptions().stream().map(option -> option.getName())
                    .filter(Objects::nonNull).collect(Collectors.joining("、"));
        }
        if (WorkItemFieldTypeEnum.USER.getValue().equals(value.getFieldType())
                && value.getValue() instanceof Long userId) {
            return userNames.getOrDefault(userId, stringValue(userId));
        }
        return value.getValue() == null ? "空" : truncate(stringValue(value.getValue()), MAX_VALUE_LENGTH);
    }

    private boolean sameValue(Object left, Object right) {
        if (left instanceof BigDecimal leftDecimal && right instanceof BigDecimal rightDecimal) {
            return leftDecimal.compareTo(rightDecimal) == 0;
        }
        return Objects.equals(left, right);
    }

    private String stringValue(Object value) {
        return value == null ? "空" : String.valueOf(value);
    }

    private String truncate(String value, int length) {
        if (value == null || value.length() <= length) {
            return value == null ? "" : value;
        }
        return value.substring(0, Math.max(0, length - 1)) + "…";
    }

    private record ChangeValue(String field, String name, Object oldValue, Object newValue,
                               String oldDisplay, String newDisplay) {
    }

    public static class WorkItemChangeContext {
        private boolean changed;
        private String summary;
        private String extra;

        public WorkItemChangeContext(boolean changed, String summary, String extra) {
            this.changed = changed;
            this.summary = summary;
            this.extra = extra;
        }

        public static WorkItemChangeContext empty() {
            return new WorkItemChangeContext(false, "", "");
        }

        public boolean isChanged() {
            return changed;
        }

        public String getSummary() {
            return summary;
        }

        public String getExtra() {
            return extra;
        }
    }

    public static class ChangeExtra {
        public Integer schemaVersion;
        public List<WorkItemChangeLogRespVO.Change> changes;
    }

}
