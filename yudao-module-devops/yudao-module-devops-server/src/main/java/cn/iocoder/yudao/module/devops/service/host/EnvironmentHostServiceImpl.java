package cn.iocoder.yudao.module.devops.service.host;

import cn.hutool.core.util.StrUtil;
import cn.iocoder.yudao.framework.common.pojo.PageResult;
import cn.iocoder.yudao.module.devops.controller.admin.host.vo.EnvironmentHostPageReqVO;
import cn.iocoder.yudao.module.devops.controller.admin.host.vo.EnvironmentHostSaveReqVO;
import cn.iocoder.yudao.module.devops.convert.host.EnvironmentHostConvert;
import cn.iocoder.yudao.module.devops.dal.dataobject.environment.EnvironmentDO;
import cn.iocoder.yudao.module.devops.dal.dataobject.host.EnvironmentHostDO;
import cn.iocoder.yudao.module.devops.dal.mysql.host.EnvironmentHostMapper;
import cn.iocoder.yudao.module.devops.enums.EnvironmentInfraTypeEnum;
import cn.iocoder.yudao.module.devops.enums.HostAuthTypeEnum;
import cn.iocoder.yudao.module.devops.enums.HostCheckStatusEnum;
import cn.iocoder.yudao.module.devops.framework.host.HostSshClient;
import cn.iocoder.yudao.module.devops.service.environment.EnvironmentService;
import com.jcraft.jsch.JSchException;
import jakarta.annotation.Resource;
import org.springframework.stereotype.Service;
import org.springframework.validation.annotation.Validated;

import java.time.LocalDateTime;

import static cn.iocoder.yudao.framework.common.exception.util.ServiceExceptionUtil.exception;
import static cn.iocoder.yudao.module.devops.enums.ErrorCodeConstants.ENVIRONMENT_HOST_CONNECTION_FAIL;
import static cn.iocoder.yudao.module.devops.enums.ErrorCodeConstants.ENVIRONMENT_HOST_KEY_DUPLICATE;
import static cn.iocoder.yudao.module.devops.enums.ErrorCodeConstants.ENVIRONMENT_HOST_NOT_EXISTS;
import static cn.iocoder.yudao.module.devops.enums.ErrorCodeConstants.ENVIRONMENT_HOST_PASSWORD_REQUIRED;
import static cn.iocoder.yudao.module.devops.enums.ErrorCodeConstants.ENVIRONMENT_HOST_PRIVATE_KEY_REQUIRED;
import static cn.iocoder.yudao.module.devops.enums.ErrorCodeConstants.ENVIRONMENT_INFRA_TYPE_NOT_SUPPORTED;

/**
 * DevOps 环境主机 Service 实现类。
 */
@Service
@Validated
public class EnvironmentHostServiceImpl implements EnvironmentHostService {

    @Resource
    private EnvironmentHostMapper environmentHostMapper;
    @Resource
    private EnvironmentService environmentService;
    @Resource
    private HostSshClient hostSshClient;

    @Override
    public Long createHost(EnvironmentHostSaveReqVO createReqVO) {
        validateHostEnvironment(createReqVO.getEnvId());
        validateHostKeyUnique(null, createReqVO.getEnvId(), createReqVO.getHostKey());
        EnvironmentHostDO host = EnvironmentHostConvert.INSTANCE.convert(createReqVO);
        validateAndFillCredential(host, createReqVO, null);
        environmentHostMapper.insert(host);
        return host.getId();
    }

    @Override
    public void updateHost(EnvironmentHostSaveReqVO updateReqVO) {
        EnvironmentHostDO oldHost = validateHostExists(updateReqVO.getId());
        validateHostEnvironment(updateReqVO.getEnvId());
        validateHostKeyUnique(updateReqVO.getId(), updateReqVO.getEnvId(), updateReqVO.getHostKey());
        EnvironmentHostDO updateObj = EnvironmentHostConvert.INSTANCE.convert(updateReqVO);
        validateAndFillCredential(updateObj, updateReqVO, oldHost);
        environmentHostMapper.updateById(updateObj);
    }

    @Override
    public void deleteHost(Long id) {
        validateHostExists(id);
        environmentHostMapper.deleteById(id);
    }

