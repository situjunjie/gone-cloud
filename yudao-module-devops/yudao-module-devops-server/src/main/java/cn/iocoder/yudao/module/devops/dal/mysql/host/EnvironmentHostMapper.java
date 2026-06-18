package cn.iocoder.yudao.module.devops.dal.mysql.host;

import cn.iocoder.yudao.framework.common.pojo.PageResult;
import cn.iocoder.yudao.framework.mybatis.core.mapper.BaseMapperX;
import cn.iocoder.yudao.framework.mybatis.core.query.LambdaQueryWrapperX;
import cn.iocoder.yudao.module.devops.controller.admin.host.vo.EnvironmentHostPageReqVO;
import cn.iocoder.yudao.module.devops.dal.dataobject.host.EnvironmentHostDO;
import org.apache.ibatis.annotations.Mapper;

import java.util.List;

@Mapper
public interface EnvironmentHostMapper extends BaseMapperX<EnvironmentHostDO> {

    default EnvironmentHostDO selectByEnvIdAndHostKey(Long envId, String hostKey) {
        return selectOne(new LambdaQueryWrapperX<EnvironmentHostDO>()
                .eq(EnvironmentHostDO::getEnvId, envId)
                .eq(EnvironmentHostDO::getHostKey, hostKey));
    }

    default List<EnvironmentHostDO> selectListByEnvId(Long envId) {
        return selectList(new LambdaQueryWrapperX<EnvironmentHostDO>()
                .eq(EnvironmentHostDO::getEnvId, envId)
                .orderByDesc(EnvironmentHostDO::getId));
    }

    default PageResult<EnvironmentHostDO> selectPage(EnvironmentHostPageReqVO reqVO) {
        return selectPage(reqVO, new LambdaQueryWrapperX<EnvironmentHostDO>()
                .eq(EnvironmentHostDO::getEnvId, reqVO.getEnvId())
                .likeIfPresent(EnvironmentHostDO::getHostKey, reqVO.getHostKey())
                .likeIfPresent(EnvironmentHostDO::getHostName, reqVO.getHostName())
                .likeIfPresent(EnvironmentHostDO::getHost, reqVO.getHost())
                .eqIfPresent(EnvironmentHostDO::getStatus, reqVO.getStatus())
                .orderByDesc(EnvironmentHostDO::getId));
    }

}
