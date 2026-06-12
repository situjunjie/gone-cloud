package cn.iocoder.yudao.module.devops.framework.build;

import org.apache.sshd.common.util.threads.ThreadUtils;
import org.apache.sshd.server.SshServer;
import org.apache.sshd.server.channel.ChannelSession;
import org.apache.sshd.server.command.AbstractCommandSupport;
import org.apache.sshd.server.keyprovider.SimpleGeneratorHostKeyProvider;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.DisabledOnOs;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.TimeUnit;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.condition.OS.WINDOWS;

/**
 * {@link SshBuildExecutor} 的单元测试。
 *
 * <p>用 Apache MINA SSHD 在本机起一个内嵌 {@code SshServer},监听随机端口,
 * 用真实 SSH 连接验证:成功退出码、失败退出码透传、env 注入、超时、cancel 关闭通道、逐行回调。
 *
 * <p>内嵌 server 的 exec 命令交由 {@link ProcessShellCommandFactory} 在 localhost 执行真实 bash,
 * 故依赖本机 bash,Windows 上禁用。
 */
@DisabledOnOs(WINDOWS)
public class SshBuildExecutorTest {

    private SshServer sshServer;
    private SshBuildExecutor executor;
    private int port;

    @BeforeEach
    public void setUp() throws Exception {
        sshServer = SshServer.setUpDefaultServer();
        sshServer.setPort(0); // 随机端口
        Path hostKey = Files.createTempFile("ssh-build-host-key", ".ser");
        sshServer.setKeyPairProvider(new SimpleGeneratorHostKeyProvider(hostKey));
        // 测试用:任意用户名/密码均通过
        sshServer.setPasswordAuthenticator((username, password, session) -> true);
        // exec 通道命令交给本机 bash -c 执行,模拟真实 sshd 把 exec 命令交给登录 shell 的行为,
        // 并通过 ExitCallback 可靠透传退出码
        sshServer.setCommandFactory((channel, command) -> new BashCommand(command));
        sshServer.start();
        port = sshServer.getPort();

        executor = new SshBuildExecutor();
        executor.init();
    }

    @AfterEach
    public void tearDown() throws Exception {
        if (executor != null) {
            executor.destroy();
        }
        if (sshServer != null) {
            sshServer.stop(true);
        }
    }

    private SshTarget passwordTarget() {
        return SshTarget.builder()
                .host("127.0.0.1")
                .port(port)
                .username("builder")
                .password("secret")
                .build();
    }

    @Test
    public void testExec_success() {
        ExecContext ctx = ExecContext.builder()
                .runId("ssh-success")
                .timeoutSeconds(30)
                .sshTarget(passwordTarget())
                .build();
        List<String> lines = new CopyOnWriteArrayList<>();

        ExecResult result = executor.exec(ctx, "echo hello", lines::add);

        assertTrue(result.isSuccess());
        assertEquals(0, result.getExitCode());
        assertTrue(lines.contains("hello"));
    }

    @Test
    public void testExec_failureExitCodePropagated() {
        ExecContext ctx = ExecContext.builder()
                .runId("ssh-failure")
                .timeoutSeconds(30)
                .sshTarget(passwordTarget())
                .build();

        ExecResult result = executor.exec(ctx, "exit 3", line -> {});

        assertFalse(result.isSuccess());
        assertEquals(3, result.getExitCode());
        assertNotNull(result.getErrorMessage());
    }

    @Test
    public void testExec_streamsLinesInOrder() {
        ExecContext ctx = ExecContext.builder()
                .runId("ssh-stream")
                .timeoutSeconds(30)
                .sshTarget(passwordTarget())
                .build();
        List<String> lines = new CopyOnWriteArrayList<>();

        ExecResult result = executor.exec(ctx, "echo line1; echo line2; echo line3", lines::add);

        assertTrue(result.isSuccess());
        assertTrue(lines.contains("line1"));
        assertTrue(lines.contains("line2"));
        assertTrue(lines.contains("line3"));
    }

    @Test
    public void testExec_injectsEnv() {
        ExecContext ctx = ExecContext.builder()
                .runId("ssh-env")
                .timeoutSeconds(30)
                .env(Map.of("MY_TOKEN", "secret-value"))
                .sshTarget(passwordTarget())
                .build();
        List<String> lines = new CopyOnWriteArrayList<>();

        ExecResult result = executor.exec(ctx, "echo $MY_TOKEN", lines::add);

        assertTrue(result.isSuccess());
        assertTrue(lines.contains("secret-value"));
    }

