package cn.iocoder.yudao.module.devops.framework.pipeline.runtime;

import cn.iocoder.yudao.module.devops.dal.dataobject.host.EnvironmentHostDO;
import lombok.Builder;
import lombok.Getter;
import lombok.ToString;

import java.util.Map;
import java.util.function.BooleanSupplier;

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
    /** Validated SSH target. Never log credentials through the command context. */
    @ToString.Exclude
    private final EnvironmentHostDO sshHost;
    /** Explicitly mapped source workspace on the SSH host. */
    private final String workingDirectory;
    /** Shared-workspace identity probe, removed after execution. */
    private final String workspaceProbe;
    /** Random shared-workspace identity token; never include it in diagnostics. */
    @ToString.Exclude
    private final String workspaceToken;
    /** Durable run cancellation check, including cancellation before executor registration. */
    @ToString.Exclude
    private final BooleanSupplier cancellationRequested;

    public boolean isCancellationRequested() {
        return cancellationRequested != null && cancellationRequested.getAsBoolean();
    }
}
