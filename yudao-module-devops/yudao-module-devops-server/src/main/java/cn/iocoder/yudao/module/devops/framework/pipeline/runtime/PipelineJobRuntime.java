package cn.iocoder.yudao.module.devops.framework.pipeline.runtime;

import lombok.Builder;
import lombok.Data;

import java.nio.file.Path;

/**
 * 流水线任务运行时。
 */
@Data
@Builder
public class PipelineJobRuntime {

    /**
     * 运行时类型。
     */
    private String runtimeType;
    /**
     * 运行时实例编号。
     */
    private String runtimeId;
    /**
     * 运行时实例名称。
     */
    private String runtimeName;
    /**
     * 执行资源池。
     */
    private String executorGroup;
    /**
     * 执行容器镜像。
     */
    private String executorImage;
    /**
     * 任务工作目录。
     */
    private Path workspace;

}
