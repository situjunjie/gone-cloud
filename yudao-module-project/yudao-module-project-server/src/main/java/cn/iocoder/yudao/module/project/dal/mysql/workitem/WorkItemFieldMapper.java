package cn.iocoder.yudao.module.project.dal.mysql.workitem;

import cn.iocoder.yudao.framework.mybatis.core.mapper.BaseMapperX;
import cn.iocoder.yudao.framework.mybatis.core.query.LambdaQueryWrapperX;
import cn.iocoder.yudao.module.project.dal.dataobject.workitem.WorkItemFieldDO;
import org.apache.ibatis.annotations.Mapper;

import java.util.Collection;
import java.util.List;

@Mapper
public interface WorkItemFieldMapper extends BaseMapperX<WorkItemFieldDO> {

    default WorkItemFieldDO selectByProjectIdAndCode(Long projectId, String code) {
        return selectOne(WorkItemFieldDO::getProjectId, projectId, WorkItemFieldDO::getCode, code);
    }

    default List<WorkItemFieldDO> selectListByProjectId(Long projectId) {
        return selectList(new LambdaQueryWrapperX<WorkItemFieldDO>()
                .eq(WorkItemFieldDO::getProjectId, projectId)
                .orderByAsc(WorkItemFieldDO::getSort).orderByAsc(WorkItemFieldDO::getId));
    }

    default List<WorkItemFieldDO> selectListByIds(Collection<Long> ids) {
        if (ids == null || ids.isEmpty()) {
            return List.of();
        }
        return selectList(WorkItemFieldDO::getId, ids);
    }

}
