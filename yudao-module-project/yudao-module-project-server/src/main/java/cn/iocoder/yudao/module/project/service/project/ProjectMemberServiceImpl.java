package cn.iocoder.yudao.module.project.service.project;

import cn.iocoder.yudao.framework.tenant.core.context.TenantContextHolder;
import cn.iocoder.yudao.module.project.controller.admin.project.vo.ProjectMemberAddReqVO;
import cn.iocoder.yudao.module.project.controller.admin.project.vo.ProjectMemberRespVO;
import cn.iocoder.yudao.module.project.controller.admin.project.vo.ProjectMemberUpdateRoleReqVO;
import cn.iocoder.yudao.module.project.controller.admin.project.vo.ProjectRoleRespVO;
import cn.iocoder.yudao.module.project.dal.dataobject.project.ProjectDO;
import cn.iocoder.yudao.module.project.dal.dataobject.project.ProjectMemberDO;
import cn.iocoder.yudao.module.project.dal.dataobject.project.ProjectMemberRoleDO;
import cn.iocoder.yudao.module.project.dal.dataobject.project.ProjectRoleDO;
import cn.iocoder.yudao.module.project.dal.mysql.project.ProjectMapper;
import cn.iocoder.yudao.module.project.dal.mysql.project.ProjectMemberMapper;
import cn.iocoder.yudao.module.project.dal.mysql.project.ProjectMemberRoleMapper;
import cn.iocoder.yudao.module.project.enums.ProjectRoleBuiltinTypeEnum;
import cn.iocoder.yudao.module.system.api.user.AdminUserApi;
import jakarta.annotation.Resource;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.validation.annotation.Validated;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.stream.Collectors;

import static cn.iocoder.yudao.framework.common.exception.util.ServiceExceptionUtil.exception;
import static cn.iocoder.yudao.module.project.enums.ErrorCodeConstants.PROJECT_MEMBER_DUPLICATE;
import static cn.iocoder.yudao.module.project.enums.ErrorCodeConstants.PROJECT_MEMBER_NOT_EXISTS;
import static cn.iocoder.yudao.module.project.enums.ErrorCodeConstants.PROJECT_MEMBER_ROLE_REQUIRED;
import static cn.iocoder.yudao.module.project.enums.ErrorCodeConstants.PROJECT_NOT_EXISTS;
import static cn.iocoder.yudao.module.project.enums.ErrorCodeConstants.PROJECT_OWNER_ADMIN_REQUIRED;
import static cn.iocoder.yudao.module.project.enums.ErrorCodeConstants.PROJECT_OWNER_CANNOT_REMOVE;
import static cn.iocoder.yudao.module.project.enums.ErrorCodeConstants.PROJECT_ROLE_NOT_EXISTS;

@Service
@Validated
public class ProjectMemberServiceImpl implements ProjectMemberService {

