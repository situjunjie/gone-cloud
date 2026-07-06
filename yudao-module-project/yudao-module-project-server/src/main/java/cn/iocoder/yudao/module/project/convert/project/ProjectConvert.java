package cn.iocoder.yudao.module.project.convert.project;

import cn.iocoder.yudao.framework.common.pojo.PageResult;
import cn.iocoder.yudao.module.project.controller.admin.project.vo.ProjectMemberRespVO;
import cn.iocoder.yudao.module.project.controller.admin.project.vo.ProjectRespVO;
import cn.iocoder.yudao.module.project.controller.admin.project.vo.ProjectSaveReqVO;
import cn.iocoder.yudao.module.project.dal.dataobject.project.ProjectDO;
import cn.iocoder.yudao.module.project.dal.dataobject.project.ProjectMemberDO;
import org.mapstruct.Mapper;
import org.mapstruct.Mapping;
import org.mapstruct.factory.Mappers;

import java.util.List;

@Mapper
public interface ProjectConvert {

    ProjectConvert INSTANCE = Mappers.getMapper(ProjectConvert.class);

    @Mapping(target = "status", ignore = true)
    @Mapping(target = "creator", ignore = true)
    @Mapping(target = "createTime", ignore = true)
    @Mapping(target = "updater", ignore = true)
    @Mapping(target = "updateTime", ignore = true)
    @Mapping(target = "deleted", ignore = true)
    @Mapping(target = "tenantId", ignore = true)
    @Mapping(target = "transMap", ignore = true)
    ProjectDO convert(ProjectSaveReqVO bean);

    @Mapping(source = "creator", target = "creatorUserId")
    @Mapping(target = "ownerUserName", ignore = true)
    @Mapping(target = "ownerUserAvatar", ignore = true)
    @Mapping(target = "creatorUserName", ignore = true)
    @Mapping(target = "creatorUserAvatar", ignore = true)
    @Mapping(target = "memberCount", ignore = true)
    @Mapping(target = "transMap", ignore = true)
    ProjectRespVO convert(ProjectDO bean);

    PageResult<ProjectRespVO> convertPage(PageResult<ProjectDO> page);

    @Mapping(target = "userName", ignore = true)
    ProjectMemberRespVO convert(ProjectMemberDO bean);

    List<ProjectMemberRespVO> convertMemberList(List<ProjectMemberDO> list);

}
