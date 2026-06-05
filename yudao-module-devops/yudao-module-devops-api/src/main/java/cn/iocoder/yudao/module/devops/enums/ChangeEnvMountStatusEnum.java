package cn.iocoder.yudao.module.devops.enums;

import lombok.AllArgsConstructor;
import lombok.Getter;

/**
 * DevOps 变更环境挂载状态。
 */
@Getter
@AllArgsConstructor
public enum ChangeEnvMountStatusEnum {

    MOUNTED(0),
    UNMOUNTED(1),
    AUTO_CLEANED(2);

    private final Integer status;

}
