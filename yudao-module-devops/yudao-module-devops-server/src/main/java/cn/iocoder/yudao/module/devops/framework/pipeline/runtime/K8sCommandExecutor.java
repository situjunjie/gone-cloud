package cn.iocoder.yudao.module.devops.framework.pipeline.runtime;

import cn.hutool.core.util.StrUtil;
import cn.iocoder.yudao.module.devops.framework.build.ExecResult;
import cn.iocoder.yudao.module.devops.framework.build.LogSink;
import cn.iocoder.yudao.module.devops.framework.kubernetes.KubernetesClientFactory;
import io.fabric8.kubernetes.client.KubernetesClient;
import io.fabric8.kubernetes.client.RequestConfigBuilder;
import io.fabric8.kubernetes.client.dsl.ExecWatch;
import jakarta.annotation.Resource;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.io.ByteArrayInputStream;
import java.io.OutputStream;
import java.nio.charset.StandardCharsets;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;

/** Executes build scripts through Kubernetes exec. */
@Slf4j
@Component
public class K8sCommandExecutor implements PipelineCommandExecutor {
    private static final long DEFAULT_TIMEOUT_SECONDS = 1800;
    private final Map<String, Set<ActiveCommand>> running = new ConcurrentHashMap<>();
    @Resource
    private KubernetesClientFactory kubernetesClientFactory;

    @Override
    public ExecResult exec(PipelineCommandContext ctx, String script, LogSink sink) {
        PipelineJobRuntime runtime = ctx.getRuntime();
        if (runtime == null || !"KUBERNETES".equals(runtime.getRuntimeType())) {
            throw new IllegalArgumentException("K8sCommandExecutor requires KUBERNETES runtime");
        }
        Map<String, Object> metadata = runtime.getMetadata();
        String kubeconfig = metadata == null ? null : (String) metadata.get("kubeconfig");
        String namespace = metadata == null ? null : (String) metadata.get("namespace");
        if (StrUtil.isBlank(kubeconfig) || StrUtil.isBlank(namespace) || StrUtil.isBlank(runtime.getRuntimeId())) {
            return ExecResult.failure(-1, "Kubernetes runtime connection metadata is incomplete");
        }
        long timeout = ctx.getTimeoutSeconds() > 0 ? ctx.getTimeoutSeconds() : DEFAULT_TIMEOUT_SECONDS;
        long started = System.nanoTime();
        String container = StrUtil.blankToDefault(ctx.getContainerName(), "builder");
        String shell = "kaniko".equals(container) ? "/busybox/sh" : "sh";
        try (KubernetesClient original = kubernetesClientFactory.create(kubeconfig)) {
            KubernetesClient client = original.newClient(new RequestConfigBuilder(original.getConfiguration().getRequestConfig())
                    .withRequestTimeout((int) Math.min(10_000, TimeUnit.SECONDS.toMillis(timeout)))
                    .withRequestRetryBackoffLimit(0).build()).adapt(KubernetesClient.class);
            ActiveCommand active = new ActiveCommand(client, namespace, runtime.getRuntimeId());
            register(ctx.getRunId(), active);
            try (LineOutputStream stdout = new LineOutputStream(sink, "stdout");
                 LineOutputStream stderr = new LineOutputStream(sink, "stderr")) {
                byte[] payload = buildScript(ctx, script).getBytes(StandardCharsets.UTF_8);
                if (Thread.currentThread().isInterrupted() || active.isCanceled()) {
                    throw new InterruptedException();
                }
                String path = "/tmp/pipeline-" + UUID.randomUUID() + ".sh";
                // 部分精简镜像（如 kaniko debug）不带 /tmp，脚本写入前先确保目录存在。
                //
                // umask 必须收窄在子 shell 里，只作用于临时脚本自身的创建：脚本内容含注入的凭证，
                // 需要 0600。若把 umask 077 放在整条命令最前面，它会随 shell 继承给步骤的所有子进程，
                // 使工作区里的文件变成 0600、目录变成 0700；这些权限会被 docker build 原样复制进镜像，
                // 导致 nginx 等非 root 用户读不到静态文件而返回 403。
                String wrapper = "mkdir -p /tmp 2>/dev/null || true; f=" + ShellQuoting.quote(path)
                        + "; trap 'rm -f \"$f\"' EXIT; (umask 077; head -c "
                        + payload.length + " > \"$f\") && " + ShellQuoting.quote(shell) + " \"$f\"";
                ExecWatch watch = client.pods().inNamespace(namespace).withName(runtime.getRuntimeId())
                        .inContainer(container).readingInput(new ByteArrayInputStream(payload))
                        .writingOutput(stdout).writingError(stderr).exec(shell, "-c", wrapper);
                active.attach(watch);
                long remaining = TimeUnit.SECONDS.toNanos(timeout) - (System.nanoTime() - started);
                if (remaining <= 0) {
                    throw new TimeoutException();
                }
                Integer code = active.result.get(remaining, TimeUnit.NANOSECONDS);
                if (active.isCanceled()) {
                    return ExecResult.failure(-1, "Command canceled");
                }
                return code != null && code == 0 ? ExecResult.success()
                        : ExecResult.failure(code == null ? -1 : code, "Script failed with exit code " + code);
            } catch (TimeoutException ex) {
                active.stop();
                return ExecResult.failure(-1, "Command timeout after " + timeout + "s");
            } catch (InterruptedException ex) {
                Thread.interrupted();
                active.stop();
                Thread.currentThread().interrupt();
                return ExecResult.failure(-1, "Command interrupted");
            } catch (Exception ex) {
                active.stop();
                log.warn("[exec][runId({}) Kubernetes command failed, type({})]", ctx.getRunId(), ex.getClass().getSimpleName());
                return ExecResult.failure(-1, "Kubernetes command execution failed");
            } finally {
                active.closeWatch();
                unregister(ctx.getRunId(), active);
            }
        } catch (Exception ex) {
            log.warn("[exec][runId({}) Kubernetes command failed, type({})]", ctx.getRunId(), ex.getClass().getSimpleName());
            return ExecResult.failure(-1, "Kubernetes command execution failed");
        }
    }

