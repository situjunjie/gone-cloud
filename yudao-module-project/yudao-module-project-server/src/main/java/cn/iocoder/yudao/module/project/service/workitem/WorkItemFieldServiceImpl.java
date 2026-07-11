package cn.iocoder.yudao.module.project.service.workitem;

import cn.hutool.core.util.ObjUtil;
import cn.iocoder.yudao.module.project.controller.admin.workitem.vo.WorkItemFieldOptionSaveReqVO;
import cn.iocoder.yudao.module.project.controller.admin.workitem.vo.WorkItemFieldRespVO;
import cn.iocoder.yudao.module.project.controller.admin.workitem.vo.WorkItemFieldSaveReqVO;
import cn.iocoder.yudao.module.project.dal.dataobject.workitem.WorkItemFieldDO;
import cn.iocoder.yudao.module.project.dal.dataobject.workitem.WorkItemFieldOptionDO;
import cn.iocoder.yudao.module.project.dal.mysql.workitem.WorkItemFieldMapper;
import cn.iocoder.yudao.module.project.dal.mysql.workitem.WorkItemFieldOptionMapper;
import cn.iocoder.yudao.module.project.enums.WorkItemFieldTypeEnum;
import cn.iocoder.yudao.module.project.service.project.ProjectService;
import jakarta.annotation.Resource;
import org.springframework.stereotype.Service;
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
    public Long createField(WorkItemFieldSaveReqVO reqVO) {
        projectService.validateProjectUsable(reqVO.getProjectId());
        validateFieldCodeUnique(null, reqVO.getProjectId(), reqVO.getCode());
        WorkItemFieldDO field = toField(reqVO);
        fieldMapper.insert(field);
        return field.getId();
    }

    @Override
    public void updateField(WorkItemFieldSaveReqVO reqVO) {
        WorkItemFieldDO old = validateFieldExists(reqVO.getId());
        if (!Objects.equals(old.getProjectId(), reqVO.getProjectId())
                || !Objects.equals(old.getCode(), reqVO.getCode())
                || !Objects.equals(old.getFieldType(), reqVO.getFieldType())) {
            throw exception(WORK_ITEM_FIELD_IMMUTABLE);
        }
        WorkItemFieldDO update = toField(reqVO);
        update.setId(reqVO.getId());
        fieldMapper.updateById(update);
    }

    @Override
    public void updateFieldEnabled(Long id, Boolean enabled) {
        validateFieldExists(id);
        WorkItemFieldDO update = new WorkItemFieldDO();
        update.setId(id);
        update.setEnabled(enabled);
        fieldMapper.updateById(update);
    }

    @Override
    public List<WorkItemFieldRespVO> getFieldList(Long projectId) {
        projectService.validateProjectUsable(projectId);
        List<WorkItemFieldDO> fields = fieldMapper.selectListByProjectId(projectId);
        Map<Long, List<WorkItemFieldOptionDO>> options = optionMapper.selectListByFieldIds(
                        fields.stream().map(WorkItemFieldDO::getId).toList()).stream()
                .collect(Collectors.groupingBy(WorkItemFieldOptionDO::getFieldId));
        return fields.stream().map(field -> toResp(field, options.getOrDefault(field.getId(), List.of()))).toList();
    }

    @Override
    public Long createOption(WorkItemFieldOptionSaveReqVO reqVO) {
        WorkItemFieldDO field = validateSelectField(reqVO.getFieldId());
        projectService.validateProjectUsable(field.getProjectId());
        validateOptionCodeUnique(null, reqVO.getFieldId(), reqVO.getCode());
        WorkItemFieldOptionDO option = toOption(reqVO);
        optionMapper.insert(option);
        return option.getId();
    }

    @Override
    public void updateOption(WorkItemFieldOptionSaveReqVO reqVO) {
        WorkItemFieldOptionDO old = validateOptionExists(reqVO.getId());
        validateSelectField(reqVO.getFieldId());
        if (!Objects.equals(old.getFieldId(), reqVO.getFieldId()) || !Objects.equals(old.getCode(), reqVO.getCode())) {
            throw exception(WORK_ITEM_FIELD_IMMUTABLE);
        }
        WorkItemFieldOptionDO update = toOption(reqVO);
        update.setId(reqVO.getId());
        optionMapper.updateById(update);
    }

    @Override
    public void updateOptionEnabled(Long id, Boolean enabled) {
        validateOptionExists(id);
        WorkItemFieldOptionDO update = new WorkItemFieldOptionDO();
        update.setId(id);
        update.setEnabled(enabled);
        optionMapper.updateById(update);
    }

    @Override
    public WorkItemFieldDO validateFieldExists(Long id) {
        WorkItemFieldDO field = id == null ? null : fieldMapper.selectById(id);
        if (field == null) {
            throw exception(WORK_ITEM_FIELD_NOT_EXISTS);
        }
        return field;
    }

    private WorkItemFieldDO validateSelectField(Long id) {
        WorkItemFieldDO field = validateFieldExists(id);
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

    private void validateFieldCodeUnique(Long id, Long projectId, String code) {
        WorkItemFieldDO existing = fieldMapper.selectByProjectIdAndCode(projectId, code);
        if (existing != null && ObjUtil.notEqual(existing.getId(), id)) {
            throw exception(WORK_ITEM_FIELD_CODE_DUPLICATE);
        }
    }

    private void validateOptionCodeUnique(Long id, Long fieldId, String code) {
        WorkItemFieldOptionDO existing = optionMapper.selectByFieldIdAndCode(fieldId, code);
        if (existing != null && ObjUtil.notEqual(existing.getId(), id)) {
            throw exception(WORK_ITEM_FIELD_OPTION_CODE_DUPLICATE);
        }
    }

    private WorkItemFieldDO toField(WorkItemFieldSaveReqVO reqVO) {
        WorkItemFieldDO field = new WorkItemFieldDO();
        field.setProjectId(reqVO.getProjectId());
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
        resp.setProjectId(field.getProjectId());
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
