package cn.iocoder.yudao.module.devops.service.environment;

import cn.hutool.core.collection.CollUtil;
import cn.iocoder.yudao.framework.common.pojo.PageResult;
import cn.iocoder.yudao.module.devops.controller.admin.environment.vo.EnvironmentConnectionCheckRespVO;
import cn.iocoder.yudao.module.devops.controller.admin.environment.vo.EnvironmentDockerComposeProjectDetailRespVO;
import cn.iocoder.yudao.module.devops.controller.admin.environment.vo.EnvironmentDockerComposeProjectRespVO;
import cn.iocoder.yudao.module.devops.controller.admin.environment.vo.EnvironmentDockerContainerRespVO;
import cn.iocoder.yudao.module.devops.controller.admin.environment.vo.EnvironmentDockerDashboardRespVO;
import cn.iocoder.yudao.module.devops.controller.admin.environment.vo.EnvironmentDockerImageRespVO;
import cn.iocoder.yudao.module.devops.controller.admin.environment.vo.EnvironmentKubernetesDashboardRespVO;
import cn.iocoder.yudao.module.devops.controller.admin.environment.vo.EnvironmentKubernetesDeploymentRespVO;
import cn.iocoder.yudao.module.devops.controller.admin.environment.vo.EnvironmentKubernetesNamespaceRespVO;
import cn.iocoder.yudao.module.devops.controller.admin.environment.vo.EnvironmentPageReqVO;
import cn.iocoder.yudao.module.devops.controller.admin.environment.vo.EnvironmentKubernetesPodRespVO;
import cn.iocoder.yudao.module.devops.controller.admin.environment.vo.EnvironmentSaveReqVO;
import cn.iocoder.yudao.module.devops.controller.admin.environment.vo.EnvironmentKubernetesServiceRespVO;
import cn.iocoder.yudao.module.devops.convert.environment.EnvironmentConvert;
import cn.iocoder.yudao.module.devops.dal.dataobject.environment.EnvironmentDO;
import cn.iocoder.yudao.module.devops.dal.mysql.application.ApplicationEnvMapper;
import cn.iocoder.yudao.module.devops.dal.mysql.environment.EnvironmentMapper;
import cn.iocoder.yudao.module.devops.enums.EnvironmentInfraTypeEnum;
import cn.iocoder.yudao.module.devops.framework.infra.EnvironmentConnector;
import cn.iocoder.yudao.module.devops.framework.infra.EnvironmentConnectorFactory;
import cn.iocoder.yudao.module.devops.framework.docker.DockerEnvironmentConnector;
import cn.iocoder.yudao.module.devops.framework.kubernetes.KubernetesEnvironmentConnector;
import com.mzt.logapi.starter.annotation.LogRecord;
import jakarta.annotation.Resource;
import org.springframework.stereotype.Service;
import org.springframework.validation.annotation.Validated;

import java.util.List;

