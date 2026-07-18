package cn.iocoder.yudao.module.project.service.workitem;

import cn.iocoder.yudao.framework.test.core.ut.BaseMockitoUnitTest;
import cn.iocoder.yudao.module.project.controller.admin.workitem.vo.WorkItemFieldBaseReqVO;
import cn.iocoder.yudao.module.project.controller.admin.workitem.vo.WorkItemFieldRespVO;
import cn.iocoder.yudao.module.project.controller.admin.workitem.vo.WorkItemFieldSaveReqVO;
import cn.iocoder.yudao.module.project.controller.admin.workitem.vo.WorkItemGlobalFieldSaveReqVO;
import cn.iocoder.yudao.module.project.dal.dataobject.workitem.WorkItemFieldDO;
import cn.iocoder.yudao.module.project.dal.dataobject.workitem.WorkItemFieldOptionDO;
import cn.iocoder.yudao.module.project.dal.mysql.workitem.WorkItemFieldMapper;
import cn.iocoder.yudao.module.project.dal.mysql.workitem.WorkItemFieldOptionMapper;
import cn.iocoder.yudao.module.project.enums.ProjectWorkItemCategoryEnum;
import cn.iocoder.yudao.module.project.enums.WorkItemFieldScopeEnum;
import cn.iocoder.yudao.module.project.enums.WorkItemFieldTypeEnum;
import cn.iocoder.yudao.module.project.service.project.ProjectService;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;

import java.util.List;

