package cn.iocoder.yudao.module.devops.enums;

import lombok.AllArgsConstructor;
import lombok.Getter;

/**
 * DevOps 流水线定义版本状态。
 */
@Getter
@AllArgsConstructor
public enum PipelineDefinitionVersionStatusEnum {

    DRAFT(0),
    PUBLISHED(1),
    ARCHIVED(2);

    private final Integer status;

}
