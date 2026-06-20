package cn.iocoder.yudao.module.devops.convert.artifactregistry;

import cn.iocoder.yudao.framework.common.pojo.PageResult;
import cn.iocoder.yudao.module.devops.controller.admin.artifactregistry.vo.ArtifactDockerSearchRespVO;
import cn.iocoder.yudao.module.devops.controller.admin.artifactregistry.vo.ArtifactMavenSearchRespVO;
import cn.iocoder.yudao.module.devops.controller.admin.artifactregistry.vo.ArtifactRegistryRespVO;
import cn.iocoder.yudao.module.devops.controller.admin.artifactregistry.vo.ArtifactRegistrySaveReqVO;
import cn.iocoder.yudao.module.devops.controller.admin.artifactregistry.vo.ArtifactRepositoryRespVO;
import cn.iocoder.yudao.module.devops.dal.dataobject.artifactregistry.ArtifactRegistryDO;
import cn.iocoder.yudao.module.devops.dal.dataobject.artifactregistry.ArtifactRepositoryDO;
import cn.iocoder.yudao.module.devops.service.artifactregistry.dto.ArtifactDockerSearchResultDTO;
import cn.iocoder.yudao.module.devops.service.artifactregistry.dto.ArtifactMavenSearchResultDTO;
import org.mapstruct.Mapper;
import org.mapstruct.factory.Mappers;

import java.util.List;

@Mapper
public interface ArtifactRegistryConvert {

    ArtifactRegistryConvert INSTANCE = Mappers.getMapper(ArtifactRegistryConvert.class);

    ArtifactRegistryDO convert(ArtifactRegistrySaveReqVO bean);

    ArtifactRegistryRespVO convert(ArtifactRegistryDO bean);

    PageResult<ArtifactRegistryRespVO> convertPage(PageResult<ArtifactRegistryDO> page);

    ArtifactRepositoryRespVO convert(ArtifactRepositoryDO bean);

    List<ArtifactRepositoryRespVO> convertList(List<ArtifactRepositoryDO> list);

    ArtifactMavenSearchRespVO convert(ArtifactMavenSearchResultDTO bean);

    ArtifactDockerSearchRespVO convert(ArtifactDockerSearchResultDTO bean);

}