import static cn.iocoder.yudao.framework.test.core.util.AssertUtils.assertServiceException;
import static cn.iocoder.yudao.module.project.enums.ErrorCodeConstants.WORK_ITEM_FIELD_CODE_DUPLICATE;
import static cn.iocoder.yudao.module.project.enums.ErrorCodeConstants.WORK_ITEM_FIELD_SCOPE_MISMATCH;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class WorkItemFieldServiceImplTest extends BaseMockitoUnitTest {

    @InjectMocks
    private WorkItemFieldServiceImpl service;

    @Mock
    private WorkItemFieldMapper fieldMapper;
    @Mock
    private WorkItemFieldOptionMapper optionMapper;
    @Mock
    private ProjectService projectService;

    @Test
    void testCreateField_projectScope() {
        WorkItemFieldSaveReqVO reqVO = buildProjectReq();
        doAnswer(invocation -> {
            WorkItemFieldDO field = invocation.getArgument(0);
            field.setId(10L);
            return 1;
        }).when(fieldMapper).insert(any(WorkItemFieldDO.class));

        Long id = service.createField(reqVO);

        assertEquals(10L, id);
        ArgumentCaptor<WorkItemFieldDO> captor = ArgumentCaptor.forClass(WorkItemFieldDO.class);
        verify(fieldMapper).insert(captor.capture());
        assertEquals(WorkItemFieldScopeEnum.PROJECT.getValue(), captor.getValue().getScope());
        assertEquals(1L, captor.getValue().getProjectId());
    }

    @Test
    void testCreateGlobalField_duplicateCodeRejected() {
        WorkItemGlobalFieldSaveReqVO reqVO = buildGlobalReq();
        when(fieldMapper.selectCountByCode(reqVO.getCode())).thenReturn(1L);

        assertServiceException(() -> service.createGlobalField(reqVO), WORK_ITEM_FIELD_CODE_DUPLICATE);
    }

    @Test
    void testCreateGlobalField_globalScope() {
        WorkItemGlobalFieldSaveReqVO reqVO = buildGlobalReq();
        doAnswer(invocation -> {
            WorkItemFieldDO field = invocation.getArgument(0);
            field.setId(10L);
            return 1;
        }).when(fieldMapper).insert(any(WorkItemFieldDO.class));

        Long id = service.createGlobalField(reqVO);

        assertEquals(10L, id);
        ArgumentCaptor<WorkItemFieldDO> captor = ArgumentCaptor.forClass(WorkItemFieldDO.class);
        verify(fieldMapper).insert(captor.capture());
        assertEquals(WorkItemFieldScopeEnum.GLOBAL.getValue(), captor.getValue().getScope());
        assertEquals(WorkItemFieldScopeEnum.GLOBAL_PROJECT_ID, captor.getValue().getProjectId());
    }

    @Test
    void testCreateField_globalCodeRejected() {
        WorkItemFieldSaveReqVO reqVO = buildProjectReq();
        WorkItemFieldDO global = buildField(10L, WorkItemFieldScopeEnum.GLOBAL.getValue(),
                WorkItemFieldScopeEnum.GLOBAL_PROJECT_ID);
        global.setCode(reqVO.getCode());
        when(fieldMapper.selectByScopeProjectIdAndCode(WorkItemFieldScopeEnum.PROJECT.getValue(),
                reqVO.getProjectId(), reqVO.getCode())).thenReturn(null);
        when(fieldMapper.selectByScopeProjectIdAndCode(WorkItemFieldScopeEnum.GLOBAL.getValue(),
                WorkItemFieldScopeEnum.GLOBAL_PROJECT_ID, reqVO.getCode())).thenReturn(global);

        assertServiceException(() -> service.createField(reqVO), WORK_ITEM_FIELD_CODE_DUPLICATE);
    }

    @Test
    void testGetFieldList_mergeGlobalAndProject() {
        WorkItemFieldDO global = buildField(10L, WorkItemFieldScopeEnum.GLOBAL.getValue(),
                WorkItemFieldScopeEnum.GLOBAL_PROJECT_ID);
        WorkItemFieldDO project = buildField(11L, WorkItemFieldScopeEnum.PROJECT.getValue(), 1L);
        when(fieldMapper.selectListByEffectiveProjectId(1L)).thenReturn(List.of(global, project));
        when(optionMapper.selectListByFieldIds(List.of(10L, 11L)))
                .thenReturn(List.of(buildOption(20L, 10L, false)));

        List<WorkItemFieldRespVO> result = service.getFieldList(1L);

        assertEquals(2, result.size());
        assertEquals(WorkItemFieldScopeEnum.GLOBAL.getValue(), result.get(0).getScope());
        assertNull(result.get(0).getProjectId());
        assertEquals(false, result.get(0).getOptions().get(0).getEnabled());
        assertEquals(1L, result.get(1).getProjectId());
    }

    @Test
    void testGetFieldSchema_filterDisabledCategoryAndOptions() {
        WorkItemFieldDO global = buildField(10L, WorkItemFieldScopeEnum.GLOBAL.getValue(),
                WorkItemFieldScopeEnum.GLOBAL_PROJECT_ID);
        WorkItemFieldDO requirement = buildField(11L, WorkItemFieldScopeEnum.PROJECT.getValue(), 1L);
        requirement.setApplicableCategory(ProjectWorkItemCategoryEnum.REQUIREMENT.getValue());
        requirement.setFieldType(WorkItemFieldTypeEnum.SINGLE_SELECT.getValue());
        WorkItemFieldDO bug = buildField(12L, WorkItemFieldScopeEnum.PROJECT.getValue(), 1L);
        bug.setApplicableCategory(ProjectWorkItemCategoryEnum.BUG.getValue());
        WorkItemFieldDO disabled = buildField(13L, WorkItemFieldScopeEnum.PROJECT.getValue(), 1L);
        disabled.setApplicableCategory(ProjectWorkItemCategoryEnum.REQUIREMENT.getValue());
        disabled.setEnabled(false);
        when(fieldMapper.selectListByEffectiveProjectId(1L)).thenReturn(List.of(global, requirement, bug, disabled));
        when(optionMapper.selectListByFieldIds(List.of(10L, 11L))).thenReturn(List.of(
                buildOption(20L, 11L, true), buildOption(21L, 11L, false)));

        List<WorkItemFieldRespVO> result = service.getFieldSchema(
                1L, ProjectWorkItemCategoryEnum.REQUIREMENT.getValue());

        assertEquals(List.of(10L, 11L), result.stream().map(WorkItemFieldRespVO::getId).toList());
        assertEquals(1, result.get(1).getOptions().size());
        assertEquals(20L, result.get(1).getOptions().get(0).getId());
        verify(projectService).validateProjectUsable(1L);
    }

    @Test
    void testGetFieldSchema_withoutCategoryReturnAllEnabledFields() {
        WorkItemFieldDO requirement = buildField(10L, WorkItemFieldScopeEnum.GLOBAL.getValue(),
                WorkItemFieldScopeEnum.GLOBAL_PROJECT_ID);
        requirement.setApplicableCategory(ProjectWorkItemCategoryEnum.REQUIREMENT.getValue());
        WorkItemFieldDO bug = buildField(11L, WorkItemFieldScopeEnum.PROJECT.getValue(), 1L);
        bug.setApplicableCategory(ProjectWorkItemCategoryEnum.BUG.getValue());
        when(fieldMapper.selectListByEffectiveProjectId(1L)).thenReturn(List.of(requirement, bug));
        when(optionMapper.selectListByFieldIds(List.of(10L, 11L))).thenReturn(List.of());

        List<WorkItemFieldRespVO> result = service.getFieldSchema(1L, null);

        assertEquals(List.of(10L, 11L), result.stream().map(WorkItemFieldRespVO::getId).toList());
    }

    @Test
    void testUpdateFieldEnabled_globalFieldRejectedByProjectEntry() {
        WorkItemFieldDO global = buildField(10L, WorkItemFieldScopeEnum.GLOBAL.getValue(),
                WorkItemFieldScopeEnum.GLOBAL_PROJECT_ID);
        when(fieldMapper.selectById(10L)).thenReturn(global);

        assertServiceException(() -> service.updateFieldEnabled(10L, false), WORK_ITEM_FIELD_SCOPE_MISMATCH);
    }

    private WorkItemFieldSaveReqVO buildProjectReq() {
        WorkItemFieldSaveReqVO reqVO = new WorkItemFieldSaveReqVO();
        reqVO.setProjectId(1L);
        fillReq(reqVO);
        return reqVO;
    }

    private WorkItemGlobalFieldSaveReqVO buildGlobalReq() {
        WorkItemGlobalFieldSaveReqVO reqVO = new WorkItemGlobalFieldSaveReqVO();
        fillReq(reqVO);
        return reqVO;
    }

    private void fillReq(WorkItemFieldBaseReqVO reqVO) {
        reqVO.setCode("risk_level");
        reqVO.setName("风险等级");
        reqVO.setFieldType(WorkItemFieldTypeEnum.TEXT.getValue());
        reqVO.setRequired(false);
        reqVO.setEnabled(true);
        reqVO.setSort(10);
    }

    private WorkItemFieldDO buildField(Long id, String scope, Long projectId) {
        WorkItemFieldDO field = new WorkItemFieldDO();
        field.setId(id);
        field.setScope(scope);
        field.setProjectId(projectId);
        field.setCode("field_" + id);
        field.setName("字段" + id);
        field.setFieldType(WorkItemFieldTypeEnum.TEXT.getValue());
        field.setRequired(false);
        field.setEnabled(true);
        field.setSort(id.intValue());
        return field;
    }

    private WorkItemFieldOptionDO buildOption(Long id, Long fieldId, boolean enabled) {
        WorkItemFieldOptionDO option = new WorkItemFieldOptionDO();
        option.setId(id);
        option.setFieldId(fieldId);
        option.setCode("option_" + id);
        option.setName("选项" + id);
        option.setEnabled(enabled);
        option.setSort(id.intValue());
        return option;
    }

}
