package cn.iocoder.yudao.module.project.service.project;

import cn.iocoder.yudao.framework.common.pojo.PageResult;
import cn.iocoder.yudao.module.project.controller.admin.project.vo.ProjectMemberAddReqVO;
import cn.iocoder.yudao.module.project.controller.admin.project.vo.ProjectMemberUpdateRoleReqVO;
import cn.iocoder.yudao.module.project.controller.admin.project.vo.ProjectPageReqVO;
import cn.iocoder.yudao.module.project.controller.admin.project.vo.ProjectSaveReqVO;
import cn.iocoder.yudao.module.project.controller.admin.project.vo.ProjectUpdateAnnouncementReqVO;
import cn.iocoder.yudao.module.project.dal.dataobject.project.ProjectDO;
import cn.iocoder.yudao.module.project.dal.dataobject.project.ProjectMemberDO;

import java.util.List;

public interface ProjectService {

    Long createProject(ProjectSaveReqVO createReqVO);

    void updateProject(ProjectSaveReqVO updateReqVO);

    void updateProjectAnnouncement(ProjectUpdateAnnouncementReqVO updateReqVO);

    void archiveProject(Long id);

    ProjectDO getProject(Long id);

    PageResult<ProjectDO> getProjectPage(ProjectPageReqVO pageReqVO);

    Long addProjectMember(ProjectMemberAddReqVO addReqVO);

    void updateProjectMemberRole(ProjectMemberUpdateRoleReqVO updateReqVO);

    void removeProjectMember(Long id);

    List<ProjectMemberDO> getProjectMemberList(Long projectId);

    Long getProjectMemberCount(Long projectId);

    ProjectDO validateProjectExists(Long id);

    ProjectDO validateProjectUsable(Long id);

    boolean hasProjectAccess(Long projectId, Long userId);

}
