package cn.iocoder.yudao.module.devops.framework.build;

import lombok.extern.slf4j.Slf4j;
import org.apache.sshd.client.SshClient;
import org.apache.sshd.client.channel.ChannelExec;
import org.apache.sshd.client.channel.ClientChannelEvent;
import org.apache.sshd.client.session.ClientSession;
import org.apache.sshd.common.NamedResource;
import org.apache.sshd.common.config.keys.FilePasswordProvider;
import org.apache.sshd.common.util.security.SecurityUtils;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;

import jakarta.annotation.PostConstruct;
import jakarta.annotation.PreDestroy;
import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.OutputStream;
import java.nio.charset.StandardCharsets;
import java.security.KeyPair;
import java.time.Duration;
import java.util.Collection;
import java.util.EnumSet;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * SSH 远程构建执行器(主路径,基于 Apache MINA SSHD)。
 *
 * <p>把 {@code StepScriptGenerator} 生成的 shell 脚本 SSH 到专用构建机执行:同一 {@code runId} 的多个
 * 节点脚本通过共用 {@link ExecContext#getWorkingDir()} 远程目录顺序执行,下游节点可见上游产物。
 *
 * <p>与 {@code LocalBuildExecutor} 并存(均为 {@link BuildExecutor} 的 {@code @Component}),
 * 不加 {@code @Primary};由后续 {@code BuildHostSelector} / 工厂按主机类型选择。
 *
 * <p>连接目标与登录凭据来自 {@link ExecContext#getSshTarget()}(由驱动器从选中的构建主机映射而来)。
 * 认证支持密码与私钥(口令可选)。凭据为敏感值,日志与异常绝不回显其内容。
 *
 * <p>{@link #cancel(String)} 通过 {@link #runningExecs} 维护 {@code runId} 到运行中会话/通道的映射,
 * 取消时关闭通道与会话以终止远程执行。
 */
@Slf4j
@Component
public class SshBuildExecutor implements BuildExecutor {

    private static final long DEFAULT_TIMEOUT_SECONDS = 1800;
    private static final int DEFAULT_SSH_PORT = 22;
    private static final Duration CONNECT_TIMEOUT = Duration.ofSeconds(30);
    private static final Duration AUTH_TIMEOUT = Duration.ofSeconds(30);

    /**
     * runId 到运行中执行(会话 + 通道)的映射,用于 {@link #cancel(String)}。
     */
    private final Map<String, RunningExec> runningExecs = new ConcurrentHashMap<>();

    /**
     * 共享 SSH 客户端。启动一次复用,避免每次执行的启动开销。
     */
    private SshClient sshClient;

    @PostConstruct
    public void init() {
        sshClient = SshClient.setUpDefaultClient();
        sshClient.start();
    }

    @PreDestroy
    public void destroy() {
        if (sshClient != null) {
            sshClient.stop();
        }
    }

    @Override
    public ExecResult exec(ExecContext ctx, String script, LogSink sink) {
        SshTarget target = ctx.getSshTarget();
        if (target == null || !StringUtils.hasText(target.getHost())) {
            return ExecResult.failure(-1, "SSH build target is not configured");
        }
        String runId = ctx.getRunId();
        long timeoutSeconds = ctx.getTimeoutSeconds() > 0 ? ctx.getTimeoutSeconds() : DEFAULT_TIMEOUT_SECONDS;
        int port = target.getPort() != null && target.getPort() > 0 ? target.getPort() : DEFAULT_SSH_PORT;

        ClientSession session = null;
        ChannelExec channel = null;
        try {
            // 建立会话与认证
            session = sshClient.connect(target.getUsername(), target.getHost(), port)
                    .verify(CONNECT_TIMEOUT)
                    .getSession();
            applyAuthentication(session, target);
            if (!session.auth().verify(AUTH_TIMEOUT).isSuccess()) {
                return ExecResult.failure(-1, "SSH authentication failed for " + sanitizeTarget(target));
            }

            // 开 exec 通道执行 cd <workingDir> && <script>(bash -lc 包裹)
            channel = session.createExecChannel(buildRemoteCommand(ctx, script));
            // 构建脚本无需标准输入,提供空 stdin 让远端命令结束后通道可正常关闭
            channel.setIn(new ByteArrayInputStream(new byte[0]));
            StreamingLineConsumer stdout = new StreamingLineConsumer(sink);
            StreamingLineConsumer stderr = new StreamingLineConsumer(sink);
            channel.setOut(stdout);
            channel.setErr(stderr);

            channel.open().verify(CONNECT_TIMEOUT);

            // 登记运行中执行,供 cancel 关闭
            if (runId != null) {
                runningExecs.put(runId, new RunningExec(session, channel));
            }

            // 同步阻塞至命令结束(收到退出码或通道关闭)或超时
            Collection<ClientChannelEvent> events = channel.waitFor(
                    EnumSet.of(ClientChannelEvent.CLOSED, ClientChannelEvent.EXIT_STATUS),
                    Duration.ofSeconds(timeoutSeconds));
            stdout.flushRemaining();
            stderr.flushRemaining();
            Integer exitStatus = channel.getExitStatus();
            if (events.contains(ClientChannelEvent.TIMEOUT)
                    || (!channel.isClosed() && exitStatus == null)) {
                return ExecResult.failure(-1, "SSH build script timeout after " + timeoutSeconds + "s");
            }
            if (exitStatus == null) {
                // 通道关闭但无退出码,通常是被取消或连接中断
                return ExecResult.failure(-1, "SSH build script terminated without exit status");
            }
            if (exitStatus == 0) {
                return ExecResult.success();
            }
            return ExecResult.failure(exitStatus, "SSH build script failed with exit code " + exitStatus);
        } catch (IOException ex) {
            // 脱敏:仅暴露目标主机/端口/用户,绝不回显凭据与异常细节中的敏感片段
            return ExecResult.failure(-1, "SSH build execution failed on " + sanitizeTarget(target)
                    + ": " + ex.getMessage());
        } catch (GeneralSecurityRuntimeException ex) {
            return ExecResult.failure(-1, "SSH credential is invalid for " + sanitizeTarget(target));
        } finally {
            if (runId != null) {
                runningExecs.remove(runId);
            }
            closeQuietly(channel);
            closeQuietly(session);
        }
    }

    @Override
    public void cancel(String runId) {
        if (runId == null) {
            return;
        }
        RunningExec running = runningExecs.get(runId);
        if (running == null) {
            return;
        }
        log.info("[cancel][runId({}) 关闭 SSH 通道与会话终止远程构建]", runId);
        closeQuietly(running.channel());
        closeQuietly(running.session());
    }

    /**
     * 应用认证方式:存在私钥走密钥认证,否则走密码认证。凭据值不进日志。
     */
    private void applyAuthentication(ClientSession session, SshTarget target) {
        if (StringUtils.hasText(target.getPrivateKey())) {
            KeyPair keyPair = loadKeyPair(target);
            session.addPublicKeyIdentity(keyPair);
            return;
        }
        if (StringUtils.hasText(target.getPassword())) {
            session.addPasswordIdentity(target.getPassword());
            return;
        }
        // 既无私钥也无密码:让后续 auth 失败并返回脱敏信息
    }

    /**
     * 解析私钥文本为 {@link KeyPair}。私钥/口令为敏感值,异常信息不得回显其内容。
     */
    private KeyPair loadKeyPair(SshTarget target) {
        try {
            FilePasswordProvider passwordProvider = StringUtils.hasText(target.getPassphrase())
                    ? FilePasswordProvider.of(target.getPassphrase())
                    : FilePasswordProvider.EMPTY;
            Iterable<KeyPair> keyPairs = SecurityUtils.loadKeyPairIdentities(
                    null,
                    NamedResource.ofName("ssh-build-key"),
                    new ByteArrayInputStream(target.getPrivateKey().getBytes(StandardCharsets.UTF_8)),
                    passwordProvider);
            if (keyPairs == null || !keyPairs.iterator().hasNext()) {
                throw new GeneralSecurityRuntimeException();
            }
            return keyPairs.iterator().next();
        } catch (GeneralSecurityRuntimeException ex) {
            throw ex;
        } catch (Exception ex) {
            // 私钥解析失败,脱敏抛出,不携带凭据内容
            throw new GeneralSecurityRuntimeException();
        }
    }

    /**
     * 拼装远程命令:注入 env,cd 进工作目录,再执行脚本。整体由 {@code bash -lc} 包裹。
     *
     * <p>env 值通过 shell 单引号转义注入,绝不出现在返回信息或日志中。
     */
    private String buildRemoteCommand(ExecContext ctx, String script) {
        StringBuilder sb = new StringBuilder();
        sb.append("set -e; ");
        Map<String, String> env = ctx.getEnv();
        if (env != null) {
            for (Map.Entry<String, String> entry : env.entrySet()) {
                sb.append("export ").append(entry.getKey()).append('=')
                        .append(shellSingleQuote(entry.getValue())).append("; ");
            }
        }
        if (ctx.getWorkingDir() != null) {
            sb.append("cd ").append(shellSingleQuote(ctx.getWorkingDir().toString())).append(" && ");
        }
        sb.append(script);
        // bash -lc 执行整体脚本,保证远程也是 bash 语义
        return "bash -lc " + shellSingleQuote(sb.toString());
    }

    /**
     * shell 单引号转义,防止注入。
     */
    private String shellSingleQuote(String value) {
        if (value == null) {
            return "''";
        }
        return "'" + value.replace("'", "'\\''") + "'";
    }

    /**
     * 脱敏的目标描述:仅暴露 user@host:port,绝不含凭据。
     */
    private String sanitizeTarget(SshTarget target) {
        return target.getUsername() + "@" + target.getHost()
                + ":" + (target.getPort() != null ? target.getPort() : DEFAULT_SSH_PORT);
    }

    private void closeQuietly(AutoCloseable closeable) {
        if (closeable == null) {
            return;
        }
        try {
            closeable.close();
        } catch (Exception ex) {
            log.debug("[closeQuietly][关闭 SSH 资源异常: {}]", ex.getMessage());
        }
    }

    /**
     * 运行中执行的会话与通道引用。
     */
    private record RunningExec(ClientSession session, ChannelExec channel) {
    }

    /**
     * 私钥/凭据无效的内部脱敏异常,不携带任何凭据内容。
     */
    private static class GeneralSecurityRuntimeException extends RuntimeException {
    }

    /**
     * 把字节流逐行回调 {@link LogSink} 的输出流。
     *
     * <p>SSHD 以 {@link OutputStream} 形式回填远程 stdout/stderr,这里按行边界切分,
     * 逐行即时回调(不攒完再回),并在执行结束后 {@link #flush()} 残余不带换行的尾行。
     */
    private static final class StreamingLineConsumer extends OutputStream {

        private final LogSink sink;
        private final StringBuilder buffer = new StringBuilder();

        private StreamingLineConsumer(LogSink sink) {
            this.sink = sink;
        }

        @Override
        public synchronized void write(int b) {
            if (b == '\n') {
                emit();
            } else if (b != '\r') {
                buffer.append((char) b);
            }
        }

        @Override
        public synchronized void write(byte[] b, int off, int len) {
            String text = new String(b, off, len, StandardCharsets.UTF_8);
            for (int i = 0; i < text.length(); i++) {
                char c = text.charAt(i);
                if (c == '\n') {
                    emit();
                } else if (c != '\r') {
                    buffer.append(c);
                }
            }
        }

        synchronized void flushRemaining() {
            if (buffer.length() > 0) {
                emit();
            }
        }

        private void emit() {
            if (sink != null) {
                sink.accept(buffer.toString());
            }
            buffer.setLength(0);
        }

    }

}
