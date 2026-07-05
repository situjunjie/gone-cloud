package cn.iocoder.yudao.module.project.dal.mysql.workitem;

import cn.iocoder.yudao.framework.mybatis.core.mapper.BaseMapperX;
import cn.iocoder.yudao.framework.mybatis.core.query.LambdaQueryWrapperX;
import cn.iocoder.yudao.module.project.dal.dataobject.workitem.WorkItemQueryViewDO;
import cn.iocoder.yudao.module.project.enums.WorkItemQueryViewScopeEnum;
import org.apache.ibatis.annotations.Mapper;

import java.util.List;

@Mapper
public interface WorkItemQueryViewMapper extends BaseMapperX<WorkItemQueryViewDO> {

    Long SYSTEM_USER_ID = 0L;

    default WorkItemQueryViewDO selectByScopeUserIdAndCode(String scope, Long userId, String code) {
        return selectOne(new LambdaQueryWrapperX<WorkItemQueryViewDO>()
                .eq(WorkItemQueryViewDO::getScope, scope)
                .eq(WorkItemQueryViewDO::getUserId, userId)
                .eq(WorkItemQueryViewDO::getCode, code));
    }

    default WorkItemQueryViewDO selectSystemByCode(String code) {
        return selectByScopeUserIdAndCode(WorkItemQueryViewScopeEnum.SYSTEM.getValue(), SYSTEM_USER_ID, code);
    }

    default List<WorkItemQueryViewDO> selectSystemList() {
        return selectList(new LambdaQueryWrapperX<WorkItemQueryViewDO>()
                .eq(WorkItemQueryViewDO::getScope, WorkItemQueryViewScopeEnum.SYSTEM.getValue())
                .eq(WorkItemQueryViewDO::getUserId, SYSTEM_USER_ID)
                .orderByAsc(WorkItemQueryViewDO::getSort)
                .orderByDesc(WorkItemQueryViewDO::getId));
    }

    default List<WorkItemQueryViewDO> selectUserList(Long userId) {
        return selectList(new LambdaQueryWrapperX<WorkItemQueryViewDO>()
                .eq(WorkItemQueryViewDO::getScope, WorkItemQueryViewScopeEnum.USER.getValue())
                .eq(WorkItemQueryViewDO::getUserId, userId)
                .orderByAsc(WorkItemQueryViewDO::getSort)
                .orderByDesc(WorkItemQueryViewDO::getId));
    }

}
