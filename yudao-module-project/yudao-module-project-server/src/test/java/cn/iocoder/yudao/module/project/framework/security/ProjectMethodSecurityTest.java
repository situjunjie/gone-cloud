package cn.iocoder.yudao.module.project.framework.security;

import cn.iocoder.yudao.framework.security.core.LoginUser;
import cn.iocoder.yudao.framework.security.core.service.SecurityFrameworkService;
import cn.iocoder.yudao.module.project.controller.admin.comment.vo.CommentCreateReqVO;
import cn.iocoder.yudao.module.project.controller.admin.project.ProjectController;
import cn.iocoder.yudao.module.project.controller.admin.project.vo.ProjectMyPermissionRespVO;
import cn.iocoder.yudao.module.project.controller.admin.sprint.ProjectSprintController;
import cn.iocoder.yudao.module.project.controller.admin.sprint.vo.ProjectSprintCreateReqVO;
import cn.iocoder.yudao.module.project.controller.admin.workitem.ProjectWorkItemController;
import cn.iocoder.yudao.module.project.controller.admin.workitem.vo.WorkItemActivityPageReqVO;
import cn.iocoder.yudao.module.project.controller.admin.workitem.vo.WorkItemChangeLogPageReqVO;
import cn.iocoder.yudao.module.project.controller.admin.workitem.vo.WorkItemCommentCreateReqVO;
import cn.iocoder.yudao.module.project.service.comment.CommentService;
import cn.iocoder.yudao.module.project.service.project.ProjectMemberService;
import cn.iocoder.yudao.module.project.service.project.ProjectPermissionService;
import cn.iocoder.yudao.module.project.service.project.ProjectService;
import cn.iocoder.yudao.module.project.service.sprint.ProjectSprintService;
import cn.iocoder.yudao.module.project.service.workitem.ProjectWorkItemService;
import cn.iocoder.yudao.module.project.service.workitem.WorkItemActivityService;
import cn.iocoder.yudao.module.project.service.workitem.WorkItemChangeLogService;
import cn.iocoder.yudao.module.project.service.workitem.WorkItemFieldService;
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
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;

class ProjectMethodSecurityTest {

    private AnnotationConfigApplicationContext context;
    private ProjectController controller;
    private ProjectSprintController sprintController;
    private ProjectWorkItemController workItemController;
    private SecurityFrameworkService systemSecurity;
    private TestProjectSecurity projectSecurity;
    private ProjectService projectService;
    private ProjectPermissionService permissionService;
    private WorkItemChangeLogService changeLogService;
    private WorkItemActivityService activityService;
    private CommentService commentService;
    private ProjectSprintService sprintService;

