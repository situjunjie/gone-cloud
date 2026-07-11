package cn.iocoder.yudao.module.project.service.workitem;

import cn.hutool.core.util.StrUtil;
import cn.iocoder.yudao.framework.common.util.json.JsonUtils;
import cn.iocoder.yudao.module.project.controller.admin.workitem.vo.WorkItemCustomFieldValueReqVO;
import cn.iocoder.yudao.module.project.controller.admin.workitem.vo.WorkItemCustomFieldValueRespVO;
import cn.iocoder.yudao.module.project.controller.admin.workitem.vo.WorkItemFieldRespVO;
import cn.iocoder.yudao.module.project.dal.dataobject.workitem.WorkItemFieldDO;
import cn.iocoder.yudao.module.project.dal.dataobject.workitem.WorkItemFieldOptionDO;
import cn.iocoder.yudao.module.project.dal.dataobject.workitem.WorkItemFieldValueDO;
import cn.iocoder.yudao.module.project.dal.dataobject.workitem.WorkItemFieldValueOptionDO;
import cn.iocoder.yudao.module.project.dal.mysql.workitem.WorkItemFieldMapper;
import cn.iocoder.yudao.module.project.dal.mysql.workitem.WorkItemFieldOptionMapper;
import cn.iocoder.yudao.module.project.dal.mysql.workitem.WorkItemFieldValueMapper;
import cn.iocoder.yudao.module.project.dal.mysql.workitem.WorkItemFieldValueOptionMapper;
import cn.iocoder.yudao.module.project.enums.WorkItemFieldTypeEnum;
import cn.iocoder.yudao.module.system.api.user.AdminUserApi;
import com.fasterxml.jackson.databind.JsonNode;
import jakarta.annotation.Resource;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.time.format.DateTimeParseException;
import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.function.Function;
import java.util.stream.Collectors;

import static cn.iocoder.yudao.framework.common.exception.util.ServiceExceptionUtil.exception;
import static cn.iocoder.yudao.module.project.enums.ErrorCodeConstants.*;

@Service
public class WorkItemCustomFieldValueServiceImpl implements WorkItemCustomFieldValueService {

    @Resource
    private WorkItemFieldMapper fieldMapper;
    @Resource
    private WorkItemFieldOptionMapper optionMapper;
    @Resource
    private WorkItemFieldValueMapper valueMapper;
    @Resource
    private WorkItemFieldValueOptionMapper valueOptionMapper;
    @Resource
    private AdminUserApi adminUserApi;

    @Override
    public void saveSnapshot(Long workItemId, Long projectId, String category,
                             List<WorkItemCustomFieldValueReqVO> values, boolean validateRequired) {
        List<WorkItemCustomFieldValueReqVO> submitted = values == null ? List.of() : values;
        Map<Long, WorkItemCustomFieldValueReqVO> requestMap = new LinkedHashMap<>();
        for (WorkItemCustomFieldValueReqVO item : submitted) {
            if (item == null || item.getFieldId() == null || requestMap.put(item.getFieldId(), item) != null) {
                throw exception(WORK_ITEM_CUSTOM_FIELD_VALUE_INVALID, "customFieldValues");
            }
        }
        Map<Long, WorkItemFieldDO> fieldMap = fieldMapper.selectListByIds(requestMap.keySet()).stream()
                .collect(Collectors.toMap(WorkItemFieldDO::getId, Function.identity()));
        List<NormalizedValue> normalized = new ArrayList<>();
        for (WorkItemCustomFieldValueReqVO item : submitted) {
            WorkItemFieldDO field = validateWritableField(fieldMap.get(item.getFieldId()), projectId, category);
            NormalizedValue value = normalize(field, item.getValue(), true);
            if (value != null) {
                normalized.add(value);
            }
        }
        if (validateRequired) {
            validateRequiredFields(projectId, category, normalized);
        }
        replaceValues(workItemId, normalized);
    }

    @Override
    public void updateValue(Long workItemId, Long projectId, String category, Long fieldId, JsonNode value) {
        WorkItemFieldDO field = validateWritableField(fieldMapper.selectById(fieldId), projectId, category);
        Map<Long, WorkItemCustomFieldValueReqVO> existing = getValueRequests(workItemId);
        WorkItemCustomFieldValueReqVO req = new WorkItemCustomFieldValueReqVO();
        req.setFieldId(fieldId);
        req.setValue(value);
        existing.put(fieldId, req);
        saveSnapshot(workItemId, projectId, category, new ArrayList<>(existing.values()), true);
    }

