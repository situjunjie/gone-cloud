package cn.iocoder.yudao.module.devops.framework.pipeline.runtime;

import lombok.Data;

import java.util.ArrayList;
import java.util.List;

/**
 * 流水线缓存目录配置。
 */
@Data
public class PipelineCacheConfig {

    /**
     * 配置 schema 版本。
     */
    private String schemaVersion;
    /**
     * 缓存目录列表。
     */
    private List<Directory> directories = new ArrayList<>();

    /**
     * 缓存目录配置项。
     */
    @Data
    public static class Directory {

        /**
         * 目录标识。
         */
        private String id;
        /**
         * 容器内缓存目录路径。
         */
        private String path;
        /**
         * 描述。
         */
        private String description;
        /**
         * 是否启用。
         */
        private Boolean enabled;

    }

}
