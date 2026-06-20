package cn.iocoder.yudao.module.devops.service.artifactregistry;

import cn.iocoder.yudao.framework.common.pojo.PageResult;
import cn.iocoder.yudao.module.devops.controller.admin.artifactregistry.vo.ArtifactDockerSearchReqVO;
import cn.iocoder.yudao.module.devops.controller.admin.artifactregistry.vo.ArtifactMavenSearchReqVO;
import cn.iocoder.yudao.module.devops.controller.admin.artifactregistry.vo.ArtifactRegistryPageReqVO;
import cn.iocoder.yudao.module.devops.controller.admin.artifactregistry.vo.ArtifactRegistrySaveReqVO;
import cn.iocoder.yudao.module.devops.dal.dataobject.artifactregistry.ArtifactRegistryDO;
import cn.iocoder.yudao.module.devops.dal.dataobject.artifactregistry.ArtifactRepositoryDO;
import cn.iocoder.yudao.module.devops.service.artifactregistry.dto.ArtifactDockerSearchResultDTO;
import cn.iocoder.yudao.module.devops.service.artifactregistry.dto.ArtifactMavenSearchResultDTO;

import java.util.List;

public interface ArtifactRegistryService {

    Long createArtifactRegistry(ArtifactRegistrySaveReqVO createReqVO);

    void updateArtifactRegistry(ArtifactRegistrySaveReqVO updateReqVO);

    void deleteArtifactRegistry(Long id);

    ArtifactRegistryDO getArtifactRegistry(Long id);

    PageResult<ArtifactRegistryDO> getArtifactRegistryPage(ArtifactRegistryPageReqVO pageReqVO);

    ArtifactRegistryDO validateArtifactRegistryExists(Long id);

    void checkArtifactRegistry(Long id);

    List<ArtifactRepositoryDO> syncRepositories(Long registryId);

    List<ArtifactRepositoryDO> getRepositories(Long registryId, String format);

    ArtifactMavenSearchResultDTO searchMaven(ArtifactMavenSearchReqVO reqVO);

    ArtifactDockerSearchResultDTO searchDocker(ArtifactDockerSearchReqVO reqVO);

}
