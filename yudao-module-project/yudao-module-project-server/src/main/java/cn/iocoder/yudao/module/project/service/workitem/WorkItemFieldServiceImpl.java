package cn.iocoder.yudao.module.project.service.workitem;

import cn.hutool.core.util.ObjUtil;
import cn.hutool.core.util.StrUtil;
import cn.iocoder.yudao.module.project.controller.admin.workitem.vo.WorkItemFieldBaseReqVO;
import cn.iocoder.yudao.module.project.controller.admin.workitem.vo.WorkItemFieldOptionSaveReqVO;
import cn.iocoder.yudao.module.project.controller.admin.workitem.vo.WorkItemFieldRespVO;
import cn.iocoder.yudao.module.project.controller.admin.workitem.vo.WorkItemFieldSaveReqVO;
import cn.iocoder.yudao.module.project.controller.admin.workitem.vo.WorkItemGlobalFieldSaveReqVO;
import cn.iocoder.yudao.module.project.dal.dataobject.workitem.WorkItemFieldDO;
import cn.iocoder.yudao.module.project.dal.dataobject.workitem.WorkItemFieldOptionDO;
import cn.iocoder.yudao.module.project.dal.mysql.workitem.WorkItemFieldMapper;
import cn.iocoder.yudao.module.project.dal.mysql.workitem.WorkItemFieldOptionMapper;
import cn.iocoder.yudao.module.project.enums.WorkItemFieldTypeEnum;
import cn.iocoder.yudao.module.project.enums.WorkItemFieldScopeEnum;
import cn.iocoder.yudao.module.project.service.project.ProjectService;
import jakarta.annotation.Resource;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.validation.annotation.Validated;

import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.stream.Collectors;

import static cn.iocoder.yudao.framework.common.exception.util.ServiceExceptionUtil.exception;
import static cn.iocoder.yudao.module.project.enums.ErrorCodeConstants.*;

@Service
@Validated
public class WorkItemFieldServiceImpl implements WorkItemFieldService {

    @Resource
    private WorkItemFieldMapper fieldMapper;
    @Resource
    private WorkItemFieldOptionMapper optionMapper;
    @Resource
    private ProjectService projectService;

    @Override
    @Transactional(rollbackFor = Exception.class)
    public Long createField(WorkItemFieldSaveReqVO reqVO) {
        projectService.validateProjectUsable(reqVO.getProjectId());
        validateFieldCodeUnique(WorkItemFieldScopeEnum.PROJECT.getValue(), reqVO.getProjectId(), reqVO.getCode());
        WorkItemFieldDO field = toField(reqVO, WorkItemFieldScopeEnum.PROJECT.getValue(), reqVO.getProjectId());
        fieldMapper.insert(field);
        return field.getId();
    }

    @Override
    public void updateField(WorkItemFieldSaveReqVO reqVO) {
        projectService.validateProjectUsable(reqVO.getProjectId());
        updateField(reqVO, reqVO.getId(), WorkItemFieldScopeEnum.PROJECT.getValue(), reqVO.getProjectId());
    }

    @Override
    public void updateFieldEnabled(Long id, Boolean enabled) {
        updateFieldEnabled(id, enabled, WorkItemFieldScopeEnum.PROJECT.getValue());
    }

    @Override
    public List<WorkItemFieldRespVO> getFieldList(Long projectId) {
        projectService.validateProjectUsable(projectId);
        return buildFieldRespList(fieldMapper.selectListByEffectiveProjectId(projectId), false);
    }

