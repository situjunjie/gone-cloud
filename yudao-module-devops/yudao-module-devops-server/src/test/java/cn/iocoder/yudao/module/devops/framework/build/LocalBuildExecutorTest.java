package cn.iocoder.yudao.module.devops.framework.build;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.DisabledOnOs;
import org.springframework.test.util.ReflectionTestUtils;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.Executor;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.condition.OS.WINDOWS;

/**
 * {@link LocalBuildExecutor} 的单元测试。
 *
 * 依赖本机 bash,Windows 上禁用。
 */
@DisabledOnOs(WINDOWS)
public class LocalBuildExecutorTest {

    private final LocalBuildExecutor executor = new LocalBuildExecutor();

    /**
     * 注入构建日志读取线程池(单测用缓存线程池替代 Spring 容器中的 buildLogReaderExecutor)。
     */
    @BeforeEach
    public void setUp() {
        Executor readerExecutor = Executors.newCachedThreadPool(runnable -> {
            Thread thread = new Thread(runnable);
            thread.setDaemon(true);
            return thread;
        });
        ReflectionTestUtils.setField(executor, "buildLogReaderExecutor", readerExecutor);
    }

    @Test
    public void testExec_success() {
        // 准备参数
        ExecContext ctx = ExecContext.builder()
                .runId("run-success")
                .timeoutSeconds(30)
                .build();
        List<String> lines = new ArrayList<>();

        // 调用
        ExecResult result = executor.exec(ctx, "echo hello", (stream, line) -> lines.add(line));

        // 断言
        assertTrue(result.isSuccess());
        assertEquals(0, result.getExitCode());
        assertTrue(lines.contains("hello"));
    }

    @Test
    public void testExec_failureExitCode() {
        // 准备参数
        ExecContext ctx = ExecContext.builder()
                .runId("run-failure")
                .timeoutSeconds(30)
                .build();

        // 调用
        ExecResult result = executor.exec(ctx, "exit 3", (stream, line) -> {});

        // 断言
        assertFalse(result.isSuccess());
        assertEquals(3, result.getExitCode());
        assertNotNull(result.getErrorMessage());
    }

    @Test
    public void testExec_streamsLinesInOrder() {
        // 准备参数
        ExecContext ctx = ExecContext.builder()
                .runId("run-stream")
                .timeoutSeconds(30)
                .build();
        List<String> lines = new ArrayList<>();

        // 调用:多行输出 + stderr 合并
        ExecResult result = executor.exec(ctx, "echo line1; echo line2 1>&2; echo line3",
                (stream, line) -> lines.add(line));

        // 断言:逐行回填且保序(redirectErrorStream 合并)
        assertTrue(result.isSuccess());
        assertEquals(List.of("line1", "line2", "line3"), lines);
    }

    @Test
    public void testExec_injectsEnv() {
        // 准备参数:env 注入(模拟凭据注入路径)
        ExecContext ctx = ExecContext.builder()
                .runId("run-env")
                .timeoutSeconds(30)
                .env(Map.of("MY_TOKEN", "secret-value"))
                .build();
        List<String> lines = new ArrayList<>();

        // 调用
        ExecResult result = executor.exec(ctx, "echo $MY_TOKEN", (stream, line) -> lines.add(line));

        // 断言
        assertTrue(result.isSuccess());
        assertTrue(lines.contains("secret-value"));
    }

    @Test
    public void testExec_timeoutKilled() {
        // 准备参数:1 秒超时
        ExecContext ctx = ExecContext.builder()
                .runId("run-timeout")
                .timeoutSeconds(1)
                .build();

        // 调用:睡 10 秒,应被强杀
        long start = System.currentTimeMillis();
        ExecResult result = executor.exec(ctx, "sleep 10", (stream, line) -> {});
        long elapsed = System.currentTimeMillis() - start;

        // 断言:未成功,且未真的等满 10 秒
        assertFalse(result.isSuccess());
        assertNotNull(result.getErrorMessage());
        assertTrue(result.getErrorMessage().contains("timeout"));
        assertTrue(elapsed < 9000, "应在超时后被强杀,而非跑满 10 秒");
    }

    @Test
    public void testCancel_terminatesRunningProcess() throws Exception {
        // 准备参数:长时运行脚本
        String runId = "run-cancel";
        ExecContext ctx = ExecContext.builder()
                .runId(runId)
                .timeoutSeconds(60)
                .build();
        List<ExecResult> resultHolder = new CopyOnWriteArrayList<>();

        // 在独立线程执行
        Thread execThread = new Thread(() ->
                resultHolder.add(executor.exec(ctx, "sleep 30", (stream, line) -> {})));
        execThread.start();

        // 等待进程登记后取消
        TimeUnit.MILLISECONDS.sleep(500);
        executor.cancel(runId);

        // 断言:exec 很快返回,且未成功
        execThread.join(TimeUnit.SECONDS.toMillis(10));
        assertFalse(execThread.isAlive());
        assertEquals(1, resultHolder.size());
        assertFalse(resultHolder.get(0).isSuccess());
    }

    @Test
    public void testCancel_unknownRunIdIsNoop() {
        // 调用未知 runId 不应抛异常
        executor.cancel("does-not-exist");
        executor.cancel(null);
    }

}
