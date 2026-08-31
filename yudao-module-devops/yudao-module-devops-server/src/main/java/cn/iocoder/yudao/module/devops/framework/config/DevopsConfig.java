package cn.iocoder.yudao.module.devops.framework.config;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import lombok.Data;

/**
 * DevOps 全局配置。
 */
@Data
@JsonIgnoreProperties(ignoreUnknown = true)
public class DevopsConfig {

    /**
     * 构建配置。
     */
    private BuildConfig build = new BuildConfig();

    /**
     * 构建配置。
     */
    @Data
    @JsonIgnoreProperties(ignoreUnknown = true)
    public static class BuildConfig {

        /**
         * 构建集群。
         */
        private BuildClusterConfig cluster = new BuildClusterConfig();

    }

    /**
     * 构建集群配置。
     */
    @Data
    @JsonIgnoreProperties(ignoreUnknown = true)
    public static class BuildClusterConfig {

        /**
         * DevOps Kubernetes 环境编号。
         */
        private Long environmentId;

    }

}
