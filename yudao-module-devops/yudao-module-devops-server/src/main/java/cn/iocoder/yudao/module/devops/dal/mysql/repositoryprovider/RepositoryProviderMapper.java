package cn.iocoder.yudao.module.devops.dal.mysql.repositoryprovider;

import cn.iocoder.yudao.framework.common.pojo.PageResult;
import cn.iocoder.yudao.framework.mybatis.core.mapper.BaseMapperX;
import cn.iocoder.yudao.framework.mybatis.core.query.LambdaQueryWrapperX;
import cn.iocoder.yudao.module.devops.controller.admin.repositoryprovider.vo.RepositoryProviderPageReqVO;
import cn.iocoder.yudao.module.devops.dal.dataobject.repositoryprovider.RepositoryProviderDO;
import org.apache.ibatis.annotations.Mapper;

@Mapper
public interface RepositoryProviderMapper extends BaseMapperX<RepositoryProviderDO> {

    default RepositoryProviderDO selectByName(String name) {
        return selectOne(RepositoryProviderDO::getName, name);
    }

    default PageResult<RepositoryProviderDO> selectPage(RepositoryProviderPageReqVO reqVO) {
        return selectPage(reqVO, new LambdaQueryWrapperX<RepositoryProviderDO>()
                .likeIfPresent(RepositoryProviderDO::getName, reqVO.getName())
                .eqIfPresent(RepositoryProviderDO::getProviderType, reqVO.getProviderType())
                .eqIfPresent(RepositoryProviderDO::getAuthType, reqVO.getAuthType())
                .eqIfPresent(RepositoryProviderDO::getStatus, reqVO.getStatus())
                .eqIfPresent(RepositoryProviderDO::getLastCheckStatus, reqVO.getLastCheckStatus())
                .betweenIfPresent(RepositoryProviderDO::getCreateTime, reqVO.getCreateTime())
                .orderByDesc(RepositoryProviderDO::getId));
    }

}
