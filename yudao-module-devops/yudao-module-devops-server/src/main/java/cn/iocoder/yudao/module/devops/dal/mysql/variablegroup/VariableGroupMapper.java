package cn.iocoder.yudao.module.devops.dal.mysql.variablegroup;

import cn.iocoder.yudao.framework.common.pojo.PageResult;
import cn.iocoder.yudao.framework.mybatis.core.mapper.BaseMapperX;
import cn.iocoder.yudao.framework.mybatis.core.query.LambdaQueryWrapperX;
import cn.iocoder.yudao.module.devops.controller.admin.variablegroup.vo.VariableGroupPageReqVO;
import cn.iocoder.yudao.module.devops.dal.dataobject.variablegroup.VariableGroupDO;
import org.apache.ibatis.annotations.Mapper;

import java.util.List;

@Mapper
public interface VariableGroupMapper extends BaseMapperX<VariableGroupDO> {
    default VariableGroupDO selectByNameAndScope(String name, String scope, Long appId) {
        return selectOne(new LambdaQueryWrapperX<VariableGroupDO>().eq(VariableGroupDO::getName, name)
                .eq(VariableGroupDO::getScope, scope).eq(VariableGroupDO::getAppId, appId == null ? 0L : appId));
    }
    default List<VariableGroupDO> selectListByAppId(Long appId) {
        return selectList(new LambdaQueryWrapperX<VariableGroupDO>().eq(VariableGroupDO::getAppId, appId)
                .eq(VariableGroupDO::getScope, "APPLICATION").orderByAsc(VariableGroupDO::getId));
    }
    default List<VariableGroupDO> selectListByIds(java.util.Collection<Long> ids) {
        return selectList(VariableGroupDO::getId, ids);
    }
    default List<VariableGroupDO> selectEffective(String scope, Long appId, Long applicationEnvId) {
        return selectList(new LambdaQueryWrapperX<VariableGroupDO>().eq(VariableGroupDO::getScope, scope)
                .eq(VariableGroupDO::getStatus, 0).eq(VariableGroupDO::getAppId, appId == null ? 0L : appId)
                .orderByAsc(VariableGroupDO::getId));
    }
    default PageResult<VariableGroupDO> selectPage(VariableGroupPageReqVO req) {
        return selectPage(req, new LambdaQueryWrapperX<VariableGroupDO>().likeIfPresent(VariableGroupDO::getName, req.getName())
                .eqIfPresent(VariableGroupDO::getScope, req.getScope()).eqIfPresent(VariableGroupDO::getAppId, req.getAppId())
                .eqIfPresent(VariableGroupDO::getStatus, req.getStatus())
                .orderByDesc(VariableGroupDO::getId));
    }
}
