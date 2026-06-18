package cn.iocoder.yudao.module.devops.service.host.terminal;

/**
 * HOST 主机终端服务。
 */
public interface HostTerminalService {

    /**
     * 打开主机 SSH 终端。
     *
     * @param environmentId 环境编号
     * @param hostId 主机编号
     * @return 终端会话
     */
    HostTerminalSession openTerminal(Long environmentId, Long hostId);

}
