package cn.iocoder.yudao.module.project.framework.security;

import cn.iocoder.yudao.framework.security.core.LoginUser;
import cn.iocoder.yudao.framework.test.core.ut.BaseMockitoUnitTest;
import cn.iocoder.yudao.module.project.dal.dataobject.workitem.ProjectWorkItemDO;
import cn.iocoder.yudao.module.project.dal.mysql.workitem.ProjectWorkItemMapper;
import cn.iocoder.yudao.module.project.dal.dataobject.worklog.WorklogDO;
import cn.iocoder.yudao.module.project.dal.mysql.worklog.WorklogMapper;
import cn.iocoder.yudao.module.project.service.project.ProjectPermissionService;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class ProjectSecurityFrameworkServiceTest extends BaseMockitoUnitTest {

    @InjectMocks
    private ProjectSecurityFrameworkService service;

    @Mock
    private ProjectPermissionService permissionService;
    @Mock
    private ProjectWorkItemMapper workItemMapper;
    @Mock
    private WorklogMapper worklogMapper;

    @BeforeEach
    void setUp() {
        LoginUser loginUser = new LoginUser();
        loginUser.setId(10L);
        SecurityContextHolder.getContext().setAuthentication(
                new UsernamePasswordAuthenticationToken(loginUser, "password", List.of()));
    }

    @AfterEach
    void tearDown() {
        SecurityContextHolder.clearContext();
    }

    @Test
    void testHasWorkItemPermissionsRejectsInvalidTargetOrPermissionList() {
        assertFalse(service.hasWorkItemPermissions(null, "WORK_ITEM_QUERY"));
        verify(workItemMapper, never()).selectById(null);

        when(workItemMapper.selectById(1L)).thenReturn(null);
        ProjectWorkItemDO workItem = new ProjectWorkItemDO();
        workItem.setProjectId(100L);
        when(workItemMapper.selectById(2L)).thenReturn(workItem);
        when(workItemMapper.selectById(3L)).thenReturn(workItem);
        assertFalse(service.hasWorkItemPermissions(1L, "WORK_ITEM_QUERY"));
        assertFalse(service.hasWorkItemPermissions(2L, (String[]) null));
        assertFalse(service.hasWorkItemPermissions(3L));

        verify(workItemMapper).selectById(1L);
        verify(workItemMapper).selectById(2L);
        verify(workItemMapper).selectById(3L);
        verify(permissionService, never()).hasPermission(100L, 10L, "WORK_ITEM_QUERY");
    }

    @Test
    void testHasWorkItemPermissionsLoadsWorkItemOnceAndRequiresEveryPermission() {
        ProjectWorkItemDO workItem = new ProjectWorkItemDO();
        workItem.setProjectId(100L);
        when(workItemMapper.selectById(1L)).thenReturn(workItem);
        when(permissionService.hasPermission(100L, 10L, "WORK_ITEM_QUERY")).thenReturn(true);
        when(permissionService.hasPermission(100L, 10L, "COMMENT_QUERY")).thenReturn(true);

        assertTrue(service.hasWorkItemPermissions(1L, "WORK_ITEM_QUERY", "COMMENT_QUERY"));

        verify(workItemMapper, times(1)).selectById(1L);
        verify(permissionService).hasPermission(100L, 10L, "WORK_ITEM_QUERY");
        verify(permissionService).hasPermission(100L, 10L, "COMMENT_QUERY");
    }

    @Test
    void testHasWorkItemPermissionsShortCircuitsOnFirstDeniedPermission() {
        ProjectWorkItemDO workItem = new ProjectWorkItemDO();
        workItem.setProjectId(100L);
        when(workItemMapper.selectById(1L)).thenReturn(workItem);
        when(permissionService.hasPermission(100L, 10L, "WORK_ITEM_QUERY")).thenReturn(false);

        assertFalse(service.hasWorkItemPermissions(1L, "WORK_ITEM_QUERY", "COMMENT_QUERY"));

        verify(permissionService).hasPermission(100L, 10L, "WORK_ITEM_QUERY");
        verify(permissionService, never()).hasPermission(100L, 10L, "COMMENT_QUERY");
    }

    @Test
    void testHasWorkItemPermissionsRejectsLaterDeniedPermission() {
        ProjectWorkItemDO workItem = new ProjectWorkItemDO();
        workItem.setProjectId(100L);
        when(workItemMapper.selectById(1L)).thenReturn(workItem);
        when(permissionService.hasPermission(100L, 10L, "WORK_ITEM_QUERY")).thenReturn(true);
        when(permissionService.hasPermission(100L, 10L, "COMMENT_QUERY")).thenReturn(false);

        assertFalse(service.hasWorkItemPermissions(1L, "WORK_ITEM_QUERY", "COMMENT_QUERY"));

        verify(permissionService).hasPermission(100L, 10L, "WORK_ITEM_QUERY");
        verify(permissionService).hasPermission(100L, 10L, "COMMENT_QUERY");
    }

    @Test
    void testCanManageWorklogForOwnerOrManagerUsingPersistedProject() {
        WorklogDO worklog = new WorklogDO();
        worklog.setProjectId(100L);
        worklog.setOwnerUserId(10L);
        when(worklogMapper.selectById(1L)).thenReturn(worklog);
        when(permissionService.hasPermission(100L, 10L, "WORKLOG_MANAGE")).thenReturn(false);
        when(permissionService.hasPermission(100L, 10L, "WORKLOG_CREATE")).thenReturn(true);
        assertTrue(service.canManageWorklog(1L));

        worklog.setOwnerUserId(20L);
        when(permissionService.hasPermission(100L, 10L, "WORKLOG_MANAGE")).thenReturn(true);
        assertTrue(service.canManageWorklog(1L));
    }

}
