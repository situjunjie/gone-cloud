package cn.iocoder.yudao.module.devops.framework.pipeline.runtime;

import cn.iocoder.yudao.module.devops.framework.build.ExecResult;
import cn.iocoder.yudao.module.devops.framework.build.LogSink;
import cn.iocoder.yudao.module.devops.framework.host.HostSshClient;
import com.jcraft.jsch.ChannelExec;
import com.jcraft.jsch.Session;
import jakarta.annotation.Resource;
import org.springframework.stereotype.Component;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.OutputStream;
import java.nio.charset.StandardCharsets;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.TimeUnit;

/** Linux SSH Command execution with a remote process-group/heartbeat lease supervisor. */
@Component
public class SshPipelineCommandExecutor {

    @Resource
    private HostSshClient hostSshClient;

    private final Map<String, RunningCommand> running = new ConcurrentHashMap<>();

    public ExecResult exec(PipelineCommandContext ctx, String script, LogSink sink) {
        RunningCommand command = new RunningCommand();
        if (ctx.getRunId() == null || running.putIfAbsent(ctx.getRunId(), command) != null) {
            return ExecResult.failure(-1, "SSH command is already running or runId is missing");
        }
        Session session = null;
        ChannelExec channel = null;
        LineOutput stdout = new LineOutput("stdout", sink);
        LineOutput stderr = new LineOutput("stderr", sink);
        long timeout = ctx.getTimeoutSeconds() > 0 ? ctx.getTimeoutSeconds() : 1800;
        long deadline = System.nanoTime() + TimeUnit.SECONDS.toNanos(timeout);
        try {
            // Validate before opening a connection. Never include script/env/credentials in diagnostics.
            String remoteCommand = buildCommand(ctx, script, timeout);
            if (isCanceled(command, ctx)) {
                return ExecResult.failure(130, "SSH command canceled");
            }
            session = hostSshClient.openSession(ctx.getSshHost(), (int) Math.max(1, Math.min(10_000,
                    TimeUnit.NANOSECONDS.toMillis(deadline - System.nanoTime()))));
            if (isCanceled(command, ctx)) {
                return ExecResult.failure(130, "SSH command canceled");
            }
            if (System.nanoTime() >= deadline) {
                return ExecResult.failure(124, "SSH command timeout after " + timeout + "s");
            }
            channel = (ChannelExec) session.openChannel("exec");
            channel.setCommand(remoteCommand);
            channel.setOutputStream(stdout, true);
            channel.setErrStream(stderr, true);
            OutputStream heartbeat = channel.getOutputStream();
            command.attach(heartbeat);
            if (isCanceled(command, ctx)) {
                return ExecResult.failure(130, "SSH command canceled");
            }
            channel.connect((int) Math.max(1, Math.min(10_000,
                    TimeUnit.NANOSECONDS.toMillis(deadline - System.nanoTime()))));
            long nextHeartbeat = 0;
            while (!channel.isClosed()) {
                if (isCanceled(command, ctx)) {
                    return ExecResult.failure(130, "SSH command canceled");
                }
                if (System.nanoTime() >= deadline) {
                    return ExecResult.failure(124, "SSH command timeout after " + timeout + "s");
                }
                if (System.nanoTime() >= nextHeartbeat) {
                    heartbeat.write('\n');
                    heartbeat.flush();
                    nextHeartbeat = System.nanoTime() + TimeUnit.SECONDS.toNanos(1);
                }
                Thread.sleep(50);
            }
            if (isCanceled(command, ctx)) {
                return ExecResult.failure(130, "SSH command canceled");
            }
            int code = channel.getExitStatus();
            return code == 0 ? ExecResult.success() : ExecResult.failure(code,
                    "SSH command failed with exit code " + code);
        } catch (InterruptedException ex) {
            Thread.currentThread().interrupt();
            return ExecResult.failure(130, "SSH command interrupted");
        } catch (Exception ex) {
            if (command.canceled || Thread.currentThread().isInterrupted()) {
                return ExecResult.failure(130, "SSH command canceled or interrupted");
            }
            if (System.nanoTime() >= deadline) {
                return ExecResult.failure(124, "SSH command timeout after " + timeout + "s");
            }
            return ExecResult.failure(-1, "SSH command connection or execution failed");
        } finally {
            // EOF immediately revokes the remote lease; missing EOF is bounded by read -t 10.
            command.cancel();
            if (channel != null) {
                channel.disconnect();
            }
            if (session != null) {
                session.disconnect();
            }
            running.remove(ctx.getRunId(), command);
            stdout.finish();
            stderr.finish();
        }
    }

    private boolean isCanceled(RunningCommand command, PipelineCommandContext ctx) {
        if (command.canceled || ctx.isCancellationRequested()) {
            command.cancel();
            return true;
        }
        return false;
    }

    public void cancel(String runId) {
        RunningCommand command = running.get(runId);
        if (command != null) {
            command.cancel();
        }
    }

