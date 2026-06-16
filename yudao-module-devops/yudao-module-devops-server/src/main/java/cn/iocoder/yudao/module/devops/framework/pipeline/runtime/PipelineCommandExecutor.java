package cn.iocoder.yudao.module.devops.framework.pipeline.runtime;

import cn.iocoder.yudao.module.devops.framework.build.ExecResult;
import cn.iocoder.yudao.module.devops.framework.build.LogSink;

/**
 * 流水线命令执行器。
 */
public interface PipelineCommandExecutor {

    /**
     * 执行 Shell 脚本。
     *
     * @param ctx 命令执行上下文
     * @param script Shell 脚本内容
     * @param sink 日志输出接收器
     * @return 命令执行结果
     */
    ExecResult exec(PipelineCommandContext ctx, String script, LogSink sink);

    /**
     * 取消指定运行编号对应的命令执行。
     *
     * @param runId 运行编号
     */
    void cancel(String runId);

}