import static cn.iocoder.yudao.framework.common.exception.util.ServiceExceptionUtil.exception;
import static cn.iocoder.yudao.module.devops.enums.ErrorCodeConstants.*;
import static cn.iocoder.yudao.module.devops.enums.LogRecordConstants.*;

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
    @Resource
    private DockerEnvironmentConnector dockerEnvironmentConnector;

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

    @Override
    public EnvironmentKubernetesDashboardRespVO getKubernetesDashboard(Long id) {
        EnvironmentDO environment = validateEnvironmentExists(id);
        validateKubernetesEnvironment(environment);
        return kubernetesEnvironmentConnector.getDashboard(environment);
    }

    @Override
    public List<EnvironmentKubernetesPodRespVO> getKubernetesPods(Long id) {
        EnvironmentDO environment = validateEnvironmentExists(id);
        validateKubernetesEnvironment(environment);
        return kubernetesEnvironmentConnector.listPods(environment);
    }

    @Override
    public List<EnvironmentKubernetesDeploymentRespVO> getKubernetesDeployments(Long id) {
        EnvironmentDO environment = validateEnvironmentExists(id);
        validateKubernetesEnvironment(environment);
        return kubernetesEnvironmentConnector.listDeployments(environment);
    }

    @Override
    public List<EnvironmentKubernetesServiceRespVO> getKubernetesServices(Long id) {
        EnvironmentDO environment = validateEnvironmentExists(id);
        validateKubernetesEnvironment(environment);
        return kubernetesEnvironmentConnector.listServices(environment);
    }

    @Override
    public EnvironmentDockerDashboardRespVO getDockerDashboard(Long id) {
        EnvironmentDO environment = validateEnvironmentExists(id);
        validateDockerEnvironment(environment);
        return dockerEnvironmentConnector.getDashboard(environment);
    }

    @Override
    public List<EnvironmentDockerContainerRespVO> getDockerContainers(Long id, Boolean all) {
        EnvironmentDO environment = validateEnvironmentExists(id);
        validateDockerEnvironment(environment);
        return dockerEnvironmentConnector.listContainers(environment, all);
    }

    @Override
    public List<EnvironmentDockerImageRespVO> getDockerImages(Long id, String keyword, Boolean dangling, Boolean unused) {
        EnvironmentDO environment = validateEnvironmentExists(id);
        validateDockerEnvironment(environment);
        return dockerEnvironmentConnector.listImages(environment, keyword, dangling, unused);
    }

    @Override
    public List<EnvironmentDockerComposeProjectRespVO> getDockerComposeProjects(Long id) {
        EnvironmentDO environment = validateEnvironmentExists(id);
        validateDockerEnvironment(environment);
        return dockerEnvironmentConnector.listComposeProjects(environment);
    }

    @Override
    public EnvironmentDockerComposeProjectDetailRespVO getDockerComposeProjectDetail(Long id, String projectName) {
        EnvironmentDO environment = validateEnvironmentExists(id);
        validateDockerEnvironment(environment);
        return dockerEnvironmentConnector.getComposeProjectDetail(environment, projectName);
    }

    @Override
    @LogRecord(type = DEVOPS_ENVIRONMENT_TYPE, subType = DEVOPS_DOCKER_CONTAINER_START_SUB_TYPE,
            bizNo = "{{#id}}", success = DEVOPS_DOCKER_CONTAINER_START_SUCCESS)
    public void startDockerContainer(Long id, String containerId) {
        EnvironmentDO environment = validateEnvironmentExists(id);
        validateDockerEnvironment(environment);
        dockerEnvironmentConnector.startContainer(environment, containerId);
    }

    @Override
    @LogRecord(type = DEVOPS_ENVIRONMENT_TYPE, subType = DEVOPS_DOCKER_CONTAINER_STOP_SUB_TYPE,
            bizNo = "{{#id}}", success = DEVOPS_DOCKER_CONTAINER_STOP_SUCCESS)
    public void stopDockerContainer(Long id, String containerId) {
        EnvironmentDO environment = validateEnvironmentExists(id);
        validateDockerEnvironment(environment);
        dockerEnvironmentConnector.stopContainer(environment, containerId);
    }

    @Override
    @LogRecord(type = DEVOPS_ENVIRONMENT_TYPE, subType = DEVOPS_DOCKER_CONTAINER_RESTART_SUB_TYPE,
            bizNo = "{{#id}}", success = DEVOPS_DOCKER_CONTAINER_RESTART_SUCCESS)
    public void restartDockerContainer(Long id, String containerId) {
        EnvironmentDO environment = validateEnvironmentExists(id);
        validateDockerEnvironment(environment);
        dockerEnvironmentConnector.restartContainer(environment, containerId);
    }

    @Override
    @LogRecord(type = DEVOPS_ENVIRONMENT_TYPE, subType = DEVOPS_DOCKER_COMPOSE_START_SUB_TYPE,
            bizNo = "{{#id}}", success = DEVOPS_DOCKER_COMPOSE_START_SUCCESS)
    public void startDockerComposeProject(Long id, String projectName) {
        EnvironmentDO environment = validateEnvironmentExists(id);
        validateDockerEnvironment(environment);
        dockerEnvironmentConnector.startComposeProject(environment, projectName);
    }

    @Override
    @LogRecord(type = DEVOPS_ENVIRONMENT_TYPE, subType = DEVOPS_DOCKER_COMPOSE_STOP_SUB_TYPE,
            bizNo = "{{#id}}", success = DEVOPS_DOCKER_COMPOSE_STOP_SUCCESS)
    public void stopDockerComposeProject(Long id, String projectName) {
        EnvironmentDO environment = validateEnvironmentExists(id);
        validateDockerEnvironment(environment);
        dockerEnvironmentConnector.stopComposeProject(environment, projectName);
    }

    private void validateEnvKeyUnique(Long id, String envKey) {
        EnvironmentDO environment = environmentMapper.selectByEnvKey(envKey);
        if (environment != null && !environment.getId().equals(id)) {
            throw exception(ENVIRONMENT_ENV_KEY_DUPLICATE);
        }
    }

    private String buildInfraConfig(EnvironmentSaveReqVO reqVO, EnvironmentDO oldEnvironment) {
        if (EnvironmentInfraTypeEnum.HOST.getInfraType().equals(reqVO.getInfraType())) {
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

    private void validateDockerEnvironment(EnvironmentDO environment) {
        if (!EnvironmentInfraTypeEnum.DOCKER.getInfraType().equals(environment.getInfraType())) {
            throw exception(ENVIRONMENT_INFRA_TYPE_NOT_SUPPORTED);
        }
    }

}