    static String buildCommand(PipelineCommandContext ctx, String script, long timeout) {
        if (ctx.getSshHost() == null || ctx.getWorkingDirectory() == null
                || !ctx.getWorkingDirectory().startsWith("/") || ctx.getWorkspaceProbe() == null
                || !ctx.getWorkspaceProbe().matches("[a-zA-Z0-9.-]+") || ctx.getWorkspaceToken() == null) {
            throw new IllegalArgumentException("Validated SSH target and workspace probe required");
        }
        StringBuilder payload = new StringBuilder();
        if (ctx.getEnv() != null) {
            ctx.getEnv().forEach((key, value) -> {
                if (key == null || !key.matches("[A-Za-z_][A-Za-z0-9_]*")) {
                    throw new IllegalArgumentException("Invalid environment variable name");
                }
                payload.append("export ").append(key).append('=').append(quote(value)).append('\n');
            });
        }
        payload.append("cd -- ").append(quote(ctx.getWorkingDirectory())).append(" || exit 125\n");
        payload.append("test \"$(cat -- ").append(quote(ctx.getWorkspaceProbe())).append(")\" = ")
                .append(quote(ctx.getWorkspaceToken())).append(" || { echo 'SSH workspace is not the pipeline source workspace' >&2; exit 125; }\n");
        payload.append(script);
        // No PTY: stdout/stderr stay separate. The user command cannot consume the lease stdin.
        // setsid owns the entire foreground process group. Deliberately daemonized commands are unsupported.
        String supervisor = """
                command -v setsid >/dev/null || { echo 'SSH Command requires setsid' >&2; exit 125; }
                exec 3<&0
                IFS= read -r -t 10 <&3 || exit 125
                child= watcher=
                cleanup() {
                  trap '' HUP INT TERM USR1
                  [ -z "$watcher" ] || kill "$watcher" 2>/dev/null || :
                  if [ -n "$child" ]; then
                    kill -TERM -- -"$child" 2>/dev/null || :
                    sleep 1
                    kill -KILL -- -"$child" 2>/dev/null || :
                  fi
                }
                trap cleanup EXIT
                trap 'exit 130' HUP INT TERM
                trap 'exit 124' USR1
                supervisor=$$
                setsid bash -lc %s </dev/null 3<&- &
                child=$!
                (end=$((SECONDS + %d))
                 while [ "$SECONDS" -lt "$end" ]; do
                   remaining=$((end - SECONDS))
                   [ "$remaining" -le 10 ] || remaining=10
                   IFS= read -r -t "$remaining" <&3 || break
                 done
                 if [ "$SECONDS" -ge "$end" ]; then
                   kill -USR1 "$supervisor" 2>/dev/null || :
                 else
                   kill -TERM "$supervisor" 2>/dev/null || :
                 fi) &
                watcher=$!
                wait "$child"
                result=$?
                exit "$result"
                """.formatted(quote(payload.toString()), timeout);
        return "bash -c " + quote(supervisor);
    }

    static String quote(String value) {
        if (value == null || value.indexOf('\0') >= 0) {
            throw new IllegalArgumentException("Invalid shell value");
        }
        return "'" + value.replace("'", "'\"'\"'") + "'";
    }

    private static final class RunningCommand {
        private volatile boolean canceled;
        private OutputStream heartbeat;

        synchronized void attach(OutputStream stream) throws IOException {
            heartbeat = stream;
            if (canceled) {
                stream.close();
            }
        }

        synchronized void cancel() {
            canceled = true;
            if (heartbeat != null) {
                try {
                    heartbeat.close();
                } catch (IOException ignored) {
                    // Remote read timeout still revokes the lease if EOF cannot be delivered.
                }
            }
        }
    }

    /** Bound log fragments while preserving UTF-8 characters split across SSH packets. */
    private static final class LineOutput extends OutputStream {
        private final ByteArrayOutputStream buffer = new ByteArrayOutputStream();
        private final StringBuilder decoded = new StringBuilder();
        private final String stream;
        private final LogSink sink;
        private final java.nio.charset.CharsetDecoder decoder = StandardCharsets.UTF_8.newDecoder()
                .onMalformedInput(java.nio.charset.CodingErrorAction.REPLACE)
                .onUnmappableCharacter(java.nio.charset.CodingErrorAction.REPLACE);
        private byte[] pending = new byte[0];

        private LineOutput(String stream, LogSink sink) {
            this.stream = stream;
            this.sink = sink;
        }

        @Override
        public synchronized void write(int value) throws IOException {
            if (value == '\n') {
                flush();
            } else {
                buffer.write(value);
                if (buffer.size() >= 8192) {
                    flush();
                }
            }
        }

        @Override
        public synchronized void flush() throws IOException {
            decode(false);
            emit();
        }

        synchronized void finish() {
            try {
                decode(true);
                emit();
            } catch (IOException ex) {
                // Replacement decoding should not fail; do not mask command cleanup if it does.
                emit();
            }
        }

        private void decode(boolean endOfInput) throws IOException {
            byte[] current = buffer.toByteArray();
            buffer.reset();
            java.nio.ByteBuffer input = java.nio.ByteBuffer.allocate(pending.length + current.length);
            input.put(pending).put(current).flip();
            java.nio.CharBuffer output = java.nio.CharBuffer.allocate(Math.max(1, input.remaining()));
            java.nio.charset.CoderResult result = decoder.decode(input, output, endOfInput);
            if (result.isError()) {
                result.throwException();
            }
            if (endOfInput) {
                result = decoder.flush(output);
                if (result.isError()) {
                    result.throwException();
                }
                pending = new byte[0];
            } else {
                pending = new byte[input.remaining()];
                input.get(pending);
            }
            output.flip();
            decoded.append(output);
        }

        private void emit() {
            if (decoded.length() > 0) {
                String line = decoded.toString();
                decoded.setLength(0);
                if (sink != null) {
                    sink.accept(stream, line.endsWith("\r") ? line.substring(0, line.length() - 1) : line);
                }
            }
        }
    }
}
