package cn.iocoder.yudao.module.devops.enums;

import lombok.AllArgsConstructor;
import lombok.Getter;

/**
 * DevOps 应用编排类型枚举。
 */
@Getter
@AllArgsConstructor
public enum ApplicationOrchestrationTypeEnum {

    K8S_DEPLOYMENT("K8S_DEPLOYMENT", "Kubernetes 部署");

    private final String type;
    private final String name;

    public static boolean isValid(String type) {
        return K8S_DEPLOYMENT.type.equals(type);
    }

}
