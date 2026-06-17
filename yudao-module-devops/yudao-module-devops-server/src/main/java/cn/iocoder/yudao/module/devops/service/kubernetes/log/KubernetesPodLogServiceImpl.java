package cn.iocoder.yudao.module.devops.service.kubernetes.log;

import cn.hutool.core.util.StrUtil;
import cn.iocoder.yudao.framework.common.util.json.JsonUtils;
import cn.iocoder.yudao.module.devops.controller.admin.environment.vo.EnvironmentKubernetesPodLogLineRespVO;
import cn.iocoder.yudao.module.devops.dal.dataobject.environment.EnvironmentDO;
import cn.iocoder.yudao.module.devops.dal.mysql.environment.EnvironmentMapper;
import cn.iocoder.yudao.module.devops.enums.EnvironmentInfraTypeEnum;
import cn.iocoder.yudao.module.devops.framework.kubernetes.KubernetesClientFactory;
import cn.iocoder.yudao.module.devops.framework.kubernetes.KubernetesEnvironmentConfig;
import io.fabric8.kubernetes.api.model.Container;
import io.fabric8.kubernetes.api.model.Pod;
import io.fabric8.kubernetes.client.KubernetesClient;
import io.fabric8.kubernetes.client.KubernetesClientException;
import io.fabric8.kubernetes.client.dsl.LogWatch;
import jakarta.annotation.Resource;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.stereotype.Service;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.concurrent.Executor;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicLong;

import static cn.iocoder.yudao.framework.common.exception.util.ServiceExceptionUtil.exception;
import static cn.iocoder.yudao.module.devops.enums.ErrorCodeConstants.ENVIRONMENT_INFRA_TYPE_NOT_SUPPORTED;
import static cn.iocoder.yudao.module.devops.enums.ErrorCodeConstants.ENVIRONMENT_KUBECONFIG_REQUIRED;
import static cn.iocoder.yudao.module.devops.enums.ErrorCodeConstants.ENVIRONMENT_KUBERNETES_CONNECTION_FAIL;
import static cn.iocoder.yudao.module.devops.enums.ErrorCodeConstants.ENVIRONMENT_KUBERNETES_NAMESPACE_NOT_MATCH;
import static cn.iocoder.yudao.module.devops.enums.ErrorCodeConstants.ENVIRONMENT_KUBERNETES_NAMESPACE_REQUIRED;
import static cn.iocoder.yudao.module.devops.enums.ErrorCodeConstants.ENVIRONMENT_NOT_EXISTS;
import static cn.iocoder.yudao.module.devops.enums.ErrorCodeConstants.KUBERNETES_POD_CONTAINER_NOT_EXISTS;
import static cn.iocoder.yudao.module.devops.enums.ErrorCodeConstants.KUBERNETES_POD_CONTAINER_REQUIRED;
import static cn.iocoder.yudao.module.devops.enums.ErrorCodeConstants.KUBERNETES_POD_LOG_STREAM_FAIL;
import static cn.iocoder.yudao.module.devops.enums.ErrorCodeConstants.KUBERNETES_POD_NOT_EXISTS;

/**
 * Kubernetes Pod 日志服务实现。
 */
@Slf4j
@Service
@Validated
public class KubernetesPodLogServiceImpl implements KubernetesPodLogService {

    private static final int DEFAULT_TAIL_LINES = 200;
    private static final int MAX_TAIL_LINES = 2000;
    private static final long SSE_TIMEOUT_MILLIS = 0L;

    @Resource
    private EnvironmentMapper environmentMapper;
    @Resource
    private KubernetesClientFactory kubernetesClientFactory;
    @Resource
    @Qualifier("pipelineLogStreamExecutor")
    private Executor pipelineLogStreamExecutor;

    @Override
    public SseEmitter streamPodLogs(Long environmentId, String namespace, String podName, String containerName,
                                    Integer tailLines) {
        EnvironmentDO environment = validateEnvironment(environmentId);
        KubernetesEnvironmentConfig config = parseConfig(environment);
        String resolvedNamespace = resolveNamespace(config, namespace);
        KubernetesClient client = createClient(config);
        LogWatch logWatch = null;
        try {
            Pod pod = client.pods().inNamespace(resolvedNamespace).withName(podName).get();
            validatePod(pod, podName);
            String resolvedContainerName = resolveContainerName(pod, containerName);
            logWatch = client.pods().inNamespace(resolvedNamespace).withName(podName)
                    .inContainer(resolvedContainerName)
                    .tailingLines(resolveTailLines(tailLines))
                    .watchLog();
            LogWatch createdLogWatch = logWatch;
            SseEmitter emitter = new SseEmitter(SSE_TIMEOUT_MILLIS);
            AtomicBoolean closed = new AtomicBoolean(false);
            emitter.onCompletion(() -> closeResources(closed, createdLogWatch, client));
            emitter.onTimeout(() -> closeResources(closed, createdLogWatch, client));
            emitter.onError(ex -> closeResources(closed, createdLogWatch, client));
            pipelineLogStreamExecutor.execute(() -> doStream(emitter, client, createdLogWatch, podName,
                    resolvedContainerName, closed));
            return emitter;
        } catch (KubernetesClientException ex) {
            closeQuietly(logWatch);
            client.close();
            throw exception(KUBERNETES_POD_LOG_STREAM_FAIL, StrUtil.subPre(ex.getMessage(), 512));
        } catch (RuntimeException ex) {
            closeQuietly(logWatch);
            client.close();
            throw ex;
        }
    }

