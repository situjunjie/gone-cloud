package cn.iocoder.yudao.module.devops.service.artifactregistry;

import cn.iocoder.yudao.module.devops.dal.dataobject.artifactregistry.ArtifactRegistryDO;
import cn.iocoder.yudao.module.devops.enums.ArtifactRegistryProviderTypeEnum;
import org.springframework.stereotype.Component;

import static cn.iocoder.yudao.framework.common.exception.util.ServiceExceptionUtil.exception;
import static cn.iocoder.yudao.module.devops.enums.ErrorCodeConstants.ARTIFACT_REGISTRY_TYPE_NOT_SUPPORTED;

/**
 * Nexus 制品仓库客户端工厂。
 */
@Component
public class NexusArtifactRegistryClientFactory implements ArtifactRegistryClientFactory {

    @Override
    public ArtifactRegistryClient getClient(ArtifactRegistryDO registry) {
        if (!ArtifactRegistryProviderTypeEnum.NEXUS3.getProviderType().equals(registry.getProviderType())) {
            throw exception(ARTIFACT_REGISTRY_TYPE_NOT_SUPPORTED);
        }
        return new NexusArtifactRegistryClient();
    }

}
