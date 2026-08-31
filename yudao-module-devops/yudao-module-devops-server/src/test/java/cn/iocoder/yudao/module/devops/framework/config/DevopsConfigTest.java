package cn.iocoder.yudao.module.devops.framework.config;

import cn.iocoder.yudao.framework.common.util.json.JsonUtils;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;

/**
 * {@link DevopsConfig} 的单元测试。
 */
class DevopsConfigTest {

    @Test
    void testDefaultConfig() {
        DevopsConfig config = new DevopsConfig();

        assertNotNull(config.getBuild());
        assertNotNull(config.getBuild().getCluster());
    }

    @Test
    void testDeserializeEmptyAndUnknownFields() {
        DevopsConfig emptyConfig = JsonUtils.parseObject("{}", DevopsConfig.class);
        DevopsConfig unknownConfig = JsonUtils.parseObject("""
                {
                  "futureRoot": true,
                  "build": {
                    "futureBuild": true,
                    "cluster": {
                      "futureCluster": true
                    }
                  }
                }
                """, DevopsConfig.class);

        assertNotNull(emptyConfig.getBuild());
        assertNotNull(emptyConfig.getBuild().getCluster());
        assertNotNull(unknownConfig.getBuild());
        assertNotNull(unknownConfig.getBuild().getCluster());
    }

    @Test
    void testBuildClusterJsonRoundTrip() {
        DevopsConfig source = new DevopsConfig();
        source.getBuild().getCluster().setEnvironmentId(1024L);

        DevopsConfig target = JsonUtils.parseObject(JsonUtils.toJsonString(source), DevopsConfig.class);

        assertEquals(1024L, target.getBuild().getCluster().getEnvironmentId());
    }

}
