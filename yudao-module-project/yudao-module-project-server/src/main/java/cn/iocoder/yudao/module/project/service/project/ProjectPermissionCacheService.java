package cn.iocoder.yudao.module.project.service.project;

import cn.iocoder.yudao.module.project.dal.dataobject.project.ProjectMemberDO;
import cn.iocoder.yudao.module.project.dal.dataobject.project.ProjectMemberRoleDO;
import cn.iocoder.yudao.module.project.dal.dataobject.project.ProjectRoleDO;
import cn.iocoder.yudao.module.project.dal.dataobject.project.ProjectRolePermissionDO;
import cn.iocoder.yudao.module.project.dal.mysql.project.ProjectMemberMapper;
import cn.iocoder.yudao.module.project.dal.mysql.project.ProjectMemberRoleMapper;
import cn.iocoder.yudao.module.project.dal.mysql.project.ProjectRoleMapper;
import cn.iocoder.yudao.module.project.dal.mysql.project.ProjectRolePermissionMapper;
import cn.iocoder.yudao.module.project.enums.ProjectRoleBuiltinTypeEnum;
import cn.iocoder.yudao.module.project.enums.ProjectRoleScopeEnum;
import jakarta.annotation.Resource;
import org.springframework.cache.Cache;
import org.springframework.cache.CacheManager;
import org.springframework.cache.annotation.Cacheable;
import org.springframework.stereotype.Service;

import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;

import static cn.iocoder.yudao.module.project.dal.redis.RedisKeyConstants.PROJECT_MEMBER_AUTH;
import static cn.iocoder.yudao.module.project.dal.redis.RedisKeyConstants.PROJECT_ROLE_PERMISSIONS;

@Service
public class ProjectPermissionCacheService {

    @Resource
    private ProjectMemberMapper memberMapper;
    @Resource
    private ProjectMemberRoleMapper memberRoleMapper;
    @Resource
    private ProjectRoleMapper projectRoleMapper;
    @Resource
    private ProjectRolePermissionMapper rolePermissionMapper;
    @Resource
    private CacheManager cacheManager;

    @Cacheable(cacheNames = PROJECT_MEMBER_AUTH, key = "#tenantId + ':' + #projectId + ':' + #userId")
    public ProjectMemberAuthCache getMemberAuth(Long tenantId, Long projectId, Long userId) {
        ProjectMemberDO member = memberMapper.selectByProjectIdAndUserId(projectId, userId);
        if (member == null) {
            return ProjectMemberAuthCache.notMember();
        }
        Set<Long> relationRoleIds = memberRoleMapper.selectListByMemberId(member.getId()).stream()
                .map(ProjectMemberRoleDO::getRoleId)
                .collect(Collectors.toCollection(LinkedHashSet::new));
        var roles = relationRoleIds.isEmpty() ? List.<ProjectRoleDO>of()
                : projectRoleMapper.selectBatchIds(relationRoleIds);
        Set<Long> roleIds = roles.stream()
                .filter(role -> ProjectRoleScopeEnum.PROJECT.getValue().equals(role.getScope()))
                .filter(role -> projectId.equals(role.getProjectId()))
                .map(ProjectRoleDO::getId)
                .collect(Collectors.toCollection(LinkedHashSet::new));
        boolean admin = roles.stream()
                .filter(role -> roleIds.contains(role.getId()))
                .anyMatch(role -> ProjectRoleBuiltinTypeEnum.ADMIN.getValue().equals(role.getBuiltinType()));
        return new ProjectMemberAuthCache(true, member.getId(), roleIds, admin);
    }

    @Cacheable(cacheNames = PROJECT_ROLE_PERMISSIONS, key = "#tenantId + ':' + #roleId")
    public Set<String> getRolePermissions(Long tenantId, Long roleId) {
        return rolePermissionMapper.selectListByRoleId(roleId).stream()
                .map(ProjectRolePermissionDO::getPermissionCode)
                .collect(Collectors.toCollection(LinkedHashSet::new));
    }

    public void evictMemberAuth(Long tenantId, Long projectId, Long userId) {
        evict(PROJECT_MEMBER_AUTH, memberKey(tenantId, projectId, userId));
    }

    public void evictRolePermissions(Long tenantId, Long roleId) {
        evict(PROJECT_ROLE_PERMISSIONS, roleKey(tenantId, roleId));
    }

    private void evict(String cacheName, String key) {
        Cache cache = cacheManager.getCache(cacheName);
        if (cache != null) {
            cache.evict(key);
        }
    }

    private static String memberKey(Long tenantId, Long projectId, Long userId) {
        return tenantId + ":" + projectId + ":" + userId;
    }

    private static String roleKey(Long tenantId, Long roleId) {
        return tenantId + ":" + roleId;
    }

}
