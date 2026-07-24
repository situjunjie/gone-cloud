package cn.iocoder.yudao.module.project.service.workitem;

import cn.iocoder.yudao.framework.common.pojo.CommonResult;
import cn.iocoder.yudao.framework.common.pojo.PageResult;
import cn.iocoder.yudao.framework.test.core.ut.BaseMockitoUnitTest;
import cn.iocoder.yudao.module.project.controller.admin.workitem.vo.WorkItemChangeLogPageReqVO;
import cn.iocoder.yudao.module.project.controller.admin.workitem.vo.WorkItemChangeLogRespVO;
import cn.iocoder.yudao.module.project.controller.admin.workitem.vo.WorkItemCustomFieldValueRespVO;
import cn.iocoder.yudao.module.project.controller.admin.workitem.vo.WorkItemFieldRespVO;
import cn.iocoder.yudao.module.project.dal.dataobject.workitem.ProjectWorkItemDO;
import cn.iocoder.yudao.module.project.enums.WorkItemFieldTypeEnum;
import cn.iocoder.yudao.module.system.api.logger.OperateLogApi;
import cn.iocoder.yudao.module.system.api.logger.dto.OperateLogPageReqDTO;
import cn.iocoder.yudao.module.system.api.logger.dto.OperateLogRespDTO;
import cn.iocoder.yudao.module.system.api.user.AdminUserApi;
import cn.iocoder.yudao.module.system.api.user.dto.AdminUserRespDTO;
import com.mzt.logapi.context.LogRecordContext;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;

