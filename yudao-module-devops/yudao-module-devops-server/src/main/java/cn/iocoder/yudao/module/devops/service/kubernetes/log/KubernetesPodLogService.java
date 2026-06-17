package cn.iocoder.yudao.module.devops.service.kubernetes.log;

import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

/**
 * Kubernetes Pod 日志服务。
 */
public interface KubernetesPodLogService {

    /**
     * 流式读取 Pod 日志。
     *
     * @param environmentId 环境编号
     * @param namespace Kubernetes Namespace，可空，默认使用环境配置 namespace
     * @param podName Pod 名称
     * @param containerName 容器名称，可空，单容器 Pod 自动选择
     * @param tailLines 首次返回的尾部日志行数，可空
     * @return SSE emitter
     */
    SseEmitter streamPodLogs(Long environmentId, String namespace, String podName, String containerName,
                             Integer tailLines);

}
