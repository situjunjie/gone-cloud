package cn.iocoder.yudao.module.devops.service.pipeline.plugin;

import java.util.List;
import java.util.Map;

/**
 * 流水线任务终态插件。
 */
public interface PipelinePlugin {

    /**
     * 获取插件类型标识。
     *
     * @return 插件类型标识
     */
    String type();

    /**
     * 校验插件参数。
     *
     * @param config 插件参数
     * @return 校验问题列表
     */
    List<PipelinePluginValidationIssue> validate(Map<String, Object> config);

    /**
     * 执行插件。
     *
     * @param context 插件执行上下文
     * @param config 插件参数
     */
    void execute(PipelinePluginExecutionContext context, Map<String, Object> config);

}