    @Override
    public void deleteByWorkItemId(Long workItemId) {
        valueOptionMapper.physicalDeleteByWorkItemId(workItemId);
        valueMapper.physicalDeleteByWorkItemId(workItemId);
    }

    @Override
    public boolean hasValue(Long workItemId) {
        return valueMapper.selectCountByWorkItemId(workItemId) > 0;
    }

    @Override
    public void validateCategoryChange(Long workItemId, String newCategory) {
        List<WorkItemFieldValueDO> values = valueMapper.selectListByWorkItemIds(List.of(workItemId));
        if (values.isEmpty()) {
            return;
        }
        Map<Long, WorkItemFieldDO> fields = fieldMapper.selectListByIds(
                values.stream().map(WorkItemFieldValueDO::getFieldId).toList()).stream()
                .collect(Collectors.toMap(WorkItemFieldDO::getId, Function.identity()));
        for (WorkItemFieldValueDO value : values) {
            WorkItemFieldDO field = fields.get(value.getFieldId());
            if (field != null && StrUtil.isNotBlank(field.getApplicableCategory())
                    && !Objects.equals(field.getApplicableCategory(), newCategory)) {
                throw exception(WORK_ITEM_CUSTOM_FIELD_CATEGORY_CHANGE_DENIED, field.getName());
            }
        }
    }

    @Override
    public Map<Long, List<WorkItemCustomFieldValueRespVO>> getValueMap(Collection<Long> workItemIds) {
        List<WorkItemFieldValueDO> values = valueMapper.selectListByWorkItemIds(workItemIds);
        if (values.isEmpty()) {
            return Map.of();
        }
        Map<Long, WorkItemFieldDO> fields = fieldMapper.selectListByIds(
                values.stream().map(WorkItemFieldValueDO::getFieldId).collect(Collectors.toSet())).stream()
                .collect(Collectors.toMap(WorkItemFieldDO::getId, Function.identity()));
        List<WorkItemFieldValueOptionDO> links = valueOptionMapper.selectListByWorkItemIds(workItemIds);
        Map<Long, List<WorkItemFieldValueOptionDO>> linksByValue = links.stream()
                .collect(Collectors.groupingBy(WorkItemFieldValueOptionDO::getFieldValueId));
        Map<Long, WorkItemFieldOptionDO> options = optionMapper.selectListByIds(
                links.stream().map(WorkItemFieldValueOptionDO::getOptionId).collect(Collectors.toSet())).stream()
                .collect(Collectors.toMap(WorkItemFieldOptionDO::getId, Function.identity()));
        Map<Long, List<WorkItemCustomFieldValueRespVO>> result = new HashMap<>();
        for (WorkItemFieldValueDO value : values) {
            WorkItemFieldDO field = fields.get(value.getFieldId());
            if (field == null) {
                continue;
            }
            result.computeIfAbsent(value.getWorkItemId(), key -> new ArrayList<>())
                    .add(toResp(field, value, linksByValue.getOrDefault(value.getId(), List.of()), options));
        }
        result.values().forEach(list -> list.sort((a, b) -> a.getFieldId().compareTo(b.getFieldId())));
        return result;
    }

    private WorkItemFieldDO validateWritableField(WorkItemFieldDO field, Long projectId, String category) {
        if (field == null) {
            throw exception(WORK_ITEM_FIELD_NOT_EXISTS);
        }
        if (!Objects.equals(field.getProjectId(), projectId) || !Boolean.TRUE.equals(field.getEnabled())
                || (StrUtil.isNotBlank(field.getApplicableCategory())
                && !Objects.equals(field.getApplicableCategory(), category))) {
            throw exception(WORK_ITEM_CUSTOM_FIELD_VALUE_INVALID, field.getName());
        }
        return field;
    }

    private void validateRequiredFields(Long projectId, String category, List<NormalizedValue> values) {
        Set<Long> present = values.stream().map(item -> item.field().getId()).collect(Collectors.toSet());
        for (WorkItemFieldDO field : fieldMapper.selectListByProjectId(projectId)) {
            if (Boolean.TRUE.equals(field.getEnabled()) && Boolean.TRUE.equals(field.getRequired())
                    && (StrUtil.isBlank(field.getApplicableCategory())
                    || Objects.equals(field.getApplicableCategory(), category)) && !present.contains(field.getId())) {
                throw exception(WORK_ITEM_CUSTOM_FIELD_REQUIRED, field.getName());
            }
        }
    }

