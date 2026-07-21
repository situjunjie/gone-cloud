package cn.iocoder.yudao.module.project.service.project;

import cn.hutool.core.util.ObjUtil;
import cn.iocoder.yudao.framework.common.pojo.PageResult;
import cn.iocoder.yudao.module.project.controller.admin.project.vo.ProjectPageReqVO;
import cn.iocoder.yudao.module.project.controller.admin.project.vo.ProjectSaveReqVO;
import cn.iocoder.yudao.module.project.controller.admin.project.vo.ProjectUpdateAnnouncementReqVO;
import cn.iocoder.yudao.module.project.convert.project.ProjectConvert;
import cn.iocoder.yudao.module.project.dal.dataobject.project.ProjectDO;
import cn.iocoder.yudao.module.project.dal.mysql.project.ProjectMapper;
import cn.iocoder.yudao.module.project.enums.ProjectStatusEnum;
import cn.iocoder.yudao.module.project.enums.ProjectVisibilityEnum;
import cn.iocoder.yudao.module.system.api.user.AdminUserApi;
import jakarta.annotation.Resource;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.validation.annotation.Validated;

import java.time.LocalDateTime;
import java.util.Map;
import java.util.Objects;

import static cn.iocoder.yudao.framework.security.core.util.SecurityFrameworkUtils.getLoginUserId;

import static cn.iocoder.yudao.framework.common.exception.util.ServiceExceptionUtil.exception;
import static cn.iocoder.yudao.module.project.enums.ErrorCodeConstants.*;

@Service
@Validated
public class ProjectServiceImpl implements ProjectService {

    @Resource
    private ProjectMapper projectMapper;
    @Resource
    private ProjectRoleService roleService;
    @Resource
    private ProjectMemberService memberService;
    @Resource
    private ProjectPermissionService permissionService;
    @Resource
    private AdminUserApi adminUserApi;

    @Override
    @Transactional(rollbackFor = Exception.class)
    public Long createProject(ProjectSaveReqVO createReqVO) {
        validatePrivateVisibility(createReqVO.getVisibility());
        validateProjectCodeUnique(null, createReqVO.getCode());
        adminUserApi.validateUser(createReqVO.getOwnerUserId());

        ProjectDO project = ProjectConvert.INSTANCE.convert(createReqVO);
        project.setStatus(ProjectStatusEnum.ACTIVE.getValue());
        project.setSort(ObjUtil.defaultIfNull(project.getSort(), 0));
        projectMapper.insert(project);
        Map<String, Long> builtinRoleIds = roleService.snapshotGlobalRoles(project.getId());
        memberService.initializeProjectMembers(project.getId(), Objects.requireNonNull(getLoginUserId()),
                createReqVO.getOwnerUserId(), builtinRoleIds);
        return project.getId();
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void updateProject(ProjectSaveReqVO updateReqVO) {
        validatePrivateVisibility(updateReqVO.getVisibility());
        ProjectDO oldProject = validateProjectExists(updateReqVO.getId());
        validateProjectCodeUnique(updateReqVO.getId(), updateReqVO.getCode());
        adminUserApi.validateUser(updateReqVO.getOwnerUserId());

        boolean ownerChanged = !Objects.equals(oldProject.getOwnerUserId(), updateReqVO.getOwnerUserId());
        if (ownerChanged) {
            permissionService.validateAdmin(oldProject.getId(), getLoginUserId());
        }
        ProjectDO updateObj = ProjectConvert.INSTANCE.convert(updateReqVO);
        updateObj.setStatus(null);
        updateObj.setSort(ObjUtil.defaultIfNull(updateObj.getSort(), oldProject.getSort()));
        projectMapper.updateById(updateObj);

        if (ownerChanged) {
            memberService.ensureOwnerAdmin(oldProject.getId(), updateReqVO.getOwnerUserId());
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
        return projectMapper.selectPage(pageReqVO, Objects.requireNonNull(getLoginUserId()));
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

    private void validateProjectCodeUnique(Long id, String code) {
        ProjectDO project = projectMapper.selectByCode(code);
        if (project == null) {
            return;
        }
        if (id == null || !Objects.equals(project.getId(), id)) {
            throw exception(PROJECT_CODE_DUPLICATE);
        }
    }

    private void validatePrivateVisibility(String visibility) {
        if (!ProjectVisibilityEnum.PRIVATE.getValue().equals(visibility)) {
            throw exception(PROJECT_ONLY_PRIVATE_SUPPORTED);
        }
    }

}
