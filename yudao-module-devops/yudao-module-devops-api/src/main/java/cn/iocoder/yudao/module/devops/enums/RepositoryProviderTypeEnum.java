package cn.iocoder.yudao.module.devops.enums;

import cn.iocoder.yudao.framework.common.core.ArrayValuable;
import lombok.AllArgsConstructor;
import lombok.Getter;

import java.util.Arrays;

/**
 * DevOps 代码源提供方类型。
 */
@Getter
@AllArgsConstructor
public enum RepositoryProviderTypeEnum implements ArrayValuable<String> {

    GITLAB("GITLAB", "GitLab");

    public static final String[] ARRAYS = Arrays.stream(values())
            .map(RepositoryProviderTypeEnum::getProviderType)
            .toArray(String[]::new);

    private final String providerType;
    private final String name;

    @Override
    public String[] array() {
        return ARRAYS;
    }

}
