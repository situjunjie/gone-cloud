package cn.iocoder.yudao.module.devops.service.docker.terminal;

/**
 * Docker 容器终端服务。
 */
public interface DockerTerminalService {

    /**
     * 打开 Docker 容器交互终端。
     *
     * @param environmentId 环境编号
     * @param containerId 容器 ID 或名称
     * @return Docker 终端会话
     */
    DockerTerminalSession openTerminal(Long environmentId, String containerId);

}
