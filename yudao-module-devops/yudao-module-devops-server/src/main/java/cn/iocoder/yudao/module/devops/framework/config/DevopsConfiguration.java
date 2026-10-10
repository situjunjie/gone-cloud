package cn.iocoder.yudao.module.devops.framework.config;

import cn.iocoder.yudao.framework.common.util.json.JsonUtils;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * DevOps 配置类。
 *
 * <p>提供 DevOps 全局配置 Bean。
 */
@Slf4j
@Configuration
public class DevopsConfiguration {

    /**
     * 创建 DevOps 全局配置 Bean。
     *
     * <p>从 application.yml 中读取 devops.config 配置。
     * <p>配置示例：
     * <pre>
     * devops:
     *   config:
     *     build:
     *       default-executor: KUBERNETES
     *       cluster:
     *         environment-id: 1
     *         namespace: devops-build
     * </pre>
     */
    @Bean
    @ConfigurationProperties(prefix = "devops.config")
    public DevopsConfig devopsConfig() {
        DevopsConfig config = new DevopsConfig();

        log.info("[devopsConfig] 初始化 DevOps 全局配置");

        return config;
    }

}
