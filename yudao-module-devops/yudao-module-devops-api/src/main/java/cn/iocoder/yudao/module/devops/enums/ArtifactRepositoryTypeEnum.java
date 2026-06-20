package cn.iocoder.yudao.module.devops.enums;

import cn.iocoder.yudao.framework.common.core.ArrayValuable;
import lombok.AllArgsConstructor;
import lombok.Getter;

import java.util.Arrays;

/**
 * DevOps 制品仓库类型。
 */
@Getter
@AllArgsConstructor
public enum ArtifactRepositoryTypeEnum implements ArrayValuable<String> {

    HOSTED("HOSTED", "hosted", "Hosted"),
    PROXY("PROXY", "proxy", "Proxy"),
    GROUP("GROUP", "group", "Group");

    public static final String[] ARRAYS = Arrays.stream(values())
            .map(ArtifactRepositoryTypeEnum::getRepositoryType)
            .toArray(String[]::new);

    private final String repositoryType;
    private final String nexusType;
    private final String name;

    @Override
    public String[] array() {
        return ARRAYS;
    }

    public static ArtifactRepositoryTypeEnum ofNexusType(String nexusType) {
        return Arrays.stream(values())
                .filter(item -> item.getNexusType().equalsIgnoreCase(nexusType))
                .findFirst()
                .orElse(null);
    }

}
