package cn.iocoder.yudao.module.devops.framework.pipeline.runtime;

import cn.iocoder.yudao.module.devops.dal.dataobject.host.EnvironmentHostDO;
import cn.iocoder.yudao.module.devops.framework.build.ExecResult;
import cn.iocoder.yudao.module.devops.framework.build.LogSink;
import cn.iocoder.yudao.module.devops.framework.host.HostSshClient;
import com.jcraft.jsch.ChannelExec;
import com.jcraft.jsch.JSchException;
import com.jcraft.jsch.Session;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.springframework.test.util.ReflectionTestUtils;

import java.io.ByteArrayOutputStream;
import java.io.OutputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

class SshPipelineCommandExecutorTest {

    private final SshPipelineCommandExecutor executor = new SshPipelineCommandExecutor();
    private final HostSshClient client = mock(HostSshClient.class);
    private final Session session = mock(Session.class);
    private final ChannelExec channel = mock(ChannelExec.class);
    private final ByteArrayOutputStream heartbeat = spy(new ByteArrayOutputStream());
    @TempDir
    Path workspace;

    @BeforeEach
    void setUp() throws Exception {
        ReflectionTestUtils.setField(executor, "hostSshClient", client);
        when(client.openSession(any(), anyInt())).thenReturn(session);
        when(session.openChannel("exec")).thenReturn(channel);
        when(channel.getOutputStream()).thenReturn(heartbeat);
    }

    @Test
    void streamsBothOutputsAndPreservesNonzeroExit() throws Exception {
        LogSink sink = mock(LogSink.class);
        doAnswer(call -> {
            OutputStream out = call.getArgument(0);
            out.write("实时输出\n尾行".getBytes(StandardCharsets.UTF_8));
            return null;
        }).when(channel).setOutputStream(any(OutputStream.class), eq(true));
        doAnswer(call -> {
            OutputStream out = call.getArgument(0);
            out.write("failure\n".getBytes(StandardCharsets.UTF_8));
            return null;
        }).when(channel).setErrStream(any(OutputStream.class), eq(true));
        when(channel.isClosed()).thenReturn(true);
        when(channel.getExitStatus()).thenReturn(7);

        ExecResult result = executor.exec(context(5), "exit 7", sink);

        assertEquals(7, result.getExitCode());
        verify(sink).accept("stdout", "实时输出");
        verify(sink).accept("stdout", "尾行");
        verify(sink).accept("stderr", "failure");
        verify(channel).disconnect();
        verify(session).disconnect();
    }

    @Test
    void preservesUtf8AcrossSshPacketFlushesAndFragmentLimit() throws Exception {
        String text = "a".repeat(8191) + "中文日志";
        StringBuilder actual = new StringBuilder();
        doAnswer(call -> {
            OutputStream out = call.getArgument(0);
            byte[] bytes = text.getBytes(StandardCharsets.UTF_8);
            // The fragment limit splits the first Chinese character; subsequent packets split the rest.
            out.write(bytes, 0, 8192);
            out.flush();
            for (int i = 8192; i < bytes.length; i++) {
                out.write(bytes[i]);
                out.flush();
            }
            return null;
        }).when(channel).setOutputStream(any(OutputStream.class), eq(true));
        when(channel.isClosed()).thenReturn(true);
        when(channel.getExitStatus()).thenReturn(0);

        assertTrue(executor.exec(context(5), "true", (stream, line) -> actual.append(line)).isSuccess());
        assertEquals(text, actual.toString());
    }

    @Test
    void timeoutRevokesLeaseAndDisconnects() throws Exception {
        when(channel.isClosed()).thenReturn(false);
        ExecResult result = executor.exec(context(1), "sleep 30", null);
        assertEquals(124, result.getExitCode());
        verify(heartbeat).close();
        verify(channel).disconnect();
        verify(session).disconnect();
    }

