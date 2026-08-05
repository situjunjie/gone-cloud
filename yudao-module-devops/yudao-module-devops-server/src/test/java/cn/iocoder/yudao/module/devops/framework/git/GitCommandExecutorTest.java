package cn.iocoder.yudao.module.devops.framework.git;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.util.List;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicReference;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTimeoutPreemptively;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * {@link GitCommandExecutor} 的单元测试。
 */
public class GitCommandExecutorTest {

    private final GitCommandExecutor executor = new GitCommandExecutor();

    @TempDir
    private Path tempDir;

    @Test
    public void testExecute_largeOutputDrainedAndReturnedCompletely() {
        GitCommandResult result = assertTimeoutPreemptively(Duration.ofSeconds(10),
                () -> executor.execute(null, javaCommand("1048576", "0"), true));

        assertEquals(0, result.getExitCode());
        assertEquals(1048576, result.getOutput().getBytes(java.nio.charset.StandardCharsets.UTF_8).length);
    }

    @Test
    public void testExecute_nonZeroExitRetainsCompleteOutput() {
        GitCommandException exception = assertTimeoutPreemptively(Duration.ofSeconds(10),
                () -> assertThrows(GitCommandException.class,
                        () -> executor.execute(null, javaCommand("1048576", "7"), true)));

        assertEquals(1048576, exception.getOutput().getBytes(java.nio.charset.StandardCharsets.UTF_8).length);
    }

    @Test
    public void testExecute_preservesCompleteUtf8Content() {
        GitCommandResult result = executor.execute(null, javaCommand("6000", "0", "utf8"), true);

        assertEquals("冲突".repeat(1000), result.getOutput());
    }

    @Test
    public void testExecute_workDirUsesGitC() {
        Path repository = tempDir.resolve("repository");
        assertTrue(repository.toFile().mkdirs());

        GitCommandResult result = executor.execute(repository, List.of("git", "init"), true);

        assertEquals(0, result.getExitCode());
        assertTrue(Files.isDirectory(repository.resolve(".git")));
    }

    @Test
    public void testExecute_interruptionTerminatesProcessAndRestoresInterruptFlag() {
        assertTimeoutPreemptively(Duration.ofSeconds(15), () -> {
            Path pidFile = tempDir.resolve("child.pid");
            AtomicReference<GitCommandException> failure = new AtomicReference<>();
            AtomicBoolean interrupted = new AtomicBoolean();
            Thread caller = new Thread(() -> {
                try {
                    executor.execute(null, sleepingJavaCommand(pidFile), true);
                } catch (GitCommandException ex) {
                    failure.set(ex);
                    interrupted.set(Thread.currentThread().isInterrupted());
                }
            }, "git-command-interruption-test");
            caller.start();
            long startupDeadline = System.nanoTime() + TimeUnit.SECONDS.toNanos(5);
            while (!Files.exists(pidFile) && caller.isAlive() && System.nanoTime() < startupDeadline) {
                Thread.sleep(10);
            }
            if (!Files.exists(pidFile)) {
                caller.interrupt();
                caller.join(TimeUnit.SECONDS.toMillis(10));
            }
            assertTrue(Files.exists(pidFile));
            long childPid = Long.parseLong(Files.readString(pidFile));

            caller.interrupt();
            caller.join(TimeUnit.SECONDS.toMillis(10));

            assertFalse(caller.isAlive());
            assertNotNull(failure.get());
            assertEquals("Git command interrupted", failure.get().getMessage());
            assertEquals("started" + System.lineSeparator(), failure.get().getOutput());
            assertTrue(interrupted.get());
            assertFalse(ProcessHandle.of(childPid).map(ProcessHandle::isAlive).orElse(false));
        });
    }

    private List<String> javaCommand(String... args) {
        List<String> command = new java.util.ArrayList<>(List.of(
                Path.of(System.getProperty("java.home"), "bin", "java").toString(),
                "-cp", System.getProperty("java.class.path"), LargeOutputProcess.class.getName()));
        command.addAll(List.of(args));
        return command;
    }

    private List<String> sleepingJavaCommand(Path pidFile) {
        return List.of(Path.of(System.getProperty("java.home"), "bin", "java").toString(),
                "-cp", System.getProperty("java.class.path"), SleepingProcess.class.getName(), pidFile.toString());
    }

    public static class LargeOutputProcess {

        public static void main(String[] args) throws Exception {
            int outputBytes = Integer.parseInt(args[0]);
            int exitCode = Integer.parseInt(args[1]);
            byte[] chunk = args.length > 2 && "utf8".equals(args[2])
                    ? "冲突".repeat(1365).getBytes(java.nio.charset.StandardCharsets.UTF_8)
                    : "x".repeat(8192).getBytes(java.nio.charset.StandardCharsets.UTF_8);
            int remaining = outputBytes;
            while (remaining > 0) {
                int length = Math.min(remaining, chunk.length);
                System.out.write(chunk, 0, length);
                remaining -= length;
            }
            System.out.flush();
            System.exit(exitCode);
        }
    }

    public static class SleepingProcess {

        public static void main(String[] args) throws Exception {
            System.out.println("started");
            System.out.flush();
            Files.writeString(Path.of(args[0]), String.valueOf(ProcessHandle.current().pid()));
            Thread.sleep(TimeUnit.MINUTES.toMillis(1));
        }
    }
}
