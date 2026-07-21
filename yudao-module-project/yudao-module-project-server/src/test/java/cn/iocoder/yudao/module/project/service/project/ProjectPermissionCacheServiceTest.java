package cn.iocoder.yudao.module.project.service.project;

import cn.iocoder.yudao.framework.test.core.ut.BaseMockitoUnitTest;
import cn.iocoder.yudao.module.project.dal.mysql.project.ProjectMemberMapper;
import cn.iocoder.yudao.module.project.dal.mysql.project.ProjectMemberRoleMapper;
import cn.iocoder.yudao.module.project.dal.mysql.project.ProjectRoleMapper;
import cn.iocoder.yudao.module.project.dal.mysql.project.ProjectRolePermissionMapper;
import org.junit.jupiter.api.Test;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.springframework.cache.Cache;
import org.springframework.cache.CacheManager;

import static cn.iocoder.yudao.module.project.dal.redis.RedisKeyConstants.PROJECT_MEMBER_AUTH;
import static cn.iocoder.yudao.module.project.dal.redis.RedisKeyConstants.PROJECT_ROLE_PERMISSIONS;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class ProjectPermissionCacheServiceTest extends BaseMockitoUnitTest {

    @InjectMocks
    private ProjectPermissionCacheService service;

    @Mock private ProjectMemberMapper memberMapper;
    @Mock private ProjectMemberRoleMapper memberRoleMapper;
    @Mock private ProjectRoleMapper roleMapper;
    @Mock private ProjectRolePermissionMapper rolePermissionMapper;
    @Mock private CacheManager cacheManager;
    @Mock private Cache cache;

    @Test
    void testNonMemberReturnsExplicitNegativeSnapshot() {
        ProjectMemberAuthCache result = service.getMemberAuth(10L, 20L, 30L);
        assertFalse(result.isMember());
        assertFalse(result.isAdmin());
    }

    @Test
    void testEvictionKeysIncludeTenantAndResourceDimensions() {
        when(cacheManager.getCache(PROJECT_MEMBER_AUTH)).thenReturn(cache);
        when(cacheManager.getCache(PROJECT_ROLE_PERMISSIONS)).thenReturn(cache);

        service.evictMemberAuth(10L, 20L, 30L);
        service.evictRolePermissions(10L, 40L);

        verify(cache).evict("10:20:30");
        verify(cache).evict("10:40");
    }

}
