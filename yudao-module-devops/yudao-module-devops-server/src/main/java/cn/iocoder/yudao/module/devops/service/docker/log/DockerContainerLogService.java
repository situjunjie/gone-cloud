package cn.iocoder.yudao.module.devops.service.docker.log;

import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

/**
 * Docker 容器日志服务。
 */
public interface DockerContainerLogService {

    /**
     * 流式读取 Docker 容器日志。
     *
     * @param environmentId 环境编号
     * @param containerId 容器 ID 或名称
     * @param tailLines 首次返回的尾部日志行数，可空
     * @return SSE emitter
     */
    SseEmitter streamContainerLogs(Long environmentId, String containerId, Integer tailLines);

}
