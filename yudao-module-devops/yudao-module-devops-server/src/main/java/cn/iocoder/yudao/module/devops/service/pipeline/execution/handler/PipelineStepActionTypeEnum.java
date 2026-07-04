package cn.iocoder.yudao.module.devops.service.pipeline.execution.handler;

import lombok.AllArgsConstructor;
import lombok.Getter;

/**
 * 流水线步骤动作类型。
 */
@Getter
@AllArgsConstructor
public enum PipelineStepActionTypeEnum {

    ROUTE("ROUTE"),
    DOWNLOAD("DOWNLOAD"),
    API("API"),
    EXTERNAL_LINK("EXTERNAL_LINK");

    private final String type;

}
