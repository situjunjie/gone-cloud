package cn.iocoder.yudao.module.devops.service.artifactregistry;

import cn.iocoder.yudao.module.devops.dal.dataobject.artifactregistry.ArtifactRegistryDO;
import cn.iocoder.yudao.module.devops.service.artifactregistry.dto.ArtifactDockerSearchReqDTO;
import cn.iocoder.yudao.module.devops.service.artifactregistry.dto.ArtifactDockerSearchResultDTO;
import cn.iocoder.yudao.module.devops.service.artifactregistry.dto.ArtifactMavenSearchReqDTO;
import cn.iocoder.yudao.module.devops.service.artifactregistry.dto.ArtifactMavenSearchResultDTO;
import cn.iocoder.yudao.module.devops.service.artifactregistry.dto.ArtifactRepositoryDTO;

import java.util.List;

/**
 * 制品仓库客户端。
 */
public interface ArtifactRegistryClient {

    void checkConnection(ArtifactRegistryDO registry);

    List<ArtifactRepositoryDTO> listRepositories(ArtifactRegistryDO registry);

    ArtifactMavenSearchResultDTO searchMaven(ArtifactRegistryDO registry, ArtifactMavenSearchReqDTO reqDTO);

    ArtifactDockerSearchResultDTO searchDocker(ArtifactRegistryDO registry, ArtifactDockerSearchReqDTO reqDTO);

}
