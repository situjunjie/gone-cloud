package cn.iocoder.yudao.module.devops.framework.git;

import cn.hutool.core.io.IORuntimeException;
import cn.hutool.core.util.RuntimeUtil;
import org.springframework.stereotype.Component;

import java.nio.charset.StandardCharsets;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.FutureTask;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;

/**
 * 本机 Git 命令执行器。
 */
@Component
public class GitCommandExecutor {

    private static final long TIMEOUT_SECONDS = 300;
    private static final long PROCESS_TERMINATION_TIMEOUT_SECONDS = 5;

    public GitCommandResult execute(Path workDir, List<String> command, boolean checkExitCode) {
        List<String> normalizedCommand = normalizeCommand(workDir, command);
        Process process;
        try {
            process = RuntimeUtil.exec(normalizedCommand.toArray(String[]::new));
        } catch (IORuntimeException ex) {
            throw new GitCommandException("Git command cannot start: " + ex.getMessage(), null);
        }

        FutureTask<String> outputTask = new FutureTask<>(
                () -> RuntimeUtil.getResult(process, StandardCharsets.UTF_8));
        Thread readerThread = new Thread(outputTask, "git-command-output-reader");
        readerThread.setDaemon(true);
        readerThread.start();
        try {
            String output = outputTask.get(TIMEOUT_SECONDS, TimeUnit.SECONDS);
            if (!process.waitFor(PROCESS_TERMINATION_TIMEOUT_SECONDS, TimeUnit.SECONDS)) {
                terminateProcessTree(process);
                throw new GitCommandException("Git command cannot exit", output);
            }
            GitCommandResult result = new GitCommandResult(process.exitValue(), output);
            if (checkExitCode && !result.isSuccess()) {
                throw new GitCommandException("Git command failed", output);
            }
            return result;
        } catch (TimeoutException ex) {
            terminateProcessTree(process);
            String output = collectOutputAfterTermination(outputTask, readerThread);
            throw new GitCommandException("Git command timeout", output);
        } catch (ExecutionException ex) {
            terminateProcessTree(process);
            Throwable cause = ex.getCause() == null ? ex : ex.getCause();
            throw new GitCommandException("Read Git command output failed: " + cause.getMessage(), null);
        } catch (InterruptedException ex) {
            terminateProcessTree(process);
            String output = collectOutputAfterTermination(outputTask, readerThread);
            Thread.currentThread().interrupt();
            throw new GitCommandException("Git command interrupted", output);
        }
    }

    private List<String> normalizeCommand(Path workDir, List<String> command) {
        if (command == null || command.isEmpty()) {
            throw new IllegalArgumentException("Git command must not be empty");
        }
        if (workDir == null) {
            return command;
        }
        String executableName = Path.of(command.get(0)).getFileName().toString();
        if (!"git".equals(executableName)) {
            throw new IllegalArgumentException("workDir is supported for Git commands only");
        }
        List<String> normalized = new ArrayList<>(command.size() + 2);
        normalized.add(command.get(0));
        normalized.add("-C");
        normalized.add(workDir.toString());
        normalized.addAll(command.subList(1, command.size()));
        return normalized;
    }

    private void terminateProcessTree(Process process) {
        List<ProcessHandle> descendants = process.descendants().toList();
        descendants.forEach(ProcessHandle::destroy);
        process.destroy();
        try {
            if (!process.waitFor(PROCESS_TERMINATION_TIMEOUT_SECONDS, TimeUnit.SECONDS)) {
                descendants.stream().filter(ProcessHandle::isAlive).forEach(ProcessHandle::destroyForcibly);
                process.destroyForcibly();
                process.waitFor(PROCESS_TERMINATION_TIMEOUT_SECONDS, TimeUnit.SECONDS);
            }
        } catch (InterruptedException ex) {
            Thread.currentThread().interrupt();
            process.destroyForcibly();
        } finally {
            descendants.stream().filter(ProcessHandle::isAlive).forEach(ProcessHandle::destroyForcibly);
        }
    }

    private void awaitReader(Thread readerThread) {
        try {
            readerThread.join(TimeUnit.SECONDS.toMillis(PROCESS_TERMINATION_TIMEOUT_SECONDS));
        } catch (InterruptedException ex) {
            Thread.currentThread().interrupt();
        }
    }

    private String collectOutputAfterTermination(FutureTask<String> outputTask, Thread readerThread) {
        try {
            return outputTask.get(PROCESS_TERMINATION_TIMEOUT_SECONDS, TimeUnit.SECONDS);
        } catch (ExecutionException | TimeoutException ex) {
            outputTask.cancel(true);
            return null;
        } catch (InterruptedException ex) {
            outputTask.cancel(true);
            Thread.currentThread().interrupt();
            return null;
        } finally {
            awaitReader(readerThread);
        }
    }

}
