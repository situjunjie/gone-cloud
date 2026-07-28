package cn.iocoder.yudao.module.project.controller.admin.worklog;

import cn.iocoder.yudao.framework.common.pojo.PageResult;
import cn.iocoder.yudao.framework.security.core.LoginUser;
import cn.iocoder.yudao.framework.security.core.service.SecurityFrameworkService;
import cn.iocoder.yudao.module.project.controller.admin.worklog.vo.WorklogCreateReqVO;
import cn.iocoder.yudao.module.project.controller.admin.worklog.vo.WorklogPageReqVO;
import cn.iocoder.yudao.module.project.controller.admin.worklog.vo.WorklogRespVO;
import cn.iocoder.yudao.module.project.controller.admin.worklog.vo.WorklogSummaryReqVO;
import cn.iocoder.yudao.module.project.controller.admin.worklog.vo.WorklogSummaryRespVO;
import cn.iocoder.yudao.module.project.controller.admin.worklog.vo.WorklogUpdateReqVO;
import cn.iocoder.yudao.module.project.dal.dataobject.workitem.ProjectWorkItemDO;
import cn.iocoder.yudao.module.project.dal.dataobject.worklog.WorklogDO;
import cn.iocoder.yudao.module.project.dal.mysql.comment.CommentMapper;
import cn.iocoder.yudao.module.project.dal.mysql.project.ProjectMemberMapper;
import cn.iocoder.yudao.module.project.dal.mysql.project.ProjectRoleMapper;
import cn.iocoder.yudao.module.project.dal.mysql.workitem.ProjectWorkItemMapper;
import cn.iocoder.yudao.module.project.dal.mysql.workitem.WorkItemFieldMapper;
import cn.iocoder.yudao.module.project.dal.mysql.workitem.WorkItemFieldOptionMapper;
import cn.iocoder.yudao.module.project.dal.mysql.worklog.WorklogMapper;
import cn.iocoder.yudao.module.project.framework.security.ProjectSecurityFrameworkService;
import cn.iocoder.yudao.module.project.service.comment.CommentTargetService;
import cn.iocoder.yudao.module.project.service.project.ProjectPermissionService;
import cn.iocoder.yudao.module.project.service.worklog.WorklogService;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.context.annotation.AnnotationConfigApplicationContext;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.config.annotation.method.configuration.EnableMethodSecurity;
import org.springframework.security.core.context.SecurityContextHolder;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class WorklogControllerMethodSecurityTest {

    private AnnotationConfigApplicationContext context;
    private WorklogController controller;
    private WorklogService worklogService;
    private SecurityFrameworkService systemSecurity;
    private ProjectPermissionService permissionService;
    private ProjectWorkItemMapper workItemMapper;
    private WorklogMapper worklogMapper;

    @BeforeEach
    void setUp() {
        context = new AnnotationConfigApplicationContext(TestConfig.class);
        controller = context.getBean(WorklogController.class);
        worklogService = context.getBean(WorklogService.class);
        systemSecurity = context.getBean(SecurityFrameworkService.class);
        permissionService = context.getBean(ProjectPermissionService.class);
        workItemMapper = context.getBean(ProjectWorkItemMapper.class);
        worklogMapper = context.getBean(WorklogMapper.class);
        LoginUser loginUser = new LoginUser();
        loginUser.setId(10L);
        SecurityContextHolder.getContext().setAuthentication(
                new UsernamePasswordAuthenticationToken(loginUser, "password", List.of()));
    }

    @AfterEach
    void tearDown() {
        SecurityContextHolder.clearContext();
        if (context != null) {
            context.close();
        }
    }

    @Test
    void testCreateSystemPermissionFailureShortCircuitsProjectLookup() {
        WorklogCreateReqVO reqVO = createReqVO();
        when(systemSecurity.hasPermission("project:worklog:create")).thenReturn(false);

        assertThrows(AccessDeniedException.class, () -> controller.createWorklog(reqVO));

        verify(workItemMapper, never()).selectById(any());
        verify(worklogService, never()).createWorklog(reqVO);
    }

    @Test
    void testCreateRequiresProjectPermission() {
        WorklogCreateReqVO reqVO = createReqVO();
        allowSystem("project:worklog:create");
        persistWorkItem(reqVO.getWorkItemId(), 100L);

        assertThrows(AccessDeniedException.class, () -> controller.createWorklog(reqVO));

        verify(permissionService).hasPermission(100L, 10L, "WORKLOG_CREATE");
        verify(permissionService).hasPermission(100L, 10L, "WORKLOG_MANAGE");
        verify(worklogService, never()).createWorklog(reqVO);
    }

    @Test
    void testCreateAllowsWorklogCreatePermission() {
        WorklogCreateReqVO reqVO = createReqVO();
        allowSystem("project:worklog:create");
        persistWorkItem(reqVO.getWorkItemId(), 100L);
        when(permissionService.hasPermission(100L, 10L, "WORKLOG_CREATE")).thenReturn(true);
        when(worklogService.createWorklog(reqVO)).thenReturn(8L);

        assertEquals(8L, controller.createWorklog(reqVO).getData());

        verify(permissionService, never()).hasPermission(100L, 10L, "WORKLOG_MANAGE");
        verify(worklogService).createWorklog(reqVO);
    }

    @Test
    void testCreateAllowsWorklogManagePermission() {
        WorklogCreateReqVO reqVO = createReqVO();
        allowSystem("project:worklog:create");
        persistWorkItem(reqVO.getWorkItemId(), 100L);
        when(permissionService.hasPermission(100L, 10L, "WORKLOG_MANAGE")).thenReturn(true);
        when(worklogService.createWorklog(reqVO)).thenReturn(9L);

        assertEquals(9L, controller.createWorklog(reqVO).getData());

        verify(worklogService).createWorklog(reqVO);
    }

    @Test
    void testUpdateAllowsPersistedOwnerWithCreatePermission() {
        WorklogUpdateReqVO reqVO = updateReqVO();
        allowSystem("project:worklog:update");
        persistWorklog(reqVO.getId(), 100L, 10L);
        when(permissionService.hasPermission(100L, 10L, "WORKLOG_CREATE")).thenReturn(true);

        assertEquals(Boolean.TRUE, controller.updateWorklog(reqVO).getData());

        verify(worklogMapper).selectById(reqVO.getId());
        verify(worklogService).updateWorklog(reqVO);
    }

    @Test
    void testDeleteAllowsManagerForPersistedOtherOwner() {
        allowSystem("project:worklog:delete");
        persistWorklog(1L, 100L, 20L);
        when(permissionService.hasPermission(100L, 10L, "WORKLOG_MANAGE")).thenReturn(true);

        assertEquals(Boolean.TRUE, controller.deleteWorklog(1L).getData());

        verify(worklogMapper).selectById(1L);
        verify(worklogService).deleteWorklog(1L);
    }

    @Test
    void testUpdateRejectsOtherOwnerWithoutManagePermission() {
        WorklogUpdateReqVO reqVO = updateReqVO();
        allowSystem("project:worklog:update");
        persistWorklog(reqVO.getId(), 100L, 20L);

        assertThrows(AccessDeniedException.class, () -> controller.updateWorklog(reqVO));

        verify(worklogService, never()).updateWorklog(reqVO);
    }

    @Test
    void testGetAllowsQueryAgainstPersistedWorklogProject() {
        allowSystem("project:worklog:query");
        persistWorklog(1L, 100L, 20L);
        when(permissionService.hasPermission(100L, 10L, "WORKLOG_QUERY")).thenReturn(true);
        WorklogRespVO response = new WorklogRespVO();
        when(worklogService.getWorklog(1L)).thenReturn(response);

        assertEquals(response, controller.getWorklog(1L).getData());

        verify(worklogService).getWorklog(1L);
    }

    @Test
    void testPageAllowsProjectQueryPermission() {
        WorklogPageReqVO reqVO = new WorklogPageReqVO();
        reqVO.setProjectId(100L);
        allowSystem("project:worklog:query");
        when(permissionService.hasPermission(100L, 10L, "WORKLOG_QUERY")).thenReturn(true);
        PageResult<WorklogRespVO> response = new PageResult<>(List.of(), 0L);
        when(worklogService.getWorklogPage(reqVO)).thenReturn(response);

        assertEquals(response, controller.getWorklogPage(reqVO).getData());

        verify(worklogService).getWorklogPage(reqVO);
    }

    @Test
    void testSummaryAllowsProjectQueryPermission() {
        WorklogSummaryReqVO reqVO = new WorklogSummaryReqVO();
        reqVO.setProjectId(100L);
        allowSystem("project:worklog:query");
        when(permissionService.hasPermission(100L, 10L, "WORKLOG_QUERY")).thenReturn(true);
        WorklogSummaryRespVO response = new WorklogSummaryRespVO();
        when(worklogService.getWorklogSummary(reqVO)).thenReturn(response);

        assertEquals(response, controller.getWorklogSummary(reqVO).getData());

        verify(worklogService).getWorklogSummary(reqVO);
    }

    private void allowSystem(String permission) {
        when(systemSecurity.hasPermission(permission)).thenReturn(true);
    }

    private void persistWorkItem(Long id, Long projectId) {
        ProjectWorkItemDO workItem = new ProjectWorkItemDO();
        workItem.setId(id);
        workItem.setProjectId(projectId);
        when(workItemMapper.selectById(id)).thenReturn(workItem);
    }

    private void persistWorklog(Long id, Long projectId, Long ownerUserId) {
        WorklogDO worklog = new WorklogDO();
        worklog.setId(id);
        worklog.setProjectId(projectId);
        worklog.setOwnerUserId(ownerUserId);
        when(worklogMapper.selectById(id)).thenReturn(worklog);
    }

    private WorklogCreateReqVO createReqVO() {
        WorklogCreateReqVO reqVO = new WorklogCreateReqVO();
        reqVO.setWorkItemId(1L);
        return reqVO;
    }

    private WorklogUpdateReqVO updateReqVO() {
        WorklogUpdateReqVO reqVO = new WorklogUpdateReqVO();
        reqVO.setId(1L);
        return reqVO;
    }

    @Configuration(proxyBeanMethods = false)
    @EnableMethodSecurity
    static class TestConfig {

        @Bean
        WorklogController worklogController() {
            return new WorklogController();
        }

        @Bean
        WorklogService worklogService() {
            return mock(WorklogService.class);
        }

        @Bean("ss")
        SecurityFrameworkService systemSecurity() {
            return mock(SecurityFrameworkService.class);
        }

        @Bean("projectSecurity")
        ProjectSecurityFrameworkService projectSecurity() {
            return new ProjectSecurityFrameworkService();
        }

        @Bean
        ProjectPermissionService projectPermissionService() {
            return mock(ProjectPermissionService.class);
        }

        @Bean
        ProjectWorkItemMapper projectWorkItemMapper() {
            return mock(ProjectWorkItemMapper.class);
        }

        @Bean
        WorklogMapper worklogMapper() {
            return mock(WorklogMapper.class);
        }

        @Bean
        WorkItemFieldMapper workItemFieldMapper() {
            return mock(WorkItemFieldMapper.class);
        }

        @Bean
        WorkItemFieldOptionMapper workItemFieldOptionMapper() {
            return mock(WorkItemFieldOptionMapper.class);
        }

        @Bean
        ProjectMemberMapper projectMemberMapper() {
            return mock(ProjectMemberMapper.class);
        }

        @Bean
        ProjectRoleMapper projectRoleMapper() {
            return mock(ProjectRoleMapper.class);
        }

        @Bean
        CommentMapper commentMapper() {
            return mock(CommentMapper.class);
        }

        @Bean
        CommentTargetService commentTargetService() {
            return mock(CommentTargetService.class);
        }

    }

}
