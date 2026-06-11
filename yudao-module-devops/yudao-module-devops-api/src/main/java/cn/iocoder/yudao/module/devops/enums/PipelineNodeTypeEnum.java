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
    APPROVAL("APPROVAL"),
    CONTAINER_DEPLOY("CONTAINER_DEPLOY"),
    EXECUTE_SHELL("EXECUTE_SHELL"),
    SSH_PUBLISH("SSH_PUBLISH"),
    MOCK("MOCK");

    private final String type;

}
