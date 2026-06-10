package cn.iocoder.yudao.module.devops.enums;

import lombok.AllArgsConstructor;
import lombok.Getter;

/**
 * DevOps 部署模式枚举。
 */
@Getter
@AllArgsConstructor
public enum DeploymentModeEnum {

    RAW_MANIFEST("RAW_MANIFEST", "原始 YAML");

    private final String mode;
    private final String name;

}
