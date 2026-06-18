package cn.iocoder.yudao.module.devops.service.host.terminal;

import cn.hutool.core.util.StrUtil;
import cn.iocoder.yudao.module.devops.dal.dataobject.host.EnvironmentHostDO;
import cn.iocoder.yudao.module.devops.framework.host.HostSshClient;
import cn.iocoder.yudao.module.devops.service.host.EnvironmentHostService;
import com.jcraft.jsch.ChannelShell;
import com.jcraft.jsch.JSchException;
import com.jcraft.jsch.Session;
import jakarta.annotation.Resource;
import org.springframework.stereotype.Service;
import org.springframework.validation.annotation.Validated;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;

import static cn.iocoder.yudao.framework.common.exception.util.ServiceExceptionUtil.exception;
import static cn.iocoder.yudao.module.devops.enums.ErrorCodeConstants.ENVIRONMENT_HOST_CONNECTION_FAIL;
import static cn.iocoder.yudao.module.devops.enums.ErrorCodeConstants.ENVIRONMENT_HOST_NOT_IN_ENVIRONMENT;
import static cn.iocoder.yudao.module.devops.enums.ErrorCodeConstants.HOST_TERMINAL_EXEC_FAIL;

/**
 * HOST 主机终端服务实现。
 */
@Service
@Validated
public class HostTerminalServiceImpl implements HostTerminalService {

    private static final int CHANNEL_CONNECT_TIMEOUT_MILLIS = 5_000;

    @Resource
    private EnvironmentHostService environmentHostService;
    @Resource
    private HostSshClient hostSshClient;

    @Override
    public HostTerminalSession openTerminal(Long environmentId, Long hostId) {
        EnvironmentHostDO host = environmentHostService.validateHostExists(hostId);
        environmentHostService.validateHostEnvironment(environmentId);
        if (!environmentId.equals(host.getEnvId())) {
            throw exception(ENVIRONMENT_HOST_NOT_IN_ENVIRONMENT);
        }
        Session session = null;
        try {
            session = hostSshClient.openSession(host);
            ChannelShell channel = (ChannelShell) session.openChannel("shell");
            channel.setPty(true);
            channel.setPtyType("xterm");
            InputStream output = channel.getInputStream();
            OutputStream input = channel.getOutputStream();
            channel.connect(CHANNEL_CONNECT_TIMEOUT_MILLIS);
            return new HostTerminalSession(session, channel, output, input);
        } catch (JSchException ex) {
            closeQuietly(session);
            throw exception(ENVIRONMENT_HOST_CONNECTION_FAIL, StrUtil.subPre(ex.getMessage(), 512));
        } catch (IOException ex) {
            closeQuietly(session);
            throw exception(HOST_TERMINAL_EXEC_FAIL, StrUtil.subPre(ex.getMessage(), 512));
        } catch (RuntimeException ex) {
            closeQuietly(session);
            throw ex;
        }
    }

    private void closeQuietly(Session session) {
        if (session != null) {
            session.disconnect();
        }
    }

}
