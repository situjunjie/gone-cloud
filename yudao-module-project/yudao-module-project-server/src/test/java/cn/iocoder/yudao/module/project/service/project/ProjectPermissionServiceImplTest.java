package cn.iocoder.yudao.module.project.service.project;

import cn.iocoder.yudao.framework.tenant.core.context.TenantContextHolder;
import cn.iocoder.yudao.framework.test.core.ut.BaseMockitoUnitTest;
import cn.iocoder.yudao.module.project.dal.dataobject.project.ProjectDO;
import cn.iocoder.yudao.module.project.dal.mysql.project.ProjectMapper;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.InjectMocks;
import org.mockito.Mock;

import java.util.LinkedHashSet;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class ProjectPermissionServiceImplTest extends BaseMockitoUnitTest {

    @InjectMocks
    private ProjectPermissionServiceImpl service;

    @Mock
    private ProjectMapper projectMapper;
    @Mock
    private ProjectPermissionCacheService cacheService;

    @BeforeEach
    void setUpTenant() {
        TenantContextHolder.setTenantId(10L);
    }

    @AfterEach
    void clearTenant() {
        TenantContextHolder.clear();
    }

    @Test
    void testAdminHasAllPermissionsWithoutRolePermissionLookup() {
        when(projectMapper.selectById(1L)).thenReturn(new ProjectDO());
        when(cacheService.getMemberAuth(10L, 1L, 2L))
                .thenReturn(new ProjectMemberAuthCache(true, 3L, Set.of(100L), true));

        assertTrue(service.hasPermission(1L, 2L, "WORK_ITEM_DELETE"));
        verify(cacheService, never()).getRolePermissions(10L, 100L);
    }

    @Test
    void testMultipleRolePermissionsAreUnioned() {
        when(projectMapper.selectById(1L)).thenReturn(new ProjectDO());
        when(cacheService.getMemberAuth(10L, 1L, 2L))
                .thenReturn(new ProjectMemberAuthCache(true, 3L,
                        new LinkedHashSet<>(Set.of(100L, 101L)), false));
        when(cacheService.getRolePermissions(10L, 100L)).thenReturn(Set.of("PROJECT_QUERY"));
        when(cacheService.getRolePermissions(10L, 101L)).thenReturn(Set.of("WORK_ITEM_UPDATE"));

        assertTrue(service.hasPermission(1L, 2L, "WORK_ITEM_UPDATE"));
        assertEquals(Set.of("PROJECT_QUERY", "WORK_ITEM_UPDATE"),
                service.getEffectivePermissions(1L, 2L));
    }

    @Test
    void testNonMemberAndUnknownPermissionFailClosed() {
        when(projectMapper.selectById(1L)).thenReturn(new ProjectDO());
        when(cacheService.getMemberAuth(10L, 1L, 2L)).thenReturn(ProjectMemberAuthCache.notMember());

        assertFalse(service.hasPermission(1L, 2L, "PROJECT_QUERY"));
        assertFalse(service.hasPermission(1L, 2L, "project:project:query"));
    }

}
