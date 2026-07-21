package cn.iocoder.yudao.module.project.framework.security;

import cn.iocoder.yudao.framework.security.core.LoginUser;
import cn.iocoder.yudao.framework.security.core.service.SecurityFrameworkService;
import cn.iocoder.yudao.module.project.controller.admin.project.ProjectController;
import cn.iocoder.yudao.module.project.controller.admin.project.vo.ProjectMyPermissionRespVO;
import cn.iocoder.yudao.module.project.service.project.ProjectMemberService;
import cn.iocoder.yudao.module.project.service.project.ProjectPermissionService;
import cn.iocoder.yudao.module.project.service.project.ProjectService;
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

class ProjectMethodSecurityTest {

    private AnnotationConfigApplicationContext context;
    private ProjectController controller;
    private SecurityFrameworkService systemSecurity;
    private TestProjectSecurity projectSecurity;
    private ProjectService projectService;
    private ProjectPermissionService permissionService;

    @BeforeEach
    void setUp() {
        context = new AnnotationConfigApplicationContext(TestConfig.class);
        controller = context.getBean(ProjectController.class);
        systemSecurity = context.getBean(SecurityFrameworkService.class);
        projectSecurity = context.getBean(TestProjectSecurity.class);
        projectService = context.getBean(ProjectService.class);
        permissionService = context.getBean(ProjectPermissionService.class);
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
                .thenReturn(Set.of("PROJECT_QUERY", "WORK_ITEM_QUERY"));

        ProjectMyPermissionRespVO result = controller.getMyProjectPermissions(1L).getData();

        assertTrue(result.isAdmin());
        assertEquals(Set.of("PROJECT_QUERY", "WORK_ITEM_QUERY"), result.getPermissionCodes());
    }

    @Configuration(proxyBeanMethods = false)
    @EnableMethodSecurity
    static class TestConfig {

        @Bean
        ProjectController projectController() {
            return new ProjectController();
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

        public boolean hasPermission(Long projectId, String permissionCode) {
            permissionChecks++;
            return permissionAllowed;
        }

    }

}
