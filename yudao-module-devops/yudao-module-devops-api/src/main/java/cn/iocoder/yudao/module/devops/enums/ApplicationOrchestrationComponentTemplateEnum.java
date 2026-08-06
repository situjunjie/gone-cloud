package cn.iocoder.yudao.module.devops.enums;

import lombok.AllArgsConstructor;
import lombok.Getter;

/**
 * DevOps 应用编排组件模板枚举。
 */
@Getter
@AllArgsConstructor
public enum ApplicationOrchestrationComponentTemplateEnum {

    SECRET("SECRET", "Secret"),
    CONFIG_MAP("CONFIG_MAP", "ConfigMap"),
    PVC("PVC", "PersistentVolumeClaim"),
    SERVICE("SERVICE", "Service"),
    DEPLOYMENT("DEPLOYMENT", "Deployment"),
    INGRESS("INGRESS", "Ingress"),
    BLANK("BLANK", null);

    private final String key;
    private final String kind;

    public static ApplicationOrchestrationComponentTemplateEnum valueOfKey(String key) {
        for (ApplicationOrchestrationComponentTemplateEnum value : values()) {
            if (value.key.equals(key)) {
                return value;
            }
        }
        return null;
    }

}
