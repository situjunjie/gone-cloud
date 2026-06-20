package cn.iocoder.yudao.module.devops.dal.mysql.artifactregistry;

import cn.iocoder.yudao.framework.mybatis.core.mapper.BaseMapperX;
import cn.iocoder.yudao.framework.mybatis.core.query.LambdaQueryWrapperX;
import cn.iocoder.yudao.module.devops.dal.dataobject.artifactregistry.ArtifactRepositoryDO;
import org.apache.ibatis.annotations.Mapper;

import java.util.List;

@Mapper
public interface ArtifactRepositoryMapper extends BaseMapperX<ArtifactRepositoryDO> {

    default ArtifactRepositoryDO selectByRegistryIdAndRepositoryName(Long registryId, String repositoryName) {
        return selectOne(new LambdaQueryWrapperX<ArtifactRepositoryDO>()
                .eq(ArtifactRepositoryDO::getRegistryId, registryId)
                .eq(ArtifactRepositoryDO::getRepositoryName, repositoryName));
    }

    default Long selectCountByRegistryId(Long registryId) {
        return selectCount(ArtifactRepositoryDO::getRegistryId, registryId);
    }

    default List<ArtifactRepositoryDO> selectListByRegistryIdAndFormat(Long registryId, String format) {
        return selectList(new LambdaQueryWrapperX<ArtifactRepositoryDO>()
                .eq(ArtifactRepositoryDO::getRegistryId, registryId)
                .eqIfPresent(ArtifactRepositoryDO::getFormat, format)
                .orderByAsc(ArtifactRepositoryDO::getRepositoryName));
    }

}
