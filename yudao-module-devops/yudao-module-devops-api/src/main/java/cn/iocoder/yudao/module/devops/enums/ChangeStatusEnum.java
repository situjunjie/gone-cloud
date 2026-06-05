package cn.iocoder.yudao.module.devops.enums;

import lombok.AllArgsConstructor;
import lombok.Getter;

/**
 * DevOps 变更状态。
 */
@Getter
@AllArgsConstructor
public enum ChangeStatusEnum {

    ACTIVE(0),
    RELEASED(1),
    DISCARDED(2);

    private final Integer status;

}