    @Test
    public void testExec_runsInWorkingDir() throws Exception {
        Path workDir = Files.createTempDirectory("ssh-build-workdir");
        ExecContext ctx = ExecContext.builder()
                .runId("ssh-workdir")
                .timeoutSeconds(30)
                .workingDir(workDir)
                .sshTarget(passwordTarget())
                .build();
        List<String> lines = new CopyOnWriteArrayList<>();

        ExecResult result = executor.exec(ctx, "pwd", lines::add);

        assertTrue(result.isSuccess());
        // pwd 输出应包含工作目录(macOS 上 /var -> /private/var 软链,故只断言后缀)
        assertTrue(lines.stream().anyMatch(line -> line.endsWith(workDir.getFileName().toString())));
    }

    @Test
    public void testExec_timeout() {
        ExecContext ctx = ExecContext.builder()
                .runId("ssh-timeout")
                .timeoutSeconds(1)
                .sshTarget(passwordTarget())
                .build();

        long start = System.currentTimeMillis();
        ExecResult result = executor.exec(ctx, "sleep 10", line -> {});
        long elapsed = System.currentTimeMillis() - start;

        assertFalse(result.isSuccess());
        assertNotNull(result.getErrorMessage());
        assertTrue(result.getErrorMessage().contains("timeout"));
        assertTrue(elapsed < 9000, "应在超时后返回,而非跑满 10 秒");
    }

    @Test
    public void testExec_missingTargetFails() {
        ExecContext ctx = ExecContext.builder()
                .runId("ssh-no-target")
                .timeoutSeconds(30)
                .build();

        ExecResult result = executor.exec(ctx, "echo hi", line -> {});

        assertFalse(result.isSuccess());
        assertEquals(-1, result.getExitCode());
        assertNotNull(result.getErrorMessage());
    }

    @Test
    public void testCancel_terminatesRunningExec() throws Exception {
        String runId = "ssh-cancel";
        ExecContext ctx = ExecContext.builder()
                .runId(runId)
                .timeoutSeconds(60)
                .sshTarget(passwordTarget())
                .build();
        List<ExecResult> resultHolder = new CopyOnWriteArrayList<>();

        Thread execThread = new Thread(() ->
                resultHolder.add(executor.exec(ctx, "sleep 30", line -> {})));
        execThread.start();

        // 等待通道登记后取消
        TimeUnit.MILLISECONDS.sleep(1000);
        executor.cancel(runId);

        execThread.join(TimeUnit.SECONDS.toMillis(15));
        assertFalse(execThread.isAlive(), "cancel 后 exec 应尽快返回");
        assertEquals(1, resultHolder.size());
        assertFalse(resultHolder.get(0).isSuccess());
    }

    @Test
    public void testCancel_unknownRunIdIsNoop() {
        executor.cancel("does-not-exist");
        executor.cancel(null);
    }

    /**
     * 测试用服务端命令:把 SSH exec 命令交给本机 {@code bash -c} 执行,
     * stdout/stderr 透传回客户端,并通过 {@link ExitCallback} 可靠回传退出码。
     *
     * <p>继承 {@link AbstractCommandSupport} 以复用其流装配与退出回调的底层管线,
     * 只实现 {@link #run()} 中的真实命令执行。
     */
    private static final class BashCommand extends AbstractCommandSupport {

        private Process process;

        private BashCommand(String command) {
            super(command, ThreadUtils.newSingleThreadExecutor("bash-cmd-" + command.hashCode()));
        }

        @Override
        public void run() {
            int exitCode;
            try {
                ProcessBuilder builder = new ProcessBuilder("bash", "-c", getCommand());
                builder.redirectErrorStream(true);
                process = builder.start();
                process.getInputStream().transferTo(getOutputStream());
                exitCode = process.waitFor();
                // 关闭输出流以发送 EOF,使客户端通道能正常关闭
                getOutputStream().flush();
                getOutputStream().close();
                getErrorStream().close();
            } catch (Exception ex) {
                onExit(-1, ex.getMessage());
                return;
            }
            onExit(exitCode);
        }

        @Override
        public void destroy(ChannelSession channel) throws Exception {
            if (process != null) {
                process.destroyForcibly();
            }
            super.destroy(channel);
        }

    }

}
