package cn.iocoder.yudao.module.devops.enums;

import lombok.AllArgsConstructor;
import lombok.Getter;

/**
 * DevOps 流水线运行状态。
 */
@Getter
@AllArgsConstructor
public enum PipelineRunStatusEnum {

    QUEUED(0),
    RUNNING(1),
    SUCCESS(2),
    FAILED(3),
    CANCELED(4);

    private final Integer status;

}
