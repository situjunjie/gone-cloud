package cn.iocoder.yudao.module.devops.service.pipeline.plugin;

import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

class PipelinePluginRegistryTest {

    @Test
    void testResolve() {
        PipelinePlugin plugin = plugin("test");

        PipelinePluginRegistry registry = new PipelinePluginRegistry(List.of(plugin));

        assertEquals(plugin, registry.resolve("test"));
    }

    @Test
    void testDuplicateTypeRejected() {
        assertThrows(IllegalStateException.class,
                () -> new PipelinePluginRegistry(List.of(plugin("test"), plugin("test"))));
    }

    private PipelinePlugin plugin(String type) {
        return new PipelinePlugin() {
            @Override
            public String type() {
                return type;
            }

            @Override
            public List<PipelinePluginValidationIssue> validate(Map<String, Object> config) {
                return List.of();
            }

            @Override
            public void execute(PipelinePluginExecutionContext context, Map<String, Object> config) {
            }
        };
    }

}
