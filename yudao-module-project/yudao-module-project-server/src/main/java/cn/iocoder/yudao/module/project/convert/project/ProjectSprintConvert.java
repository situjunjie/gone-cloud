package cn.iocoder.yudao.module.project.convert.project;

import cn.iocoder.yudao.module.project.controller.admin.sprint.vo.ProjectSprintCreateReqVO;
import cn.iocoder.yudao.module.project.dal.dataobject.project.ProjectSprintDO;
import org.mapstruct.Mapper;
import org.mapstruct.Mapping;
import org.mapstruct.factory.Mappers;

import java.time.LocalDate;
import java.time.LocalDateTime;

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

    default LocalDateTime toStartOfDay(LocalDate date) {
        return date != null ? date.atStartOfDay() : null;
    }

}
