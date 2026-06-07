package cn.iocoder.yudao.module.devops.framework.git;

import cn.hutool.core.util.StrUtil;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Path;
import java.util.List;
import java.util.concurrent.TimeUnit;

/**
 * 本机 Git 命令执行器。
 */
@Component
public class GitCommandExecutor {

    private static final int OUTPUT_LIMIT = 4000;
    private static final long TIMEOUT_SECONDS = 300;

    public GitCommandResult execute(Path workDir, List<String> command, boolean checkExitCode) {
        ProcessBuilder builder = new ProcessBuilder(command);
        if (workDir != null) {
            builder.directory(workDir.toFile());
        }
        builder.redirectErrorStream(true);
        try {
            Process process = builder.start();
            boolean finished = process.waitFor(TIMEOUT_SECONDS, TimeUnit.SECONDS);
            String output = StrUtil.subPre(new String(process.getInputStream().readAllBytes(), StandardCharsets.UTF_8),
                    OUTPUT_LIMIT);
            if (!finished) {
                process.destroyForcibly();
                throw new GitCommandException("Git command timeout", output);
            }
            GitCommandResult result = new GitCommandResult(process.exitValue(), output);
            if (checkExitCode && !result.isSuccess()) {
                throw new GitCommandException("Git command failed", output);
            }
            return result;
        } catch (IOException ex) {
            throw new GitCommandException("Git command cannot start: " + ex.getMessage(), null);
        } catch (InterruptedException ex) {
            Thread.currentThread().interrupt();
            throw new GitCommandException("Git command interrupted", null);
        }
    }

}
