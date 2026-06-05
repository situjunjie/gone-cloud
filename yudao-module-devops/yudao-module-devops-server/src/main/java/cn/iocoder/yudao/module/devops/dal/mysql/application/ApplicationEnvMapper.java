package cn.iocoder.yudao.module.devops.dal.mysql.application;

import cn.iocoder.yudao.framework.mybatis.core.mapper.BaseMapperX;
import cn.iocoder.yudao.module.devops.dal.dataobject.application.ApplicationEnvDO;
import org.apache.ibatis.annotations.Mapper;

import java.util.Collection;
import java.util.List;

@Mapper
public interface ApplicationEnvMapper extends BaseMapperX<ApplicationEnvDO> {

    default List<ApplicationEnvDO> selectListByAppId(Long appId) {
        return selectList(ApplicationEnvDO::getAppId, appId);
    }

    default List<ApplicationEnvDO> selectListByEnvId(Long envId) {
        return selectList(ApplicationEnvDO::getEnvId, envId);
    }

    default ApplicationEnvDO selectByAppIdAndEnvId(Long appId, Long envId) {
        return selectOne(ApplicationEnvDO::getAppId, appId, ApplicationEnvDO::getEnvId, envId);
    }

    default List<ApplicationEnvDO> selectListByIds(Collection<Long> ids) {
        return selectList(ApplicationEnvDO::getId, ids);
    }

    default int deleteByAppId(Long appId) {
        return delete(ApplicationEnvDO::getAppId, appId);
    }

}
