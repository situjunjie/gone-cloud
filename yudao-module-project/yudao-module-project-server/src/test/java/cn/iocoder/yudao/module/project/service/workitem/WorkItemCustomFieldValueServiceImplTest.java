package cn.iocoder.yudao.module.project.service.workitem;

import cn.iocoder.yudao.framework.common.util.json.JsonUtils;
import cn.iocoder.yudao.framework.test.core.ut.BaseMockitoUnitTest;
import cn.iocoder.yudao.module.project.controller.admin.workitem.vo.WorkItemCustomFieldValueReqVO;
import cn.iocoder.yudao.module.project.dal.dataobject.workitem.WorkItemFieldDO;
import cn.iocoder.yudao.module.project.dal.dataobject.workitem.WorkItemFieldOptionDO;
import cn.iocoder.yudao.module.project.dal.dataobject.workitem.WorkItemFieldValueDO;
import cn.iocoder.yudao.module.project.dal.mysql.workitem.WorkItemFieldMapper;
import cn.iocoder.yudao.module.project.dal.mysql.workitem.WorkItemFieldOptionMapper;
import cn.iocoder.yudao.module.project.dal.mysql.workitem.WorkItemFieldValueMapper;
import cn.iocoder.yudao.module.project.dal.mysql.workitem.WorkItemFieldValueOptionMapper;
import cn.iocoder.yudao.module.project.enums.WorkItemFieldTypeEnum;
import cn.iocoder.yudao.module.project.enums.WorkItemFieldScopeEnum;
import cn.iocoder.yudao.module.system.api.user.AdminUserApi;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;

import java.util.List;

