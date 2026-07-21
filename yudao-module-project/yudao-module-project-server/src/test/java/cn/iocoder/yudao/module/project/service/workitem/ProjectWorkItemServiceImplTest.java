package cn.iocoder.yudao.module.project.service.workitem;

import cn.iocoder.yudao.framework.common.pojo.PageResult;
import cn.iocoder.yudao.framework.common.util.json.JsonUtils;
import cn.iocoder.yudao.framework.mybatis.core.query.LambdaQueryWrapperX;
import cn.iocoder.yudao.framework.test.core.ut.BaseMockitoUnitTest;
import cn.iocoder.yudao.module.project.controller.admin.workitem.vo.ProjectWorkItemGroupCountReqVO;
import cn.iocoder.yudao.module.project.controller.admin.workitem.vo.ProjectWorkItemGroupCountRespVO;
import cn.iocoder.yudao.module.project.controller.admin.workitem.vo.ProjectWorkItemPageReqVO;
import cn.iocoder.yudao.module.project.controller.admin.workitem.vo.ProjectWorkItemRespVO;
import cn.iocoder.yudao.module.project.controller.admin.workitem.vo.ProjectWorkItemUpdateFieldReqVO;
import cn.iocoder.yudao.module.project.controller.admin.workitem.vo.WorkItemCustomFieldValueReqVO;
import cn.iocoder.yudao.module.project.controller.admin.workitem.vo.WorkItemCustomFieldValueRespVO;
import cn.iocoder.yudao.module.project.dal.dataobject.workitem.ProjectWorkItemDO;
import cn.iocoder.yudao.module.project.dal.dataobject.workitem.ProjectWorkItemGroupCountResult;
import cn.iocoder.yudao.module.project.dal.dataobject.workitem.ProjectWorkItemGroupField;
import cn.iocoder.yudao.module.project.dal.mysql.workitem.ProjectWorkItemMapper;
import cn.iocoder.yudao.module.project.enums.ProjectWorkItemQueryFieldEnum;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;

