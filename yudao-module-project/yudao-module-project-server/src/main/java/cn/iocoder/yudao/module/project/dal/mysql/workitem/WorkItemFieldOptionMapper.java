package cn.iocoder.yudao.module.project.dal.mysql.workitem;

import cn.iocoder.yudao.framework.mybatis.core.mapper.BaseMapperX;
import cn.iocoder.yudao.framework.mybatis.core.query.LambdaQueryWrapperX;
import cn.iocoder.yudao.module.project.dal.dataobject.workitem.WorkItemFieldOptionDO;
import org.apache.ibatis.annotations.Mapper;

import java.util.Collection;
import java.util.List;

@Mapper
public interface WorkItemFieldOptionMapper extends BaseMapperX<WorkItemFieldOptionDO> {

    default WorkItemFieldOptionDO selectByFieldIdAndCode(Long fieldId, String code) {
        return selectOne(WorkItemFieldOptionDO::getFieldId, fieldId, WorkItemFieldOptionDO::getCode, code);
    }

    default List<WorkItemFieldOptionDO> selectListByFieldId(Long fieldId) {
        return selectList(new LambdaQueryWrapperX<WorkItemFieldOptionDO>()
                .eq(WorkItemFieldOptionDO::getFieldId, fieldId)
                .orderByAsc(WorkItemFieldOptionDO::getSort).orderByAsc(WorkItemFieldOptionDO::getId));
    }

    default List<WorkItemFieldOptionDO> selectListByFieldIds(Collection<Long> fieldIds) {
        if (fieldIds == null || fieldIds.isEmpty()) {
            return List.of();
        }
        return selectList(new LambdaQueryWrapperX<WorkItemFieldOptionDO>()
                .in(WorkItemFieldOptionDO::getFieldId, fieldIds)
                .orderByAsc(WorkItemFieldOptionDO::getSort).orderByAsc(WorkItemFieldOptionDO::getId));
    }

    default List<WorkItemFieldOptionDO> selectListByIds(Collection<Long> ids) {
        if (ids == null || ids.isEmpty()) {
            return List.of();
        }
        return selectList(WorkItemFieldOptionDO::getId, ids);
    }

}
