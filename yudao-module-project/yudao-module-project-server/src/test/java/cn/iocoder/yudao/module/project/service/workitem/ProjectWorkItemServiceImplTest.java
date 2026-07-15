package cn.iocoder.yudao.module.project.service.workitem;

import cn.iocoder.yudao.framework.common.pojo.PageResult;
import cn.iocoder.yudao.framework.test.core.ut.BaseMockitoUnitTest;
import cn.iocoder.yudao.module.project.controller.admin.workitem.vo.ProjectWorkItemPageReqVO;
import cn.iocoder.yudao.module.project.controller.admin.workitem.vo.ProjectWorkItemRespVO;
import cn.iocoder.yudao.module.project.controller.admin.workitem.vo.WorkItemCustomFieldValueRespVO;
import cn.iocoder.yudao.module.project.dal.dataobject.workitem.ProjectWorkItemDO;
import cn.iocoder.yudao.module.project.dal.dataobject.workitem.WorkItemFieldDO;
import cn.iocoder.yudao.module.project.dal.mysql.workitem.ProjectWorkItemMapper;
import cn.iocoder.yudao.module.project.dal.mysql.workitem.WorkItemFieldMapper;
import cn.iocoder.yudao.module.project.enums.WorkItemFieldScopeEnum;
import cn.iocoder.yudao.module.project.enums.WorkItemFieldTypeEnum;
import org.junit.jupiter.api.Test;
import org.mockito.InjectMocks;
import org.mockito.Mock;

import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static cn.iocoder.yudao.framework.test.core.util.AssertUtils.assertServiceException;
import static cn.iocoder.yudao.module.project.enums.ErrorCodeConstants.PROJECT_WORK_ITEM_QUERY_FIELD_NOT_SUPPORTED;
import static org.mockito.Mockito.when;

class ProjectWorkItemServiceImplTest extends BaseMockitoUnitTest {

    @InjectMocks
    private ProjectWorkItemServiceImpl service;

    @Mock
    private ProjectWorkItemMapper workItemMapper;
    @Mock
    private WorkItemCustomFieldValueService customFieldValueService;
    @Mock
    private WorkItemFieldMapper customFieldMapper;

    @Test
    void testGetWorkItem_assembleCustomFields() {
        ProjectWorkItemDO workItem = buildWorkItem(1L);
        WorkItemCustomFieldValueRespVO customField = buildCustomField(10L);
        when(workItemMapper.selectById(1L)).thenReturn(workItem);
        when(customFieldValueService.getValueMap(List.of(1L))).thenReturn(Map.of(1L, List.of(customField)));

        ProjectWorkItemRespVO result = service.getWorkItem(1L);

        assertNotNull(result);
        assertEquals(1L, result.getId());
        assertEquals(List.of(customField), result.getCustomFields());
    }

    @Test
    void testGetWorkItemPage_assembleCustomFields() {
        ProjectWorkItemPageReqVO pageReqVO = new ProjectWorkItemPageReqVO();
        ProjectWorkItemDO first = buildWorkItem(1L);
        ProjectWorkItemDO second = buildWorkItem(2L);
        WorkItemCustomFieldValueRespVO customField = buildCustomField(10L);
        when(workItemMapper.selectPage(pageReqVO)).thenReturn(new PageResult<>(List.of(first, second), 2L));
        when(customFieldValueService.getValueMap(List.of(1L, 2L)))
                .thenReturn(Map.of(1L, List.of(customField)));

        PageResult<ProjectWorkItemRespVO> result = service.getWorkItemPage(pageReqVO);

        assertEquals(2L, result.getTotal());
        assertEquals(List.of(customField), result.getList().get(0).getCustomFields());
        assertEquals(List.of(), result.getList().get(1).getCustomFields());
    }

    @Test
    void testGetWorkItemPage_globalCustomFieldAccepted() {
        ProjectWorkItemPageReqVO pageReqVO = buildCustomFieldQuery(1L);
        WorkItemFieldDO field = buildCustomFieldDefinition(10L, WorkItemFieldScopeEnum.GLOBAL.getValue(),
                WorkItemFieldScopeEnum.GLOBAL_PROJECT_ID);
        when(customFieldMapper.selectById(10L)).thenReturn(field);
        when(workItemMapper.selectPage(pageReqVO)).thenReturn(new PageResult<>(List.of(), 0L));

        service.getWorkItemPage(pageReqVO);

        assertEquals(field, pageReqVO.getConditions().get(0).getCustomField());
    }

    @Test
    void testGetWorkItemPage_foreignProjectCustomFieldRejected() {
        ProjectWorkItemPageReqVO pageReqVO = buildCustomFieldQuery(1L);
        WorkItemFieldDO field = buildCustomFieldDefinition(10L, WorkItemFieldScopeEnum.PROJECT.getValue(), 2L);
        when(customFieldMapper.selectById(10L)).thenReturn(field);

        assertServiceException(() -> service.getWorkItemPage(pageReqVO),
                PROJECT_WORK_ITEM_QUERY_FIELD_NOT_SUPPORTED, "custom:10");
    }

    @Test
    void testGetWorkItemPage_malformedGlobalCustomFieldRejected() {
        ProjectWorkItemPageReqVO pageReqVO = buildCustomFieldQuery(1L);
        WorkItemFieldDO field = buildCustomFieldDefinition(10L, WorkItemFieldScopeEnum.GLOBAL.getValue(), 2L);
        when(customFieldMapper.selectById(10L)).thenReturn(field);

        assertServiceException(() -> service.getWorkItemPage(pageReqVO),
                PROJECT_WORK_ITEM_QUERY_FIELD_NOT_SUPPORTED, "custom:10");
    }

    private ProjectWorkItemDO buildWorkItem(Long id) {
        ProjectWorkItemDO workItem = new ProjectWorkItemDO();
        workItem.setId(id);
        workItem.setTitle("工作项" + id);
        return workItem;
    }

    private WorkItemCustomFieldValueRespVO buildCustomField(Long fieldId) {
        WorkItemCustomFieldValueRespVO customField = new WorkItemCustomFieldValueRespVO();
        customField.setFieldId(fieldId);
        return customField;
    }

    private ProjectWorkItemPageReqVO buildCustomFieldQuery(Long projectId) {
        ProjectWorkItemPageReqVO.Condition condition = new ProjectWorkItemPageReqVO.Condition();
        condition.setField("custom:10");
        condition.setOperator("eq");
        condition.setValue("查询值");
        ProjectWorkItemPageReqVO pageReqVO = new ProjectWorkItemPageReqVO();
        pageReqVO.setProjectId(projectId);
        pageReqVO.setConditions(List.of(condition));
        return pageReqVO;
    }

    private WorkItemFieldDO buildCustomFieldDefinition(Long id, String scope, Long projectId) {
        WorkItemFieldDO field = new WorkItemFieldDO();
        field.setId(id);
        field.setScope(scope);
        field.setProjectId(projectId);
        field.setCode("global_text");
        field.setName("全局文本");
        field.setFieldType(WorkItemFieldTypeEnum.TEXT.getValue());
        field.setEnabled(true);
        return field;
    }

}
