package cn.iocoder.yudao.module.project.framework.security;

import cn.iocoder.yudao.module.project.dal.mysql.project.ProjectMapper;
import cn.iocoder.yudao.module.project.dal.mysql.project.ProjectMemberMapper;
import cn.iocoder.yudao.module.project.dal.mysql.project.ProjectMemberRoleMapper;
import cn.iocoder.yudao.module.project.dal.mysql.project.ProjectRoleMapper;
import cn.iocoder.yudao.module.project.dal.mysql.project.ProjectRolePermissionMapper;
import cn.iocoder.yudao.module.project.dal.mysql.comment.CommentMapper;
import cn.iocoder.yudao.module.project.dal.mysql.worklog.WorklogMapper;
import cn.iocoder.yudao.module.project.dal.mysql.workitem.ProjectWorkItemMapper;
import cn.iocoder.yudao.module.project.dal.mysql.workitem.WorkItemFieldMapper;
import cn.iocoder.yudao.module.project.dal.mysql.workitem.WorkItemFieldOptionMapper;
import cn.iocoder.yudao.module.project.service.project.ProjectPermissionCacheService;
import cn.iocoder.yudao.module.project.service.project.ProjectPermissionService;
import cn.iocoder.yudao.module.project.service.project.ProjectRoleServiceImpl;
import cn.iocoder.yudao.module.project.service.comment.CommentTargetService;
import org.junit.jupiter.api.Test;
import org.springframework.cache.CacheManager;
import org.springframework.context.annotation.AnnotationConfigApplicationContext;

import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.mockito.Mockito.mock;

class ProjectRoleMapperInjectionTest {

    @Test
    void testProjectRoleMapperDoesNotConflictWithSystemRoleMapper() {
        try (AnnotationConfigApplicationContext context = new AnnotationConfigApplicationContext()) {
            context.registerBean("roleMapper", Runnable.class, () -> () -> { });
            context.registerBean("projectRoleMapper", ProjectRoleMapper.class,
                    () -> mock(ProjectRoleMapper.class));
            registerMock(context, ProjectMapper.class);
            registerMock(context, ProjectMemberMapper.class);
            registerMock(context, ProjectMemberRoleMapper.class);
            registerMock(context, ProjectRolePermissionMapper.class);
            registerMock(context, CommentMapper.class);
            registerMock(context, WorklogMapper.class);
            registerMock(context, ProjectWorkItemMapper.class);
            registerMock(context, WorkItemFieldMapper.class);
            registerMock(context, WorkItemFieldOptionMapper.class);
            registerMock(context, ProjectPermissionService.class);
            registerMock(context, CommentTargetService.class);
            registerMock(context, CacheManager.class);
            context.registerBean(ProjectPermissionCacheService.class);
            context.registerBean(ProjectRoleServiceImpl.class);
            context.registerBean(ProjectSecurityFrameworkService.class);

            context.refresh();

            assertNotNull(context.getBean(ProjectPermissionCacheService.class));
            assertNotNull(context.getBean(ProjectRoleServiceImpl.class));
            assertNotNull(context.getBean(ProjectSecurityFrameworkService.class));
        }
    }

    private static <T> void registerMock(AnnotationConfigApplicationContext context, Class<T> type) {
        context.registerBean(type, () -> mock(type));
    }

}
