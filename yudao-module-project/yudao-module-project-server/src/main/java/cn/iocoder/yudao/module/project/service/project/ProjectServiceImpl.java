package cn.iocoder.yudao.module.project.service.project;

import cn.hutool.core.util.ObjUtil;
import cn.iocoder.yudao.framework.common.pojo.PageResult;
import cn.iocoder.yudao.module.project.controller.admin.project.vo.ProjectMemberAddReqVO;
import cn.iocoder.yudao.module.project.controller.admin.project.vo.ProjectMemberUpdateRoleReqVO;
import cn.iocoder.yudao.module.project.controller.admin.project.vo.ProjectPageReqVO;
import cn.iocoder.yudao.module.project.controller.admin.project.vo.ProjectSaveReqVO;
import cn.iocoder.yudao.module.project.controller.admin.project.vo.ProjectUpdateAnnouncementReqVO;
import cn.iocoder.yudao.module.project.convert.project.ProjectConvert;
import cn.iocoder.yudao.module.project.dal.dataobject.project.ProjectDO;
import cn.iocoder.yudao.module.project.dal.dataobject.project.ProjectMemberDO;
import cn.iocoder.yudao.module.project.dal.mysql.project.ProjectMapper;
import cn.iocoder.yudao.module.project.dal.mysql.project.ProjectMemberMapper;
import cn.iocoder.yudao.module.project.enums.ProjectMemberRoleEnum;
import cn.iocoder.yudao.module.project.enums.ProjectStatusEnum;
import cn.iocoder.yudao.module.project.enums.ProjectVisibilityEnum;
import cn.iocoder.yudao.module.system.api.user.AdminUserApi;
import cn.iocoder.yudao.framework.tenant.core.context.TenantContextHolder;
import jakarta.annotation.Resource;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.validation.annotation.Validated;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Objects;

import static cn.iocoder.yudao.framework.common.exception.util.ServiceExceptionUtil.exception;
import static cn.iocoder.yudao.module.project.enums.ErrorCodeConstants.*;

@Service
@Validated
public class ProjectServiceImpl implements ProjectService {

    @Resource
    private ProjectMapper projectMapper;
    @Resource
    private ProjectMemberMapper projectMemberMapper;
    @Resource
    private AdminUserApi adminUserApi;

