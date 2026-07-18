package cn.iocoder.yudao.module.devops.service.pipeline.plugin;

import org.springframework.stereotype.Component;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * 流水线插件注册表。
 */
@Component
public class PipelinePluginRegistry {

    private final Map<String, PipelinePlugin> pluginMap = new LinkedHashMap<>();

    public PipelinePluginRegistry(List<PipelinePlugin> plugins) {
        for (PipelinePlugin plugin : plugins) {
            PipelinePlugin oldPlugin = pluginMap.putIfAbsent(plugin.type(), plugin);
            if (oldPlugin != null) {
                throw new IllegalStateException("流水线插件类型重复：" + plugin.type());
            }
        }
    }

    /**
     * 根据类型查找插件。
     *
     * @param pluginType 插件类型
     * @return 插件；不存在时返回 null
     */
    public PipelinePlugin resolve(String pluginType) {
        return pluginMap.get(pluginType);
    }

}
