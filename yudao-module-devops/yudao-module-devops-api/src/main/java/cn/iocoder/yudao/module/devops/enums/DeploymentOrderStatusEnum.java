package cn.iocoder.yudao.module.devops.enums;

import lombok.AllArgsConstructor;
import lombok.Getter;

/**
 * DevOps 部署单状态。
 */
@Getter
@AllArgsConstructor
public enum DeploymentOrderStatusEnum {

    CREATED("CREATED"),
    RUNNING("RUNNING"),
    SUCCESS("SUCCESS"),
    FAILED("FAILED"),
    CANCELED("CANCELED");

    private final String status;

}
