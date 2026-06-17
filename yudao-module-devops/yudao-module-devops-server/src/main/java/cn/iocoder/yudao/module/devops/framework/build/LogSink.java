package cn.iocoder.yudao.module.devops.framework.build;

/**
 * 构建执行器的行级日志回调。
 *
 * 执行器在执行脚本过程中,把 stdout/stderr 逐行回填给调用方,
 * 通常由上层收进 {@code dev_pipeline_run_log} 的节点级摘要 / {@code result_json}。
 */
@FunctionalInterface
public interface LogSink {

    /**
     * 接收一行构建输出。
     *
     * @param stream 输出流类型，stdout / stderr
     * @param line 一行日志(不含换行符)
     */
    void accept(String stream, String line);

    /**
     * 接收一行默认输出流类型的构建输出。
     *
     * @param line 一行日志(不含换行符)
     */
    default void accept(String line) {
        accept("stdout", line);
    }

}
