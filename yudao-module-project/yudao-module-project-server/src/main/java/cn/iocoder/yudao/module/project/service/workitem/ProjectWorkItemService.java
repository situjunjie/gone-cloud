package cn.iocoder.yudao.module.project.service.workitem;

import cn.iocoder.yudao.framework.common.pojo.PageResult;
import cn.iocoder.yudao.module.project.controller.admin.workitem.vo.ProjectWorkItemGroupCountReqVO;
import cn.iocoder.yudao.module.project.controller.admin.workitem.vo.ProjectWorkItemGroupCountRespVO;
import cn.iocoder.yudao.module.project.controller.admin.workitem.vo.ProjectWorkItemPageReqVO;
import cn.iocoder.yudao.module.project.controller.admin.workitem.vo.ProjectWorkItemRespVO;
import cn.iocoder.yudao.module.project.controller.admin.workitem.vo.ProjectWorkItemSaveReqVO;
import cn.iocoder.yudao.module.project.controller.admin.workitem.vo.ProjectWorkItemUpdateFieldReqVO;
import cn.iocoder.yudao.module.project.dal.dataobject.workitem.ProjectWorkItemDO;

public interface ProjectWorkItemService {

    Long createWorkItem(ProjectWorkItemSaveReqVO createReqVO);

    void updateWorkItem(ProjectWorkItemSaveReqVO updateReqVO);

    void updateWorkItemField(ProjectWorkItemUpdateFieldReqVO updateReqVO);

    void deleteWorkItem(Long id);

    ProjectWorkItemRespVO getWorkItem(Long id);

    PageResult<ProjectWorkItemRespVO> getWorkItemPage(ProjectWorkItemPageReqVO pageReqVO);

    ProjectWorkItemGroupCountRespVO getWorkItemGroupCount(ProjectWorkItemGroupCountReqVO reqVO);

    ProjectWorkItemDO validateWorkItemExists(Long id);

}