    @Override
    public void cancel(String runId) {
        Set<ActiveCommand> commands = runId == null ? null : running.get(runId);
        if (commands != null) {
            commands.forEach(ActiveCommand::stop);
        }
    }

    private void register(String runId, ActiveCommand command) {
        if (runId != null) {
            running.compute(runId, (ignored, commands) -> {
                Set<ActiveCommand> result = commands == null ? ConcurrentHashMap.newKeySet() : commands;
                result.add(command);
                return result;
            });
        }
    }

    private void unregister(String runId, ActiveCommand command) {
        if (runId != null) {
            running.computeIfPresent(runId, (ignored, commands) -> {
                commands.remove(command);
                return commands.isEmpty() ? null : commands;
            });
        }
    }

    static String buildScript(PipelineCommandContext ctx, String script) {
        if (script == null || script.indexOf('\0') >= 0) {
            throw new IllegalArgumentException("Script must be non-null and contain no NUL");
        }
        StringBuilder result = new StringBuilder("set -e\n");
        if (ctx.getEnv() != null) {
            ctx.getEnv().forEach((key, value) -> result.append("export ")
                    .append(ShellQuoting.requireEnvironmentName(key)).append('=').append(ShellQuoting.quote(value)).append('\n'));
        }
        result.append("cd ").append(ShellQuoting.quote(StrUtil.blankToDefault(ctx.getWorkingDirectory(), "/workspace")))
                .append('\n').append(script).append('\n');
        return result.toString();
    }

    private static final class ActiveCommand {
        private final KubernetesClient client;
        private final String namespace;
        private final String podName;
        private final CompletableFuture<Integer> result = new CompletableFuture<>();
        private ExecWatch watch;
        private boolean canceled;
        private ActiveCommand(KubernetesClient client, String namespace, String podName) {
            this.client = client;
            this.namespace = namespace;
            this.podName = podName;
        }
        synchronized void attach(ExecWatch watch) {
            this.watch = watch;
            if (canceled) {
                closeWatch();
                return;
            }
            watch.exitCode().whenComplete((code, error) -> {
                if (error == null) result.complete(code);
                else result.completeExceptionally(error);
            });
        }
        synchronized boolean isCanceled() {
            return canceled;
        }
        synchronized void stop() {
            if (canceled) return;
            canceled = true;
            result.completeExceptionally(new InterruptedException("command canceled"));
            try {
                client.pods().inNamespace(namespace).withName(podName).withGracePeriod(0).delete();
            } catch (Exception ex) {
                log.warn("[cancel][pod({}) deletion failed, type({})]", podName, ex.getClass().getSimpleName());
            } finally {
                closeWatch();
            }
        }
        synchronized void closeWatch() {
            if (watch != null) {
                watch.close();
                watch = null;
            }
        }
    }

    static final class LineOutputStream extends OutputStream {
        private static final int MAX_LINE_BYTES = 16 * 1024;
        private final byte[] buffer = new byte[MAX_LINE_BYTES];
        private final LogSink sink;
        private final String stream;
        private int length;
        private boolean truncated;
        LineOutputStream(LogSink sink, String stream) { this.sink = sink; this.stream = stream; }
        @Override public synchronized void write(int value) {
            if (sink == null) return;
            if ((value & 0xff) == '\n') { emit(); return; }
            if (length < buffer.length) buffer[length++] = (byte) value; else truncated = true;
        }
        @Override public synchronized void write(byte[] bytes, int offset, int size) {
            for (int i = offset; i < offset + size; i++) write(bytes[i]);
        }
        private void emit() {
            int size = length > 0 && buffer[length - 1] == '\r' ? length - 1 : length;
            String value = new String(buffer, 0, size, StandardCharsets.UTF_8);
            sink.accept(stream, value + (truncated ? " [line truncated]" : ""));
            length = 0; truncated = false;
        }
        @Override public synchronized void close() { if (length > 0) emit(); }
    }
}
