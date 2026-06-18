package cn.iocoder.yudao.module.devops.service.docker.log;

import cn.hutool.core.util.StrUtil;
import cn.iocoder.yudao.framework.common.util.json.JsonUtils;
import cn.iocoder.yudao.module.devops.controller.admin.environment.vo.EnvironmentDockerContainerLogLineRespVO;
import cn.iocoder.yudao.module.devops.dal.dataobject.environment.EnvironmentDO;
import cn.iocoder.yudao.module.devops.dal.mysql.environment.EnvironmentMapper;
import cn.iocoder.yudao.module.devops.enums.EnvironmentInfraTypeEnum;
import cn.iocoder.yudao.module.devops.framework.docker.DockerClientFactory;
import cn.iocoder.yudao.module.devops.framework.docker.DockerEnvironmentConfig;
import com.github.dockerjava.api.DockerClient;
import com.github.dockerjava.api.async.ResultCallback;
import com.github.dockerjava.api.command.InspectContainerResponse;
import com.github.dockerjava.api.exception.DockerException;
import com.github.dockerjava.api.exception.NotFoundException;
import com.github.dockerjava.api.model.Frame;
import jakarta.annotation.Resource;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.stereotype.Service;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

import java.io.Closeable;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.concurrent.Executor;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicLong;

import static cn.iocoder.yudao.framework.common.exception.util.ServiceExceptionUtil.exception;
import static cn.iocoder.yudao.module.devops.enums.ErrorCodeConstants.DOCKER_CONTAINER_LOG_STREAM_FAIL;
import static cn.iocoder.yudao.module.devops.enums.ErrorCodeConstants.DOCKER_CONTAINER_NOT_EXISTS;
import static cn.iocoder.yudao.module.devops.enums.ErrorCodeConstants.ENVIRONMENT_DOCKER_CONNECTION_FAIL;
import static cn.iocoder.yudao.module.devops.enums.ErrorCodeConstants.ENVIRONMENT_DOCKER_HOST_REQUIRED;
import static cn.iocoder.yudao.module.devops.enums.ErrorCodeConstants.ENVIRONMENT_INFRA_TYPE_NOT_SUPPORTED;
import static cn.iocoder.yudao.module.devops.enums.ErrorCodeConstants.ENVIRONMENT_NOT_EXISTS;

/**
 * Docker 容器日志服务实现。
 */
@Slf4j
@Service
@Validated
public class DockerContainerLogServiceImpl implements DockerContainerLogService {

    private static final int DEFAULT_TAIL_LINES = 200;
    private static final int MAX_TAIL_LINES = 2000;
    private static final long SSE_TIMEOUT_MILLIS = 0L;

    @Resource
    private EnvironmentMapper environmentMapper;
    @Resource
    private DockerClientFactory dockerClientFactory;
    @Resource
    @Qualifier("pipelineLogStreamExecutor")
    private Executor pipelineLogStreamExecutor;

    @Override
    public SseEmitter streamContainerLogs(Long environmentId, String containerId, Integer tailLines) {
        EnvironmentDO environment = validateEnvironment(environmentId);
        DockerClient client = createClient(environment);
        try {
            InspectContainerResponse container = inspectContainer(client, containerId);
            SseEmitter emitter = new SseEmitter(SSE_TIMEOUT_MILLIS);
            AtomicBoolean closed = new AtomicBoolean(false);
            AtomicLong lineNo = new AtomicLong(0);
            DockerLogCallback callback = new DockerLogCallback(emitter, container.getId(), trimContainerName(container.getName()),
                    lineNo, closed);
            emitter.onCompletion(() -> closeResources(closed, callback, client));
            emitter.onTimeout(() -> closeResources(closed, callback, client));
            emitter.onError(ex -> closeResources(closed, callback, client));
            pipelineLogStreamExecutor.execute(() -> startLogStream(client, container.getId(), resolveTailLines(tailLines),
                    callback, closed));
            return emitter;
        } catch (RuntimeException ex) {
            closeQuietly(client);
            throw ex;
        }
    }

    private void startLogStream(DockerClient client, String containerId, Integer tailLines,
                                DockerLogCallback callback, AtomicBoolean closed) {
        try {
            client.logContainerCmd(containerId)
                    .withStdOut(true)
                    .withStdErr(true)
                    .withFollowStream(true)
                    .withTail(tailLines)
                    .exec(callback)
                    .awaitCompletion();
            if (!closed.get()) {
                send(callback.getEmitter(), "complete", "EOF");
                callback.getEmitter().complete();
            }
        } catch (InterruptedException ex) {
            Thread.currentThread().interrupt();
            callback.getEmitter().completeWithError(ex);
        } catch (DockerException ex) {
            log.warn("[startLogStream][containerId({}) Docker log stream failed: {}]", containerId, ex.getMessage(), ex);
            sendErrorQuietly(callback.getEmitter(), ex.getMessage());
            callback.getEmitter().completeWithError(ex);
        } catch (IOException ex) {
            callback.getEmitter().completeWithError(ex);
        } finally {
            closeResources(closed, callback, client);
        }
    }

