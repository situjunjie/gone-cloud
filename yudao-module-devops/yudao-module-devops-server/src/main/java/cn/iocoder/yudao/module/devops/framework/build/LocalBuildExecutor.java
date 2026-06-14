package cn.iocoder.yudao.module.devops.framework.build;

import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.stereotype.Component;

import jakarta.annotation.Resource;
import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.Executor;
import java.util.concurrent.TimeUnit;

/**
 * 本机构建执行器(退化特例)。
 *
 * 用 {@link ProcessBuilder} 执行 {@code bash -c <script>},对齐
 * {@code GitCommandExecutor} 的超时/redirectErrorStream/destroyForcibly 写法,
 * 区别在于用独立 reader 线程流式逐行读取并回调 {@link LogSink}(而非一次性 readAllBytes)。
 *
 * {@link #cancel(String)} 通过 {@link #runningProcesses} 维护 runId 到 Process 的映射,
 * 取消时对运行中的进程调用 {@code destroyForcibly}。
 */
@Slf4j
@Component
public class LocalBuildExecutor implements BuildExecutor {

    private static final long DEFAULT_TIMEOUT_SECONDS = 1800;

    /**
     * runId 到运行中进程的映射,用于 {@link #cancel(String)}。
     */
    private final Map<String, Process> runningProcesses = new ConcurrentHashMap<>();

    /**
     * 构建日志读取线程池。
     */
    @Resource
    @Qualifier("buildLogReaderExecutor")
    private Executor buildLogReaderExecutor;

    @Override
    public ExecResult exec(ExecContext ctx, String script, LogSink sink) {
        ProcessBuilder builder = new ProcessBuilder("bash", "-c", script);
        if (ctx.getWorkingDir() != null) {
            builder.directory(ctx.getWorkingDir().toFile());
        }
        if (ctx.getEnv() != null) {
            // 注意:env 可能含凭据,绝不可记录其内容
            builder.environment().putAll(ctx.getEnv());
        }
        builder.redirectErrorStream(true);

        String runId = ctx.getRunId();
        long timeoutSeconds = ctx.getTimeoutSeconds() > 0 ? ctx.getTimeoutSeconds() : DEFAULT_TIMEOUT_SECONDS;

        Process process;
        try {
            process = builder.start();
        } catch (IOException ex) {
            return ExecResult.failure(-1, "Build script cannot start: " + ex.getMessage());
        }
        if (runId != null) {
            runningProcesses.put(runId, process);
        }

        // 独立 reader 线程流式逐行回填日志
        CountDownLatch readerDone = new CountDownLatch(1);
        buildLogReaderExecutor.execute(() -> {
            try (BufferedReader bufferedReader = new BufferedReader(
                    new InputStreamReader(process.getInputStream(), StandardCharsets.UTF_8))) {
                String line;
                while ((line = bufferedReader.readLine()) != null) {
                    if (sink != null) {
                        sink.accept(line);
                    }
                }
            } catch (IOException ex) {
                // 进程被强杀时读流抛异常属预期,降级记录
                log.debug("[exec][runId({}) 读取构建输出结束: {}]", runId, ex.getMessage());
            } finally {
                readerDone.countDown();
            }
        });

        try {
            boolean finished = process.waitFor(timeoutSeconds, TimeUnit.SECONDS);
            if (!finished) {
                process.destroyForcibly();
                awaitReader(readerDone);
                return ExecResult.failure(-1, "Build script timeout after " + timeoutSeconds + "s");
            }
            awaitReader(readerDone);
            int exitCode = process.exitValue();
            if (exitCode == 0) {
                return ExecResult.success();
            }
            return ExecResult.failure(exitCode, "Build script failed with exit code " + exitCode);
        } catch (InterruptedException ex) {
            Thread.currentThread().interrupt();
            process.destroyForcibly();
            return ExecResult.failure(-1, "Build script interrupted");
        } finally {
            if (runId != null) {
                runningProcesses.remove(runId);
            }
        }
    }

    @Override
    public void cancel(String runId) {
        if (runId == null) {
            return;
        }
        Process process = runningProcesses.get(runId);
        if (process != null) {
            log.info("[cancel][runId({}) 终止构建进程]", runId);
            process.destroyForcibly();
        }
    }

    private void awaitReader(CountDownLatch readerDone) {
        try {
            readerDone.await(5, TimeUnit.SECONDS);
        } catch (InterruptedException ex) {
            Thread.currentThread().interrupt();
        }
    }

}
