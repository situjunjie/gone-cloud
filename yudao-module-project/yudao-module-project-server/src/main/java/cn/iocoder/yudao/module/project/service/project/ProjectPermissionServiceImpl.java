package cn.iocoder.yudao.module.project.service.project;

import cn.iocoder.yudao.framework.tenant.core.context.TenantContextHolder;
import cn.iocoder.yudao.module.project.dal.mysql.project.ProjectMapper;
import cn.iocoder.yudao.module.project.enums.ProjectPermissionEnum;
import jakarta.annotation.Resource;
import org.springframework.stereotype.Service;

import java.util.LinkedHashSet;
import java.util.Set;

import static cn.iocoder.yudao.framework.common.exception.util.ServiceExceptionUtil.exception;
import static cn.iocoder.yudao.module.project.enums.ErrorCodeConstants.PROJECT_ACCESS_DENIED;
import static cn.iocoder.yudao.module.project.enums.ErrorCodeConstants.PROJECT_ADMIN_REQUIRED;
import static cn.iocoder.yudao.module.project.enums.ErrorCodeConstants.PROJECT_NOT_EXISTS;
import static cn.iocoder.yudao.module.project.enums.ErrorCodeConstants.PROJECT_PERMISSION_CODE_INVALID;
import static cn.iocoder.yudao.module.project.enums.ErrorCodeConstants.PROJECT_PERMISSION_DENIED;

@Service
public class ProjectPermissionServiceImpl implements ProjectPermissionService {

    @Resource
    private ProjectMapper projectMapper;
    @Resource
    private ProjectPermissionCacheService permissionCacheService;

    @Override
    public boolean hasPermission(Long projectId, Long userId, String permissionCode) {
        if (projectId == null || userId == null || !ProjectPermissionEnum.contains(permissionCode)) {
            return false;
        }
        if (projectMapper.selectById(projectId) == null) {
            return false;
        }
        Long tenantId = TenantContextHolder.getRequiredTenantId();
        ProjectMemberAuthCache memberAuth = permissionCacheService.getMemberAuth(tenantId, projectId, userId);
        if (!memberAuth.isMember()) {
            return false;
        }
        if (memberAuth.isAdmin()) {
            return true;
        }
        for (Long roleId : memberAuth.getRoleIds()) {
            if (permissionCacheService.getRolePermissions(tenantId, roleId).contains(permissionCode)) {
                return true;
            }
        }
        return false;
    }

    @Override
    public boolean isAdmin(Long projectId, Long userId) {
        if (projectId == null || userId == null || projectMapper.selectById(projectId) == null) {
            return false;
        }
        return permissionCacheService.getMemberAuth(TenantContextHolder.getRequiredTenantId(), projectId, userId)
                .isAdmin();
    }

    @Override
    public void validateMember(Long projectId, Long userId) {
        if (projectId == null || userId == null || projectMapper.selectById(projectId) == null) {
            throw exception(PROJECT_NOT_EXISTS);
        }
        ProjectMemberAuthCache auth = permissionCacheService.getMemberAuth(
                TenantContextHolder.getRequiredTenantId(), projectId, userId);
        if (!auth.isMember()) {
            throw exception(PROJECT_ACCESS_DENIED);
        }
    }

    @Override
    public void validatePermission(Long projectId, Long userId, String permissionCode) {
        if (!ProjectPermissionEnum.contains(permissionCode)) {
            throw exception(PROJECT_PERMISSION_CODE_INVALID, permissionCode);
        }
        if (!hasPermission(projectId, userId, permissionCode)) {
            throw exception(PROJECT_PERMISSION_DENIED, permissionCode);
        }
    }

    @Override
    public void validateAdmin(Long projectId, Long userId) {
        if (!isAdmin(projectId, userId)) {
            throw exception(PROJECT_ADMIN_REQUIRED);
        }
    }

    @Override
    public Set<String> getEffectivePermissions(Long projectId, Long userId) {
        validateMember(projectId, userId);
        Long tenantId = TenantContextHolder.getRequiredTenantId();
        ProjectMemberAuthCache memberAuth = permissionCacheService.getMemberAuth(tenantId, projectId, userId);
        if (memberAuth.isAdmin()) {
            return ProjectPermissionEnum.ALL_CODES;
        }
        Set<String> result = new LinkedHashSet<>();
        for (Long roleId : memberAuth.getRoleIds()) {
            result.addAll(permissionCacheService.getRolePermissions(tenantId, roleId));
        }
        return result;
    }

}