import static cn.iocoder.yudao.module.project.enums.LogRecordConstants.PROJECT_WORK_ITEM_TYPE;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class WorkItemChangeLogServiceImplTest extends BaseMockitoUnitTest {

    @InjectMocks
    private WorkItemChangeLogServiceImpl service;

    @Mock
    private WorkItemCustomFieldValueService customFieldValueService;
    @Mock
    private OperateLogApi operateLogApi;
    @Mock
    private AdminUserApi adminUserApi;

    @BeforeEach
    void setUpContext() {
        LogRecordContext.putEmptySpan();
    }

    @AfterEach
    void tearDownContext() {
        LogRecordContext.clear();
        LogRecordContext.clearGlobal();
    }

    @Test
    void testRecord_priorityChange() {
        ProjectWorkItemDO before = buildWorkItem("LOW");
        ProjectWorkItemDO after = buildWorkItem("HIGH");
        service.initContext();

        service.record(new WorkItemChangeLogService.Snapshot(before, Map.of()),
                new WorkItemChangeLogService.Snapshot(after, Map.of()));

        WorkItemChangeLogServiceImpl.WorkItemChangeContext context =
                (WorkItemChangeLogServiceImpl.WorkItemChangeContext) LogRecordContext.getVariable("workItemChange");
        assertTrue(context.isChanged());
        assertTrue(context.getSummary().contains("【优先级】从【低】修改为【高】"));
        assertTrue(context.getExtra().contains("\"field\":\"priority\""));
    }

    @Test
    void testRecord_descriptionChangeHidesRichTextValues() {
        ProjectWorkItemDO before = buildWorkItem("LOW");
        ProjectWorkItemDO after = buildWorkItem("LOW");
        before.setDescription("<p>旧内容</p>");
        after.setDescription("<p><img src=\"data:image/png;base64,long-content\"></p>");
        service.initContext();

        service.record(new WorkItemChangeLogService.Snapshot(before, Map.of()),
                new WorkItemChangeLogService.Snapshot(after, Map.of()));

        WorkItemChangeLogServiceImpl.WorkItemChangeContext context =
                (WorkItemChangeLogServiceImpl.WorkItemChangeContext) LogRecordContext.getVariable("workItemChange");
        assertTrue(context.isChanged());
        assertTrue(context.getSummary().contains("【描述】发生了修改"));
        assertFalse(context.getSummary().contains("旧内容"));
        assertFalse(context.getSummary().contains("data:image"));
        assertTrue(context.getExtra().contains("\"field\":\"description\""));
        assertTrue(context.getExtra().contains("\"name\":\"描述\""));
        assertFalse(context.getExtra().contains("旧内容"));
        assertFalse(context.getExtra().contains("data:image"));
    }

    @Test
    void testRecord_customOptionReorderDoesNotCreateChange() {
        ProjectWorkItemDO workItem = buildWorkItem("LOW");
        WorkItemCustomFieldValueRespVO beforeValue = buildSelectValue(10L, 1L, 2L);
        WorkItemCustomFieldValueRespVO afterValue = buildSelectValue(10L, 2L, 1L);
        service.initContext();

        service.record(new WorkItemChangeLogService.Snapshot(workItem, Map.of(10L, beforeValue)),
                new WorkItemChangeLogService.Snapshot(workItem, Map.of(10L, afterValue)));

        WorkItemChangeLogServiceImpl.WorkItemChangeContext context =
                (WorkItemChangeLogServiceImpl.WorkItemChangeContext) LogRecordContext.getVariable("workItemChange");
        assertFalse(context.isChanged());
    }

    @Test
    void testRecord_assigneeChangeUsesUserName() {
        ProjectWorkItemDO before = buildWorkItem("LOW");
        ProjectWorkItemDO after = buildWorkItem("LOW");
        after.setAssigneeUserId(11L);
        when(adminUserApi.getUserMap(java.util.Set.of(10L, 11L))).thenReturn(Map.of(
                10L, buildUser(10L, "张三"), 11L, buildUser(11L, "李四")));
        service.initContext();

        service.record(new WorkItemChangeLogService.Snapshot(before, Map.of()),
                new WorkItemChangeLogService.Snapshot(after, Map.of()));

        WorkItemChangeLogServiceImpl.WorkItemChangeContext context =
                (WorkItemChangeLogServiceImpl.WorkItemChangeContext) LogRecordContext.getVariable("workItemChange");
        assertTrue(context.getSummary().contains("【负责人】从【张三】修改为【李四】"));
    }

    @Test
    void testRecord_customUserChangeUsesUserName() {
        ProjectWorkItemDO workItem = buildWorkItem("LOW");
        WorkItemCustomFieldValueRespVO beforeValue = buildUserValue(10L, 10L);
        WorkItemCustomFieldValueRespVO afterValue = buildUserValue(10L, 11L);
        when(adminUserApi.getUserMap(java.util.Set.of(10L, 11L))).thenReturn(Map.of(
                10L, buildUser(10L, "张三"), 11L, buildUser(11L, "李四")));
        service.initContext();

        service.record(new WorkItemChangeLogService.Snapshot(workItem, Map.of(10L, beforeValue)),
                new WorkItemChangeLogService.Snapshot(workItem, Map.of(10L, afterValue)));

        WorkItemChangeLogServiceImpl.WorkItemChangeContext context =
                (WorkItemChangeLogServiceImpl.WorkItemChangeContext) LogRecordContext.getVariable("workItemChange");
        assertTrue(context.getSummary().contains("【验收人】从【张三】修改为【李四】"));
    }

    @Test
    void testCapture_customFieldFailureFallsBackToBuiltInFields() {
        ProjectWorkItemDO before = buildWorkItem("LOW");
        ProjectWorkItemDO after = buildWorkItem("HIGH");
        when(customFieldValueService.getValueMap(List.of(1L))).thenThrow(new IllegalStateException("mock"));
        WorkItemChangeLogService.Snapshot beforeSnapshot = service.capture(before, true);
        WorkItemChangeLogService.Snapshot afterSnapshot = service.capture(after, true);

        service.initContext();
        service.record(beforeSnapshot, afterSnapshot);

        WorkItemChangeLogServiceImpl.WorkItemChangeContext context =
                (WorkItemChangeLogServiceImpl.WorkItemChangeContext) LogRecordContext.getVariable("workItemChange");
        assertTrue(context.isChanged());
        assertTrue(context.getSummary().contains("【优先级】从【低】修改为【高】"));
    }

    @Test
    void testGetPage() {
        WorkItemChangeLogPageReqVO reqVO = new WorkItemChangeLogPageReqVO();
        reqVO.setWorkItemId(1L);
        reqVO.setPageNo(2);
        reqVO.setPageSize(10);
        OperateLogRespDTO log = new OperateLogRespDTO();
        log.setId(100L);
        log.setUserId(200L);
        log.setUserName("张三");
        log.setSubType("更新工作项");
        log.setAction("更新了工作项");
        log.setCreateTime(LocalDateTime.now());
        log.setExtra("{\"schemaVersion\":1,\"changes\":[{\"field\":\"priority\",\"name\":\"优先级\"}]}");
        when(operateLogApi.getOperateLogPage(any())).thenReturn(
                CommonResult.success(new PageResult<>(List.of(log), 1L)));

        PageResult<WorkItemChangeLogRespVO> result = service.getPage(reqVO);

        assertEquals(1L, result.getTotal());
        assertEquals("张三", result.getList().get(0).getUserName());
        assertEquals("priority", result.getList().get(0).getChanges().get(0).getField());
        ArgumentCaptor<OperateLogPageReqDTO> captor = ArgumentCaptor.forClass(OperateLogPageReqDTO.class);
        verify(operateLogApi).getOperateLogPage(captor.capture());
        assertEquals(PROJECT_WORK_ITEM_TYPE, captor.getValue().getType());
        assertEquals(1L, captor.getValue().getBizId());
        assertEquals(2, captor.getValue().getPageNo());
        assertEquals(10, captor.getValue().getPageSize());
    }

    @Test
    void testGetTimelinePage_enablesCreateTimeOrder() {
        WorkItemChangeLogPageReqVO reqVO = new WorkItemChangeLogPageReqVO();
        reqVO.setWorkItemId(1L);
        reqVO.setPageSize(20);
        when(operateLogApi.getOperateLogTimelinePage(any())).thenReturn(
                CommonResult.success(PageResult.empty()));

        service.getTimelinePage(reqVO);

        ArgumentCaptor<OperateLogPageReqDTO> captor = ArgumentCaptor.forClass(OperateLogPageReqDTO.class);
        verify(operateLogApi).getOperateLogTimelinePage(captor.capture());
        assertEquals(PROJECT_WORK_ITEM_TYPE, captor.getValue().getType());
        assertEquals(1L, captor.getValue().getBizId());
    }

    @Test
    void testGetPage_unknownSchemaVersionReturnsEmptyChanges() {
        WorkItemChangeLogPageReqVO reqVO = new WorkItemChangeLogPageReqVO();
        reqVO.setWorkItemId(1L);
        OperateLogRespDTO log = new OperateLogRespDTO();
        log.setExtra("{\"schemaVersion\":2,\"changes\":[{\"field\":\"priority\"}]}");
        when(operateLogApi.getOperateLogPage(any())).thenReturn(
                CommonResult.success(new PageResult<>(List.of(log), 1L)));

        PageResult<WorkItemChangeLogRespVO> result = service.getPage(reqVO);

        assertTrue(result.getList().get(0).getChanges().isEmpty());
    }

    private ProjectWorkItemDO buildWorkItem(String priority) {
        ProjectWorkItemDO workItem = new ProjectWorkItemDO();
        workItem.setId(1L);
        workItem.setTitle("登录异常");
        workItem.setCategory("BUG");
        workItem.setPriority(priority);
        workItem.setStatus("PENDING");
        workItem.setAssigneeUserId(10L);
        workItem.setProjectId(20L);
        return workItem;
    }

    private WorkItemCustomFieldValueRespVO buildSelectValue(Long fieldId, Long... optionIds) {
        WorkItemCustomFieldValueRespVO value = new WorkItemCustomFieldValueRespVO();
        value.setFieldId(fieldId);
        value.setName("影响版本");
        value.setSelectedOptions(java.util.Arrays.stream(optionIds).map(id -> {
            WorkItemFieldRespVO.Option option = new WorkItemFieldRespVO.Option();
            option.setId(id);
            option.setName("V" + id);
            return option;
        }).toList());
        return value;
    }

    private WorkItemCustomFieldValueRespVO buildUserValue(Long fieldId, Long userId) {
        WorkItemCustomFieldValueRespVO value = new WorkItemCustomFieldValueRespVO();
        value.setFieldId(fieldId);
        value.setName("验收人");
        value.setFieldType(WorkItemFieldTypeEnum.USER.getValue());
        value.setValue(userId);
        return value;
    }

    private AdminUserRespDTO buildUser(Long id, String nickname) {
        AdminUserRespDTO user = new AdminUserRespDTO();
        user.setId(id);
        user.setNickname(nickname);
        return user;
    }

}
