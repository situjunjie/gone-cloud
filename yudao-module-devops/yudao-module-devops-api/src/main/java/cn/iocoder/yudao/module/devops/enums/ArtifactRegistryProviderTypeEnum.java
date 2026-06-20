package cn.iocoder.yudao.module.devops.enums;

import cn.iocoder.yudao.framework.common.core.ArrayValuable;
import lombok.AllArgsConstructor;
import lombok.Getter;

import java.util.Arrays;

/**
 * DevOps 制品仓库提供方类型。
 */
@Getter
@AllArgsConstructor
public enum ArtifactRegistryProviderTypeEnum implements ArrayValuable<String> {

    NEXUS3("NEXUS3", "Nexus3");

    public static final String[] ARRAYS = Arrays.stream(values())
            .map(ArtifactRegistryProviderTypeEnum::getProviderType)
            .toArray(String[]::new);

    private final String providerType;
    private final String name;

    @Override
    public String[] array() {
        return ARRAYS;
    }

}
