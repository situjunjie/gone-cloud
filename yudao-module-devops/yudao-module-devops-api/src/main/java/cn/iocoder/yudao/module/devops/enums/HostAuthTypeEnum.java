package cn.iocoder.yudao.module.devops.enums;

import cn.iocoder.yudao.framework.common.core.ArrayValuable;
import lombok.AllArgsConstructor;
import lombok.Getter;

import java.util.Arrays;

/**
 * DevOps 主机 SSH 认证方式。
 */
@Getter
@AllArgsConstructor
public enum HostAuthTypeEnum implements ArrayValuable<String> {

    PASSWORD("PASSWORD", "密码"),
    PRIVATE_KEY("PRIVATE_KEY", "私钥");

    public static final String[] ARRAYS = Arrays.stream(values())
            .map(HostAuthTypeEnum::getAuthType)
            .toArray(String[]::new);

    private final String authType;
    private final String name;

    @Override
    public String[] array() {
        return ARRAYS;
    }

}
