package cn.iocoder.yudao.module.devops.framework.pipeline.runtime;

import lombok.Builder;
import lombok.Data;

import java.nio.file.Path;

/**
 * 流水线缓存挂载描述。
 */
@Data
@Builder
public class PipelineCacheMount {

    /**
     * 缓存目录标识。
     */
    private String id;
    /**
     * 容器内缓存目录路径。
     */
    private String containerPath;
    /**
     * 宿主机缓存目录路径。
     */
    private Path hostPath;
    /**
     * 描述。
     */
    private String description;

}