    @Override
    public EnvironmentHostDO getHost(Long id) {
        return environmentHostMapper.selectById(id);
    }

    @Override
    public PageResult<EnvironmentHostDO> getHostPage(EnvironmentHostPageReqVO pageReqVO) {
        validateHostEnvironment(pageReqVO.getEnvId());
        return environmentHostMapper.selectPage(pageReqVO);
    }

    @Override
    public EnvironmentHostDO checkHost(Long id) {
        EnvironmentHostDO host = validateHostExists(id);
        validateHostEnvironment(host.getEnvId());
        try {
            hostSshClient.checkConnection(host);
            updateCheckResult(host, HostCheckStatusEnum.SUCCESS, "连接成功");
        } catch (JSchException | RuntimeException ex) {
            String message = sanitizeErrorMessage(ex);
            updateCheckResult(host, HostCheckStatusEnum.FAIL, message);
            throw exception(ENVIRONMENT_HOST_CONNECTION_FAIL, message);
        }
        return environmentHostMapper.selectById(id);
    }

    @Override
    public EnvironmentHostDO validateHostExists(Long id) {
        EnvironmentHostDO host = environmentHostMapper.selectById(id);
        if (host == null) {
            throw exception(ENVIRONMENT_HOST_NOT_EXISTS);
        }
        return host;
    }

    private EnvironmentDO validateHostEnvironment(Long envId) {
        EnvironmentDO environment = environmentService.validateEnvironmentExists(envId);
        if (!EnvironmentInfraTypeEnum.HOST.getInfraType().equals(environment.getInfraType())) {
            throw exception(ENVIRONMENT_INFRA_TYPE_NOT_SUPPORTED);
        }
        return environment;
    }

    private void validateHostKeyUnique(Long id, Long envId, String hostKey) {
        EnvironmentHostDO host = environmentHostMapper.selectByEnvIdAndHostKey(envId, hostKey);
        if (host != null && !host.getId().equals(id)) {
            throw exception(ENVIRONMENT_HOST_KEY_DUPLICATE);
        }
    }

    private void validateAndFillCredential(EnvironmentHostDO host, EnvironmentHostSaveReqVO reqVO,
                                           EnvironmentHostDO oldHost) {
        if (HostAuthTypeEnum.PASSWORD.getAuthType().equals(host.getAuthType())) {
            host.setPassword(resolveSecret(reqVO.getPassword(), oldHost == null ? null : oldHost.getPassword()));
            host.setPrivateKey(null);
            host.setPassphrase(null);
            if (StrUtil.isBlank(host.getPassword())) {
                throw exception(ENVIRONMENT_HOST_PASSWORD_REQUIRED);
            }
            return;
        }
        if (HostAuthTypeEnum.PRIVATE_KEY.getAuthType().equals(host.getAuthType())) {
            host.setPrivateKey(resolveSecret(reqVO.getPrivateKey(), oldHost == null ? null : oldHost.getPrivateKey()));
            host.setPassphrase(resolveSecret(reqVO.getPassphrase(), oldHost == null ? null : oldHost.getPassphrase()));
            host.setPassword(null);
            if (StrUtil.isBlank(host.getPrivateKey())) {
                throw exception(ENVIRONMENT_HOST_PRIVATE_KEY_REQUIRED);
            }
        }
    }

    private String resolveSecret(String newValue, String oldValue) {
        return StrUtil.isNotBlank(newValue) ? newValue : oldValue;
    }

    private void updateCheckResult(EnvironmentHostDO host, HostCheckStatusEnum status, String message) {
        EnvironmentHostDO updateObj = new EnvironmentHostDO();
        updateObj.setId(host.getId());
        updateObj.setLastCheckStatus(status.getStatus());
        updateObj.setLastCheckTime(LocalDateTime.now());
        updateObj.setLastCheckMessage(StrUtil.subPre(message, 512));
        environmentHostMapper.updateById(updateObj);
    }

    private String sanitizeErrorMessage(Exception ex) {
        return StrUtil.subPre(StrUtil.blankToDefault(ex.getMessage(), ex.getClass().getSimpleName()), 512);
    }

}