    @Test
    void durableCancellationBeforeConnectionDoesNotOpenSession() {
        PipelineCommandContext ctx = PipelineCommandContext.builder().sshHost(new EnvironmentHostDO())
                .runId("1:job").workingDirectory("/workspace").workspaceProbe(".probe").workspaceToken("token")
                .cancellationRequested(() -> true).build();

        assertEquals(130, executor.exec(ctx, "true", null).getExitCode());
        verifyNoInteractions(client);
    }

    @Test
    void durableCancellationAfterSessionOpenDoesNotOpenChannel() throws Exception {
        java.util.concurrent.atomic.AtomicBoolean canceled = new java.util.concurrent.atomic.AtomicBoolean();
        when(client.openSession(any(), anyInt())).thenAnswer(call -> {
            canceled.set(true);
            return session;
        });
        PipelineCommandContext ctx = PipelineCommandContext.builder().sshHost(new EnvironmentHostDO())
                .runId("1:job").workingDirectory("/workspace").workspaceProbe(".probe").workspaceToken("token")
                .cancellationRequested(canceled::get).build();

        assertEquals(130, executor.exec(ctx, "true", null).getExitCode());
        verifyNoInteractions(channel);
        verify(session).disconnect();
    }

    @Test
    void durableCancellationDuringChannelConnectNeverGrantsLease() throws Exception {
        java.util.concurrent.atomic.AtomicBoolean canceled = new java.util.concurrent.atomic.AtomicBoolean();
        doAnswer(call -> { canceled.set(true); return null; }).when(channel).connect(anyInt());
        PipelineCommandContext ctx = PipelineCommandContext.builder().sshHost(new EnvironmentHostDO())
                .runId("1:job").workingDirectory("/workspace").workspaceProbe(".probe").workspaceToken("token")
                .cancellationRequested(canceled::get).build();

        assertEquals(130, executor.exec(ctx, "true", null).getExitCode());
        assertEquals(0, heartbeat.size());
        verify(heartbeat, atLeastOnce()).close();
        verify(channel).disconnect();
        verify(session).disconnect();
    }

    @Test
    void cancellationRevokesLeaseAndReturns130() throws Exception {
        CountDownLatch connected = new CountDownLatch(1);
        doAnswer(call -> { connected.countDown(); return null; }).when(channel).connect(anyInt());
        CompletableFuture<ExecResult> result = CompletableFuture.supplyAsync(
                () -> executor.exec(context(30), "sleep 30", null));
        assertTrue(connected.await(3, TimeUnit.SECONDS));
        executor.cancel("1:job");
        assertEquals(130, result.get(3, TimeUnit.SECONDS).getExitCode());
        verify(heartbeat, atLeastOnce()).close();
    }

    @Test
    void interruptionPreservesThreadFlagAndCleansUp() throws Exception {
        Thread.currentThread().interrupt();
        try {
            assertEquals(130, executor.exec(context(30), "sleep 30", null).getExitCode());
            assertTrue(Thread.currentThread().isInterrupted());
            verify(channel).disconnect();
        } finally {
            Thread.interrupted();
        }
    }

    @Test
    void connectionFailureDoesNotExposeCredentialsOrScript() throws Exception {
        when(client.openSession(any(), anyInt())).thenThrow(new JSchException("password=secret"));
        ExecResult result = executor.exec(context(5), "echo private-command", null);
        assertEquals(-1, result.getExitCode());
        assertFalse(result.getErrorMessage().contains("secret"));
        assertFalse(result.getErrorMessage().contains("private-command"));
        verifyNoInteractions(channel);
    }

    @Test
    void connectionAttemptUsesRemainingDeadline() throws Exception {
        when(client.openSession(any(), anyInt())).thenAnswer(call -> {
            int timeout = call.getArgument(1);
            assertTrue(timeout > 0 && timeout <= 1000);
            Thread.sleep(1050);
            throw new JSchException("connect timeout");
        });
        assertEquals(124, executor.exec(context(1), "true", null).getExitCode());
        verifyNoInteractions(channel);
    }

