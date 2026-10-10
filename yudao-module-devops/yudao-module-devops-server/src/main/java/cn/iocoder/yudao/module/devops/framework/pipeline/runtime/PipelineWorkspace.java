package cn.iocoder.yudao.module.devops.framework.pipeline.runtime;

import lombok.Builder;
import lombok.Data;

import java.nio.file.Path;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * 流水线工作目录描述。
 */
@Data
@Builder
public class PipelineWorkspace {

    /**
     * 单次运行共享工作目录。
     */
    private Path runWorkspace;
    /**
     * 流水线持久缓存根目录。
     */
    private Path cacheWorkspace;
    /**
     * 缓存键。
     */
    private String cacheKey;
    /**
     * 缓存挂载列表。
     */
    private List<PipelineCacheMount> cacheMounts;
    /**
     * 运行时元数据（用于存储工作空间子路径、缓存映射等配置）。
     */
    @Builder.Default
    private Map<String, Object> metadata = new HashMap<>();

}
