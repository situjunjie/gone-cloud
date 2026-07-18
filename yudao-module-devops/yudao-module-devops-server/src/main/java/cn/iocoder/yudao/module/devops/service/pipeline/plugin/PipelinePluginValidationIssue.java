package cn.iocoder.yudao.module.devops.service.pipeline.plugin;

import lombok.AllArgsConstructor;
import lombok.Getter;

/**
 * 流水线插件参数校验问题。
 */
@Getter
@AllArgsConstructor
public class PipelinePluginValidationIssue {

    /**
     * 插件 with 下的相对字段。
     */
    private final String field;
    /**
     * 错误码。
     */
    private final String code;
    /**
     * 安全错误消息。
     */
    private final String message;

}