    @Resource
    private ProjectMapper projectMapper;
    @Resource
    private ProjectMemberMapper memberMapper;
    @Resource
    private ProjectMemberRoleMapper memberRoleMapper;
    @Resource
    private ProjectRoleService roleService;
    @Resource
    private ProjectPermissionCacheService permissionCacheService;
    @Resource
    private AdminUserApi adminUserApi;

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void initializeProjectMembers(Long projectId, Long creatorUserId, Long ownerUserId,
                                         Map<String, Long> builtinRoleIds) {
        Long adminRoleId = requiredBuiltinRoleId(builtinRoleIds, ProjectRoleBuiltinTypeEnum.ADMIN.getValue());
        Long participantRoleId = requiredBuiltinRoleId(builtinRoleIds,
                ProjectRoleBuiltinTypeEnum.PARTICIPANT.getValue());
        ProjectMemberDO creator = insertOrRestoreMember(projectId, creatorUserId, false);
        replaceRoles(creator, Set.of(adminRoleId, participantRoleId), false);
        if (!Objects.equals(creatorUserId, ownerUserId)) {
            ProjectMemberDO owner = insertOrRestoreMember(projectId, ownerUserId, false);
            replaceRoles(owner, Set.of(adminRoleId), false);
        }
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public Long addProjectMember(ProjectMemberAddReqVO reqVO) {
        validateProjectExists(reqVO.getProjectId());
        adminUserApi.validateUser(reqVO.getUserId());
        Set<Long> roleIds = reqVO.getRoleIds();
        if (roleIds == null || roleIds.isEmpty()) {
            roleIds = Set.of(roleService.getProjectBuiltinRole(reqVO.getProjectId(),
                    ProjectRoleBuiltinTypeEnum.PARTICIPANT.getValue()).getId());
        }
        ProjectMemberDO member = insertOrRestoreMember(reqVO.getProjectId(), reqVO.getUserId(), true);
        replaceRoles(member, roleIds, false);
        return member.getId();
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void updateProjectMemberRoles(ProjectMemberUpdateRoleReqVO reqVO) {
        ProjectMemberDO member = validateMemberExists(reqVO.getId());
        if (reqVO.getRoleIds() == null || reqVO.getRoleIds().isEmpty()) {
            throw exception(PROJECT_MEMBER_ROLE_REQUIRED);
        }
        ProjectDO project = validateProjectExists(member.getProjectId());
        if (Objects.equals(project.getOwnerUserId(), member.getUserId())
                && !containsAdminRole(member.getProjectId(), reqVO.getRoleIds())) {
            throw exception(PROJECT_OWNER_ADMIN_REQUIRED);
        }
        replaceRoles(member, reqVO.getRoleIds(), false);
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void removeProjectMember(Long memberId) {
        ProjectMemberDO member = validateMemberExists(memberId);
        ProjectDO project = validateProjectExists(member.getProjectId());
        if (Objects.equals(project.getOwnerUserId(), member.getUserId())) {
            throw exception(PROJECT_OWNER_CANNOT_REMOVE);
        }
        memberRoleMapper.deleteListByMemberId(memberId);
        memberMapper.deleteById(memberId);
        permissionCacheService.evictMemberAuth(TenantContextHolder.getRequiredTenantId(),
                member.getProjectId(), member.getUserId());
    }

    @Override
    public List<ProjectMemberRespVO> getProjectMemberList(Long projectId) {
        validateProjectExists(projectId);
        List<ProjectMemberDO> members = memberMapper.selectListByProjectId(projectId);
        if (members.isEmpty()) {
            return Collections.emptyList();
        }
        List<ProjectMemberRoleDO> relations = memberRoleMapper.selectListByMemberIds(
                members.stream().map(ProjectMemberDO::getId).toList());
        Map<Long, ProjectRoleRespVO> roleMap = roleService.getProjectRoleList(projectId).stream()
                .collect(Collectors.toMap(ProjectRoleRespVO::getId, role -> role));
        Map<Long, List<ProjectRoleRespVO>> memberRoleMap = new LinkedHashMap<>();
        for (ProjectMemberRoleDO relation : relations) {
            ProjectRoleRespVO role = roleMap.get(relation.getRoleId());
            if (role != null) {
                memberRoleMap.computeIfAbsent(relation.getMemberId(), key -> new ArrayList<>()).add(role);
            }
        }
        List<ProjectMemberRespVO> result = new ArrayList<>(members.size());
        for (ProjectMemberDO member : members) {
            ProjectMemberRespVO respVO = new ProjectMemberRespVO();
            respVO.setId(member.getId());
            respVO.setProjectId(member.getProjectId());
            respVO.setUserId(member.getUserId());
            respVO.setCreateTime(member.getCreateTime());
            List<ProjectRoleRespVO> roles = memberRoleMap.getOrDefault(member.getId(), Collections.emptyList());
            roles.sort(Comparator.comparing(ProjectRoleRespVO::getSort).thenComparing(ProjectRoleRespVO::getId));
            respVO.setRoles(roles);
            result.add(respVO);
        }
        return result;
    }

    @Override
    public Long getProjectMemberCount(Long projectId) {
        return memberMapper.selectCountByProjectId(projectId);
    }

    @Override
    public boolean isProjectMember(Long projectId, Long userId) {
        return memberMapper.selectByProjectIdAndUserId(projectId, userId) != null;
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void ensureOwnerAdmin(Long projectId, Long ownerUserId) {
        adminUserApi.validateUser(ownerUserId);
        ProjectMemberDO member = insertOrRestoreMember(projectId, ownerUserId, false);
        Long adminRoleId = roleService.getProjectBuiltinRole(projectId,
                ProjectRoleBuiltinTypeEnum.ADMIN.getValue()).getId();
        replaceRoles(member, Set.of(adminRoleId), true);
    }

    private ProjectMemberDO insertOrRestoreMember(Long projectId, Long userId, boolean rejectActive) {
        Long tenantId = TenantContextHolder.getRequiredTenantId();
        ProjectMemberDO existing = memberMapper.selectByTenantIdAndProjectIdAndUserIdIncludingDeleted(
                tenantId, projectId, userId);
        if (existing == null) {
            ProjectMemberDO member = new ProjectMemberDO();
            member.setProjectId(projectId);
            member.setUserId(userId);
            memberMapper.insert(member);
            permissionCacheService.evictMemberAuth(tenantId, projectId, userId);
            return member;
        }
        if (!Boolean.TRUE.equals(existing.getDeleted())) {
            if (rejectActive) {
                throw exception(PROJECT_MEMBER_DUPLICATE);
            }
            return existing;
        }
        memberMapper.restoreById(existing.getId(), tenantId, LocalDateTime.now());
        existing.setDeleted(false);
        permissionCacheService.evictMemberAuth(tenantId, projectId, userId);
        return existing;
    }

    private void replaceRoles(ProjectMemberDO member, Collection<Long> requestedRoleIds, boolean merge) {
        Set<Long> desiredRoleIds = new LinkedHashSet<>(requestedRoleIds);
        if (desiredRoleIds.isEmpty()) {
            throw exception(PROJECT_MEMBER_ROLE_REQUIRED);
        }
        for (Long roleId : desiredRoleIds) {
            roleService.validateProjectRole(roleId, member.getProjectId());
        }
        Set<Long> currentRoleIds = memberRoleMapper.selectListByMemberId(member.getId()).stream()
                .map(ProjectMemberRoleDO::getRoleId).collect(Collectors.toCollection(LinkedHashSet::new));
        if (merge) {
            desiredRoleIds.addAll(currentRoleIds);
        }
        Set<Long> additions = new LinkedHashSet<>(desiredRoleIds);
        additions.removeAll(currentRoleIds);
        Long tenantId = TenantContextHolder.getRequiredTenantId();
        for (Long roleId : additions) {
            ProjectMemberRoleDO existing = memberRoleMapper.selectIncludingDeleted(
                    tenantId, member.getProjectId(), member.getId(), roleId);
            if (existing == null) {
                ProjectMemberRoleDO relation = new ProjectMemberRoleDO();
                relation.setProjectId(member.getProjectId());
                relation.setMemberId(member.getId());
                relation.setRoleId(roleId);
                memberRoleMapper.insert(relation);
            } else if (Boolean.TRUE.equals(existing.getDeleted())) {
                memberRoleMapper.restoreById(existing.getId(), tenantId, LocalDateTime.now());
            }
        }
        Set<Long> removals = new LinkedHashSet<>(currentRoleIds);
        removals.removeAll(desiredRoleIds);
        if (!removals.isEmpty()) {
            memberRoleMapper.deleteListByMemberIdAndRoleIds(member.getId(), removals);
        }
        permissionCacheService.evictMemberAuth(tenantId, member.getProjectId(), member.getUserId());
    }

    private boolean containsAdminRole(Long projectId, Set<Long> roleIds) {
        for (Long roleId : roleIds) {
            ProjectRoleDO role = roleService.validateProjectRole(roleId, projectId);
            if (ProjectRoleBuiltinTypeEnum.ADMIN.getValue().equals(role.getBuiltinType())) {
                return true;
            }
        }
        return false;
    }

    private ProjectMemberDO validateMemberExists(Long id) {
        ProjectMemberDO member = memberMapper.selectById(id);
        if (member == null) {
            throw exception(PROJECT_MEMBER_NOT_EXISTS);
        }
        return member;
    }

    private ProjectDO validateProjectExists(Long id) {
        ProjectDO project = projectMapper.selectById(id);
        if (project == null) {
            throw exception(PROJECT_NOT_EXISTS);
        }
        return project;
    }

    private Long requiredBuiltinRoleId(Map<String, Long> roleIds, String type) {
        Long roleId = roleIds.get(type);
        if (roleId == null) {
            throw exception(PROJECT_ROLE_NOT_EXISTS);
        }
        return roleId;
    }

}
