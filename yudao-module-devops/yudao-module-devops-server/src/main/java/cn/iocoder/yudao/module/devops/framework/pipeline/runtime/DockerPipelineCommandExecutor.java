package cn.iocoder.yudao.module.devops.framework.pipeline.runtime;

import cn.hutool.core.util.StrUtil;
import cn.iocoder.yudao.module.devops.framework.build.ExecResult;
import cn.iocoder.yudao.module.devops.framework.build.LogSink;
import cn.iocoder.yudao.module.devops.framework.docker.DockerClientFactory;
import com.github.dockerjava.api.DockerClient;
import com.github.dockerjava.api.async.ResultCallback;
import com.github.dockerjava.api.command.ExecCreateCmdResponse;
import com.github.dockerjava.api.model.Frame;
import jakarta.annotation.Resource;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;

/**
 * Docker 容器内命令执行器。
 */
@Slf4j
@Component
public class DockerPipelineCommandExecutor implements PipelineCommandExecutor {

    private static final long DEFAULT_TIMEOUT_SECONDS = 1800;

    private final Map<String, String> runningExecIds = new ConcurrentHashMap<>();
    /**
     * 正在执行命令的容器,按 runId 记录,用于取消时主动停止容器以终止运行中的命令。
     */
    private final Map<String, String> runningContainerIds = new ConcurrentHashMap<>();

    @Resource
    private DockerClientFactory dockerClientFactory;

    @Override
    public ExecResult exec(PipelineCommandContext ctx, String script, LogSink sink) {
        PipelineJobRuntime runtime = ctx.getRuntime();
        if (runtime == null || StrUtil.isBlank(runtime.getRuntimeId())) {
            return ExecResult.failure(-1, "Job runtime is not ready");
        }
        DockerClient client = dockerClientFactory.getDefaultClient();
        ExecCreateCmdResponse exec = client.execCreateCmd(runtime.getRuntimeId())
                .withAttachStdout(true)
                .withAttachStderr(true)
                .withWorkingDir("/workspace")
                .withEnv(buildEnv(ctx.getEnv()))
                .withCmd("sh", "-lc", script)
                .exec();
        if (ctx.getRunId() != null) {
            runningExecIds.put(ctx.getRunId(), exec.getId());
            runningContainerIds.put(ctx.getRunId(), runtime.getRuntimeId());
        }
        CountDownLatch done = new CountDownLatch(1);
        try {
            client.execStartCmd(exec.getId())
                    .exec(new ResultCallback.Adapter<Frame>() {
                        @Override
                        public void onNext(Frame frame) {
                            if (sink == null || frame == null || frame.getPayload() == null) {
                                return;
                            }
                            String output = new String(frame.getPayload(), StandardCharsets.UTF_8);
                            String streamType = frame.getStreamType() == null
                                    ? "stdout" : frame.getStreamType().name().toLowerCase(Locale.ROOT);
                            for (String line : output.split("\\R")) {
                                if (StrUtil.isNotBlank(line)) {
                                    sink.accept(streamType, line);
                                }
                            }
                        }

                        @Override
                        public void onComplete() {
                            done.countDown();
                            super.onComplete();
                        }

                        @Override
                        public void onError(Throwable throwable) {
                            done.countDown();
                            super.onError(throwable);
                        }
                    });
            long timeoutSeconds = ctx.getTimeoutSeconds() > 0 ? ctx.getTimeoutSeconds() : DEFAULT_TIMEOUT_SECONDS;
            if (!done.await(timeoutSeconds, TimeUnit.SECONDS)) {
                return ExecResult.failure(-1, "Command timeout after " + timeoutSeconds + "s");
            }
            Long exitCode = client.inspectExecCmd(exec.getId()).exec().getExitCodeLong();
            if (exitCode != null && exitCode == 0) {
                return ExecResult.success();
            }
            int code = exitCode == null ? -1 : exitCode.intValue();
            return ExecResult.failure(code, "Command failed with exit code " + code);
        } catch (InterruptedException ex) {
            Thread.currentThread().interrupt();
            return ExecResult.failure(-1, "Command interrupted");
        } catch (Exception ex) {
            log.warn("[exec][runId({}) command failed: {}]", ctx.getRunId(), ex.getMessage());
            return ExecResult.failure(-1, "Command cannot execute: " + ex.getMessage());
        } finally {
            if (ctx.getRunId() != null) {
                runningExecIds.remove(ctx.getRunId());
                runningContainerIds.remove(ctx.getRunId());
            }
        }
    }

    @Override
    public void cancel(String runId) {
        String containerId = runningContainerIds.remove(runId);
        String execId = runningExecIds.get(runId);
        if (StrUtil.isBlank(containerId)) {
            return;
        }
        // 主动停止容器,终止构建机上正在运行的命令,使 exec 流结束并解除 await 阻塞。
        try {
            dockerClientFactory.getDefaultClient().stopContainerCmd(containerId).withTimeout(5).exec();
            log.info("[cancel][runId({}) containerId({}) execId({}) 已停止容器以终止运行中的命令]",
                    runId, containerId, execId);
        } catch (Exception ex) {
            log.warn("[cancel][runId({}) containerId({}) 停止容器异常: {}]", runId, containerId, ex.getMessage());
        }
    }

    private List<String> buildEnv(Map<String, String> env) {
        List<String> result = new ArrayList<>();
        if (env == null) {
            return result;
        }
        for (Map.Entry<String, String> entry : env.entrySet()) {
            result.add(entry.getKey() + "=" + entry.getValue());
        }
        return result;
    }

}
