package cn.iocoder.yudao.module.devops.enums;

import cn.iocoder.yudao.framework.common.core.ArrayValuable;
import lombok.AllArgsConstructor;
import lombok.Getter;

import java.util.Arrays;

/**
 * DevOps 代码源认证类型。
 */
@Getter
@AllArgsConstructor
public enum RepositoryProviderAuthTypeEnum implements ArrayValuable<String> {

    ACCESS_TOKEN("ACCESS_TOKEN", "Access Token");

    public static final String[] ARRAYS = Arrays.stream(values())
            .map(RepositoryProviderAuthTypeEnum::getAuthType)
            .toArray(String[]::new);

    private final String authType;
    private final String name;

    @Override
    public String[] array() {
        return ARRAYS;
    }

}
