package cn.iocoder.yudao.module.devops.enums;

import lombok.AllArgsConstructor;
import lombok.Getter;

/**
 * DevOps 流水线任务运行状态。
 */
@Getter
@AllArgsConstructor
public enum PipelineRunJobStatusEnum {

    /**
     * 待执行。
     */
    PENDING("PENDING"),
    /**
     * 执行中。
     */
    RUNNING("RUNNING"),
    /**
     * 阻塞中，等待外部处理。
     */
    BLOCKED("BLOCKED"),
    /**
     * 执行成功。
     */
    SUCCESS("SUCCESS"),
    /**
     * 执行失败。
     */
    FAILED("FAILED"),
    /**
     * 已跳过。
     */
    SKIPPED("SKIPPED"),
    /**
     * 已取消。
     */
    CANCELED("CANCELED");

    /**
     * 状态值。
     */
    private final String status;

}
