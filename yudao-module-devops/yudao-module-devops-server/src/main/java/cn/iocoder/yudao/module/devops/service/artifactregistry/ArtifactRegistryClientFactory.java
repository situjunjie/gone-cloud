package cn.iocoder.yudao.module.devops.service.artifactregistry;

import cn.iocoder.yudao.module.devops.dal.dataobject.artifactregistry.ArtifactRegistryDO;

/**
 * 制品仓库客户端工厂。
 */
public interface ArtifactRegistryClientFactory {

    ArtifactRegistryClient getClient(ArtifactRegistryDO registry);

}
