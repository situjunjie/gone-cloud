package cn.iocoder.yudao.module.devops.framework.build;

/**
 * 构建脚本执行器。
 *
 * 流水线构建类节点(CHECKOUT / BUILD / DOCKER_BUILD_PUSH / EXECUTE_SHELL 等)由
 * {@code StepScriptGenerator} 生成纯 shell 脚本,再交给本接口执行:
 * <ul>
 *     <li>{@code LocalBuildExecutor}:本机 ProcessBuilder,退化特例。</li>
 *     <li>{@code SshBuildExecutor}:SSH 到专用构建机,主路径(后续实现)。</li>
 * </ul>
 *
 * 实现需把 stdout/stderr 流式逐行回调 {@link LogSink},并支持按 runId 取消运行中的执行。
 */
public interface BuildExecutor {

    /**
     * 执行脚本。同步阻塞至进程结束或超时。
     *
     * @param ctx    执行上下文(工作目录 / 环境变量 / 超时 / runId)
     * @param script 待执行的 shell 脚本
     * @param sink   行级日志回调
     * @return 执行结果(退出码 / 错误信息)
     */
    ExecResult exec(ExecContext ctx, String script, LogSink sink);

    /**
     * 取消指定 run 当前运行中的执行,用于流水线取消。
     *
     * @param runId 运行 id
     */
    void cancel(String runId);

}