import static cn.iocoder.yudao.framework.test.core.util.AssertUtils.assertServiceException;
import static cn.iocoder.yudao.module.project.enums.ErrorCodeConstants.PROJECT_WORK_ITEM_FIELD_NOT_SUPPORTED;
import static cn.iocoder.yudao.module.project.enums.ErrorCodeConstants.PROJECT_WORK_ITEM_FIELD_VALUE_INVALID;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyMap;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class ProjectWorkItemServiceImplTest extends BaseMockitoUnitTest {

    @InjectMocks
    private ProjectWorkItemServiceImpl service;

    @Mock
    private ProjectWorkItemMapper workItemMapper;
    @Mock
    private WorkItemCustomFieldValueService customFieldValueService;
    @Mock
    private ProjectWorkItemQueryConditionService queryConditionService;

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
        when(workItemMapper.selectPage(pageReqVO, (Long) null))
                .thenReturn(new PageResult<>(List.of(first, second), 2L));
        when(customFieldValueService.getValueMap(List.of(1L, 2L)))
                .thenReturn(Map.of(1L, List.of(customField)));

        PageResult<ProjectWorkItemRespVO> result = service.getWorkItemPage(pageReqVO);

        assertEquals(2L, result.getTotal());
        assertEquals(List.of(customField), result.getList().get(0).getCustomFields());
        assertEquals(List.of(), result.getList().get(1).getCustomFields());
    }

    @Test
    void testGetWorkItemPage_normalizeConditions() {
        ProjectWorkItemPageReqVO pageReqVO = new ProjectWorkItemPageReqVO();
        when(workItemMapper.selectPage(pageReqVO, (Long) null)).thenReturn(new PageResult<>(List.of(), 0L));

        service.getWorkItemPage(pageReqVO);

        verify(queryConditionService).normalize(pageReqVO);
    }

    @Test
    void testGetWorkItemGroupCount() {
        ProjectWorkItemGroupCountReqVO reqVO = new ProjectWorkItemGroupCountReqVO();
        reqVO.setGroupField("status");
        ProjectWorkItemGroupField groupField = ProjectWorkItemGroupField.fixed(ProjectWorkItemQueryFieldEnum.STATUS);
        LambdaQueryWrapperX<ProjectWorkItemDO> queryWrapper = new LambdaQueryWrapperX<>();
        ProjectWorkItemGroupCountResult first = new ProjectWorkItemGroupCountResult();
        first.setValue("PENDING");
        first.setCount(3L);
        ProjectWorkItemGroupCountResult empty = new ProjectWorkItemGroupCountResult();
        empty.setValue(null);
        empty.setCount(1L);
        when(queryConditionService.resolveGroupField(reqVO, "status")).thenReturn(groupField);
        when(workItemMapper.buildQueryWrapper(reqVO)).thenReturn(queryWrapper);
        when(workItemMapper.selectGroupCount(queryWrapper, groupField)).thenReturn(List.of(first, empty));

        ProjectWorkItemGroupCountRespVO result = service.getWorkItemGroupCount(reqVO);

        assertEquals("status", result.getGroupField());
        assertEquals(List.of(new ProjectWorkItemGroupCountRespVO.Group("PENDING", 3L),
                new ProjectWorkItemGroupCountRespVO.Group(null, 1L)), result.getGroups());
        verify(queryConditionService).normalize(reqVO);
    }

    @Test
    @SuppressWarnings({"unchecked", "rawtypes"})
    void testUpdateWorkItemField_customFieldsBatch() {
        ProjectWorkItemDO workItem = buildWorkItem(1L);
        when(workItemMapper.selectById(1L)).thenReturn(workItem);
        ProjectWorkItemUpdateFieldReqVO reqVO = buildUpdateReq(1L,
                buildFieldValue("custom:10", "\"产品需求\""),
                buildFieldValue("custom:11", "true"));

        service.updateWorkItemField(reqVO);

        ArgumentCaptor<List<WorkItemCustomFieldValueReqVO>> captor = ArgumentCaptor.forClass(List.class);
        verify(customFieldValueService).updateValues(eq(1L), eq(100L), eq("REQUIREMENT"), captor.capture());
        assertEquals(List.of(10L, 11L), captor.getValue().stream()
                .map(WorkItemCustomFieldValueReqVO::getFieldId).toList());
        assertEquals("产品需求", captor.getValue().get(0).getValue().asText());
        assertTrue(captor.getValue().get(1).getValue().asBoolean());
    }

    @Test
    void testUpdateWorkItemField_builtInAndCustomFields() {
        ProjectWorkItemDO workItem = buildWorkItem(1L);
        when(workItemMapper.selectById(1L)).thenReturn(workItem);
        ProjectWorkItemUpdateFieldReqVO reqVO = buildUpdateReq(1L,
                buildFieldValue("priority", "\"HIGH\""),
                buildFieldValue("custom:10", "\"产品需求\""));

        service.updateWorkItemField(reqVO);

        verify(workItemMapper).updateFieldsById(eq(1L), anyMap(), any(LocalDateTime.class));
        verify(customFieldValueService).updateValues(eq(1L), eq(100L), eq("REQUIREMENT"), any());
    }

    @Test
    void testUpdateWorkItemField_invalidCustomFieldCodeBeforeWrite() {
        ProjectWorkItemDO workItem = buildWorkItem(1L);
        when(workItemMapper.selectById(1L)).thenReturn(workItem);
        ProjectWorkItemUpdateFieldReqVO reqVO = buildUpdateReq(1L,
                buildFieldValue("priority", "\"HIGH\""),
                buildFieldValue("custom:invalid", "\"产品需求\""));

        assertServiceException(() -> service.updateWorkItemField(reqVO),
                PROJECT_WORK_ITEM_FIELD_NOT_SUPPORTED, "custom:invalid");

        verify(workItemMapper, never()).updateFieldsById(any(), anyMap(), any(LocalDateTime.class));
        verify(customFieldValueService, never()).updateValues(any(), any(), any(), any());
    }

    @Test
    void testUpdateWorkItemField_duplicateCustomFieldRejected() {
        ProjectWorkItemDO workItem = buildWorkItem(1L);
        when(workItemMapper.selectById(1L)).thenReturn(workItem);
        ProjectWorkItemUpdateFieldReqVO reqVO = buildUpdateReq(1L,
                buildFieldValue("custom:10", "\"产品需求\""),
                buildFieldValue("custom:10", "\"技术需求\""));

        assertServiceException(() -> service.updateWorkItemField(reqVO),
                PROJECT_WORK_ITEM_FIELD_VALUE_INVALID, "custom:10");

        verify(customFieldValueService, never()).updateValues(any(), any(), any(), any());
    }

    private ProjectWorkItemDO buildWorkItem(Long id) {
        ProjectWorkItemDO workItem = new ProjectWorkItemDO();
        workItem.setId(id);
        workItem.setTitle("工作项" + id);
        workItem.setProjectId(100L);
        workItem.setCategory("REQUIREMENT");
        workItem.setPriority("LOW");
        return workItem;
    }

    private ProjectWorkItemUpdateFieldReqVO buildUpdateReq(
            Long id, ProjectWorkItemUpdateFieldReqVO.FieldValue... fields) {
        ProjectWorkItemUpdateFieldReqVO reqVO = new ProjectWorkItemUpdateFieldReqVO();
        reqVO.setId(id);
        reqVO.setFields(List.of(fields));
        return reqVO;
    }

    private ProjectWorkItemUpdateFieldReqVO.FieldValue buildFieldValue(String field, String jsonValue) {
        ProjectWorkItemUpdateFieldReqVO.FieldValue value = new ProjectWorkItemUpdateFieldReqVO.FieldValue();
        value.setField(field);
        value.setValue(JsonUtils.parseTree(jsonValue));
        return value;
    }

    private WorkItemCustomFieldValueRespVO buildCustomField(Long fieldId) {
        WorkItemCustomFieldValueRespVO customField = new WorkItemCustomFieldValueRespVO();
        customField.setFieldId(fieldId);
        return customField;
    }

}