import static cn.iocoder.yudao.framework.test.core.util.AssertUtils.assertServiceException;
import static cn.iocoder.yudao.module.project.enums.ErrorCodeConstants.WORK_ITEM_CUSTOM_FIELD_REQUIRED;
import static cn.iocoder.yudao.module.project.enums.ErrorCodeConstants.WORK_ITEM_CUSTOM_FIELD_VALUE_INVALID;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyCollection;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class WorkItemCustomFieldValueServiceImplTest extends BaseMockitoUnitTest {

    @InjectMocks
    private WorkItemCustomFieldValueServiceImpl service;

    @Mock
    private WorkItemFieldMapper fieldMapper;
    @Mock
    private WorkItemFieldOptionMapper optionMapper;
    @Mock
    private WorkItemFieldValueMapper valueMapper;
    @Mock
    private WorkItemFieldValueOptionMapper valueOptionMapper;
    @Mock
    private AdminUserApi adminUserApi;

    @Test
    void testSaveSnapshot_textValue() {
        WorkItemFieldDO field = buildField(10L, WorkItemFieldTypeEnum.TEXT, false);
        when(fieldMapper.selectListByIds(anyCollection())).thenReturn(List.of(field));
        when(fieldMapper.selectListByEffectiveProjectId(1L)).thenReturn(List.of(field));
        doAnswer(invocation -> {
            WorkItemFieldValueDO value = invocation.getArgument(0);
            value.setId(100L);
            return 1;
        }).when(valueMapper).insert(any(WorkItemFieldValueDO.class));
        WorkItemCustomFieldValueReqVO reqVO = new WorkItemCustomFieldValueReqVO();
        reqVO.setFieldId(10L);
        reqVO.setValue(JsonUtils.parseTree("\"产品需求\""));

        service.saveSnapshot(20L, 1L, "REQUIREMENT", List.of(reqVO), true);

        ArgumentCaptor<WorkItemFieldValueDO> captor = ArgumentCaptor.forClass(WorkItemFieldValueDO.class);
        verify(valueMapper).insert(captor.capture());
        assertEquals("产品需求", captor.getValue().getTextValue());
        assertEquals(20L, captor.getValue().getWorkItemId());
        assertEquals(10L, captor.getValue().getFieldId());
    }

    @Test
    void testSaveSnapshot_requiredMissing() {
        WorkItemFieldDO field = buildField(10L, WorkItemFieldTypeEnum.TEXT, true);
        when(fieldMapper.selectListByIds(anyCollection())).thenReturn(List.of());
        when(fieldMapper.selectListByEffectiveProjectId(1L)).thenReturn(List.of(field));

        assertServiceException(() -> service.saveSnapshot(20L, 1L, "REQUIREMENT", List.of(), true),
                WORK_ITEM_CUSTOM_FIELD_REQUIRED, field.getName());
    }

    @Test
    void testSaveSnapshot_disabledOptionRejected() {
        WorkItemFieldDO field = buildField(10L, WorkItemFieldTypeEnum.SINGLE_SELECT, false);
        WorkItemFieldOptionDO option = new WorkItemFieldOptionDO();
        option.setId(30L);
        option.setFieldId(10L);
        option.setEnabled(false);
        when(fieldMapper.selectListByIds(anyCollection())).thenReturn(List.of(field));
        when(optionMapper.selectListByIds(anyCollection())).thenReturn(List.of(option));
        WorkItemCustomFieldValueReqVO reqVO = new WorkItemCustomFieldValueReqVO();
        reqVO.setFieldId(10L);
        reqVO.setValue(JsonUtils.parseTree("30"));

        assertServiceException(() -> service.saveSnapshot(20L, 1L, "REQUIREMENT", List.of(reqVO), true),
                WORK_ITEM_CUSTOM_FIELD_VALUE_INVALID, field.getName());
    }

    @Test
    void testSaveSnapshot_globalField() {
        WorkItemFieldDO field = buildField(10L, WorkItemFieldTypeEnum.TEXT, false);
        field.setScope(WorkItemFieldScopeEnum.GLOBAL.getValue());
        field.setProjectId(WorkItemFieldScopeEnum.GLOBAL_PROJECT_ID);
        when(fieldMapper.selectListByIds(anyCollection())).thenReturn(List.of(field));
        when(fieldMapper.selectListByEffectiveProjectId(1L)).thenReturn(List.of(field));
        doAnswer(invocation -> {
            WorkItemFieldValueDO value = invocation.getArgument(0);
            value.setId(100L);
            return 1;
        }).when(valueMapper).insert(any(WorkItemFieldValueDO.class));
        WorkItemCustomFieldValueReqVO reqVO = new WorkItemCustomFieldValueReqVO();
        reqVO.setFieldId(10L);
        reqVO.setValue(JsonUtils.parseTree("\"全局值\""));

        service.saveSnapshot(20L, 1L, "REQUIREMENT", List.of(reqVO), true);

        ArgumentCaptor<WorkItemFieldValueDO> captor = ArgumentCaptor.forClass(WorkItemFieldValueDO.class);
        verify(valueMapper).insert(captor.capture());
        assertEquals("全局值", captor.getValue().getTextValue());
    }

    @Test
    void testSaveSnapshot_foreignProjectFieldRejected() {
        WorkItemFieldDO field = buildField(10L, WorkItemFieldTypeEnum.TEXT, false);
        field.setProjectId(2L);
        when(fieldMapper.selectListByIds(anyCollection())).thenReturn(List.of(field));
        WorkItemCustomFieldValueReqVO reqVO = new WorkItemCustomFieldValueReqVO();
        reqVO.setFieldId(10L);
        reqVO.setValue(JsonUtils.parseTree("\"越界值\""));

        assertServiceException(() -> service.saveSnapshot(20L, 1L, "REQUIREMENT", List.of(reqVO), true),
                WORK_ITEM_CUSTOM_FIELD_VALUE_INVALID, field.getName());
    }

    @Test
    void testSaveSnapshot_globalRequiredMissing() {
        WorkItemFieldDO globalField = buildField(10L, WorkItemFieldTypeEnum.TEXT, true);
        globalField.setScope(WorkItemFieldScopeEnum.GLOBAL.getValue());
        globalField.setProjectId(WorkItemFieldScopeEnum.GLOBAL_PROJECT_ID);
        when(fieldMapper.selectListByIds(anyCollection())).thenReturn(List.of());
        when(fieldMapper.selectListByEffectiveProjectId(1L)).thenReturn(List.of(globalField));

        assertServiceException(() -> service.saveSnapshot(20L, 1L, "REQUIREMENT", List.of(), true),
                WORK_ITEM_CUSTOM_FIELD_REQUIRED, globalField.getName());
    }

    private WorkItemFieldDO buildField(Long id, WorkItemFieldTypeEnum type, boolean required) {
        WorkItemFieldDO field = new WorkItemFieldDO();
        field.setId(id);
        field.setScope(WorkItemFieldScopeEnum.PROJECT.getValue());
        field.setProjectId(1L);
        field.setCode("requirement_type");
        field.setName("需求类型");
        field.setFieldType(type.getValue());
        field.setApplicableCategory("REQUIREMENT");
        field.setRequired(required);
        field.setEnabled(true);
        return field;
    }

}
