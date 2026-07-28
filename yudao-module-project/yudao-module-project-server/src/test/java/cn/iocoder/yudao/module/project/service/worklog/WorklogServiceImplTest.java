package cn.iocoder.yudao.module.project.service.worklog;

import cn.iocoder.yudao.framework.common.pojo.CommonResult;
import cn.iocoder.yudao.framework.security.core.LoginUser;
import cn.iocoder.yudao.framework.test.core.ut.BaseMockitoUnitTest;
import cn.iocoder.yudao.module.project.controller.admin.worklog.vo.WorklogCreateReqVO;
import cn.iocoder.yudao.module.project.controller.admin.worklog.vo.WorklogUpdateReqVO;
import cn.iocoder.yudao.module.project.dal.dataobject.workitem.ProjectWorkItemDO;
import cn.iocoder.yudao.module.project.dal.dataobject.worklog.WorklogDO;
import cn.iocoder.yudao.module.project.dal.mysql.workitem.ProjectWorkItemMapper;
import cn.iocoder.yudao.module.project.dal.mysql.worklog.WorklogMapper;
import cn.iocoder.yudao.module.project.service.project.ProjectMemberService;
import cn.iocoder.yudao.module.project.service.project.ProjectPermissionService;
import cn.iocoder.yudao.module.project.service.project.ProjectService;
import cn.iocoder.yudao.module.system.api.dict.DictDataApi;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;

import static cn.iocoder.yudao.framework.test.core.util.AssertUtils.assertServiceException;
import static cn.iocoder.yudao.module.project.enums.ErrorCodeConstants.PROJECT_WORKLOG_ACTUAL_DATE_REQUIRED;
import static cn.iocoder.yudao.module.project.enums.ErrorCodeConstants.PROJECT_WORKLOG_OPERATION_DENIED;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class WorklogServiceImplTest extends BaseMockitoUnitTest {

    @InjectMocks
    private WorklogServiceImpl service;
    @Mock
    private WorklogMapper worklogMapper;
    @Mock
    private ProjectWorkItemMapper workItemMapper;
    @Mock
    private ProjectService projectService;
    @Mock
    private ProjectMemberService projectMemberService;
    @Mock
    private ProjectPermissionService projectPermissionService;
    @Mock
    private DictDataApi dictDataApi;

    @BeforeEach
    void setUp() {
        LoginUser user = new LoginUser();
        user.setId(10L);
        SecurityContextHolder.getContext().setAuthentication(
                new UsernamePasswordAuthenticationToken(user, "password", List.of()));
    }

    @AfterEach
    void tearDown() {
        SecurityContextHolder.clearContext();
    }

    @Test
    void testCreateOwnActualWorklog() {
        ProjectWorkItemDO workItem = workItem();
        when(workItemMapper.selectById(1L)).thenReturn(workItem);
        when(projectPermissionService.hasPermission(100L, 10L, "WORKLOG_MANAGE")).thenReturn(false);
        when(projectMemberService.isProjectMember(100L, 10L)).thenReturn(true);
        when(dictDataApi.validateDictDataList("project_worklog_category", List.of("DEVELOPMENT")))
                .thenReturn(CommonResult.success(true));
        doAnswer(invocation -> {
            ((WorklogDO) invocation.getArgument(0)).setId(9L);
            return 1;
        }).when(worklogMapper).insert(any(WorklogDO.class));

        WorklogCreateReqVO reqVO = createReqVO();
        assertEquals(9L, service.createWorklog(reqVO));

        verify(projectPermissionService).validatePermission(100L, 10L, "WORKLOG_CREATE");
        ArgumentCaptor<WorklogDO> captor = ArgumentCaptor.forClass(WorklogDO.class);
        verify(worklogMapper).insert(captor.capture());
        assertEquals(100L, captor.getValue().getProjectId());
        assertEquals(10L, captor.getValue().getOwnerUserId());
    }

    @Test
    void testCreateRejectsMissingActualDateInService() {
        when(workItemMapper.selectById(1L)).thenReturn(workItem());
        WorklogCreateReqVO reqVO = createReqVO();
        reqVO.setWorkDate(null);

        assertServiceException(() -> service.createWorklog(reqVO), PROJECT_WORKLOG_ACTUAL_DATE_REQUIRED);
        verify(worklogMapper, never()).insert(any(WorklogDO.class));
    }

    @Test
    void testUpdateRejectsOtherOwnersRecordWithoutManagePermission() {
        WorklogDO worklog = new WorklogDO();
        worklog.setId(9L);
        worklog.setProjectId(100L);
        worklog.setOwnerUserId(20L);
        worklog.setType("ACTUAL");
        when(worklogMapper.selectById(9L)).thenReturn(worklog);
        when(projectPermissionService.hasPermission(100L, 10L, "WORKLOG_MANAGE")).thenReturn(false);
        WorklogUpdateReqVO reqVO = new WorklogUpdateReqVO();
        reqVO.setId(9L);
        reqVO.setHours(BigDecimal.ONE);
        reqVO.setCategory("DEVELOPMENT");
        reqVO.setWorkDate(LocalDate.now());

        assertServiceException(() -> service.updateWorklog(reqVO), PROJECT_WORKLOG_OPERATION_DENIED);
        verify(worklogMapper, never()).updateById(any(WorklogDO.class));
    }

    private ProjectWorkItemDO workItem() {
        ProjectWorkItemDO workItem = new ProjectWorkItemDO();
        workItem.setId(1L);
        workItem.setProjectId(100L);
        return workItem;
    }

    private WorklogCreateReqVO createReqVO() {
        WorklogCreateReqVO reqVO = new WorklogCreateReqVO();
        reqVO.setWorkItemId(1L);
        reqVO.setType("ACTUAL");
        reqVO.setHours(new BigDecimal("8.25"));
        reqVO.setCategory("DEVELOPMENT");
        reqVO.setWorkDate(LocalDate.of(2026, 7, 28));
        return reqVO;
    }

}
