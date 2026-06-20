package cn.iocoder.yudao.module.devops.enums;

import cn.iocoder.yudao.framework.common.core.ArrayValuable;
import lombok.AllArgsConstructor;
import lombok.Getter;

import java.util.Arrays;

/**
 * DevOps 制品仓库认证类型。
 */
@Getter
@AllArgsConstructor
public enum ArtifactRegistryAuthTypeEnum implements ArrayValuable<String> {

    USERNAME_PASSWORD("USERNAME_PASSWORD", "用户名密码");

    public static final String[] ARRAYS = Arrays.stream(values())
            .map(ArtifactRegistryAuthTypeEnum::getAuthType)
            .toArray(String[]::new);

    private final String authType;
    private final String name;

    @Override
    public String[] array() {
        return ARRAYS;
    }

}
