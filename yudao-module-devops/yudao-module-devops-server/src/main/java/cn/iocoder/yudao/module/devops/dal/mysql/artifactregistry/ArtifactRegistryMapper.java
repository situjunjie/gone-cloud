package cn.iocoder.yudao.module.devops.dal.mysql.artifactregistry;

import cn.iocoder.yudao.framework.common.pojo.PageResult;
import cn.iocoder.yudao.framework.mybatis.core.mapper.BaseMapperX;
import cn.iocoder.yudao.framework.mybatis.core.query.LambdaQueryWrapperX;
import cn.iocoder.yudao.module.devops.controller.admin.artifactregistry.vo.ArtifactRegistryPageReqVO;
import cn.iocoder.yudao.module.devops.dal.dataobject.artifactregistry.ArtifactRegistryDO;
import org.apache.ibatis.annotations.Mapper;

@Mapper
public interface ArtifactRegistryMapper extends BaseMapperX<ArtifactRegistryDO> {

    default ArtifactRegistryDO selectByName(String name) {
        return selectOne(ArtifactRegistryDO::getName, name);
    }

    default PageResult<ArtifactRegistryDO> selectPage(ArtifactRegistryPageReqVO reqVO) {
        return selectPage(reqVO, new LambdaQueryWrapperX<ArtifactRegistryDO>()
                .likeIfPresent(ArtifactRegistryDO::getName, reqVO.getName())
                .eqIfPresent(ArtifactRegistryDO::getProviderType, reqVO.getProviderType())
                .eqIfPresent(ArtifactRegistryDO::getAuthType, reqVO.getAuthType())
                .eqIfPresent(ArtifactRegistryDO::getStatus, reqVO.getStatus())
                .eqIfPresent(ArtifactRegistryDO::getLastCheckStatus, reqVO.getLastCheckStatus())
                .betweenIfPresent(ArtifactRegistryDO::getCreateTime, reqVO.getCreateTime())
                .orderByDesc(ArtifactRegistryDO::getId));
    }

}
