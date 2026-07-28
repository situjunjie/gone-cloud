package cn.iocoder.yudao.module.project.convert.workitem;

import cn.iocoder.yudao.framework.common.pojo.PageResult;
import cn.iocoder.yudao.module.project.controller.admin.workitem.vo.ProjectWorkItemRespVO;
import cn.iocoder.yudao.module.project.controller.admin.workitem.vo.ProjectWorkItemSaveReqVO;
import cn.iocoder.yudao.module.project.controller.admin.workitem.vo.WorkItemQueryViewRespVO;
import cn.iocoder.yudao.module.project.controller.admin.workitem.vo.WorkItemQueryViewSaveReqVO;
import cn.iocoder.yudao.module.project.dal.dataobject.workitem.ProjectWorkItemDO;
import cn.iocoder.yudao.module.project.dal.dataobject.workitem.WorkItemQueryViewDO;
import org.mapstruct.Mapper;
import org.mapstruct.Mapping;
import org.mapstruct.factory.Mappers;

import java.util.List;

@Mapper
public interface ProjectWorkItemConvert {

    ProjectWorkItemConvert INSTANCE = Mappers.getMapper(ProjectWorkItemConvert.class);

    @Mapping(target = "workItemNo", ignore = true)
    @Mapping(target = "status", ignore = true)
    @Mapping(target = "creator", ignore = true)
    @Mapping(target = "createTime", ignore = true)
    @Mapping(target = "updater", ignore = true)
    @Mapping(target = "updateTime", ignore = true)
    @Mapping(target = "deleted", ignore = true)
    @Mapping(target = "tenantId", ignore = true)
    @Mapping(target = "transMap", ignore = true)
    @Mapping(target = "estimatedHours", ignore = true)
    @Mapping(target = "actualHours", ignore = true)
    ProjectWorkItemDO convert(ProjectWorkItemSaveReqVO bean);

    @Mapping(target = "assigneeUserName", ignore = true)
    @Mapping(target = "creatorUserName", ignore = true)
    @Mapping(target = "customFields", ignore = true)
    @Mapping(target = "transMap", ignore = true)
    ProjectWorkItemRespVO convert(ProjectWorkItemDO bean);

    PageResult<ProjectWorkItemRespVO> convertPage(PageResult<ProjectWorkItemDO> page);

    @Mapping(target = "userId", ignore = true)
    @Mapping(target = "creator", ignore = true)
    @Mapping(target = "createTime", ignore = true)
    @Mapping(target = "updater", ignore = true)
    @Mapping(target = "updateTime", ignore = true)
    @Mapping(target = "deleted", ignore = true)
    @Mapping(target = "tenantId", ignore = true)
    @Mapping(target = "transMap", ignore = true)
    WorkItemQueryViewDO convert(WorkItemQueryViewSaveReqVO bean);

    WorkItemQueryViewRespVO convert(WorkItemQueryViewDO bean);

    List<WorkItemQueryViewRespVO> convertQueryViewList(List<WorkItemQueryViewDO> list);

}