    private EnvironmentDO validateEnvironment(Long environmentId) {
        EnvironmentDO environment = environmentMapper.selectById(environmentId);
        if (environment == null) {
            throw exception(ENVIRONMENT_NOT_EXISTS);
        }
        if (!EnvironmentInfraTypeEnum.DOCKER.getInfraType().equals(environment.getInfraType())) {
            throw exception(ENVIRONMENT_INFRA_TYPE_NOT_SUPPORTED);
        }
        return environment;
    }

    private DockerClient createClient(EnvironmentDO environment) {
        DockerEnvironmentConfig config = JsonUtils.parseObject(environment.getInfraConfig(), DockerEnvironmentConfig.class);
        if (config == null || StrUtil.isBlank(config.getHost())) {
            throw exception(ENVIRONMENT_DOCKER_HOST_REQUIRED);
        }
        try {
            return dockerClientFactory.createClient(config);
        } catch (RuntimeException ex) {
            throw exception(ENVIRONMENT_DOCKER_CONNECTION_FAIL, StrUtil.subPre(ex.getMessage(), 512));
        }
    }

    private InspectContainerResponse inspectContainer(DockerClient client, String containerId) {
        try {
            return client.inspectContainerCmd(containerId).exec();
        } catch (NotFoundException ex) {
            throw exception(DOCKER_CONTAINER_NOT_EXISTS, containerId);
        } catch (DockerException ex) {
            throw exception(DOCKER_CONTAINER_LOG_STREAM_FAIL, StrUtil.subPre(ex.getMessage(), 512));
        }
    }

    private int resolveTailLines(Integer tailLines) {
        if (tailLines == null || tailLines < 0) {
            return DEFAULT_TAIL_LINES;
        }
        return Math.min(tailLines, MAX_TAIL_LINES);
    }

    private void sendLogLine(SseEmitter emitter, String containerId, String containerName, Long lineNo,
                             String streamType, String line) throws IOException {
        EnvironmentDockerContainerLogLineRespVO respVO = new EnvironmentDockerContainerLogLineRespVO();
        respVO.setLineNo(lineNo);
        respVO.setContainerId(containerId);
        respVO.setContainerName(containerName);
        respVO.setStreamType(streamType);
        respVO.setContent(line);
        emitter.send(SseEmitter.event().name("log").id(String.valueOf(lineNo)).data(respVO));
    }

    private void send(SseEmitter emitter, String event, Object data) throws IOException {
        emitter.send(SseEmitter.event().name(event).data(data));
    }

    private void sendErrorQuietly(SseEmitter emitter, String message) {
        try {
            send(emitter, "error", StrUtil.blankToDefault(message, "Docker 容器日志读取失败"));
        } catch (IOException ignored) {
            // 客户端已断开时无需再次处理。
        }
    }

    private void closeResources(AtomicBoolean closed, ResultCallback<?> callback, DockerClient client) {
        if (!closed.compareAndSet(false, true)) {
            return;
        }
        closeQuietly(callback);
        closeQuietly(client);
    }

    private void closeQuietly(Closeable closeable) {
        if (closeable == null) {
            return;
        }
        try {
            closeable.close();
        } catch (IOException | RuntimeException ignored) {
            // ignore
        }
    }

    private String trimContainerName(String name) {
        return StrUtil.removePrefix(name, "/");
    }

    private String normalizeStreamType(Frame frame) {
        if (frame.getStreamType() == null) {
            return "stdout";
        }
        return frame.getStreamType().name().toLowerCase();
    }

    private class DockerLogCallback extends ResultCallback.Adapter<Frame> {

        private final SseEmitter emitter;
        private final String containerId;
        private final String containerName;
        private final AtomicLong lineNo;
        private final AtomicBoolean closed;

        DockerLogCallback(SseEmitter emitter, String containerId, String containerName, AtomicLong lineNo,
                          AtomicBoolean closed) {
            this.emitter = emitter;
            this.containerId = containerId;
            this.containerName = containerName;
            this.lineNo = lineNo;
            this.closed = closed;
        }

        @Override
        public void onNext(Frame frame) {
            if (closed.get() || frame == null || frame.getPayload() == null) {
                return;
            }
            String content = new String(frame.getPayload(), StandardCharsets.UTF_8);
            String[] lines = content.split("\\R", -1);
            int limit = content.endsWith("\n") || content.endsWith("\r") ? lines.length - 1 : lines.length;
            for (int i = 0; i < limit; i++) {
                try {
                    sendLogLine(emitter, containerId, containerName, lineNo.incrementAndGet(),
                            normalizeStreamType(frame), lines[i]);
                } catch (IOException ex) {
                    closed.set(true);
                    emitter.completeWithError(ex);
                    return;
                }
            }
        }

        SseEmitter getEmitter() {
            return emitter;
        }

    }

}
