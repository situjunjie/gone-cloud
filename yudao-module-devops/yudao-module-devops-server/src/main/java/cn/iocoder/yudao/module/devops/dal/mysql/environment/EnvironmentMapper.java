package cn.iocoder.yudao.module.devops.dal.mysql.environment;

import cn.iocoder.yudao.framework.common.pojo.PageResult;
import cn.iocoder.yudao.framework.mybatis.core.mapper.BaseMapperX;
import cn.iocoder.yudao.framework.mybatis.core.query.LambdaQueryWrapperX;
import cn.iocoder.yudao.module.devops.controller.admin.environment.vo.EnvironmentPageReqVO;
import cn.iocoder.yudao.module.devops.dal.dataobject.environment.EnvironmentDO;
import org.apache.ibatis.annotations.Mapper;

import java.util.Collection;
import java.util.List;

@Mapper
public interface EnvironmentMapper extends BaseMapperX<EnvironmentDO> {

    default EnvironmentDO selectByEnvKey(String envKey) {
        return selectOne(EnvironmentDO::getEnvKey, envKey);
    }

    default List<EnvironmentDO> selectListByIds(Collection<Long> ids) {
        return selectList(EnvironmentDO::getId, ids);
    }

    default PageResult<EnvironmentDO> selectPage(EnvironmentPageReqVO reqVO) {
        return selectPage(reqVO, new LambdaQueryWrapperX<EnvironmentDO>()
                .likeIfPresent(EnvironmentDO::getEnvKey, reqVO.getEnvKey())
                .likeIfPresent(EnvironmentDO::getEnvName, reqVO.getEnvName())
                .eqIfPresent(EnvironmentDO::getEnvStage, reqVO.getEnvStage())
                .eqIfPresent(EnvironmentDO::getInfraType, reqVO.getInfraType())
                .eqIfPresent(EnvironmentDO::getStatus, reqVO.getStatus())
                .betweenIfPresent(EnvironmentDO::getCreateTime, reqVO.getCreateTime())
                .orderByDesc(EnvironmentDO::getId));
    }

}
