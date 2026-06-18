package cn.iocoder.yudao.module.devops.framework.host;

import cn.hutool.core.util.StrUtil;
import cn.iocoder.yudao.module.devops.dal.dataobject.host.EnvironmentHostDO;
import cn.iocoder.yudao.module.devops.enums.HostAuthTypeEnum;
import com.jcraft.jsch.JSch;
import com.jcraft.jsch.JSchException;
import com.jcraft.jsch.Session;
import org.springframework.stereotype.Component;

import java.nio.charset.StandardCharsets;
import java.util.Properties;

/**
 * SSH 客户端工厂。
 */
@Component
public class HostSshClient {

    private static final int DEFAULT_CONNECT_TIMEOUT_MILLIS = 10_000;

    public Session openSession(EnvironmentHostDO host) throws JSchException {
        JSch jsch = new JSch();
        if (HostAuthTypeEnum.PRIVATE_KEY.getAuthType().equals(host.getAuthType())) {
            byte[] privateKey = StrUtil.nullToEmpty(host.getPrivateKey()).getBytes(StandardCharsets.UTF_8);
            byte[] passphrase = StrUtil.isBlank(host.getPassphrase()) ? null
                    : host.getPassphrase().getBytes(StandardCharsets.UTF_8);
            jsch.addIdentity(host.getHostKey(), privateKey, null, passphrase);
        }
        Session session = jsch.getSession(host.getUsername(), host.getHost(), host.getPort());
        if (HostAuthTypeEnum.PASSWORD.getAuthType().equals(host.getAuthType())) {
            session.setPassword(host.getPassword());
        }
        Properties config = new Properties();
        config.put("StrictHostKeyChecking", "no");
        session.setConfig(config);
        session.connect(DEFAULT_CONNECT_TIMEOUT_MILLIS);
        return session;
    }

    public void checkConnection(EnvironmentHostDO host) throws JSchException {
        Session session = openSession(host);
        try {
            // openSession 已完成 SSH 认证，第一步只验证连通性和凭据。
        } finally {
            session.disconnect();
        }
    }

}
