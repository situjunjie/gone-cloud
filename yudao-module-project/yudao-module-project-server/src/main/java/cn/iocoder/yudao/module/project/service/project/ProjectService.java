package cn.iocoder.yudao.module.project.service.project;

import cn.iocoder.yudao.framework.common.pojo.PageResult;
import cn.iocoder.yudao.module.project.controller.admin.project.vo.ProjectPageReqVO;
import cn.iocoder.yudao.module.project.controller.admin.project.vo.ProjectSaveReqVO;
import cn.iocoder.yudao.module.project.controller.admin.project.vo.ProjectUpdateAnnouncementReqVO;
import cn.iocoder.yudao.module.project.dal.dataobject.project.ProjectDO;

public interface ProjectService {

    Long createProject(ProjectSaveReqVO createReqVO);

    void updateProject(ProjectSaveReqVO updateReqVO);

    void updateProjectAnnouncement(ProjectUpdateAnnouncementReqVO updateReqVO);

    void archiveProject(Long id);

    ProjectDO getProject(Long id);

    PageResult<ProjectDO> getProjectPage(ProjectPageReqVO pageReqVO);

    ProjectDO validateProjectExists(Long id);

    ProjectDO validateProjectUsable(Long id);

}
