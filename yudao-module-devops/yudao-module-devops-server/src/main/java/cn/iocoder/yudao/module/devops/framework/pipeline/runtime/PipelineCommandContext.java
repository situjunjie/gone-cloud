package cn.iocoder.yudao.module.devops.framework.pipeline.runtime;

import lombok.Builder;
import lombok.Getter;
import lombok.ToString;

import java.util.Map;

/**
 * 流水线命令执行上下文。
 */
@Getter
@Builder
@ToString
public class PipelineCommandContext {

    /**
     * 任务运行时。
     */
    private final PipelineJobRuntime runtime;
    /**
     * 命令运行编号。
     */
    private final String runId;
    /**
     * 命令环境变量。
     */
    @ToString.Exclude
    private final Map<String, String> env;
    /**
     * 命令超时时间，单位：秒。
     */
    private final long timeoutSeconds;

}
