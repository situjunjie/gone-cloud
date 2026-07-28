package cn.iocoder.yudao.module.project.service.worklog;

import cn.iocoder.yudao.framework.common.pojo.CommonResult;
import cn.iocoder.yudao.framework.security.core.LoginUser;
import cn.iocoder.yudao.framework.test.core.ut.BaseMockitoUnitTest;
import cn.iocoder.yudao.framework.tenant.core.context.TenantContextHolder;
import cn.iocoder.yudao.module.project.controller.admin.worklog.vo.WorklogCreateReqVO;
import cn.iocoder.yudao.module.project.controller.admin.worklog.vo.WorklogPageReqVO;
import cn.iocoder.yudao.module.project.controller.admin.worklog.vo.WorklogSummaryReqVO;
import cn.iocoder.yudao.module.project.controller.admin.worklog.vo.WorklogSummaryRespVO;
import cn.iocoder.yudao.module.project.controller.admin.worklog.vo.WorklogUpdateReqVO;
import cn.iocoder.yudao.module.project.dal.dataobject.workitem.ProjectWorkItemDO;
import cn.iocoder.yudao.module.project.dal.dataobject.worklog.WorklogDO;
import cn.iocoder.yudao.module.project.dal.dataobject.worklog.WorklogSummaryResult;
import cn.iocoder.yudao.module.project.dal.mysql.workitem.ProjectWorkItemMapper;
import cn.iocoder.yudao.module.project.dal.mysql.worklog.WorklogMapper;
import cn.iocoder.yudao.module.project.service.project.ProjectMemberService;
import cn.iocoder.yudao.module.project.service.project.ProjectPermissionService;
import cn.iocoder.yudao.module.project.service.project.ProjectService;
import cn.iocoder.yudao.module.system.api.dict.DictDataApi;
import cn.iocoder.yudao.framework.common.pojo.PageResult;
import cn.iocoder.yudao.framework.mybatis.core.query.LambdaQueryWrapperX;
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
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
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
        TenantContextHolder.clear();
    }

    @Test
    void testCreateOwnActualWorklog() {
        ProjectWorkItemDO workItem = workItem();
        when(workItemMapper.selectByIdForUpdate(1L)).thenReturn(workItem);
        when(projectPermissionService.hasPermission(100L, 10L, "WORKLOG_MANAGE")).thenReturn(false);
        when(projectMemberService.isProjectMember(100L, 10L)).thenReturn(true);
        when(dictDataApi.validateDictDataList("project_worklog_category", List.of("DEVELOPMENT")))
                .thenReturn(CommonResult.success(true));
        doAnswer(invocation -> {
            ((WorklogDO) invocation.getArgument(0)).setId(9L);
            return 1;
        }).when(worklogMapper).insert(any(WorklogDO.class));
        when(workItemMapper.addWorklogHours(any(), any(), any())).thenReturn(1);

        WorklogCreateReqVO reqVO = createReqVO();
        assertEquals(9L, service.createWorklog(reqVO));

        verify(projectPermissionService).validatePermission(100L, 10L, "WORKLOG_CREATE");
        ArgumentCaptor<WorklogDO> captor = ArgumentCaptor.forClass(WorklogDO.class);
        verify(worklogMapper).insert(captor.capture());
        assertEquals(100L, captor.getValue().getProjectId());
        assertEquals(10L, captor.getValue().getOwnerUserId());
        verify(workItemMapper).addWorklogHours(1L, cn.iocoder.yudao.module.project.enums.WorklogTypeEnum.ACTUAL,
                new BigDecimal("8.25"));
    }

    @Test
    void testCreateRejectsMissingActualDateInService() {
        when(workItemMapper.selectByIdForUpdate(1L)).thenReturn(workItem());
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
        worklog.setWorkItemId(1L);
        worklog.setOwnerUserId(20L);
        worklog.setType("ACTUAL");
        when(worklogMapper.selectById(9L)).thenReturn(worklog);
        when(worklogMapper.selectByIdForUpdate(9L)).thenReturn(worklog);
        when(workItemMapper.selectByIdForUpdate(1L)).thenReturn(workItem());
        when(projectPermissionService.hasPermission(100L, 10L, "WORKLOG_MANAGE")).thenReturn(false);
        WorklogUpdateReqVO reqVO = new WorklogUpdateReqVO();
        reqVO.setId(9L);
        reqVO.setHours(BigDecimal.ONE);
        reqVO.setCategory("DEVELOPMENT");
        reqVO.setWorkDate(LocalDate.now());

        assertServiceException(() -> service.updateWorklog(reqVO), PROJECT_WORKLOG_OPERATION_DENIED);
        verify(worklogMapper, never()).updateById(any(WorklogDO.class));
    }

    @Test
    void testUpdateAndDeleteMaintainMaterializedCounterDeltas() {
        WorklogDO worklog = worklog();
        when(worklogMapper.selectById(9L)).thenReturn(worklog);
        when(worklogMapper.selectByIdForUpdate(9L)).thenReturn(worklog);
        when(workItemMapper.selectByIdForUpdate(1L)).thenReturn(workItem());
        when(projectPermissionService.hasPermission(100L, 10L, "WORKLOG_MANAGE")).thenReturn(true);
        when(projectMemberService.isProjectMember(100L, 10L)).thenReturn(true);
        when(dictDataApi.validateDictDataList("project_worklog_category", List.of("DEVELOPMENT")))
                .thenReturn(CommonResult.success(true));
        when(workItemMapper.addWorklogHours(any(), any(), any())).thenReturn(1);

        WorklogUpdateReqVO updateReqVO = new WorklogUpdateReqVO();
        updateReqVO.setId(9L);
        updateReqVO.setHours(new BigDecimal("5.25"));
        updateReqVO.setCategory("DEVELOPMENT");
        updateReqVO.setWorkDate(LocalDate.of(2026, 7, 28));
        service.updateWorklog(updateReqVO);
        verify(workItemMapper).addWorklogHours(1L, cn.iocoder.yudao.module.project.enums.WorklogTypeEnum.ACTUAL,
                new BigDecimal("-3.00"));

        service.deleteWorklog(9L);
        verify(workItemMapper).addWorklogHours(1L, cn.iocoder.yudao.module.project.enums.WorklogTypeEnum.ACTUAL,
                new BigDecimal("-8.25"));
    }

    @Test
    void testUpdateRevalidatesLockedWorklogBeforeMutation() {
        WorklogDO snapshot = worklog();
        WorklogDO lockedWorklog = worklog();
        lockedWorklog.setOwnerUserId(20L);
        when(worklogMapper.selectById(9L)).thenReturn(snapshot);
        when(worklogMapper.selectByIdForUpdate(9L)).thenReturn(lockedWorklog);
        when(workItemMapper.selectByIdForUpdate(1L)).thenReturn(workItem());
        when(projectPermissionService.hasPermission(100L, 10L, "WORKLOG_MANAGE")).thenReturn(false);
        WorklogUpdateReqVO reqVO = new WorklogUpdateReqVO();
        reqVO.setId(9L);
        reqVO.setHours(BigDecimal.ONE);
        reqVO.setCategory("DEVELOPMENT");
        reqVO.setWorkDate(LocalDate.of(2026, 7, 28));

        assertServiceException(() -> service.updateWorklog(reqVO), PROJECT_WORKLOG_OPERATION_DENIED);

        verify(worklogMapper, never()).updateById(any(WorklogDO.class));
        verify(workItemMapper, never()).addWorklogHours(any(), any(), any());
    }

    @Test
    void testSelfScopeDefaultsToRootForPageAndSummary() {
        WorklogPageReqVO pageReqVO = new WorklogPageReqVO();
        pageReqVO.setProjectId(100L);
        pageReqVO.setWorkItemId(1L);
        pageReqVO.setPageNo(1);
        pageReqVO.setPageSize(10);
        LambdaQueryWrapperX<WorklogDO> pageWrapper = new LambdaQueryWrapperX<>();
        WorklogDO rootWorklog = worklog();
        rootWorklog.setWorkItemId(1L);
        when(worklogMapper.buildQueryWrapper(pageReqVO, List.of(1L))).thenReturn(pageWrapper);
        when(worklogMapper.selectPage(pageReqVO, pageWrapper))
                .thenReturn(new PageResult<>(List.of(rootWorklog), 1L));
        when(workItemMapper.selectBatchIds(List.of(1L))).thenReturn(List.of(workItem()));

        assertEquals(1L, service.getWorklogPage(pageReqVO).getTotal());

        WorklogSummaryReqVO summaryReqVO = new WorklogSummaryReqVO();
        summaryReqVO.setProjectId(100L);
        summaryReqVO.setWorkItemId(1L);
        LambdaQueryWrapperX<WorklogDO> summaryWrapper = new LambdaQueryWrapperX<>();
        WorklogSummaryResult total = new WorklogSummaryResult();
        total.setEstimatedHours(new BigDecimal("3.00"));
        total.setActualHours(new BigDecimal("2.50"));
        when(worklogMapper.buildQueryWrapper(summaryReqVO, List.of(1L))).thenReturn(summaryWrapper);
        when(worklogMapper.selectSummary(summaryWrapper)).thenReturn(total);

        WorklogSummaryRespVO summary = service.getWorklogSummary(summaryReqVO);
        assertEquals(new BigDecimal("3.00"), summary.getEstimatedHours());
        assertEquals(new BigDecimal("2.50"), summary.getActualHours());
        verify(workItemMapper, never()).selectDescendantIds(any(), any(), any());
    }

    @Test
    void testDescendantScopeResolvesOnceAndEmptyDescendantsReturnZero() {
        TenantContextHolder.setTenantId(1L);
        WorklogPageReqVO pageReqVO = new WorklogPageReqVO();
        pageReqVO.setProjectId(100L);
        pageReqVO.setWorkItemId(1L);
        pageReqVO.setWorkItemScope("DESCENDANTS");
        pageReqVO.setPageNo(1);
        pageReqVO.setPageSize(10);
        when(workItemMapper.selectDescendantIds(1L, 100L, 1L)).thenReturn(List.of());

        PageResult<?> page = service.getWorklogPage(pageReqVO);
        assertEquals(0L, page.getTotal());
        assertTrue(page.getList().isEmpty());

        WorklogSummaryReqVO summaryReqVO = new WorklogSummaryReqVO();
        summaryReqVO.setProjectId(100L);
        summaryReqVO.setWorkItemId(1L);
        summaryReqVO.setWorkItemScope("DESCENDANTS");
        summaryReqVO.setGroupBy("WORK_ITEM");
        WorklogSummaryRespVO summary = service.getWorklogSummary(summaryReqVO);
        assertEquals(new BigDecimal("0.00"), summary.getEstimatedHours());
        assertEquals(new BigDecimal("0.00"), summary.getActualHours());
        assertTrue(summary.getGroups().isEmpty());
        verify(worklogMapper, never()).selectSummary(any(LambdaQueryWrapperX.class));
    }

    @Test
    void testDescendantScopeUsesResolvedIdsForPageAndSummary() {
        TenantContextHolder.setTenantId(1L);
        List<Long> descendantIds = List.of(2L, 3L);
        WorklogPageReqVO pageReqVO = new WorklogPageReqVO();
        pageReqVO.setProjectId(100L);
        pageReqVO.setWorkItemId(1L);
        pageReqVO.setWorkItemScope("DESCENDANTS");
        pageReqVO.setPageNo(1);
        pageReqVO.setPageSize(10);
        LambdaQueryWrapperX<WorklogDO> pageWrapper = new LambdaQueryWrapperX<>();
        WorklogDO descendantWorklog = worklog();
        descendantWorklog.setWorkItemId(2L);
        ProjectWorkItemDO descendantWorkItem = workItem();
        descendantWorkItem.setId(2L);
        when(workItemMapper.selectDescendantIds(1L, 100L, 1L)).thenReturn(descendantIds);
        when(worklogMapper.buildQueryWrapper(pageReqVO, descendantIds)).thenReturn(pageWrapper);
        when(worklogMapper.selectPage(pageReqVO, pageWrapper))
                .thenReturn(new PageResult<>(List.of(descendantWorklog), 1L));
        when(workItemMapper.selectBatchIds(List.of(2L))).thenReturn(List.of(descendantWorkItem));

        PageResult<?> page = service.getWorklogPage(pageReqVO);
        assertEquals(1L, page.getTotal());
        verify(workItemMapper).selectDescendantIds(1L, 100L, 1L);

        WorklogSummaryReqVO summaryReqVO = new WorklogSummaryReqVO();
        summaryReqVO.setProjectId(100L);
        summaryReqVO.setWorkItemId(1L);
        summaryReqVO.setWorkItemScope("DESCENDANTS");
        summaryReqVO.setGroupBy("WORK_ITEM");
        LambdaQueryWrapperX<WorklogDO> summaryWrapper = new LambdaQueryWrapperX<>();
        WorklogSummaryResult total = new WorklogSummaryResult();
        total.setActualHours(new BigDecimal("8.25"));
        when(worklogMapper.buildQueryWrapper(summaryReqVO, descendantIds)).thenReturn(summaryWrapper);
        when(worklogMapper.selectSummary(summaryWrapper)).thenReturn(total);
        when(worklogMapper.selectGroupSummary(summaryWrapper,
                cn.iocoder.yudao.module.project.enums.WorklogGroupByEnum.WORK_ITEM)).thenReturn(List.of());

        assertEquals(new BigDecimal("8.25"), service.getWorklogSummary(summaryReqVO).getActualHours());
        verify(workItemMapper, times(2)).selectDescendantIds(1L, 100L, 1L);
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

    private WorklogDO worklog() {
        WorklogDO worklog = new WorklogDO();
        worklog.setId(9L);
        worklog.setProjectId(100L);
        worklog.setWorkItemId(1L);
        worklog.setType("ACTUAL");
        worklog.setHours(new BigDecimal("8.25"));
        worklog.setOwnerUserId(10L);
        return worklog;
    }

}
