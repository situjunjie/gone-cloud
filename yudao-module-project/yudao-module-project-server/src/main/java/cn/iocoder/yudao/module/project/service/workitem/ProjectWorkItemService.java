package cn.iocoder.yudao.module.project.service.workitem;

import cn.iocoder.yudao.framework.common.pojo.PageResult;
import cn.iocoder.yudao.module.project.controller.admin.workitem.vo.ProjectWorkItemAssignReqVO;
import cn.iocoder.yudao.module.project.controller.admin.workitem.vo.ProjectWorkItemPageReqVO;
import cn.iocoder.yudao.module.project.controller.admin.workitem.vo.ProjectWorkItemSaveReqVO;
import cn.iocoder.yudao.module.project.dal.dataobject.workitem.ProjectWorkItemDO;

public interface ProjectWorkItemService {

    Long createWorkItem(ProjectWorkItemSaveReqVO createReqVO);

    void updateWorkItem(ProjectWorkItemSaveReqVO updateReqVO);

    void deleteWorkItem(Long id);

    ProjectWorkItemDO getWorkItem(Long id);

    PageResult<ProjectWorkItemDO> getWorkItemPage(ProjectWorkItemPageReqVO pageReqVO);

    void assignWorkItem(ProjectWorkItemAssignReqVO assignReqVO);

    ProjectWorkItemDO validateWorkItemExists(Long id);

}