    private NormalizedValue normalize(WorkItemFieldDO field, JsonNode node, boolean requireEnabledOption) {
        if (node == null || node.isNull() || (node.isTextual() && StrUtil.isBlank(node.asText()))) {
            return null;
        }
        WorkItemFieldTypeEnum type = WorkItemFieldTypeEnum.of(field.getFieldType());
        try {
            return switch (type) {
                case TEXT -> normalized(field, node.isTextual() ? node.asText() : invalid(field), List.of());
                case NUMBER -> normalized(field, parseNumber(node, field), List.of());
                case DATE_TIME -> normalized(field, parseDateTime(node, field), List.of());
                case BOOLEAN -> normalized(field, parseBoolean(node, field), List.of());
                case USER -> normalizedUser(field, node);
                case SINGLE_SELECT -> normalizedOptions(field, parseOptionIds(node, false, field), requireEnabledOption);
                case MULTI_SELECT -> normalizedOptions(field, parseOptionIds(node, true, field), requireEnabledOption);
            };
        } catch (NumberFormatException | DateTimeParseException e) {
            throw exception(WORK_ITEM_CUSTOM_FIELD_VALUE_INVALID, field.getName());
        }
    }

    private NormalizedValue normalized(WorkItemFieldDO field, Object scalar, List<Long> optionIds) {
        return new NormalizedValue(field, scalar, optionIds);
    }

    private NormalizedValue normalizedUser(WorkItemFieldDO field, JsonNode node) {
        Long userId = node.isIntegralNumber() ? node.longValue() : Long.valueOf(node.asText());
        adminUserApi.validateUser(userId);
        return normalized(field, userId, List.of());
    }

    private NormalizedValue normalizedOptions(WorkItemFieldDO field, List<Long> optionIds, boolean requireEnabled) {
        if (optionIds.isEmpty()) {
            return null;
        }
        List<WorkItemFieldOptionDO> options = optionMapper.selectListByIds(optionIds);
        Set<Long> found = options.stream().filter(option -> Objects.equals(option.getFieldId(), field.getId())
                        && (!requireEnabled || Boolean.TRUE.equals(option.getEnabled())))
                .map(WorkItemFieldOptionDO::getId).collect(Collectors.toSet());
        if (found.size() != new HashSet<>(optionIds).size()) {
            throw exception(WORK_ITEM_CUSTOM_FIELD_VALUE_INVALID, field.getName());
        }
        return normalized(field, null, optionIds.stream().distinct().toList());
    }

    private BigDecimal parseNumber(JsonNode node, WorkItemFieldDO field) {
        if (node.isNumber() || node.isTextual()) {
            return new BigDecimal(node.asText());
        }
        throw exception(WORK_ITEM_CUSTOM_FIELD_VALUE_INVALID, field.getName());
    }

    private LocalDateTime parseDateTime(JsonNode node, WorkItemFieldDO field) {
        if (node.isIntegralNumber()) {
            return LocalDateTime.ofInstant(Instant.ofEpochMilli(node.longValue()), ZoneId.systemDefault());
        }
        if (node.isTextual()) {
            return LocalDateTime.parse(node.asText());
        }
        throw exception(WORK_ITEM_CUSTOM_FIELD_VALUE_INVALID, field.getName());
    }

    private Boolean parseBoolean(JsonNode node, WorkItemFieldDO field) {
        if (node.isBoolean()) {
            return node.booleanValue();
        }
        if (node.isTextual() && ("true".equalsIgnoreCase(node.asText()) || "false".equalsIgnoreCase(node.asText()))) {
            return Boolean.valueOf(node.asText());
        }
        throw exception(WORK_ITEM_CUSTOM_FIELD_VALUE_INVALID, field.getName());
    }

    private List<Long> parseOptionIds(JsonNode node, boolean multiple, WorkItemFieldDO field) {
        if (multiple) {
            if (!node.isArray()) {
                throw exception(WORK_ITEM_CUSTOM_FIELD_VALUE_INVALID, field.getName());
            }
            List<Long> ids = new ArrayList<>();
            node.forEach(item -> ids.add(item.isIntegralNumber() ? item.longValue() : Long.valueOf(item.asText())));
            return ids;
        }
        if (node.isArray()) {
            if (node.size() != 1) {
                throw exception(WORK_ITEM_CUSTOM_FIELD_VALUE_INVALID, field.getName());
            }
            node = node.get(0);
        }
        return List.of(node.isIntegralNumber() ? node.longValue() : Long.valueOf(node.asText()));
    }

