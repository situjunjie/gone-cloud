package cn.iocoder.yudao.module.project.service.project;

import cn.hutool.core.util.ObjUtil;
import cn.iocoder.yudao.framework.tenant.core.context.TenantContextHolder;
import cn.iocoder.yudao.module.project.controller.admin.project.vo.ProjectPermissionRespVO;
import cn.iocoder.yudao.module.project.controller.admin.project.vo.ProjectRoleAssignPermissionsReqVO;
import cn.iocoder.yudao.module.project.controller.admin.project.vo.ProjectRoleCreateReqVO;
import cn.iocoder.yudao.module.project.controller.admin.project.vo.ProjectRoleRespVO;
import cn.iocoder.yudao.module.project.controller.admin.project.vo.ProjectRoleUpdateReqVO;
import cn.iocoder.yudao.module.project.dal.dataobject.project.ProjectRoleDO;
import cn.iocoder.yudao.module.project.dal.dataobject.project.ProjectRolePermissionDO;
import cn.iocoder.yudao.module.project.dal.mysql.project.ProjectMapper;
import cn.iocoder.yudao.module.project.dal.mysql.project.ProjectMemberRoleMapper;
import cn.iocoder.yudao.module.project.dal.mysql.project.ProjectRoleMapper;
import cn.iocoder.yudao.module.project.dal.mysql.project.ProjectRolePermissionMapper;
import cn.iocoder.yudao.module.project.enums.ProjectPermissionEnum;
import cn.iocoder.yudao.module.project.enums.ProjectRoleBuiltinTypeEnum;
import cn.iocoder.yudao.module.project.enums.ProjectRoleScopeEnum;
import jakarta.annotation.Resource;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.validation.annotation.Validated;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.stream.Collectors;

import static cn.iocoder.yudao.framework.common.exception.util.ServiceExceptionUtil.exception;
import static cn.iocoder.yudao.module.project.enums.ErrorCodeConstants.PROJECT_ADMIN_PERMISSION_IMMUTABLE;
import static cn.iocoder.yudao.module.project.enums.ErrorCodeConstants.PROJECT_BUILTIN_ROLE_IMMUTABLE;
import static cn.iocoder.yudao.module.project.enums.ErrorCodeConstants.PROJECT_PARTICIPANT_PERMISSION_REQUIRED;
import static cn.iocoder.yudao.module.project.enums.ErrorCodeConstants.PROJECT_PERMISSION_CODE_INVALID;
import static cn.iocoder.yudao.module.project.enums.ErrorCodeConstants.PROJECT_ROLE_CODE_DUPLICATE;
import static cn.iocoder.yudao.module.project.enums.ErrorCodeConstants.PROJECT_ROLE_IN_USE;
import static cn.iocoder.yudao.module.project.enums.ErrorCodeConstants.PROJECT_ROLE_NOT_EXISTS;
import static cn.iocoder.yudao.module.project.enums.ErrorCodeConstants.PROJECT_ROLE_SCOPE_MISMATCH;
import static cn.iocoder.yudao.module.project.enums.ErrorCodeConstants.PROJECT_NOT_EXISTS;

@Service
@Validated
public class ProjectRoleServiceImpl implements ProjectRoleService {

    private static final long GLOBAL_PROJECT_ID = 0L;

    @Resource
    private ProjectRoleMapper roleMapper;
    @Resource
    private ProjectMapper projectMapper;
    @Resource
    private ProjectRolePermissionMapper rolePermissionMapper;
    @Resource
    private ProjectMemberRoleMapper memberRoleMapper;
    @Resource
    private ProjectPermissionCacheService permissionCacheService;

