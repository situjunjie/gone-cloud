package cn.iocoder.yudao.module.project.dal.mysql.project;

import cn.iocoder.yudao.framework.mybatis.core.mapper.BaseMapperX;
import cn.iocoder.yudao.framework.mybatis.core.query.LambdaQueryWrapperX;
import cn.iocoder.yudao.module.project.dal.dataobject.project.ProjectSprintDO;
import org.apache.ibatis.annotations.Mapper;

import java.util.List;

@Mapper
public interface ProjectSprintMapper extends BaseMapperX<ProjectSprintDO> {

    default List<ProjectSprintDO> selectListByProjectId(Long projectId) {
        return selectList(new LambdaQueryWrapperX<ProjectSprintDO>()
                .eq(ProjectSprintDO::getProjectId, projectId)
                .orderByDesc(ProjectSprintDO::getId));
    }

}
