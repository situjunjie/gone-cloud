package cn.iocoder.yudao.module.devops.framework.docker;

import cn.hutool.core.util.StrUtil;
import cn.iocoder.yudao.framework.common.util.json.JsonUtils;
import cn.iocoder.yudao.module.devops.controller.admin.environment.vo.EnvironmentConnectionCheckRespVO;
import cn.iocoder.yudao.module.devops.controller.admin.environment.vo.EnvironmentDockerContainerPortRespVO;
import cn.iocoder.yudao.module.devops.controller.admin.environment.vo.EnvironmentDockerContainerRespVO;
import cn.iocoder.yudao.module.devops.controller.admin.environment.vo.EnvironmentDockerConfigReqVO;
import cn.iocoder.yudao.module.devops.controller.admin.environment.vo.EnvironmentDockerDashboardRespVO;
import cn.iocoder.yudao.module.devops.controller.admin.environment.vo.EnvironmentSaveReqVO;
import cn.iocoder.yudao.module.devops.dal.dataobject.environment.EnvironmentDO;
import cn.iocoder.yudao.module.devops.enums.EnvironmentInfraTypeEnum;
import cn.iocoder.yudao.module.devops.framework.infra.EnvironmentConnector;
import com.github.dockerjava.api.DockerClient;
import com.github.dockerjava.api.exception.DockerException;
import com.github.dockerjava.api.model.Container;
import com.github.dockerjava.api.model.ContainerPort;
import com.github.dockerjava.api.model.Info;
import com.github.dockerjava.api.model.Version;
import jakarta.annotation.Resource;
import org.springframework.stereotype.Component;

import java.util.Arrays;
import java.util.List;
import java.util.Map;

import static cn.iocoder.yudao.framework.common.exception.util.ServiceExceptionUtil.exception;
import static cn.iocoder.yudao.module.devops.enums.ErrorCodeConstants.ENVIRONMENT_DOCKER_CONNECTION_FAIL;
import static cn.iocoder.yudao.module.devops.enums.ErrorCodeConstants.ENVIRONMENT_DOCKER_HOST_REQUIRED;

/**
 * Docker 环境连接器。
 */
@Component
public class DockerEnvironmentConnector implements EnvironmentConnector {

    private static final String CONTAINER_STATE_RUNNING = "running";

    @Resource
    private DockerClientFactory dockerClientFactory;

    @Override
    public String getInfraType() {
        return EnvironmentInfraTypeEnum.DOCKER.getInfraType();
    }

    @Override
    public String buildInfraConfig(EnvironmentSaveReqVO reqVO, EnvironmentDO oldEnvironment) {
        EnvironmentDockerConfigReqVO dockerConfig = reqVO.getDockerConfig();
        DockerEnvironmentConfig oldConfig = parseOldConfig(oldEnvironment);
        DockerEnvironmentConfig config = new DockerEnvironmentConfig();
        config.setHost(resolveValue(dockerConfig == null ? null : dockerConfig.getHost(),
                oldConfig == null ? null : oldConfig.getHost()));
        if (StrUtil.isBlank(config.getHost())) {
            throw exception(ENVIRONMENT_DOCKER_HOST_REQUIRED);
        }
        config.setTlsVerify(dockerConfig == null || dockerConfig.getTlsVerify() == null
                ? oldConfig == null ? null : oldConfig.getTlsVerify() : dockerConfig.getTlsVerify());
        config.setApiVersion(resolveValue(dockerConfig == null ? null : dockerConfig.getApiVersion(),
                oldConfig == null ? null : oldConfig.getApiVersion()));
        config.setCaCert(resolveValue(dockerConfig == null ? null : dockerConfig.getCaCert(),
                oldConfig == null ? null : oldConfig.getCaCert()));
        config.setClientCert(resolveValue(dockerConfig == null ? null : dockerConfig.getClientCert(),
                oldConfig == null ? null : oldConfig.getClientCert()));
        config.setClientKey(resolveValue(dockerConfig == null ? null : dockerConfig.getClientKey(),
                oldConfig == null ? null : oldConfig.getClientKey()));
        return JsonUtils.toJsonString(config);
    }

    @Override
    public EnvironmentConnectionCheckRespVO checkConnection(EnvironmentDO environment) {
        try (DockerClient client = dockerClientFactory.createClient(requireConfig(environment))) {
            client.pingCmd().exec();
            Version version = client.versionCmd().exec();
            EnvironmentConnectionCheckRespVO respVO = new EnvironmentConnectionCheckRespVO();
            respVO.setInfraType(getInfraType());
            respVO.setDockerServerVersion(version.getVersion());
            respVO.setDockerApiVersion(version.getApiVersion());
            respVO.setMessage(StrUtil.format("连接成功，Docker 版本：{}，API 版本：{}",
                    version.getVersion(), version.getApiVersion()));
            return respVO;
        } catch (DockerException ex) {
            throw exception(ENVIRONMENT_DOCKER_CONNECTION_FAIL, StrUtil.subPre(ex.getMessage(), 512));
        } catch (Exception ex) {
            throw exception(ENVIRONMENT_DOCKER_CONNECTION_FAIL, StrUtil.subPre(ex.getMessage(), 512));
        }
    }

    public DockerEnvironmentConfig parseConfig(EnvironmentDO environment) {
        return JsonUtils.parseObject(environment.getInfraConfig(), DockerEnvironmentConfig.class);
    }

