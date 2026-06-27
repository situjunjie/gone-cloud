package cn.iocoder.yudao.module.devops.enums;

import cn.iocoder.yudao.framework.common.core.ArrayValuable;
import lombok.AllArgsConstructor;
import lombok.Getter;

import java.util.Arrays;

/**
 * DevOps Kubernetes 资源类型。
 */
@Getter
@AllArgsConstructor
public enum EnvironmentKubernetesResourceKindEnum implements ArrayValuable<String> {

    NAMESPACE("NAMESPACE", "Namespace"),
    POD("POD", "Pod"),
    DEPLOYMENT("DEPLOYMENT", "Deployment"),
    SERVICE("SERVICE", "Service");

    public static final String[] ARRAYS = Arrays.stream(values())
            .map(EnvironmentKubernetesResourceKindEnum::getKind)
            .toArray(String[]::new);

    private final String kind;
    private final String name;

    @Override
    public String[] array() {
        return ARRAYS;
    }

}
