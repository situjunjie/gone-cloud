package cn.iocoder.yudao.module.devops.enums;

import lombok.AllArgsConstructor;
import lombok.Getter;

/**
 * DevOps 合并状态。
 */
@Getter
@AllArgsConstructor
public enum MergeStatusEnum {

    PENDING(0),
    SUCCESS(1),
    FAILED(2),
    CONFLICT(3);

    private final Integer status;

}
