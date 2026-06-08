package cn.iocoder.yudao.module.devops.enums;

import lombok.AllArgsConstructor;
import lombok.Getter;

/**
 * DevOps 流水线节点类型。
 */
@Getter
@AllArgsConstructor
public enum PipelineNodeTypeEnum {

    CODE_MERGE("CODE_MERGE"),
    MOCK("MOCK");

    private final String type;

}