    @Override
    public List<WorkItemFieldRespVO> getFieldSchema(Long projectId, String category) {
        projectService.validateProjectUsable(projectId);
        List<WorkItemFieldDO> fields = fieldMapper.selectListByEffectiveProjectId(projectId).stream()
                .filter(field -> Boolean.TRUE.equals(field.getEnabled()))
                .filter(field -> StrUtil.isBlank(category) || StrUtil.isBlank(field.getApplicableCategory())
                        || Objects.equals(field.getApplicableCategory(), category))
                .toList();
        return buildFieldRespList(fields, true);
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public Long createGlobalField(WorkItemGlobalFieldSaveReqVO reqVO) {
        validateFieldCodeUnique(WorkItemFieldScopeEnum.GLOBAL.getValue(),
                WorkItemFieldScopeEnum.GLOBAL_PROJECT_ID, reqVO.getCode());
        WorkItemFieldDO field = toField(reqVO, WorkItemFieldScopeEnum.GLOBAL.getValue(),
                WorkItemFieldScopeEnum.GLOBAL_PROJECT_ID);
        fieldMapper.insert(field);
        return field.getId();
    }

    @Override
    public void updateGlobalField(WorkItemGlobalFieldSaveReqVO reqVO) {
        updateField(reqVO, reqVO.getId(), WorkItemFieldScopeEnum.GLOBAL.getValue(),
                WorkItemFieldScopeEnum.GLOBAL_PROJECT_ID);
    }

    @Override
    public void updateGlobalFieldEnabled(Long id, Boolean enabled) {
        updateFieldEnabled(id, enabled, WorkItemFieldScopeEnum.GLOBAL.getValue());
    }

    @Override
    public List<WorkItemFieldRespVO> getGlobalFieldList() {
        return buildFieldRespList(fieldMapper.selectGlobalList(), false);
    }

    @Override
    public Long createOption(WorkItemFieldOptionSaveReqVO reqVO) {
        return createOption(reqVO, WorkItemFieldScopeEnum.PROJECT.getValue());
    }

    @Override
    public void updateOption(WorkItemFieldOptionSaveReqVO reqVO) {
        updateOption(reqVO, WorkItemFieldScopeEnum.PROJECT.getValue());
    }

    @Override
    public void updateOptionEnabled(Long id, Boolean enabled) {
        updateOptionEnabled(id, enabled, WorkItemFieldScopeEnum.PROJECT.getValue());
    }

    @Override
    public Long createGlobalOption(WorkItemFieldOptionSaveReqVO reqVO) {
        return createOption(reqVO, WorkItemFieldScopeEnum.GLOBAL.getValue());
    }

    @Override
    public void updateGlobalOption(WorkItemFieldOptionSaveReqVO reqVO) {
        updateOption(reqVO, WorkItemFieldScopeEnum.GLOBAL.getValue());
    }

    @Override
    public void updateGlobalOptionEnabled(Long id, Boolean enabled) {
        updateOptionEnabled(id, enabled, WorkItemFieldScopeEnum.GLOBAL.getValue());
    }

    @Override
    public WorkItemFieldDO validateFieldExists(Long id) {
        WorkItemFieldDO field = id == null ? null : fieldMapper.selectById(id);
        if (field == null) {
            throw exception(WORK_ITEM_FIELD_NOT_EXISTS);
        }
        return field;
    }

    private WorkItemFieldDO validateFieldScope(Long id, String expectedScope) {
        WorkItemFieldDO field = validateFieldExists(id);
        if (!Objects.equals(field.getScope(), expectedScope)) {
            throw exception(WORK_ITEM_FIELD_SCOPE_MISMATCH);
        }
        return field;
    }

    private WorkItemFieldDO validateSelectField(Long id, String expectedScope) {
        WorkItemFieldDO field = validateFieldScope(id, expectedScope);
        WorkItemFieldTypeEnum type = WorkItemFieldTypeEnum.of(field.getFieldType());
        if (type == null || !type.isSelect()) {
            throw exception(WORK_ITEM_FIELD_OPTION_UNSUPPORTED);
        }
        return field;
    }

    private WorkItemFieldOptionDO validateOptionExists(Long id) {
        WorkItemFieldOptionDO option = id == null ? null : optionMapper.selectById(id);
        if (option == null) {
            throw exception(WORK_ITEM_FIELD_OPTION_NOT_EXISTS);
        }
        return option;
    }

    private void validateFieldCodeUnique(String scope, Long projectId, String code) {
        if (WorkItemFieldScopeEnum.isGlobal(scope)) {
            if (fieldMapper.selectCountByCode(code) > 0) {
                throw exception(WORK_ITEM_FIELD_CODE_DUPLICATE);
            }
            return;
        }
        WorkItemFieldDO projectField = fieldMapper.selectByScopeProjectIdAndCode(
                WorkItemFieldScopeEnum.PROJECT.getValue(), projectId, code);
        WorkItemFieldDO globalField = fieldMapper.selectByScopeProjectIdAndCode(
                WorkItemFieldScopeEnum.GLOBAL.getValue(), WorkItemFieldScopeEnum.GLOBAL_PROJECT_ID, code);
        if (projectField != null || globalField != null) {
            throw exception(WORK_ITEM_FIELD_CODE_DUPLICATE);
        }
    }

    private void validateOptionCodeUnique(Long id, Long fieldId, String code) {
        WorkItemFieldOptionDO existing = optionMapper.selectByFieldIdAndCode(fieldId, code);
        if (existing != null && ObjUtil.notEqual(existing.getId(), id)) {
            throw exception(WORK_ITEM_FIELD_OPTION_CODE_DUPLICATE);
        }
    }

    private WorkItemFieldDO toField(WorkItemFieldBaseReqVO reqVO, String scope, Long projectId) {
        WorkItemFieldDO field = new WorkItemFieldDO();
        field.setScope(scope);
        field.setProjectId(projectId);
        field.setCode(reqVO.getCode());
        field.setName(reqVO.getName());
        field.setFieldType(reqVO.getFieldType());
        field.setApplicableCategory(reqVO.getApplicableCategory());
        field.setRequired(reqVO.getRequired());
        field.setEnabled(reqVO.getEnabled());
        field.setSort(reqVO.getSort());
        field.setConfigJson(reqVO.getConfigJson());
        return field;
    }

    private void updateField(WorkItemFieldBaseReqVO reqVO, Long id, String scope, Long projectId) {
        WorkItemFieldDO old = validateFieldScope(id, scope);
        if (!Objects.equals(old.getProjectId(), projectId)
                || !Objects.equals(old.getCode(), reqVO.getCode())
                || !Objects.equals(old.getFieldType(), reqVO.getFieldType())) {
            throw exception(WORK_ITEM_FIELD_IMMUTABLE);
        }
        WorkItemFieldDO update = toField(reqVO, scope, projectId);
        update.setId(id);
        fieldMapper.updateById(update);
    }

    private void updateFieldEnabled(Long id, Boolean enabled, String scope) {
        validateFieldScope(id, scope);
        WorkItemFieldDO update = new WorkItemFieldDO();
        update.setId(id);
        update.setEnabled(enabled);
        fieldMapper.updateById(update);
    }

    private Long createOption(WorkItemFieldOptionSaveReqVO reqVO, String scope) {
        WorkItemFieldDO field = validateSelectField(reqVO.getFieldId(), scope);
        validateProjectFieldUsable(field);
        validateOptionCodeUnique(null, reqVO.getFieldId(), reqVO.getCode());
        WorkItemFieldOptionDO option = toOption(reqVO);
        optionMapper.insert(option);
        return option.getId();
    }

    private void updateOption(WorkItemFieldOptionSaveReqVO reqVO, String scope) {
        WorkItemFieldOptionDO old = validateOptionExists(reqVO.getId());
        WorkItemFieldDO field = validateSelectField(reqVO.getFieldId(), scope);
        validateProjectFieldUsable(field);
        if (!Objects.equals(old.getFieldId(), reqVO.getFieldId()) || !Objects.equals(old.getCode(), reqVO.getCode())) {
            throw exception(WORK_ITEM_FIELD_IMMUTABLE);
        }
        WorkItemFieldOptionDO update = toOption(reqVO);
        update.setId(reqVO.getId());
        optionMapper.updateById(update);
    }

    private void updateOptionEnabled(Long id, Boolean enabled, String scope) {
        WorkItemFieldOptionDO old = validateOptionExists(id);
        WorkItemFieldDO field = validateFieldScope(old.getFieldId(), scope);
        validateProjectFieldUsable(field);
        WorkItemFieldOptionDO update = new WorkItemFieldOptionDO();
        update.setId(id);
        update.setEnabled(enabled);
        optionMapper.updateById(update);
    }

    private void validateProjectFieldUsable(WorkItemFieldDO field) {
        if (!WorkItemFieldScopeEnum.isGlobal(field.getScope())) {
            projectService.validateProjectUsable(field.getProjectId());
        }
    }

    private List<WorkItemFieldRespVO> buildFieldRespList(List<WorkItemFieldDO> fields, boolean enabledOptionsOnly) {
        List<WorkItemFieldOptionDO> optionList = optionMapper.selectListByFieldIds(
                fields.stream().map(WorkItemFieldDO::getId).toList());
        if (enabledOptionsOnly) {
            optionList = optionList.stream().filter(option -> Boolean.TRUE.equals(option.getEnabled())).toList();
        }
        Map<Long, List<WorkItemFieldOptionDO>> options = optionList.stream()
                .collect(Collectors.groupingBy(WorkItemFieldOptionDO::getFieldId));
        return fields.stream().map(field -> toResp(field, options.getOrDefault(field.getId(), List.of()))).toList();
    }

    private WorkItemFieldOptionDO toOption(WorkItemFieldOptionSaveReqVO reqVO) {
        WorkItemFieldOptionDO option = new WorkItemFieldOptionDO();
        option.setFieldId(reqVO.getFieldId());
        option.setCode(reqVO.getCode());
        option.setName(reqVO.getName());
        option.setColor(reqVO.getColor());
        option.setEnabled(reqVO.getEnabled());
        option.setSort(reqVO.getSort());
        return option;
    }

    private WorkItemFieldRespVO toResp(WorkItemFieldDO field, List<WorkItemFieldOptionDO> options) {
        WorkItemFieldRespVO resp = new WorkItemFieldRespVO();
        resp.setId(field.getId());
        resp.setScope(field.getScope());
        resp.setProjectId(WorkItemFieldScopeEnum.isGlobal(field.getScope()) ? null : field.getProjectId());
        resp.setCode(field.getCode());
        resp.setName(field.getName());
        resp.setFieldType(field.getFieldType());
        resp.setApplicableCategory(field.getApplicableCategory());
        resp.setRequired(field.getRequired());
        resp.setEnabled(field.getEnabled());
        resp.setSort(field.getSort());
        resp.setConfigJson(field.getConfigJson());
        resp.setCreateTime(field.getCreateTime());
        resp.setOptions(options.stream().map(this::toOptionResp).toList());
        return resp;
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

}
