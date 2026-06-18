package cn.iocoder.yudao.module.devops.framework.host;

import cn.hutool.core.util.StrUtil;
import cn.iocoder.yudao.module.devops.controller.admin.environment.vo.EnvironmentConnectionCheckRespVO;
import cn.iocoder.yudao.module.devops.controller.admin.environment.vo.EnvironmentSaveReqVO;
import cn.iocoder.yudao.module.devops.dal.dataobject.environment.EnvironmentDO;
import cn.iocoder.yudao.module.devops.dal.dataobject.host.EnvironmentHostDO;
import cn.iocoder.yudao.module.devops.dal.mysql.host.EnvironmentHostMapper;
import cn.iocoder.yudao.module.devops.enums.EnvironmentInfraTypeEnum;
import cn.iocoder.yudao.module.devops.framework.infra.EnvironmentConnector;
import com.jcraft.jsch.JSchException;
import jakarta.annotation.Resource;
import org.springframework.stereotype.Component;

import java.util.List;

/**
 * HOST 主机组环境连接器。
 */
@Component
public class HostEnvironmentConnector implements EnvironmentConnector {

    @Resource
    private EnvironmentHostMapper environmentHostMapper;
    @Resource
    private HostSshClient hostSshClient;

    @Override
    public String getInfraType() {
        return EnvironmentInfraTypeEnum.HOST.getInfraType();
    }

    @Override
    public String buildInfraConfig(EnvironmentSaveReqVO reqVO, EnvironmentDO oldEnvironment) {
        return null;
    }

    @Override
    public EnvironmentConnectionCheckRespVO checkConnection(EnvironmentDO environment) {
        List<EnvironmentHostDO> hosts = environmentHostMapper.selectListByEnvId(environment.getId());
        int successCount = 0;
        int failCount = 0;
        for (EnvironmentHostDO host : hosts) {
            try {
                hostSshClient.checkConnection(host);
                successCount++;
            } catch (JSchException | RuntimeException ex) {
                failCount++;
            }
        }
        EnvironmentConnectionCheckRespVO respVO = new EnvironmentConnectionCheckRespVO();
        respVO.setInfraType(getInfraType());
        respVO.setHostCount(hosts.size());
        respVO.setHostSuccessCount(successCount);
        respVO.setHostFailCount(failCount);
        respVO.setMessage(StrUtil.format("主机组检测完成：共 {} 台，成功 {} 台，失败 {} 台",
                hosts.size(), successCount, failCount));
        return respVO;
    }

}
