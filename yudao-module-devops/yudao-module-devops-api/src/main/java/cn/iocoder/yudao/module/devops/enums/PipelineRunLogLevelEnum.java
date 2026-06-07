package cn.iocoder.yudao.module.devops.enums;

import lombok.AllArgsConstructor;
import lombok.Getter;

/**
 * DevOps 流水线运行日志层级。
 */
@Getter
@AllArgsConstructor
public enum PipelineRunLogLevelEnum {

    NODE("NODE"),
    STEP("STEP"),
    EVENT("EVENT");

    private final String level;

}