    private String invalid(WorkItemFieldDO field) {
        throw exception(WORK_ITEM_CUSTOM_FIELD_VALUE_INVALID, field.getName());
    }

    private void replaceValues(Long workItemId, List<NormalizedValue> values) {
        deleteByWorkItemId(workItemId);
        for (NormalizedValue normalized : values) {
            WorkItemFieldValueDO value = new WorkItemFieldValueDO();
            value.setWorkItemId(workItemId);
            value.setFieldId(normalized.field().getId());
            switch (WorkItemFieldTypeEnum.of(normalized.field().getFieldType())) {
                case TEXT -> value.setTextValue((String) normalized.scalar());
                case NUMBER -> value.setNumberValue((BigDecimal) normalized.scalar());
                case DATE_TIME -> value.setDatetimeValue((LocalDateTime) normalized.scalar());
                case BOOLEAN -> value.setBooleanValue((Boolean) normalized.scalar());
                case USER -> value.setUserValue((Long) normalized.scalar());
                case SINGLE_SELECT, MULTI_SELECT -> { }
            }
            valueMapper.insert(value);
            for (Long optionId : normalized.optionIds()) {
                WorkItemFieldValueOptionDO link = new WorkItemFieldValueOptionDO();
                link.setFieldValueId(value.getId());
                link.setWorkItemId(workItemId);
                link.setFieldId(value.getFieldId());
                link.setOptionId(optionId);
                valueOptionMapper.insert(link);
            }
        }
    }

    private Map<Long, WorkItemCustomFieldValueReqVO> getValueRequests(Long workItemId) {
        Map<Long, WorkItemCustomFieldValueReqVO> result = new LinkedHashMap<>();
        for (WorkItemCustomFieldValueRespVO resp : getValueMap(List.of(workItemId)).getOrDefault(workItemId, List.of())) {
            WorkItemCustomFieldValueReqVO req = new WorkItemCustomFieldValueReqVO();
            req.setFieldId(resp.getFieldId());
            req.setValue(JsonUtils.getObjectMapper().valueToTree(resp.getValue()));
            result.put(req.getFieldId(), req);
        }
        return result;
    }

    private WorkItemCustomFieldValueRespVO toResp(WorkItemFieldDO field, WorkItemFieldValueDO value,
                                                   List<WorkItemFieldValueOptionDO> links,
                                                   Map<Long, WorkItemFieldOptionDO> options) {
        WorkItemCustomFieldValueRespVO resp = new WorkItemCustomFieldValueRespVO();
        resp.setFieldId(field.getId());
        resp.setCode(field.getCode());
        resp.setName(field.getName());
        resp.setFieldType(field.getFieldType());
        resp.setRequired(field.getRequired());
        resp.setEnabled(field.getEnabled());
        WorkItemFieldTypeEnum type = WorkItemFieldTypeEnum.of(field.getFieldType());
        if (type.isSelect()) {
            List<Long> optionIds = links.stream().map(WorkItemFieldValueOptionDO::getOptionId).toList();
            resp.setValue(type == WorkItemFieldTypeEnum.SINGLE_SELECT
                    ? (optionIds.isEmpty() ? null : optionIds.get(0)) : optionIds);
            resp.setSelectedOptions(optionIds.stream().map(options::get).filter(Objects::nonNull)
                    .map(this::toOptionResp).toList());
        } else {
            resp.setValue(scalarValue(type, value));
            resp.setSelectedOptions(List.of());
        }
        return resp;
    }

    private Object scalarValue(WorkItemFieldTypeEnum type, WorkItemFieldValueDO value) {
        return switch (type) {
            case TEXT -> value.getTextValue();
            case NUMBER -> value.getNumberValue();
            case DATE_TIME -> value.getDatetimeValue();
            case BOOLEAN -> value.getBooleanValue();
            case USER -> value.getUserValue();
            case SINGLE_SELECT, MULTI_SELECT -> null;
        };
    }

    private WorkItemFieldRespVO.Option toOptionResp(WorkItemFieldOptionDO option) {
        WorkItemFieldRespVO.Option resp = new WorkItemFieldRespVO.Option();
        resp.setId(option.getId());
        resp.setFieldId(option.getFieldId());
        resp.setCode(option.getCode());
        resp.setName(option.getName());
        resp.setColor(option.getColor());
        resp.setEnabled(option.getEnabled());
        resp.setSort(option.getSort());
        return resp;
    }

    private record NormalizedValue(WorkItemFieldDO field, Object scalar, List<Long> optionIds) { }

}
