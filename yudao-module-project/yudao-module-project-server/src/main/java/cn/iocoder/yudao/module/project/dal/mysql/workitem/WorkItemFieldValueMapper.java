package cn.iocoder.yudao.module.project.dal.mysql.workitem;

import cn.iocoder.yudao.framework.mybatis.core.mapper.BaseMapperX;
import cn.iocoder.yudao.module.project.dal.dataobject.workitem.WorkItemFieldValueDO;
import org.apache.ibatis.annotations.Delete;
import org.apache.ibatis.annotations.Mapper;

import java.util.Collection;
import java.util.List;

@Mapper
public interface WorkItemFieldValueMapper extends BaseMapperX<WorkItemFieldValueDO> {

    default List<WorkItemFieldValueDO> selectListByWorkItemIds(Collection<Long> workItemIds) {
        if (workItemIds == null || workItemIds.isEmpty()) {
            return List.of();
        }
        return selectList(WorkItemFieldValueDO::getWorkItemId, workItemIds);
    }

    default Long selectCountByWorkItemId(Long workItemId) {
        return selectCount(WorkItemFieldValueDO::getWorkItemId, workItemId);
    }

    @Delete("DELETE FROM proj_work_item_field_value WHERE work_item_id = #{workItemId}")
    int physicalDeleteByWorkItemId(Long workItemId);

}
