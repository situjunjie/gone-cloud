package cn.iocoder.yudao.module.project.service.workitem;

import cn.iocoder.yudao.framework.common.pojo.PageResult;
import cn.iocoder.yudao.framework.test.core.ut.BaseMockitoUnitTest;
import cn.iocoder.yudao.module.project.controller.admin.workitem.vo.ProjectWorkItemPageReqVO;
import cn.iocoder.yudao.module.project.controller.admin.workitem.vo.ProjectWorkItemRespVO;
import cn.iocoder.yudao.module.project.controller.admin.workitem.vo.WorkItemCustomFieldValueRespVO;
import cn.iocoder.yudao.module.project.dal.dataobject.workitem.ProjectWorkItemDO;
import cn.iocoder.yudao.module.project.dal.mysql.workitem.ProjectWorkItemMapper;
import org.junit.jupiter.api.Test;
import org.mockito.InjectMocks;
import org.mockito.Mock;

import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.mockito.Mockito.when;

class ProjectWorkItemServiceImplTest extends BaseMockitoUnitTest {

    @InjectMocks
    private ProjectWorkItemServiceImpl service;

    @Mock
    private ProjectWorkItemMapper workItemMapper;
    @Mock
    private WorkItemCustomFieldValueService customFieldValueService;

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

}
