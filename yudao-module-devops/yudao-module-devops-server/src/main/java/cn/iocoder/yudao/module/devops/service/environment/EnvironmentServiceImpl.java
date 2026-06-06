package cn.iocoder.yudao.module.devops.service.environment;

import cn.hutool.core.collection.CollUtil;
import cn.iocoder.yudao.framework.common.pojo.PageResult;
import cn.iocoder.yudao.module.devops.controller.admin.environment.vo.EnvironmentConnectionCheckRespVO;
import cn.iocoder.yudao.module.devops.controller.admin.environment.vo.EnvironmentKubernetesNamespaceRespVO;
import cn.iocoder.yudao.module.devops.controller.admin.environment.vo.EnvironmentPageReqVO;
import cn.iocoder.yudao.module.devops.controller.admin.environment.vo.EnvironmentSaveReqVO;
import cn.iocoder.yudao.module.devops.convert.environment.EnvironmentConvert;
import cn.iocoder.yudao.module.devops.dal.dataobject.environment.EnvironmentDO;
import cn.iocoder.yudao.module.devops.dal.mysql.application.ApplicationEnvMapper;
import cn.iocoder.yudao.module.devops.dal.mysql.environment.EnvironmentMapper;
import cn.iocoder.yudao.module.devops.enums.EnvironmentInfraTypeEnum;
import cn.iocoder.yudao.module.devops.framework.infra.EnvironmentConnector;
import cn.iocoder.yudao.module.devops.framework.infra.EnvironmentConnectorFactory;
import cn.iocoder.yudao.module.devops.framework.kubernetes.KubernetesEnvironmentConnector;
import jakarta.annotation.Resource;
import org.springframework.stereotype.Service;
import org.springframework.validation.annotation.Validated;

import java.util.List;

import static cn.iocoder.yudao.framework.common.exception.util.ServiceExceptionUtil.exception;
import static cn.iocoder.yudao.module.devops.enums.ErrorCodeConstants.*;

/**
 * DevOps 环境 Service 实现类。
 */
@Service
@Validated
public class EnvironmentServiceImpl implements EnvironmentService {

    @Resource
    private EnvironmentMapper environmentMapper;
    @Resource
    private ApplicationEnvMapper applicationEnvMapper;
    @Resource
    private EnvironmentConnectorFactory environmentConnectorFactory;
    @Resource
    private KubernetesEnvironmentConnector kubernetesEnvironmentConnector;

    @Override
    public Long createEnvironment(EnvironmentSaveReqVO createReqVO) {
        validateEnvKeyUnique(null, createReqVO.getEnvKey());

        EnvironmentDO environment = EnvironmentConvert.INSTANCE.convert(createReqVO);
        environment.setInfraConfig(buildInfraConfig(createReqVO, null));
        environmentMapper.insert(environment);
        return environment.getId();
    }

    @Override
    public void updateEnvironment(EnvironmentSaveReqVO updateReqVO) {
        EnvironmentDO oldEnvironment = validateEnvironmentExists(updateReqVO.getId());
        validateEnvKeyUnique(updateReqVO.getId(), updateReqVO.getEnvKey());

        EnvironmentDO updateObj = EnvironmentConvert.INSTANCE.convert(updateReqVO);
        updateObj.setInfraConfig(buildInfraConfig(updateReqVO, oldEnvironment));
        environmentMapper.updateById(updateObj);
    }

    @Override
    public void deleteEnvironment(Long id) {
        validateEnvironmentExists(id);
        if (CollUtil.isNotEmpty(applicationEnvMapper.selectListByEnvId(id))) {
            throw exception(ENVIRONMENT_DELETE_FAIL_APPLICATION_ENV_EXISTS);
        }
        environmentMapper.deleteById(id);
    }

    @Override
    public EnvironmentDO getEnvironment(Long id) {
        return environmentMapper.selectById(id);
    }

    @Override
    public PageResult<EnvironmentDO> getEnvironmentPage(EnvironmentPageReqVO pageReqVO) {
        return environmentMapper.selectPage(pageReqVO);
    }

    @Override
    public EnvironmentDO validateEnvironmentExists(Long id) {
        EnvironmentDO environment = environmentMapper.selectById(id);
        if (environment == null) {
            throw exception(ENVIRONMENT_NOT_EXISTS);
        }
        return environment;
    }

    @Override
    public EnvironmentConnectionCheckRespVO checkEnvironmentConnection(Long id) {
        EnvironmentDO environment = validateEnvironmentExists(id);
        EnvironmentConnector connector = environmentConnectorFactory.getConnector(environment.getInfraType());
        return connector.checkConnection(environment);
    }

    @Override
    public List<EnvironmentKubernetesNamespaceRespVO> getKubernetesNamespaces(Long id) {
        EnvironmentDO environment = validateEnvironmentExists(id);
        validateKubernetesEnvironment(environment);
        return kubernetesEnvironmentConnector.listNamespaces(environment);
    }

    private void validateEnvKeyUnique(Long id, String envKey) {
        EnvironmentDO environment = environmentMapper.selectByEnvKey(envKey);
        if (environment != null && !environment.getId().equals(id)) {
            throw exception(ENVIRONMENT_ENV_KEY_DUPLICATE);
        }
    }

    private String buildInfraConfig(EnvironmentSaveReqVO reqVO, EnvironmentDO oldEnvironment) {
        if (!EnvironmentInfraTypeEnum.K8S.getInfraType().equals(reqVO.getInfraType())) {
            return null;
        }
        EnvironmentConnector connector = environmentConnectorFactory.getConnector(reqVO.getInfraType());
        return connector.buildInfraConfig(reqVO, oldEnvironment);
    }

    private void validateKubernetesEnvironment(EnvironmentDO environment) {
        if (!EnvironmentInfraTypeEnum.K8S.getInfraType().equals(environment.getInfraType())) {
            throw exception(ENVIRONMENT_INFRA_TYPE_NOT_SUPPORTED);
        }
    }

}
