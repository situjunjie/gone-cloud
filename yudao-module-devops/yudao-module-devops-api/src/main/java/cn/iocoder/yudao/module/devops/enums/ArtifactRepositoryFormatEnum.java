package cn.iocoder.yudao.module.devops.enums;

import cn.iocoder.yudao.framework.common.core.ArrayValuable;
import lombok.AllArgsConstructor;
import lombok.Getter;

import java.util.Arrays;

/**
 * DevOps 制品仓库格式。
 */
@Getter
@AllArgsConstructor
public enum ArtifactRepositoryFormatEnum implements ArrayValuable<String> {

    MAVEN2("MAVEN2", "maven2", "Maven"),
    DOCKER("DOCKER", "docker", "Docker"),
    NPM("NPM", "npm", "npm");

    public static final String[] ARRAYS = Arrays.stream(values())
            .map(ArtifactRepositoryFormatEnum::getFormat)
            .toArray(String[]::new);

    private final String format;
    private final String nexusFormat;
    private final String name;

    @Override
    public String[] array() {
        return ARRAYS;
    }

    public static ArtifactRepositoryFormatEnum ofNexusFormat(String nexusFormat) {
        return Arrays.stream(values())
                .filter(item -> item.getNexusFormat().equalsIgnoreCase(nexusFormat))
                .findFirst()
                .orElse(null);
    }

}
