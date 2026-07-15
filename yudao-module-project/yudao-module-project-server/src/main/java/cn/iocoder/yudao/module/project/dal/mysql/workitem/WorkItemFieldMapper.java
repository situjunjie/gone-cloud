package cn.iocoder.yudao.module.project.dal.mysql.workitem;

import cn.iocoder.yudao.framework.mybatis.core.mapper.BaseMapperX;
import cn.iocoder.yudao.framework.mybatis.core.query.LambdaQueryWrapperX;
import cn.iocoder.yudao.module.project.dal.dataobject.workitem.WorkItemFieldDO;
import cn.iocoder.yudao.module.project.enums.WorkItemFieldScopeEnum;
import org.apache.ibatis.annotations.Mapper;

import java.util.Collection;
import java.util.List;

@Mapper
public interface WorkItemFieldMapper extends BaseMapperX<WorkItemFieldDO> {

    default WorkItemFieldDO selectByScopeProjectIdAndCode(String scope, Long projectId, String code) {
        return selectOne(new LambdaQueryWrapperX<WorkItemFieldDO>()
                .eq(WorkItemFieldDO::getScope, scope)
                .eq(WorkItemFieldDO::getProjectId, projectId)
                .eq(WorkItemFieldDO::getCode, code));
    }

    default Long selectCountByCode(String code) {
        return selectCount(new LambdaQueryWrapperX<WorkItemFieldDO>()
                .eq(WorkItemFieldDO::getCode, code));
    }

    default List<WorkItemFieldDO> selectListByEffectiveProjectId(Long projectId) {
        return selectList(new LambdaQueryWrapperX<WorkItemFieldDO>()
                .and(query -> query
                        .eq(WorkItemFieldDO::getScope, WorkItemFieldScopeEnum.GLOBAL.getValue())
                        .eq(WorkItemFieldDO::getProjectId, WorkItemFieldScopeEnum.GLOBAL_PROJECT_ID)
                        .or()
                        .eq(WorkItemFieldDO::getScope, WorkItemFieldScopeEnum.PROJECT.getValue())
                        .eq(WorkItemFieldDO::getProjectId, projectId))
                .orderByAsc(WorkItemFieldDO::getSort).orderByAsc(WorkItemFieldDO::getId));
    }

    default List<WorkItemFieldDO> selectGlobalList() {
        return selectList(new LambdaQueryWrapperX<WorkItemFieldDO>()
                .eq(WorkItemFieldDO::getScope, WorkItemFieldScopeEnum.GLOBAL.getValue())
                .eq(WorkItemFieldDO::getProjectId, WorkItemFieldScopeEnum.GLOBAL_PROJECT_ID)
                .orderByAsc(WorkItemFieldDO::getSort).orderByAsc(WorkItemFieldDO::getId));
    }

    default List<WorkItemFieldDO> selectListByIds(Collection<Long> ids) {
        if (ids == null || ids.isEmpty()) {
            return List.of();
        }
        return selectList(WorkItemFieldDO::getId, ids);
    }

}