    private void doStream(SseEmitter emitter, KubernetesClient client, LogWatch logWatch, String podName,
                          String containerName, AtomicBoolean closed) {
        AtomicLong lineNo = new AtomicLong(0);
        try (KubernetesClient ignoredClient = client;
             LogWatch ignoredWatch = logWatch;
             InputStream inputStream = logWatch.getOutput();
             BufferedReader reader = new BufferedReader(new InputStreamReader(inputStream, StandardCharsets.UTF_8))) {
            String line;
            while ((line = reader.readLine()) != null) {
                long currentLineNo = lineNo.incrementAndGet();
                sendLogLine(emitter, podName, containerName, currentLineNo, line);
            }
            send(emitter, "complete", "EOF");
            emitter.complete();
        } catch (IOException ex) {
            log.warn("[doStream][podName({}) containerName({}) log stream IO failed: {}]",
                    podName, containerName, ex.getMessage(), ex);
            emitter.completeWithError(ex);
        } catch (Throwable ex) {
            log.warn("[doStream][podName({}) containerName({}) log stream failed: {}]",
                    podName, containerName, ex.getMessage(), ex);
            sendErrorQuietly(emitter, ex.getMessage());
            emitter.completeWithError(ex);
        } finally {
            closed.set(true);
        }
    }

    private EnvironmentDO validateEnvironment(Long environmentId) {
        EnvironmentDO environment = environmentMapper.selectById(environmentId);
        if (environment == null) {
            throw exception(ENVIRONMENT_NOT_EXISTS);
        }
        if (!EnvironmentInfraTypeEnum.K8S.getInfraType().equals(environment.getInfraType())) {
            throw exception(ENVIRONMENT_INFRA_TYPE_NOT_SUPPORTED);
        }
        return environment;
    }

    private KubernetesEnvironmentConfig parseConfig(EnvironmentDO environment) {
        KubernetesEnvironmentConfig config = JsonUtils.parseObject(environment.getInfraConfig(),
                KubernetesEnvironmentConfig.class);
        if (config == null || StrUtil.isBlank(config.getKubeconfig())) {
            throw exception(ENVIRONMENT_KUBECONFIG_REQUIRED);
        }
        if (StrUtil.isBlank(config.getNamespace())) {
            throw exception(ENVIRONMENT_KUBERNETES_NAMESPACE_REQUIRED);
        }
        return config;
    }

    private KubernetesClient createClient(KubernetesEnvironmentConfig config) {
        try {
            return kubernetesClientFactory.create(config.getKubeconfig());
        } catch (KubernetesClientException ex) {
            throw exception(ENVIRONMENT_KUBERNETES_CONNECTION_FAIL, StrUtil.subPre(ex.getMessage(), 512));
        }
    }

    private String resolveNamespace(KubernetesEnvironmentConfig config, String namespace) {
        if (StrUtil.isBlank(namespace)) {
            return config.getNamespace();
        }
        if (!namespace.equals(config.getNamespace())) {
            throw exception(ENVIRONMENT_KUBERNETES_NAMESPACE_NOT_MATCH, config.getNamespace());
        }
        return namespace;
    }

    private void validatePod(Pod pod, String podName) {
        if (pod == null) {
            throw exception(KUBERNETES_POD_NOT_EXISTS, podName);
        }
    }

    private String resolveContainerName(Pod pod, String containerName) {
        List<Container> containers = pod.getSpec() == null ? null : pod.getSpec().getContainers();
        if (containers == null || containers.isEmpty()) {
            throw exception(KUBERNETES_POD_CONTAINER_NOT_EXISTS, containerName);
        }
        if (StrUtil.isBlank(containerName)) {
            if (containers.size() == 1) {
                return containers.get(0).getName();
            }
            throw exception(KUBERNETES_POD_CONTAINER_REQUIRED);
        }
        boolean exists = containers.stream().anyMatch(container -> containerName.equals(container.getName()));
        if (!exists) {
            throw exception(KUBERNETES_POD_CONTAINER_NOT_EXISTS, containerName);
        }
        return containerName;
    }

    private int resolveTailLines(Integer tailLines) {
        if (tailLines == null) {
            return DEFAULT_TAIL_LINES;
        }
        if (tailLines < 0) {
            return DEFAULT_TAIL_LINES;
        }
        return Math.min(tailLines, MAX_TAIL_LINES);
    }

    private void sendLogLine(SseEmitter emitter, String podName, String containerName, Long lineNo, String line)
            throws IOException {
        EnvironmentKubernetesPodLogLineRespVO respVO = new EnvironmentKubernetesPodLogLineRespVO();
        respVO.setLineNo(lineNo);
        respVO.setPodName(podName);
        respVO.setContainerName(containerName);
        respVO.setContent(line);
        emitter.send(SseEmitter.event().name("log").id(String.valueOf(lineNo)).data(respVO));
    }

    private void send(SseEmitter emitter, String event, Object data) throws IOException {
        emitter.send(SseEmitter.event().name(event).data(data));
    }

    private void sendErrorQuietly(SseEmitter emitter, String message) {
        try {
            send(emitter, "error", StrUtil.blankToDefault(message, "Kubernetes Pod 日志读取失败"));
        } catch (IOException ignored) {
            // 客户端已断开时无需再次处理。
        }
    }

    private void closeResources(AtomicBoolean closed, LogWatch logWatch, KubernetesClient client) {
        if (!closed.compareAndSet(false, true)) {
            return;
        }
        closeQuietly(logWatch);
        try {
            client.close();
        } catch (RuntimeException ignored) {
            // ignore
        }
    }

    private void closeQuietly(LogWatch logWatch) {
        if (logWatch == null) {
            return;
        }
        try {
            logWatch.close();
        } catch (RuntimeException ignored) {
            // ignore
        }
    }

}