    @Override
    public List<ProjectPermissionRespVO> getPermissionList() {
        List<ProjectPermissionRespVO> result = new ArrayList<>(ProjectPermissionEnum.values().length);
        for (ProjectPermissionEnum permission : ProjectPermissionEnum.values()) {
            result.add(new ProjectPermissionRespVO(permission.name(), permission.getName()));
        }
        return result;
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void ensureTenantBuiltinRoles() {
        ProjectRoleDO admin = ensureBuiltinRole(ProjectRoleBuiltinTypeEnum.ADMIN, 0);
        // 管理员的全部权限由代码直接判定，不持久化授权关系。
        List<ProjectRolePermissionDO> staleAdminPermissions = rolePermissionMapper.selectListByRoleId(admin.getId());
        if (!staleAdminPermissions.isEmpty()) {
            rolePermissionMapper.deleteListByRoleId(admin.getId());
        }
        ProjectRoleDO participant = ensureBuiltinRole(ProjectRoleBuiltinTypeEnum.PARTICIPANT, 10);
        Set<String> currentCodes = permissionCodes(participant.getId());
        Set<String> requiredCodes = currentCodes.isEmpty()
                ? ProjectPermissionEnum.PARTICIPANT_DEFAULT_CODES
                : ProjectPermissionEnum.PARTICIPANT_REQUIRED_CODES;
        addPermissions(participant.getId(), requiredCodes);
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public Map<String, Long> snapshotGlobalRoles(Long projectId) {
        ensureTenantBuiltinRoles();
        List<ProjectRoleDO> globalRoles = roleMapper.selectListByScopeAndProjectId(
                ProjectRoleScopeEnum.GLOBAL.getValue(), GLOBAL_PROJECT_ID);
        Map<Long, Set<String>> permissionMap = rolePermissionMapper.selectListByRoleIds(
                        globalRoles.stream().map(ProjectRoleDO::getId).toList()).stream()
                .collect(Collectors.groupingBy(ProjectRolePermissionDO::getRoleId,
                        Collectors.mapping(ProjectRolePermissionDO::getPermissionCode,
                                Collectors.toCollection(LinkedHashSet::new))));
        Map<String, Long> builtinRoleIds = new LinkedHashMap<>();
        for (ProjectRoleDO source : globalRoles) {
            ProjectRoleDO target = new ProjectRoleDO();
            target.setScope(ProjectRoleScopeEnum.PROJECT.getValue());
            target.setProjectId(projectId);
            target.setCode(source.getCode());
            target.setName(source.getName());
            target.setBuiltinType(source.getBuiltinType());
            target.setSort(source.getSort());
            target.setRemark(source.getRemark());
            roleMapper.insert(target);
            if (!ProjectRoleBuiltinTypeEnum.ADMIN.getValue().equals(target.getBuiltinType())) {
                addPermissions(target.getId(), permissionMap.getOrDefault(source.getId(), Collections.emptySet()));
            }
            if (ProjectRoleBuiltinTypeEnum.isBuiltin(target.getBuiltinType())) {
                builtinRoleIds.put(target.getBuiltinType(), target.getId());
            }
        }
        return builtinRoleIds;
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public Long createGlobalRole(ProjectRoleCreateReqVO reqVO) {
        ensureTenantBuiltinRoles();
        return createCustomRole(ProjectRoleScopeEnum.GLOBAL.getValue(), GLOBAL_PROJECT_ID, reqVO);
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public Long createProjectRole(ProjectRoleCreateReqVO reqVO) {
        if (reqVO.getProjectId() == null || projectMapper.selectById(reqVO.getProjectId()) == null) {
            if (reqVO.getProjectId() != null) {
                throw exception(PROJECT_NOT_EXISTS);
            }
            throw exception(PROJECT_ROLE_SCOPE_MISMATCH);
        }
        return createCustomRole(ProjectRoleScopeEnum.PROJECT.getValue(), reqVO.getProjectId(), reqVO);
    }

    @Override
    public void updateGlobalRole(ProjectRoleUpdateReqVO reqVO) {
        updateRole(reqVO, ProjectRoleScopeEnum.GLOBAL.getValue(), GLOBAL_PROJECT_ID);
    }

    @Override
    public void updateProjectRole(ProjectRoleUpdateReqVO reqVO) {
        updateRole(reqVO, ProjectRoleScopeEnum.PROJECT.getValue(), null);
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void deleteGlobalRole(Long id) {
        deleteRole(id, ProjectRoleScopeEnum.GLOBAL.getValue());
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void deleteProjectRole(Long id) {
        deleteRole(id, ProjectRoleScopeEnum.PROJECT.getValue());
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void assignGlobalRolePermissions(ProjectRoleAssignPermissionsReqVO reqVO) {
        assignPermissions(reqVO, ProjectRoleScopeEnum.GLOBAL.getValue());
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void assignProjectRolePermissions(ProjectRoleAssignPermissionsReqVO reqVO) {
        assignPermissions(reqVO, ProjectRoleScopeEnum.PROJECT.getValue());
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public List<ProjectRoleRespVO> getGlobalRoleList() {
        ensureTenantBuiltinRoles();
        return buildRoleRespList(roleMapper.selectListByScopeAndProjectId(
                ProjectRoleScopeEnum.GLOBAL.getValue(), GLOBAL_PROJECT_ID));
    }

    @Override
    public List<ProjectRoleRespVO> getProjectRoleList(Long projectId) {
        if (projectMapper.selectById(projectId) == null) {
            throw exception(PROJECT_NOT_EXISTS);
        }
        return buildRoleRespList(roleMapper.selectListByScopeAndProjectId(
                ProjectRoleScopeEnum.PROJECT.getValue(), projectId));
    }

    @Override
    public ProjectRoleDO validateProjectRole(Long id, Long projectId) {
        ProjectRoleDO role = validateRoleExists(id);
        if (!ProjectRoleScopeEnum.PROJECT.getValue().equals(role.getScope())
                || !Objects.equals(projectId, role.getProjectId())) {
            throw exception(PROJECT_ROLE_SCOPE_MISMATCH);
        }
        return role;
    }

    @Override
    public ProjectRoleDO getProjectBuiltinRole(Long projectId, String builtinType) {
        ProjectRoleDO role = roleMapper.selectBuiltinByProjectId(projectId, builtinType);
        if (role == null || !ProjectRoleScopeEnum.PROJECT.getValue().equals(role.getScope())) {
            throw exception(PROJECT_ROLE_NOT_EXISTS);
        }
        return role;
    }

    private ProjectRoleDO ensureBuiltinRole(ProjectRoleBuiltinTypeEnum builtinType, int sort) {
        Long tenantId = TenantContextHolder.getRequiredTenantId();
        ProjectRoleDO role = roleMapper.selectIncludingDeleted(tenantId, ProjectRoleScopeEnum.GLOBAL.getValue(),
                GLOBAL_PROJECT_ID, builtinType.getValue());
        if (role == null) {
            role = new ProjectRoleDO();
            role.setScope(ProjectRoleScopeEnum.GLOBAL.getValue());
            role.setProjectId(GLOBAL_PROJECT_ID);
            role.setCode(builtinType.getValue());
            role.setName(builtinType.getRoleName());
            role.setBuiltinType(builtinType.getValue());
            role.setSort(sort);
            try {
                roleMapper.insert(role);
                return role;
            } catch (DuplicateKeyException ignored) {
                role = roleMapper.selectIncludingDeleted(tenantId, ProjectRoleScopeEnum.GLOBAL.getValue(),
                        GLOBAL_PROJECT_ID, builtinType.getValue());
            }
        }
        if (Boolean.TRUE.equals(role.getDeleted())) {
            roleMapper.restoreById(role.getId(), tenantId, builtinType.getRoleName(), builtinType.getValue(),
                    sort, null, LocalDateTime.now());
            role.setDeleted(false);
        }
        return role;
    }

    private Long createCustomRole(String scope, Long projectId, ProjectRoleCreateReqVO reqVO) {
        if (ProjectRoleBuiltinTypeEnum.isBuiltin(reqVO.getCode())) {
            throw exception(PROJECT_ROLE_CODE_DUPLICATE);
        }
        Long tenantId = TenantContextHolder.getRequiredTenantId();
        ProjectRoleDO existing = roleMapper.selectIncludingDeleted(tenantId, scope, projectId, reqVO.getCode());
        if (existing != null && !Boolean.TRUE.equals(existing.getDeleted())) {
            throw exception(PROJECT_ROLE_CODE_DUPLICATE);
        }
        if (existing != null) {
            roleMapper.restoreById(existing.getId(), tenantId, reqVO.getName(),
                    ProjectRoleBuiltinTypeEnum.NONE.getValue(), ObjUtil.defaultIfNull(reqVO.getSort(), 0),
                    reqVO.getRemark(), LocalDateTime.now());
            return existing.getId();
        }
        ProjectRoleDO role = new ProjectRoleDO();
        role.setScope(scope);
        role.setProjectId(projectId);
        role.setCode(reqVO.getCode());
        role.setName(reqVO.getName());
        role.setBuiltinType(ProjectRoleBuiltinTypeEnum.NONE.getValue());
        role.setSort(ObjUtil.defaultIfNull(reqVO.getSort(), 0));
        role.setRemark(reqVO.getRemark());
        try {
            roleMapper.insert(role);
        } catch (DuplicateKeyException ex) {
            throw exception(PROJECT_ROLE_CODE_DUPLICATE);
        }
        return role.getId();
    }

    private void updateRole(ProjectRoleUpdateReqVO reqVO, String scope, Long projectId) {
        ProjectRoleDO role = validateRoleScope(reqVO.getId(), scope, projectId);
        validateCustomRole(role);
        ProjectRoleDO update = new ProjectRoleDO();
        update.setId(role.getId());
        update.setName(reqVO.getName());
        update.setSort(ObjUtil.defaultIfNull(reqVO.getSort(), role.getSort()));
        update.setRemark(reqVO.getRemark());
        roleMapper.updateById(update);
    }

    private void deleteRole(Long id, String scope) {
        ProjectRoleDO role = validateRoleScope(id, scope, null);
        validateCustomRole(role);
        if (memberRoleMapper.selectCountByRoleId(id) > 0) {
            throw exception(PROJECT_ROLE_IN_USE);
        }
        rolePermissionMapper.deleteListByRoleId(id);
        roleMapper.deleteById(id);
        permissionCacheService.evictRolePermissions(TenantContextHolder.getRequiredTenantId(), id);
    }

    private void assignPermissions(ProjectRoleAssignPermissionsReqVO reqVO, String scope) {
        ProjectRoleDO role = validateRoleScope(reqVO.getRoleId(), scope, null);
        Set<String> permissionCodes = new LinkedHashSet<>(reqVO.getPermissionCodes());
        for (String code : permissionCodes) {
            if (!ProjectPermissionEnum.contains(code)) {
                throw exception(PROJECT_PERMISSION_CODE_INVALID, code);
            }
        }
        if (ProjectRoleBuiltinTypeEnum.ADMIN.getValue().equals(role.getBuiltinType())) {
            throw exception(PROJECT_ADMIN_PERMISSION_IMMUTABLE);
        }
        if (ProjectRoleBuiltinTypeEnum.PARTICIPANT.getValue().equals(role.getBuiltinType())
                && !permissionCodes.containsAll(ProjectPermissionEnum.PARTICIPANT_REQUIRED_CODES)) {
            Set<String> missing = new LinkedHashSet<>(ProjectPermissionEnum.PARTICIPANT_REQUIRED_CODES);
            missing.removeAll(permissionCodes);
            throw exception(PROJECT_PARTICIPANT_PERMISSION_REQUIRED, String.join(",", missing));
        }
        updatePermissions(role.getId(), permissionCodes);
        permissionCacheService.evictRolePermissions(TenantContextHolder.getRequiredTenantId(), role.getId());
    }

    private void updatePermissions(Long roleId, Set<String> desiredCodes) {
        Set<String> currentCodes = permissionCodes(roleId);
        Set<String> additions = new LinkedHashSet<>(desiredCodes);
        additions.removeAll(currentCodes);
        addPermissions(roleId, additions);
        Set<String> removals = new LinkedHashSet<>(currentCodes);
        removals.removeAll(desiredCodes);
        if (!removals.isEmpty()) {
            rolePermissionMapper.deleteListByRoleIdAndPermissionCodes(roleId, removals);
        }
    }

    private void addPermissions(Long roleId, Collection<String> codes) {
        Long tenantId = TenantContextHolder.getRequiredTenantId();
        for (String code : codes) {
            ProjectRolePermissionDO existing = rolePermissionMapper.selectIncludingDeleted(tenantId, roleId, code);
            if (existing == null) {
                ProjectRolePermissionDO relation = new ProjectRolePermissionDO();
                relation.setRoleId(roleId);
                relation.setPermissionCode(code);
                rolePermissionMapper.insert(relation);
            } else if (Boolean.TRUE.equals(existing.getDeleted())) {
                rolePermissionMapper.restoreById(existing.getId(), tenantId, LocalDateTime.now());
            }
        }
    }

    private Set<String> permissionCodes(Long roleId) {
        return rolePermissionMapper.selectListByRoleId(roleId).stream()
                .map(ProjectRolePermissionDO::getPermissionCode)
                .collect(Collectors.toCollection(LinkedHashSet::new));
    }

    private ProjectRoleDO validateRoleScope(Long id, String scope, Long projectId) {
        ProjectRoleDO role = validateRoleExists(id);
        if (!scope.equals(role.getScope()) || projectId != null && !Objects.equals(projectId, role.getProjectId())) {
            throw exception(PROJECT_ROLE_SCOPE_MISMATCH);
        }
        return role;
    }

    private ProjectRoleDO validateRoleExists(Long id) {
        ProjectRoleDO role = roleMapper.selectById(id);
        if (role == null) {
            throw exception(PROJECT_ROLE_NOT_EXISTS);
        }
        return role;
    }

    private void validateCustomRole(ProjectRoleDO role) {
        if (ProjectRoleBuiltinTypeEnum.isBuiltin(role.getBuiltinType())) {
            throw exception(PROJECT_BUILTIN_ROLE_IMMUTABLE);
        }
    }

    private List<ProjectRoleRespVO> buildRoleRespList(List<ProjectRoleDO> roles) {
        if (roles.isEmpty()) {
            return Collections.emptyList();
        }
        Map<Long, Set<String>> permissionMap = rolePermissionMapper.selectListByRoleIds(
                        roles.stream().map(ProjectRoleDO::getId).toList()).stream()
                .collect(Collectors.groupingBy(ProjectRolePermissionDO::getRoleId,
                        Collectors.mapping(ProjectRolePermissionDO::getPermissionCode,
                                Collectors.toCollection(LinkedHashSet::new))));
        List<ProjectRoleRespVO> result = new ArrayList<>(roles.size());
        for (ProjectRoleDO role : roles) {
            ProjectRoleRespVO respVO = new ProjectRoleRespVO();
            respVO.setId(role.getId());
            respVO.setScope(role.getScope());
            respVO.setProjectId(role.getProjectId());
            respVO.setCode(role.getCode());
            respVO.setName(role.getName());
            respVO.setBuiltinType(role.getBuiltinType());
            respVO.setSort(role.getSort());
            respVO.setRemark(role.getRemark());
            respVO.setCreateTime(role.getCreateTime());
            respVO.setPermissionCodes(ProjectRoleBuiltinTypeEnum.ADMIN.getValue().equals(role.getBuiltinType())
                    ? ProjectPermissionEnum.ALL_CODES
                    : permissionMap.getOrDefault(role.getId(), Collections.emptySet()));
            result.add(respVO);
        }
        return result;
    }

}
