package cn.iocoder.yudao.module.project.convert.project;

import cn.iocoder.yudao.module.project.controller.admin.sprint.vo.ProjectSprintCreateReqVO;
import cn.iocoder.yudao.module.project.controller.admin.sprint.vo.ProjectSprintRespVO;
import cn.iocoder.yudao.module.project.dal.dataobject.project.ProjectSprintDO;
import org.mapstruct.Mapper;
import org.mapstruct.Mapping;
import org.mapstruct.factory.Mappers;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;

@Mapper
public interface ProjectSprintConvert {

    ProjectSprintConvert INSTANCE = Mappers.getMapper(ProjectSprintConvert.class);

    @Mapping(target = "status", ignore = true)
    @Mapping(target = "plannedStartTime", expression = "java(toStartOfDay(bean.getStartDate()))")
    @Mapping(target = "plannedEndTime", expression = "java(toStartOfDay(bean.getEndDate()))")
    @Mapping(target = "actualStartTime", ignore = true)
    @Mapping(target = "actualEndTime", ignore = true)
    @Mapping(target = "id", ignore = true)
    @Mapping(target = "creator", ignore = true)
    @Mapping(target = "createTime", ignore = true)
    @Mapping(target = "updater", ignore = true)
    @Mapping(target = "updateTime", ignore = true)
    @Mapping(target = "deleted", ignore = true)
    @Mapping(target = "tenantId", ignore = true)
    @Mapping(target = "transMap", ignore = true)
    ProjectSprintDO convert(ProjectSprintCreateReqVO bean);

    @Mapping(source = "plannedStartTime", target = "startDate")
    @Mapping(source = "plannedEndTime", target = "endDate")
    @Mapping(target = "ownerUserName", ignore = true)
    @Mapping(target = "ownerUserAvatar", ignore = true)
    @Mapping(target = "usedHours", ignore = true)
    @Mapping(target = "totalWorkItemCount", ignore = true)
    @Mapping(target = "completedWorkItemCount", ignore = true)
    ProjectSprintRespVO convert(ProjectSprintDO bean);

    List<ProjectSprintRespVO> convertList(List<ProjectSprintDO> beans);

    default LocalDateTime toStartOfDay(LocalDate date) {
        return date != null ? date.atStartOfDay() : null;
    }

    default LocalDate toLocalDate(LocalDateTime dateTime) {
        return dateTime != null ? dateTime.toLocalDate() : null;
    }

}