    @BeforeEach
    void setUp() {
        context = new AnnotationConfigApplicationContext(TestConfig.class);
        controller = context.getBean(ProjectController.class);
        sprintController = context.getBean(ProjectSprintController.class);
        workItemController = context.getBean(ProjectWorkItemController.class);
        systemSecurity = context.getBean(SecurityFrameworkService.class);
        projectSecurity = context.getBean(TestProjectSecurity.class);
        projectService = context.getBean(ProjectService.class);
        permissionService = context.getBean(ProjectPermissionService.class);
        changeLogService = context.getBean(WorkItemChangeLogService.class);
        activityService = context.getBean(WorkItemActivityService.class);
        commentService = context.getBean(CommentService.class);
        sprintService = context.getBean(ProjectSprintService.class);
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
    void testSystemPermissionFailureShortCircuitsProjectPermission() {
        when(systemSecurity.hasPermission("project:project:query")).thenReturn(false);

        assertThrows(AccessDeniedException.class, () -> controller.getProject(1L));

        assertEquals(0, projectSecurity.permissionChecks);
        verify(projectService, never()).getProject(1L);
    }

    @Test
    void testProjectPermissionIsRequiredAfterSystemPermission() {
        when(systemSecurity.hasPermission("project:project:query")).thenReturn(true);

        assertThrows(AccessDeniedException.class, () -> controller.getProject(1L));

        assertEquals(1, projectSecurity.permissionChecks);
        verify(projectService, never()).getProject(1L);
    }

    @Test
    void testBothPermissionLayersAllowInvocation() {
        when(systemSecurity.hasPermission("project:project:query")).thenReturn(true);
        projectSecurity.permissionAllowed = true;

        assertDoesNotThrow(() -> controller.getProject(1L));

        verify(projectService).getProject(1L);
    }

    @Test
    void testGetMyProjectPermissionsReturnsAdminAndEffectivePermissions() {
        when(systemSecurity.hasPermission("project:project:query")).thenReturn(true);
        projectSecurity.permissionAllowed = true;
        when(permissionService.isAdmin(1L, 10L)).thenReturn(true);
        when(permissionService.getEffectivePermissions(1L, 10L))
                .thenReturn(Set.of("PROJECT_QUERY", "WORK_ITEM_QUERY", "WORKLOG_MANAGE"));

        ProjectMyPermissionRespVO result = controller.getMyProjectPermissions(1L).getData();

        assertTrue(result.isAdmin());
        assertEquals(Set.of("PROJECT_QUERY", "WORK_ITEM_QUERY", "WORKLOG_MANAGE"), result.getPermissionCodes());
    }

    @Test
    void testCreateSprintSystemPermissionFailureShortCircuitsProjectPermission() {
        ProjectSprintCreateReqVO reqVO = sprintReqVO();
        when(systemSecurity.hasPermission("project:sprint:create")).thenReturn(false);

        assertThrows(AccessDeniedException.class, () -> sprintController.createSprint(reqVO));

        assertEquals(0, projectSecurity.permissionChecks);
        verify(sprintService, never()).createSprint(reqVO);
    }

    @Test
    void testCreateSprintRequiresProjectPermission() {
        ProjectSprintCreateReqVO reqVO = sprintReqVO();
        when(systemSecurity.hasPermission("project:sprint:create")).thenReturn(true);

        assertThrows(AccessDeniedException.class, () -> sprintController.createSprint(reqVO));

        assertEquals(1, projectSecurity.permissionChecks);
        assertEquals(1L, projectSecurity.lastProjectId);
        assertEquals("SPRINT_CREATE", projectSecurity.lastPermissionCode);
        verify(sprintService, never()).createSprint(reqVO);
    }

    @Test
    void testCreateSprintBothPermissionLayersAllowInvocation() {
        ProjectSprintCreateReqVO reqVO = sprintReqVO();
        when(systemSecurity.hasPermission("project:sprint:create")).thenReturn(true);
        projectSecurity.permissionAllowed = true;
        when(sprintService.createSprint(reqVO)).thenReturn(10L);

        assertEquals(10L, sprintController.createSprint(reqVO).getData());

        verify(sprintService).createSprint(reqVO);
    }

    @Test
    void testGetSprintListSystemPermissionFailureShortCircuitsProjectPermission() {
        when(systemSecurity.hasPermission("project:project:query")).thenReturn(false);

        assertThrows(AccessDeniedException.class, () -> sprintController.getSprintList(1L));

        assertEquals(0, projectSecurity.permissionChecks);
        verify(sprintService, never()).getSprintList(1L);
    }

    @Test
    void testGetSprintListRequiresProjectPermission() {
        when(systemSecurity.hasPermission("project:project:query")).thenReturn(true);

        assertThrows(AccessDeniedException.class, () -> sprintController.getSprintList(1L));

        assertEquals(1, projectSecurity.permissionChecks);
        assertEquals(1L, projectSecurity.lastProjectId);
        assertEquals("PROJECT_QUERY", projectSecurity.lastPermissionCode);
        verify(sprintService, never()).getSprintList(1L);
    }

    @Test
    void testGetSprintListBothPermissionLayersAllowInvocation() {
        when(systemSecurity.hasPermission("project:project:query")).thenReturn(true);
        projectSecurity.permissionAllowed = true;
        when(sprintService.getSprintList(1L)).thenReturn(List.of());

        assertTrue(sprintController.getSprintList(1L).getData().isEmpty());

        verify(sprintService).getSprintList(1L);
    }

    @Test
    void testGetSprintSimpleListSystemPermissionFailureShortCircuitsProjectPermission() {
        when(systemSecurity.hasPermission("project:project:query")).thenReturn(false);

        assertThrows(AccessDeniedException.class, () -> sprintController.getSprintSimpleList(1L));

        assertEquals(0, projectSecurity.permissionChecks);
        verify(sprintService, never()).getSprintSimpleList(1L);
    }

    @Test
    void testGetSprintSimpleListRequiresProjectPermission() {
        when(systemSecurity.hasPermission("project:project:query")).thenReturn(true);

        assertThrows(AccessDeniedException.class, () -> sprintController.getSprintSimpleList(1L));

        assertEquals(1, projectSecurity.permissionChecks);
        assertEquals(1L, projectSecurity.lastProjectId);
        assertEquals("PROJECT_QUERY", projectSecurity.lastPermissionCode);
        verify(sprintService, never()).getSprintSimpleList(1L);
    }

    @Test
    void testGetSprintSimpleListBothPermissionLayersAllowInvocation() {
        when(systemSecurity.hasPermission("project:project:query")).thenReturn(true);
        projectSecurity.permissionAllowed = true;
        when(sprintService.getSprintSimpleList(1L)).thenReturn(List.of());

        assertTrue(sprintController.getSprintSimpleList(1L).getData().isEmpty());

        verify(sprintService).getSprintSimpleList(1L);
    }

    @Test
    void testChangeLogSystemPermissionFailureShortCircuitsWorkItemPermission() {
        when(systemSecurity.hasPermission("project:work-item:query")).thenReturn(false);
        WorkItemChangeLogPageReqVO reqVO = changeLogPageReqVO();

        assertThrows(AccessDeniedException.class, () -> workItemController.getWorkItemChangeLogPage(reqVO));

        assertEquals(0, projectSecurity.permissionChecks);
        verify(changeLogService, never()).getPage(reqVO);
    }

    @Test
    void testChangeLogWorkItemPermissionIsRequiredAfterSystemPermission() {
        when(systemSecurity.hasPermission("project:work-item:query")).thenReturn(true);
        WorkItemChangeLogPageReqVO reqVO = changeLogPageReqVO();

        assertThrows(AccessDeniedException.class, () -> workItemController.getWorkItemChangeLogPage(reqVO));

        assertEquals(1, projectSecurity.permissionChecks);
        assertEquals(1L, projectSecurity.lastWorkItemId);
        assertEquals("WORK_ITEM_QUERY", projectSecurity.lastPermissionCode);
        verify(changeLogService, never()).getPage(reqVO);
    }

    @Test
    void testChangeLogBothPermissionLayersAllowInvocation() {
        when(systemSecurity.hasPermission("project:work-item:query")).thenReturn(true);
        projectSecurity.permissionAllowed = true;
        WorkItemChangeLogPageReqVO reqVO = changeLogPageReqVO();

        assertDoesNotThrow(() -> workItemController.getWorkItemChangeLogPage(reqVO));

        verify(changeLogService).getPage(reqVO);
    }

    @Test
    void testActivitySystemPermissionFailureShortCircuitsProjectPermissions() {
        when(systemSecurity.hasPermission("project:work-item:query")).thenReturn(false);
        WorkItemActivityPageReqVO reqVO = activityPageReqVO();

        assertThrows(AccessDeniedException.class, () -> workItemController.getWorkItemActivityPage(reqVO));

        assertEquals(0, projectSecurity.permissionChecks);
        verify(activityService, never()).getPage(reqVO);
    }

    @Test
    void testActivityRequiresWorkItemAndCommentQueryPermissions() {
        when(systemSecurity.hasPermission("project:work-item:query")).thenReturn(true);
        WorkItemActivityPageReqVO reqVO = activityPageReqVO();

        assertThrows(AccessDeniedException.class, () -> workItemController.getWorkItemActivityPage(reqVO));

        assertEquals(1, projectSecurity.permissionChecks);
        assertEquals(1L, projectSecurity.lastWorkItemId);
        assertEquals(List.of("WORK_ITEM_QUERY", "COMMENT_QUERY"), projectSecurity.lastPermissionCodes);
        verify(activityService, never()).getPage(reqVO);
    }

    @Test
    void testActivityBothPermissionLayersAllowInvocation() {
        when(systemSecurity.hasPermission("project:work-item:query")).thenReturn(true);
        projectSecurity.permissionAllowed = true;
        WorkItemActivityPageReqVO reqVO = activityPageReqVO();

        assertDoesNotThrow(() -> workItemController.getWorkItemActivityPage(reqVO));

        verify(activityService).getPage(reqVO);
    }

    @Test
    void testCreateWorkItemCommentUsesOnlyProjectPermissionAndMapsFixedTarget() {
        projectSecurity.permissionAllowed = true;
        WorkItemCommentCreateReqVO reqVO = new WorkItemCommentCreateReqVO();
        reqVO.setWorkItemId(1L);
        reqVO.setContent("请确认验收口径");
        reqVO.setReplyToCommentId(8L);
        reqVO.setMentionUserIds(Set.of(2L, 3L));
        when(commentService.createComment(any())).thenReturn(9L);

        assertEquals(9L, workItemController.createWorkItemComment(reqVO).getData());

        assertEquals(1, projectSecurity.permissionChecks);
        assertEquals(1L, projectSecurity.lastWorkItemId);
        assertEquals("COMMENT_CREATE", projectSecurity.lastPermissionCode);
        verify(systemSecurity, never()).hasPermission(anyString());
        org.mockito.ArgumentCaptor<CommentCreateReqVO> captor =
                org.mockito.ArgumentCaptor.forClass(CommentCreateReqVO.class);
        verify(commentService).createComment(captor.capture());
        assertEquals("WORK_ITEM", captor.getValue().getTargetType());
        assertEquals(1L, captor.getValue().getTargetId());
        assertEquals("请确认验收口径", captor.getValue().getContent());
        assertEquals(8L, captor.getValue().getReplyToCommentId());
        assertEquals(Set.of(2L, 3L), captor.getValue().getMentionUserIds());
    }

    @Test
    void testCreateWorkItemCommentPermissionDenialSkipsService() {
        WorkItemCommentCreateReqVO reqVO = new WorkItemCommentCreateReqVO();
        reqVO.setWorkItemId(1L);
        reqVO.setContent("请确认验收口径");

        assertThrows(AccessDeniedException.class, () -> workItemController.createWorkItemComment(reqVO));

        assertEquals("COMMENT_CREATE", projectSecurity.lastPermissionCode);
        verify(commentService, never()).createComment(any());
        verify(systemSecurity, never()).hasPermission(anyString());
    }

    private WorkItemChangeLogPageReqVO changeLogPageReqVO() {
        WorkItemChangeLogPageReqVO reqVO = new WorkItemChangeLogPageReqVO();
        reqVO.setWorkItemId(1L);
        return reqVO;
    }

    private WorkItemActivityPageReqVO activityPageReqVO() {
        WorkItemActivityPageReqVO reqVO = new WorkItemActivityPageReqVO();
        reqVO.setWorkItemId(1L);
        return reqVO;
    }

    private ProjectSprintCreateReqVO sprintReqVO() {
        ProjectSprintCreateReqVO reqVO = new ProjectSprintCreateReqVO();
        reqVO.setProjectId(1L);
        reqVO.setTitle("第 1 迭代");
        reqVO.setOwnerUserId(2L);
        return reqVO;
    }

    @Configuration(proxyBeanMethods = false)
    @EnableMethodSecurity
    static class TestConfig {

        @Bean
        ProjectController projectController() {
            return new ProjectController();
        }

        @Bean
        ProjectSprintController projectSprintController() {
            return new ProjectSprintController();
        }

        @Bean
        ProjectWorkItemController projectWorkItemController() {
            return new ProjectWorkItemController();
        }

        @Bean
        ProjectService projectService() {
            return mock(ProjectService.class);
        }

        @Bean
        ProjectMemberService projectMemberService() {
            return mock(ProjectMemberService.class);
        }

        @Bean
        ProjectPermissionService projectPermissionService() {
            return mock(ProjectPermissionService.class);
        }

        @Bean
        ProjectSprintService projectSprintService() {
            return mock(ProjectSprintService.class);
        }

        @Bean
        ProjectWorkItemService projectWorkItemService() {
            return mock(ProjectWorkItemService.class);
        }

        @Bean
        WorkItemFieldService workItemFieldService() {
            return mock(WorkItemFieldService.class);
        }

        @Bean
        WorkItemChangeLogService workItemChangeLogService() {
            return mock(WorkItemChangeLogService.class);
        }

        @Bean
        WorkItemActivityService workItemActivityService() {
            return mock(WorkItemActivityService.class);
        }

        @Bean
        CommentService commentService() {
            return mock(CommentService.class);
        }

        @Bean("ss")
        SecurityFrameworkService systemSecurity() {
            return mock(SecurityFrameworkService.class);
        }

        @Bean("projectSecurity")
        TestProjectSecurity projectSecurity() {
            return new TestProjectSecurity();
        }

    }

    public static class TestProjectSecurity {

        private boolean permissionAllowed;
        private int permissionChecks;
        private Long lastProjectId;
        private Long lastWorkItemId;
        private String lastPermissionCode;
        private List<String> lastPermissionCodes;

        public boolean hasPermission(Long projectId, String permissionCode) {
            permissionChecks++;
            lastProjectId = projectId;
            lastPermissionCode = permissionCode;
            return permissionAllowed;
        }

        public boolean hasWorkItemPermission(Long workItemId, String permissionCode) {
            permissionChecks++;
            lastWorkItemId = workItemId;
            lastPermissionCode = permissionCode;
            return permissionAllowed;
        }

        public boolean hasWorkItemPermissions(Long workItemId, String... permissionCodes) {
            permissionChecks++;
            lastWorkItemId = workItemId;
            lastPermissionCodes = List.of(permissionCodes);
            return permissionAllowed;
        }

    }

}
