package cn.iocoder.yudao.module.devops.enums;

import cn.iocoder.yudao.framework.common.core.ArrayValuable;
import lombok.AllArgsConstructor;
import lombok.Getter;

import java.util.Arrays;

/**
 * DevOps 环境基础设施类型。
 */
@Getter
@AllArgsConstructor
public enum EnvironmentInfraTypeEnum implements ArrayValuable<String> {

    K8S("K8S", "Kubernetes"),
    DOCKER("DOCKER", "Docker"),
    HOST("HOST", "主机");

    public static final String[] ARRAYS = Arrays.stream(values())
            .map(EnvironmentInfraTypeEnum::getInfraType)
            .toArray(String[]::new);

    private final String infraType;
    private final String name;

    @Override
    public String[] array() {
        return ARRAYS;
    }

}