    @Override
    @Transactional(rollbackFor = Exception.class)
    public Long createProject(ProjectSaveReqVO createReqVO) {
        validateProjectCodeUnique(null, createReqVO.getCode());
        adminUserApi.validateUser(createReqVO.getOwnerUserId());

        ProjectDO project = ProjectConvert.INSTANCE.convert(createReqVO);
        project.setStatus(ProjectStatusEnum.ACTIVE.getValue());
        project.setSort(ObjUtil.defaultIfNull(project.getSort(), 0));
        projectMapper.insert(project);
        project.setTenantId(TenantContextHolder.getRequiredTenantId());

        insertOrRestoreMember(project, createReqVO.getOwnerUserId(), ProjectMemberRoleEnum.OWNER.getValue());
        return project.getId();
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void updateProject(ProjectSaveReqVO updateReqVO) {
        ProjectDO oldProject = validateProjectExists(updateReqVO.getId());
        validateProjectCodeUnique(updateReqVO.getId(), updateReqVO.getCode());
        adminUserApi.validateUser(updateReqVO.getOwnerUserId());

        ProjectDO updateObj = ProjectConvert.INSTANCE.convert(updateReqVO);
        updateObj.setStatus(null);
        updateObj.setSort(ObjUtil.defaultIfNull(updateObj.getSort(), oldProject.getSort()));
        projectMapper.updateById(updateObj);

        if (!Objects.equals(oldProject.getOwnerUserId(), updateReqVO.getOwnerUserId())) {
            transferOwner(oldProject, updateReqVO.getOwnerUserId());
        }
    }

    @Override
    public void updateProjectAnnouncement(ProjectUpdateAnnouncementReqVO updateReqVO) {
        validateProjectExists(updateReqVO.getProjectId());
        projectMapper.updateAnnouncementById(updateReqVO.getProjectId(), updateReqVO.getAnnouncement(),
                LocalDateTime.now());
    }

    @Override
    public void archiveProject(Long id) {
        validateProjectExists(id);
        ProjectDO updateObj = new ProjectDO();
        updateObj.setId(id);
        updateObj.setStatus(ProjectStatusEnum.ARCHIVED.getValue());
        projectMapper.updateById(updateObj);
    }

    @Override
    public ProjectDO getProject(Long id) {
        return projectMapper.selectById(id);
    }

    @Override
    public PageResult<ProjectDO> getProjectPage(ProjectPageReqVO pageReqVO) {
        return projectMapper.selectPage(pageReqVO);
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public Long addProjectMember(ProjectMemberAddReqVO addReqVO) {
        ProjectDO project = validateProjectExists(addReqVO.getProjectId());
        adminUserApi.validateUser(addReqVO.getUserId());
        if (ProjectMemberRoleEnum.OWNER.getValue().equals(addReqVO.getRole())) {
            throw exception(PROJECT_OWNER_ROLE_CHANGE_UNSUPPORTED);
        }
        return insertOrRestoreMember(project, addReqVO.getUserId(), addReqVO.getRole()).getId();
    }

    @Override
    public void updateProjectMemberRole(ProjectMemberUpdateRoleReqVO updateReqVO) {
        ProjectMemberDO member = validateProjectMemberExists(updateReqVO.getId());
        if (ProjectMemberRoleEnum.OWNER.getValue().equals(updateReqVO.getRole())) {
            throw exception(PROJECT_OWNER_ROLE_CHANGE_UNSUPPORTED);
        }
        if (ProjectMemberRoleEnum.OWNER.getValue().equals(member.getRole())) {
            throw exception(PROJECT_OWNER_CANNOT_REMOVE);
        }
        projectMemberMapper.updateRoleById(updateReqVO.getId(), updateReqVO.getRole(), LocalDateTime.now());
    }

    @Override
    public void removeProjectMember(Long id) {
        ProjectMemberDO member = validateProjectMemberExists(id);
        if (ProjectMemberRoleEnum.OWNER.getValue().equals(member.getRole())) {
            throw exception(PROJECT_OWNER_CANNOT_REMOVE);
        }
        projectMemberMapper.deleteById(id);
    }

    @Override
    public List<ProjectMemberDO> getProjectMemberList(Long projectId) {
        validateProjectExists(projectId);
        return projectMemberMapper.selectListByProjectId(projectId);
    }

    @Override
    public Long getProjectMemberCount(Long projectId) {
        return projectMemberMapper.selectCountByProjectId(projectId);
    }

    @Override
    public ProjectDO validateProjectExists(Long id) {
        ProjectDO project = projectMapper.selectById(id);
        if (project == null) {
            throw exception(PROJECT_NOT_EXISTS);
        }
        return project;
    }

    @Override
    public ProjectDO validateProjectUsable(Long id) {
        ProjectDO project = validateProjectExists(id);
        if (ProjectStatusEnum.ARCHIVED.getValue().equals(project.getStatus())) {
            throw exception(PROJECT_ARCHIVED);
        }
        return project;
    }

    @Override
    public boolean hasProjectAccess(Long projectId, Long userId) {
        ProjectDO project = validateProjectExists(projectId);
        if (ProjectVisibilityEnum.PUBLIC.getValue().equals(project.getVisibility())) {
            return true;
        }
        return projectMemberMapper.selectByProjectIdAndUserId(projectId, userId) != null;
    }

    private void validateProjectCodeUnique(Long id, String code) {
        ProjectDO project = projectMapper.selectByCode(code);
        if (project == null) {
            return;
        }
        if (id == null || !Objects.equals(project.getId(), id)) {
            throw exception(PROJECT_CODE_DUPLICATE);
        }
    }

    private ProjectMemberDO validateProjectMemberExists(Long id) {
        ProjectMemberDO member = projectMemberMapper.selectById(id);
        if (member == null) {
            throw exception(PROJECT_MEMBER_NOT_EXISTS);
        }
        return member;
    }

    private void transferOwner(ProjectDO project, Long newOwnerUserId) {
        ProjectMemberDO oldOwnerMember = projectMemberMapper.selectOwnerByProjectId(project.getId());
        if (oldOwnerMember != null) {
            projectMemberMapper.updateRoleById(oldOwnerMember.getId(), ProjectMemberRoleEnum.ADMIN.getValue(),
                    LocalDateTime.now());
        }
        insertOrRestoreMember(project, newOwnerUserId, ProjectMemberRoleEnum.OWNER.getValue());
    }

    private ProjectMemberDO insertOrRestoreMember(ProjectDO project, Long userId, String role) {
        ProjectMemberDO existingMember = projectMemberMapper
                .selectByTenantIdAndProjectIdAndUserIdIncludingDeleted(project.getTenantId(), project.getId(), userId);
        if (existingMember == null) {
            ProjectMemberDO member = new ProjectMemberDO();
            member.setProjectId(project.getId());
            member.setUserId(userId);
            member.setRole(role);
            projectMemberMapper.insert(member);
            return member;
        }
        if (Boolean.TRUE.equals(existingMember.getDeleted())) {
            projectMemberMapper.restoreById(existingMember.getId(), project.getTenantId(), role, LocalDateTime.now());
            existingMember.setRole(role);
            existingMember.setDeleted(false);
            return existingMember;
        }
        if (ProjectMemberRoleEnum.OWNER.getValue().equals(role)) {
            projectMemberMapper.updateRoleById(existingMember.getId(), role, LocalDateTime.now());
            existingMember.setRole(role);
            return existingMember;
        }
        throw exception(PROJECT_MEMBER_DUPLICATE);
    }

}
