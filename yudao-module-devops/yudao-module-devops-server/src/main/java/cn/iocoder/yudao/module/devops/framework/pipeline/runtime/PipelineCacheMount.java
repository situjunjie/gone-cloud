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
     * 规范化的缓存目录存储路径，可能包含 ${WORKSPACE} 变量。
     *
     * <p>用作宿主机缓存目录的哈希键、元数据记录键与缓存清理键。
     */
    private String canonicalPath;
    /**
     * 容器内缓存目录路径（已解析变量），用作 Docker bind 的挂载点。
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
