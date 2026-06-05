package cn.iocoder.yudao.module.devops.dal.mysql.application;

import cn.iocoder.yudao.framework.common.pojo.PageResult;
import cn.iocoder.yudao.framework.mybatis.core.mapper.BaseMapperX;
import cn.iocoder.yudao.framework.mybatis.core.query.LambdaQueryWrapperX;
import cn.iocoder.yudao.module.devops.controller.admin.application.vo.ApplicationPageReqVO;
import cn.iocoder.yudao.module.devops.dal.dataobject.application.ApplicationDO;
import org.apache.ibatis.annotations.Mapper;

@Mapper
public interface ApplicationMapper extends BaseMapperX<ApplicationDO> {

    default ApplicationDO selectByAppKey(String appKey) {
        return selectOne(ApplicationDO::getAppKey, appKey);
    }

    default ApplicationDO selectByRepoIdentifier(String repoIdentifier) {
        return selectOne(ApplicationDO::getRepoIdentifier, repoIdentifier);
    }

    default PageResult<ApplicationDO> selectPage(ApplicationPageReqVO reqVO) {
        return selectPage(reqVO, new LambdaQueryWrapperX<ApplicationDO>()
                .likeIfPresent(ApplicationDO::getAppKey, reqVO.getAppKey())
                .likeIfPresent(ApplicationDO::getName, reqVO.getName())
                .eqIfPresent(ApplicationDO::getRepoProviderType, reqVO.getRepoProviderType())
                .eqIfPresent(ApplicationDO::getOwnerUserId, reqVO.getOwnerUserId())
                .eqIfPresent(ApplicationDO::getStatus, reqVO.getStatus())
                .betweenIfPresent(ApplicationDO::getCreateTime, reqVO.getCreateTime())
                .orderByDesc(ApplicationDO::getId));
    }

}
