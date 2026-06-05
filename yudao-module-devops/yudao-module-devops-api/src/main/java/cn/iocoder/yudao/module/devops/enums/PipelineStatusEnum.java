package cn.iocoder.yudao.module.devops.enums;

import lombok.AllArgsConstructor;
import lombok.Getter;

/**
 * DevOps 流水线阶段状态。
 */
@Getter
@AllArgsConstructor
public enum PipelineStatusEnum {

    PENDING(0),
    RUNNING(1),
    SUCCESS(2),
    FAILED(3),
    CANCELED(4);

    private final Integer status;

}
