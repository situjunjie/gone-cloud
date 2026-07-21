package cn.iocoder.yudao.module.project.service.project;

import java.util.Set;

public interface ProjectPermissionService {

    boolean hasPermission(Long projectId, Long userId, String permissionCode);

    boolean isAdmin(Long projectId, Long userId);

    void validateMember(Long projectId, Long userId);

    void validatePermission(Long projectId, Long userId, String permissionCode);

    void validateAdmin(Long projectId, Long userId);

    Set<String> getEffectivePermissions(Long projectId, Long userId);

}
