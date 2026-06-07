package cn.iocoder.yudao.module.devops.enums;

import lombok.AllArgsConstructor;
import lombok.Getter;

/**
 * DevOps 流水线运行日志状态。
 */
@Getter
@AllArgsConstructor
public enum PipelineRunLogStatusEnum {

    PENDING("PENDING"),
    RUNNING("RUNNING"),
    WAITING_INPUT("WAITING_INPUT"),
    SUCCESS("SUCCESS"),
    FAILED("FAILED"),
    CANCELED("CANCELED");

    private final String status;

}
