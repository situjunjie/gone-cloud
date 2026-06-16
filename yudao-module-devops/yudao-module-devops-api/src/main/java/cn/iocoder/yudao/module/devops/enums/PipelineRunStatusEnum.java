package cn.iocoder.yudao.module.devops.enums;

import lombok.AllArgsConstructor;
import lombok.Getter;

/**
 * DevOps 流水线运行状态。
 */
@Getter
@AllArgsConstructor
public enum PipelineRunStatusEnum {

    /**
     * 排队中。
     */
    QUEUED(0),
    /**
     * 运行中。
     */
    RUNNING(1),
    /**
     * 执行成功。
     */
    SUCCESS(2),
    /**
     * 执行失败。
     */
    FAILED(3),
    /**
     * 已取消。
     */
    CANCELED(4),
    /**
     * 等待外部输入。
     */
    WAITING_INPUT(5);

    /**
     * 状态值。
     */
    private final Integer status;

}
