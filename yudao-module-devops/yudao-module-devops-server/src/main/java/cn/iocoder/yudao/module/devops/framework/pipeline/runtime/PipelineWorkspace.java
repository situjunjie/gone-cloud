package cn.iocoder.yudao.module.devops.framework.pipeline.runtime;

import lombok.Builder;
import lombok.Data;

import java.nio.file.Path;
import java.util.List;

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

}