    public EnvironmentDockerDashboardRespVO getDashboard(EnvironmentDO environment) {
        DockerEnvironmentConfig config = requireConfig(environment);
        try (DockerClient client = dockerClientFactory.createClient(config)) {
            Version version = client.versionCmd().exec();
            Info info = client.infoCmd().exec();
            EnvironmentDockerDashboardRespVO respVO = new EnvironmentDockerDashboardRespVO();
            respVO.setEnvironmentId(environment.getId());
            respVO.setEnvKey(environment.getEnvKey());
            respVO.setEnvName(environment.getEnvName());
            respVO.setEnvStage(environment.getEnvStage());
            respVO.setDockerHost(config == null ? null : config.getHost());
            respVO.setServerVersion(version.getVersion());
            respVO.setApiVersion(version.getApiVersion());
            respVO.setOperatingSystem(info.getOperatingSystem());
            respVO.setOsType(info.getOsType());
            respVO.setArchitecture(info.getArchitecture());
            respVO.setContainerCount(info.getContainers());
            respVO.setRunningContainerCount(info.getContainersRunning());
            respVO.setPausedContainerCount(info.getContainersPaused());
            respVO.setStoppedContainerCount(info.getContainersStopped());
            respVO.setImageCount(info.getImages());
            respVO.setDockerRootDir(info.getDockerRootDir());
            return respVO;
        } catch (DockerException ex) {
            throw exception(ENVIRONMENT_DOCKER_CONNECTION_FAIL, StrUtil.subPre(ex.getMessage(), 512));
        } catch (Exception ex) {
            throw exception(ENVIRONMENT_DOCKER_CONNECTION_FAIL, StrUtil.subPre(ex.getMessage(), 512));
        }
    }

    public List<EnvironmentDockerContainerRespVO> listContainers(EnvironmentDO environment, Boolean all) {
        try (DockerClient client = dockerClientFactory.createClient(requireConfig(environment))) {
            return client.listContainersCmd()
                    .withShowAll(Boolean.TRUE.equals(all))
                    .exec()
                    .stream()
                    .map(this::convertContainer)
                    .toList();
        } catch (DockerException ex) {
            throw exception(ENVIRONMENT_DOCKER_CONNECTION_FAIL, StrUtil.subPre(ex.getMessage(), 512));
        } catch (Exception ex) {
            throw exception(ENVIRONMENT_DOCKER_CONNECTION_FAIL, StrUtil.subPre(ex.getMessage(), 512));
        }
    }

    EnvironmentDockerContainerRespVO convertContainer(Container container) {
        EnvironmentDockerContainerRespVO respVO = new EnvironmentDockerContainerRespVO();
        respVO.setId(container.getId());
        respVO.setShortId(shortId(container.getId()));
        respVO.setNames(formatNames(container.getNames()));
        respVO.setName(respVO.getNames().isEmpty() ? null : respVO.getNames().get(0));
        respVO.setImage(container.getImage());
        respVO.setImageId(container.getImageId());
        respVO.setCommand(container.getCommand());
        respVO.setState(container.getState());
        respVO.setStatus(container.getStatus());
        respVO.setCreated(container.getCreated());
        respVO.setPorts(convertPorts(container.getPorts()));
        respVO.setLabels(container.getLabels() == null ? Map.of() : container.getLabels());
        respVO.setTerminalEnabled(CONTAINER_STATE_RUNNING.equals(container.getState()));
        return respVO;
    }

    private DockerEnvironmentConfig requireConfig(EnvironmentDO environment) {
        DockerEnvironmentConfig config = parseConfig(environment);
        if (config == null || StrUtil.isBlank(config.getHost())) {
            throw exception(ENVIRONMENT_DOCKER_HOST_REQUIRED);
        }
        return config;
    }

    private DockerEnvironmentConfig parseOldConfig(EnvironmentDO oldEnvironment) {
        if (oldEnvironment == null || !getInfraType().equals(oldEnvironment.getInfraType())
                || StrUtil.isBlank(oldEnvironment.getInfraConfig())) {
            return null;
        }
        return JsonUtils.parseObject(oldEnvironment.getInfraConfig(), DockerEnvironmentConfig.class);
    }

    private String resolveValue(String newValue, String oldValue) {
        return StrUtil.isBlank(newValue) ? oldValue : newValue;
    }

    private List<String> formatNames(String[] names) {
        if (names == null || names.length == 0) {
            return List.of();
        }
        return Arrays.stream(names)
                .filter(StrUtil::isNotBlank)
                .map(name -> StrUtil.removePrefix(name, "/"))
                .toList();
    }

    private List<EnvironmentDockerContainerPortRespVO> convertPorts(ContainerPort[] ports) {
        if (ports == null || ports.length == 0) {
            return List.of();
        }
        return Arrays.stream(ports).map(this::convertPort).toList();
    }

    private EnvironmentDockerContainerPortRespVO convertPort(ContainerPort port) {
        EnvironmentDockerContainerPortRespVO respVO = new EnvironmentDockerContainerPortRespVO();
        respVO.setIp(port.getIp());
        respVO.setPrivatePort(port.getPrivatePort());
        respVO.setPublicPort(port.getPublicPort());
        respVO.setType(port.getType());
        return respVO;
    }

    private String shortId(String id) {
        if (StrUtil.isBlank(id)) {
            return id;
        }
        return StrUtil.subPre(id, 12);
    }

}
