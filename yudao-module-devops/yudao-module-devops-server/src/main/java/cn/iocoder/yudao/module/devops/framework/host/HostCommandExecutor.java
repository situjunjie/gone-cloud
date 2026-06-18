package cn.iocoder.yudao.module.devops.framework.host;

import cn.hutool.core.util.StrUtil;
import cn.iocoder.yudao.module.devops.dal.dataobject.host.EnvironmentHostDO;
import com.jcraft.jsch.ChannelExec;
import com.jcraft.jsch.JSchException;
import com.jcraft.jsch.Session;
import jakarta.annotation.Resource;
import org.springframework.stereotype.Component;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.nio.charset.StandardCharsets;

/**
 * 主机固定命令执行器。
 */
@Component
public class HostCommandExecutor {

    private static final int COMMAND_CONNECT_TIMEOUT_MILLIS = 5_000;
    private static final int COMMAND_WAIT_INTERVAL_MILLIS = 50;

    @Resource
    private HostSshClient hostSshClient;

    public String execute(EnvironmentHostDO host, String command, int timeoutMillis) throws JSchException, IOException {
        Session session = hostSshClient.openSession(host);
        try {
            return execute(session, command, timeoutMillis);
        } finally {
            session.disconnect();
        }
    }

    public String execute(Session session, String command, int timeoutMillis) throws JSchException, IOException {
        ChannelExec channel = (ChannelExec) session.openChannel("exec");
        ByteArrayOutputStream output = new ByteArrayOutputStream();
        ByteArrayOutputStream error = new ByteArrayOutputStream();
        try {
            channel.setCommand(command);
            channel.setInputStream(null);
            channel.setOutputStream(output);
            channel.setErrStream(error);
            channel.connect(COMMAND_CONNECT_TIMEOUT_MILLIS);
            long deadline = System.currentTimeMillis() + timeoutMillis;
            while (!channel.isClosed()) {
                if (System.currentTimeMillis() > deadline) {
                    throw new IOException("SSH command timeout");
                }
                sleepQuietly();
            }
            String errorText = error.toString(StandardCharsets.UTF_8);
            if (channel.getExitStatus() != 0 && StrUtil.isNotBlank(errorText)) {
                throw new IOException(StrUtil.subPre(errorText.trim(), 512));
            }
            return output.toString(StandardCharsets.UTF_8);
        } finally {
            channel.disconnect();
        }
    }

    private void sleepQuietly() {
        try {
            Thread.sleep(COMMAND_WAIT_INTERVAL_MILLIS);
        } catch (InterruptedException ex) {
            Thread.currentThread().interrupt();
        }
    }

}