    @Test
    void rejectsInvalidEnvBeforeOpeningConnection() {
        PipelineCommandContext ctx = PipelineCommandContext.builder().sshHost(new EnvironmentHostDO())
                .runId("1:job").workingDirectory("/workspace").workspaceProbe(".probe").workspaceToken("token")
                .env(Map.of("BAD;echo injected", "x")).build();
        assertEquals(-1, executor.exec(ctx, "true", null).getExitCode());
        verifyNoInteractions(client);
    }

    @Test
    void shellWrapperQuotesValuesChecksWorkspaceAndPreservesExit() throws Exception {
        Files.writeString(workspace.resolve(".probe"), "token");
        PipelineCommandContext ctx = context(5);
        Process process = startWrapper(ctx, "printf '%s\\n' \"$VALUE\"; pwd; echo error >&2; exit 7", 5);
        sendLease(process);
        assertTrue(process.waitFor(8, TimeUnit.SECONDS));
        assertEquals(7, process.exitValue());
        String output = new String(process.getInputStream().readAllBytes(), StandardCharsets.UTF_8);
        assertTrue(output.contains("a'b;$(echo injected)\n"), output);
        assertTrue(output.contains(workspace.toString()), output);
        assertEquals("error\n", new String(process.getErrorStream().readAllBytes(), StandardCharsets.UTF_8));
    }

    @Test
    void wrongWorkspaceNeverRunsUserScript() throws Exception {
        Files.writeString(workspace.resolve(".probe"), "wrong-token");
        Process process = startWrapper(context(5), "touch should-not-exist", 5);
        sendLease(process);
        assertTrue(process.waitFor(8, TimeUnit.SECONDS));
        assertEquals(125, process.exitValue());
        assertFalse(Files.exists(workspace.resolve("should-not-exist")));
    }

    @Test
    void eofAndRemoteDeadlineTerminateForegroundProcessGroup() throws Exception {
        Files.writeString(workspace.resolve(".probe"), "token");
        for (boolean eof : new boolean[]{true, false}) {
            Process process = startWrapper(context(30), "sleep 30 & echo $! > child.pid; wait", eof ? 30 : 2);
            sendLease(process);
            Path pidFile = workspace.resolve("child.pid");
            long deadline = System.nanoTime() + TimeUnit.SECONDS.toNanos(5);
            while (!Files.exists(pidFile) && System.nanoTime() < deadline) {
                Thread.sleep(20);
            }
            assertTrue(Files.exists(pidFile));
            long child = Long.parseLong(Files.readString(pidFile).trim());
            if (eof) {
                process.getOutputStream().close();
            }
            assertTrue(process.waitFor(8, TimeUnit.SECONDS));
            assertEquals(eof ? 130 : 124, process.exitValue());
            assertFalse(ProcessHandle.of(child).map(ProcessHandle::isAlive).orElse(false), "child survived lease revocation");
            Files.delete(pidFile);
        }
    }

    private PipelineCommandContext context(long timeout) {
        return PipelineCommandContext.builder().runId("1:job").sshHost(new EnvironmentHostDO())
                .workingDirectory(workspace.toString()).workspaceProbe(".probe").workspaceToken("token")
                .timeoutSeconds(timeout).env(Map.of("VALUE", "a'b;$(echo injected)")).build();
    }

    private void sendLease(Process process) throws Exception {
        process.getOutputStream().write('\n');
        process.getOutputStream().flush();
    }

    private Process startWrapper(PipelineCommandContext ctx, String script, long timeout) throws Exception {
        // macOS has no setsid utility. A test-only Python shim gives the same POSIX session semantics.
        Path bin = Files.createDirectories(workspace.resolve("bin"));
        Path setsid = bin.resolve("setsid");
        Files.writeString(setsid, "#!/usr/bin/env python3\nimport os,sys\nos.setsid()\nos.execvp(sys.argv[1],sys.argv[1:])\n");
        assertTrue(setsid.toFile().setExecutable(true));
        ProcessBuilder builder = new ProcessBuilder("bash", "-c", SshPipelineCommandExecutor.buildCommand(ctx, script, timeout));
        builder.environment().put("PATH", bin + ":" + System.getenv("PATH"));
        return builder.start();
    }
}
