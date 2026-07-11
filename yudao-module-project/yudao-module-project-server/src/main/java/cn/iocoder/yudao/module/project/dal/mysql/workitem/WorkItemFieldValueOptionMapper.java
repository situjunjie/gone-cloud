package cn.iocoder.yudao.module.project.dal.mysql.workitem;

import cn.iocoder.yudao.framework.mybatis.core.mapper.BaseMapperX;
import cn.iocoder.yudao.module.project.dal.dataobject.workitem.WorkItemFieldValueOptionDO;
import org.apache.ibatis.annotations.Delete;
import org.apache.ibatis.annotations.Mapper;

import java.util.Collection;
import java.util.List;

@Mapper
public interface WorkItemFieldValueOptionMapper extends BaseMapperX<WorkItemFieldValueOptionDO> {

    default List<WorkItemFieldValueOptionDO> selectListByWorkItemIds(Collection<Long> workItemIds) {
        if (workItemIds == null || workItemIds.isEmpty()) {
            return List.of();
        }
        return selectList(WorkItemFieldValueOptionDO::getWorkItemId, workItemIds);
    }

    @Delete("DELETE FROM proj_work_item_field_value_option WHERE work_item_id = #{workItemId}")
    int physicalDeleteByWorkItemId(Long workItemId);

}
