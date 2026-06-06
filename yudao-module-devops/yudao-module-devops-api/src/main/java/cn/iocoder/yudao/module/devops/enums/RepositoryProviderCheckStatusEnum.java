package cn.iocoder.yudao.module.devops.enums;

import lombok.AllArgsConstructor;
import lombok.Getter;

/**
 * DevOps 代码源连接检测状态。
 */
@Getter
@AllArgsConstructor
public enum RepositoryProviderCheckStatusEnum {

    SUCCESS(0),
    FAIL(1);

    private final Integer status;

}
