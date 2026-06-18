package cn.iocoder.yudao.module.devops.framework.docker;

import cn.hutool.core.util.StrUtil;
import cn.iocoder.yudao.framework.common.exception.ServiceException;
import cn.iocoder.yudao.framework.common.pojo.PageResult;
import cn.iocoder.yudao.framework.common.util.json.JsonUtils;
import cn.iocoder.yudao.module.devops.controller.admin.environment.vo.EnvironmentConnectionCheckRespVO;
import cn.iocoder.yudao.module.devops.controller.admin.environment.vo.EnvironmentDockerComposeProjectDetailRespVO;
import cn.iocoder.yudao.module.devops.controller.admin.environment.vo.EnvironmentDockerComposeProjectRespVO;
import cn.iocoder.yudao.module.devops.controller.admin.environment.vo.EnvironmentDockerComposeServiceRespVO;
import cn.iocoder.yudao.module.devops.controller.admin.environment.vo.EnvironmentDockerContainerPortRespVO;
import cn.iocoder.yudao.module.devops.controller.admin.environment.vo.EnvironmentDockerContainerRespVO;
import cn.iocoder.yudao.module.devops.controller.admin.environment.vo.EnvironmentDockerConfigReqVO;
import cn.iocoder.yudao.module.devops.controller.admin.environment.vo.EnvironmentDockerDashboardRespVO;
import cn.iocoder.yudao.module.devops.controller.admin.environment.vo.EnvironmentDockerImagePageReqVO;
import cn.iocoder.yudao.module.devops.controller.admin.environment.vo.EnvironmentDockerImageRespVO;
import cn.iocoder.yudao.module.devops.controller.admin.environment.vo.EnvironmentDockerNetworkRespVO;
import cn.iocoder.yudao.module.devops.controller.admin.environment.vo.EnvironmentDockerVolumeMountRespVO;
import cn.iocoder.yudao.module.devops.controller.admin.environment.vo.EnvironmentSaveReqVO;
import cn.iocoder.yudao.module.devops.dal.dataobject.environment.EnvironmentDO;
import cn.iocoder.yudao.module.devops.enums.EnvironmentInfraTypeEnum;
import cn.iocoder.yudao.module.devops.framework.infra.EnvironmentConnector;
import com.github.dockerjava.api.DockerClient;
import com.github.dockerjava.api.command.InspectContainerResponse;
import com.github.dockerjava.api.exception.DockerException;
import com.github.dockerjava.api.exception.NotFoundException;
import com.github.dockerjava.api.model.Container;
import com.github.dockerjava.api.model.ContainerMount;
import com.github.dockerjava.api.model.ContainerNetwork;
import com.github.dockerjava.api.model.ContainerNetworkSettings;
import com.github.dockerjava.api.model.ContainerPort;
import com.github.dockerjava.api.model.Image;
import com.github.dockerjava.api.model.Info;
import com.github.dockerjava.api.model.Network;
import com.github.dockerjava.api.model.Version;
import jakarta.annotation.Resource;
import org.springframework.stereotype.Component;

import java.time.Duration;
import java.time.LocalDateTime;
import java.util.Arrays;
import java.util.Collections;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;
import java.util.function.Consumer;
import java.util.function.Predicate;
import java.util.stream.Collectors;

import static cn.iocoder.yudao.framework.common.exception.util.ServiceExceptionUtil.exception;
import static cn.iocoder.yudao.module.devops.enums.ErrorCodeConstants.DOCKER_COMPOSE_PROJECT_NOT_EXISTS;
import static cn.iocoder.yudao.module.devops.enums.ErrorCodeConstants.DOCKER_CONTAINER_NOT_EXISTS;
import static cn.iocoder.yudao.module.devops.enums.ErrorCodeConstants.ENVIRONMENT_DOCKER_CONNECTION_FAIL;
import static cn.iocoder.yudao.module.devops.enums.ErrorCodeConstants.ENVIRONMENT_DOCKER_HOST_REQUIRED;

/**
 * Docker 环境连接器。
 */
@Component
public class DockerEnvironmentConnector implements EnvironmentConnector {

    private static final String CONTAINER_STATE_RUNNING = "running";
    private static final String CONTAINER_STATE_RESTARTING = "restarting";
    private static final String CONTAINER_STATE_PAUSED = "paused";
    private static final String COMPOSE_LABEL_PROJECT = "com.docker.compose.project";
    private static final String COMPOSE_LABEL_SERVICE = "com.docker.compose.service";
    private static final String COMPOSE_STATUS_RUNNING = "RUNNING";
    private static final String COMPOSE_STATUS_STOPPED = "STOPPED";
    private static final String COMPOSE_STATUS_ABNORMAL = "ABNORMAL";
    private static final String COMPOSE_STATUS_PARTIAL = "PARTIAL";
    private static final Duration IMAGE_CACHE_TTL = Duration.ofHours(1);

    @Resource
    private DockerClientFactory dockerClientFactory;

    private final ConcurrentMap<Long, DockerImageCacheEntry> imageCacheMap = new ConcurrentHashMap<>();

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
        DockerEnvironmentConfig config = requireConfig(environment);
        try (DockerClient client = dockerClientFactory.createClient(config)) {
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
            List<Container> containers = listDockerContainers(client);
            List<Network> networks = listDockerNetworks(client);
            respVO.setComposeProjects(containers.stream()
                    .filter(this::isComposeContainer)
                    .collect(Collectors.groupingBy(container -> label(container, COMPOSE_LABEL_PROJECT)))
                    .entrySet()
                    .stream()
                    .map(entry -> buildComposeProjectSummary(entry.getKey(), entry.getValue(), networks))
                    .sorted(Comparator.comparing(EnvironmentDockerComposeProjectRespVO::getProjectName))
                    .toList());
            respVO.setImages(client.listImagesCmd().withShowAll(true).exec().stream()
                    .map(image -> convertImage(image, containers))
                    .sorted(Comparator.comparing(EnvironmentDockerImageRespVO::getCreated,
                            Comparator.nullsLast(Comparator.reverseOrder())))
                    .toList());
            return respVO;
        } catch (ServiceException ex) {
            throw ex;
        } catch (DockerException ex) {
            throw exception(ENVIRONMENT_DOCKER_CONNECTION_FAIL, StrUtil.subPre(ex.getMessage(), 512));
        } catch (Exception ex) {
            throw exception(ENVIRONMENT_DOCKER_CONNECTION_FAIL, StrUtil.subPre(ex.getMessage(), 512));
        }
    }

    public List<EnvironmentDockerContainerRespVO> listContainers(EnvironmentDO environment, Boolean all) {
        DockerEnvironmentConfig config = requireConfig(environment);
        try (DockerClient client = dockerClientFactory.createClient(config)) {
            return client.listContainersCmd()
                    .withShowAll(Boolean.TRUE.equals(all))
                    .exec()
                    .stream()
                    .map(this::convertContainer)
                    .toList();
        } catch (ServiceException ex) {
            throw ex;
        } catch (DockerException ex) {
            throw exception(ENVIRONMENT_DOCKER_CONNECTION_FAIL, StrUtil.subPre(ex.getMessage(), 512));
        } catch (Exception ex) {
            throw exception(ENVIRONMENT_DOCKER_CONNECTION_FAIL, StrUtil.subPre(ex.getMessage(), 512));
        }
    }

    public PageResult<EnvironmentDockerImageRespVO> listImages(EnvironmentDockerImagePageReqVO pageReqVO,
                                                               EnvironmentDO environment) {
        DockerEnvironmentConfig config = requireConfig(environment);
        try {
            DockerImageCacheEntry cacheEntry = getImageCacheEntry(environment.getId(), config,
                    Boolean.TRUE.equals(pageReqVO.getRefreshCache()));
            List<EnvironmentDockerImageRespVO> filteredImages = cacheEntry.getImages().stream()
                    .map(image -> convertImage(image, cacheEntry.getContainers()))
                    .filter(image -> StrUtil.isBlank(pageReqVO.getKeyword()) || containsKeyword(image, pageReqVO.getKeyword()))
                    .filter(image -> pageReqVO.getDangling() == null || pageReqVO.getDangling().equals(image.getDangling()))
                    .filter(image -> pageReqVO.getUnused() == null || pageReqVO.getUnused().equals(image.getUnused()))
                    .sorted(Comparator.comparing(EnvironmentDockerImageRespVO::getCreated,
                            Comparator.nullsLast(Comparator.reverseOrder())))
                    .toList();
            return new PageResult<>(pageList(filteredImages, pageReqVO.getPageNo(), pageReqVO.getPageSize()),
                    (long) filteredImages.size());
        } catch (ServiceException ex) {
            throw ex;
        } catch (DockerException ex) {
            throw exception(ENVIRONMENT_DOCKER_CONNECTION_FAIL, StrUtil.subPre(ex.getMessage(), 512));
        } catch (Exception ex) {
            throw exception(ENVIRONMENT_DOCKER_CONNECTION_FAIL, StrUtil.subPre(ex.getMessage(), 512));
        }
    }

    public List<EnvironmentDockerComposeProjectRespVO> listComposeProjects(EnvironmentDO environment) {
        DockerEnvironmentConfig config = requireConfig(environment);
        try (DockerClient client = dockerClientFactory.createClient(config)) {
            List<Container> containers = listDockerContainers(client);
            List<Network> networks = listDockerNetworks(client);
            return containers.stream()
                    .filter(this::isComposeContainer)
                    .collect(Collectors.groupingBy(container -> label(container, COMPOSE_LABEL_PROJECT)))
                    .entrySet()
                    .stream()
                    .map(entry -> buildComposeProjectSummary(entry.getKey(), entry.getValue(), networks))
                    .sorted(Comparator.comparing(EnvironmentDockerComposeProjectRespVO::getProjectName))
                    .toList();
        } catch (ServiceException ex) {
            throw ex;
        } catch (DockerException ex) {
            throw exception(ENVIRONMENT_DOCKER_CONNECTION_FAIL, StrUtil.subPre(ex.getMessage(), 512));
        } catch (Exception ex) {
            throw exception(ENVIRONMENT_DOCKER_CONNECTION_FAIL, StrUtil.subPre(ex.getMessage(), 512));
        }
    }

    public EnvironmentDockerComposeProjectDetailRespVO getComposeProjectDetail(EnvironmentDO environment,
                                                                              String projectName) {
        DockerEnvironmentConfig config = requireConfig(environment);
        try (DockerClient client = dockerClientFactory.createClient(config)) {
            List<Container> projectContainers = listComposeContainers(client, projectName);
            if (projectContainers.isEmpty()) {
                throw exception(DOCKER_COMPOSE_PROJECT_NOT_EXISTS, projectName);
            }
            List<Network> projectNetworks = listComposeNetworks(client, projectName);
            EnvironmentDockerComposeProjectDetailRespVO respVO = new EnvironmentDockerComposeProjectDetailRespVO();
            respVO.setSummary(buildComposeProjectSummary(projectName, projectContainers, projectNetworks));
            respVO.setContainers(projectContainers.stream().map(this::convertContainer).toList());
            respVO.setServices(buildComposeServices(projectContainers));
            respVO.setNetworks(projectNetworks.stream().map(this::convertNetwork).toList());
            respVO.setVolumes(buildComposeVolumes(projectContainers));
            respVO.setImages(buildComposeImages(client, projectContainers));
            return respVO;
        } catch (ServiceException ex) {
            throw ex;
        } catch (DockerException ex) {
            throw exception(ENVIRONMENT_DOCKER_CONNECTION_FAIL, StrUtil.subPre(ex.getMessage(), 512));
        } catch (Exception ex) {
            throw exception(ENVIRONMENT_DOCKER_CONNECTION_FAIL, StrUtil.subPre(ex.getMessage(), 512));
        }
    }

    public void startContainer(EnvironmentDO environment, String containerId) {
        operateContainer(environment, containerId, client -> client.startContainerCmd(containerId).exec());
    }

    public void stopContainer(EnvironmentDO environment, String containerId) {
        operateContainer(environment, containerId, client -> client.stopContainerCmd(containerId).exec());
    }

    public void restartContainer(EnvironmentDO environment, String containerId) {
        operateContainer(environment, containerId, client -> client.restartContainerCmd(containerId).exec());
    }

    public void startComposeProject(EnvironmentDO environment, String projectName) {
        operateComposeProject(environment, projectName, container -> !CONTAINER_STATE_RUNNING.equals(container.getState()),
                (client, container) -> client.startContainerCmd(container.getId()).exec());
    }

    public void stopComposeProject(EnvironmentDO environment, String projectName) {
        operateComposeProject(environment, projectName, container -> CONTAINER_STATE_RUNNING.equals(container.getState()),
                (client, container) -> client.stopContainerCmd(container.getId()).exec());
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

    EnvironmentDockerImageRespVO convertImage(Image image, List<Container> containers) {
        EnvironmentDockerImageRespVO respVO = new EnvironmentDockerImageRespVO();
        respVO.setId(image.getId());
        respVO.setShortId(shortId(removeSha256Prefix(image.getId())));
        respVO.setRepoTags(formatArray(image.getRepoTags()));
        respVO.setRepoDigests(formatArray(image.getRepoDigests()));
        respVO.setCreated(image.getCreated());
        respVO.setSize(image.getSize());
        respVO.setVirtualSize(image.getVirtualSize());
        respVO.setLabels(image.getLabels() == null ? Map.of() : image.getLabels());
        List<Container> usedContainers = containers.stream()
                .filter(container -> imageMatchesContainer(image, container))
                .toList();
        respVO.setUsedContainerCount(usedContainers.size());
        respVO.setUsedContainerNames(usedContainers.stream()
                .map(container -> convertContainer(container).getName())
                .filter(StrUtil::isNotBlank)
                .distinct()
                .sorted()
                .toList());
        respVO.setComposeProjects(usedContainers.stream()
                .map(container -> label(container, COMPOSE_LABEL_PROJECT))
                .filter(StrUtil::isNotBlank)
                .distinct()
                .sorted()
                .toList());
        respVO.setUnused(usedContainers.isEmpty());
        respVO.setDangling(isDanglingImage(respVO.getRepoTags()));
        return respVO;
    }

    EnvironmentDockerComposeProjectRespVO buildComposeProjectSummaryForTest(String projectName, List<Container> containers) {
        return buildComposeProjectSummary(projectName, containers, List.of());
    }

    private DockerEnvironmentConfig requireConfig(EnvironmentDO environment) {
        DockerEnvironmentConfig config = parseConfig(environment);
        if (config == null || StrUtil.isBlank(config.getHost())) {
            throw exception(ENVIRONMENT_DOCKER_HOST_REQUIRED);
        }
        return config;
    }

    private List<Container> listDockerContainers(DockerClient client) {
        return client.listContainersCmd().withShowAll(true).exec();
    }

    private DockerImageCacheEntry getImageCacheEntry(Long environmentId, DockerEnvironmentConfig config,
                                                     boolean refreshCache) throws Exception {
        String configKey = buildImageCacheConfigKey(config);
        DockerImageCacheEntry cacheEntry = imageCacheMap.get(environmentId);
        if (!refreshCache && cacheEntry != null && !cacheEntry.isExpired()
                && StrUtil.equals(cacheEntry.getConfigKey(), configKey)) {
            return cacheEntry;
        }
        DockerImageCacheEntry latestEntry = loadImageCacheEntry(configKey, config);
        imageCacheMap.put(environmentId, latestEntry);
        return latestEntry;
    }

    private DockerImageCacheEntry loadImageCacheEntry(String configKey, DockerEnvironmentConfig config) throws Exception {
        try (DockerClient client = dockerClientFactory.createClient(config)) {
            return new DockerImageCacheEntry(configKey, client.listImagesCmd().withShowAll(true).exec(),
                    listDockerContainers(client), LocalDateTime.now().plus(IMAGE_CACHE_TTL));
        }
    }

    private String buildImageCacheConfigKey(DockerEnvironmentConfig config) {
        return StrUtil.format("{}|{}|{}", config.getHost(), config.getTlsVerify(), config.getApiVersion());
    }

    private List<Network> listDockerNetworks(DockerClient client) {
        return client.listNetworksCmd().exec();
    }

    private List<Container> listComposeContainers(DockerClient client, String projectName) {
        if (StrUtil.isBlank(projectName)) {
            return List.of();
        }
        return listDockerContainers(client).stream()
                .filter(container -> projectName.equals(label(container, COMPOSE_LABEL_PROJECT)))
                .toList();
    }

    private List<Network> listComposeNetworks(DockerClient client, String projectName) {
        if (StrUtil.isBlank(projectName)) {
            return List.of();
        }
        return listDockerNetworks(client).stream()
                .filter(network -> projectName.equals(label(network.getLabels(), COMPOSE_LABEL_PROJECT)))
                .toList();
    }

    private EnvironmentDockerComposeProjectRespVO buildComposeProjectSummary(String projectName,
                                                                            List<Container> containers,
                                                                            List<Network> networks) {
        EnvironmentDockerComposeProjectRespVO respVO = new EnvironmentDockerComposeProjectRespVO();
        respVO.setProjectName(projectName);
        respVO.setContainerCount(containers.size());
        respVO.setRunningContainerCount(countContainers(containers, container -> CONTAINER_STATE_RUNNING.equals(container.getState())));
        respVO.setStoppedContainerCount(countContainers(containers, container -> !CONTAINER_STATE_RUNNING.equals(container.getState())));
        respVO.setAbnormalContainerCount(countContainers(containers, this::isAbnormalContainer));
        respVO.setServices(distinctLabels(containers, COMPOSE_LABEL_SERVICE));
        respVO.setServiceCount(respVO.getServices().size());
        respVO.setImages(containers.stream().map(Container::getImage).filter(StrUtil::isNotBlank).distinct().sorted().toList());
        respVO.setImageCount(respVO.getImages().size());
        respVO.setNetworks(networks.stream()
                .filter(network -> projectName.equals(label(network.getLabels(), COMPOSE_LABEL_PROJECT)))
                .map(Network::getName)
                .filter(StrUtil::isNotBlank)
                .distinct()
                .sorted()
                .toList());
        respVO.setNetworkCount(respVO.getNetworks().size());
        respVO.setStatus(resolveComposeStatus(containers, respVO.getRunningContainerCount(), respVO.getAbnormalContainerCount()));
        return respVO;
    }

    private List<EnvironmentDockerComposeServiceRespVO> buildComposeServices(List<Container> containers) {
        return containers.stream()
                .collect(Collectors.groupingBy(container -> label(container, COMPOSE_LABEL_SERVICE)))
                .entrySet()
                .stream()
                .filter(entry -> StrUtil.isNotBlank(entry.getKey()))
                .map(entry -> buildComposeService(entry.getKey(), entry.getValue()))
                .sorted(Comparator.comparing(EnvironmentDockerComposeServiceRespVO::getServiceName))
                .toList();
    }

    private EnvironmentDockerComposeServiceRespVO buildComposeService(String serviceName, List<Container> containers) {
        EnvironmentDockerComposeServiceRespVO respVO = new EnvironmentDockerComposeServiceRespVO();
        respVO.setServiceName(serviceName);
        respVO.setContainerCount(containers.size());
        respVO.setRunningContainerCount(countContainers(containers, container -> CONTAINER_STATE_RUNNING.equals(container.getState())));
        respVO.setImages(containers.stream().map(Container::getImage).filter(StrUtil::isNotBlank).distinct().sorted().toList());
        respVO.setContainerNames(containers.stream()
                .map(container -> convertContainer(container).getName())
                .filter(StrUtil::isNotBlank)
                .sorted()
                .toList());
        return respVO;
    }

    private EnvironmentDockerNetworkRespVO convertNetwork(Network network) {
        EnvironmentDockerNetworkRespVO respVO = new EnvironmentDockerNetworkRespVO();
        respVO.setId(network.getId());
        respVO.setName(network.getName());
        respVO.setDriver(network.getDriver());
        respVO.setScope(network.getScope());
        respVO.setInternal(network.getInternal());
        respVO.setAttachable(network.isAttachable());
        respVO.setLabels(network.getLabels() == null ? Map.of() : network.getLabels());
        respVO.setContainerIds(network.getContainers() == null ? List.of() : network.getContainers().keySet().stream().sorted().toList());
        return respVO;
    }

    private List<EnvironmentDockerVolumeMountRespVO> buildComposeVolumes(List<Container> containers) {
        return containers.stream()
                .flatMap(container -> convertMounts(container).stream())
                .toList();
    }

    private List<EnvironmentDockerVolumeMountRespVO> convertMounts(Container container) {
        if (container.getMounts() == null) {
            return List.of();
        }
        EnvironmentDockerContainerRespVO containerResp = convertContainer(container);
        return container.getMounts().stream()
                .map(mount -> convertMount(mount, containerResp, label(container, COMPOSE_LABEL_SERVICE)))
                .toList();
    }

    private EnvironmentDockerVolumeMountRespVO convertMount(ContainerMount mount,
                                                            EnvironmentDockerContainerRespVO container,
                                                            String serviceName) {
        EnvironmentDockerVolumeMountRespVO respVO = new EnvironmentDockerVolumeMountRespVO();
        respVO.setName(mount.getName());
        respVO.setSource(mount.getSource());
        respVO.setDestination(mount.getDestination());
        respVO.setDriver(mount.getDriver());
        respVO.setMode(mount.getMode());
        respVO.setRw(mount.getRw());
        respVO.setContainerId(container.getId());
        respVO.setContainerName(container.getName());
        respVO.setServiceName(serviceName);
        return respVO;
    }

    private List<EnvironmentDockerImageRespVO> buildComposeImages(DockerClient client, List<Container> projectContainers) {
        Set<String> imageNames = projectContainers.stream()
                .map(Container::getImage)
                .filter(StrUtil::isNotBlank)
                .collect(Collectors.toSet());
        if (imageNames.isEmpty()) {
            return List.of();
        }
        return client.listImagesCmd().withShowAll(true).exec().stream()
                .filter(image -> formatArray(image.getRepoTags()).stream().anyMatch(imageNames::contains)
                        || projectContainers.stream().anyMatch(container -> imageMatchesContainer(image, container)))
                .map(image -> convertImage(image, projectContainers))
                .toList();
    }

    private void operateContainer(EnvironmentDO environment, String containerId, Consumer<DockerClient> operation) {
        DockerEnvironmentConfig config = requireConfig(environment);
        try (DockerClient client = dockerClientFactory.createClient(config)) {
            inspectContainer(client, containerId);
            operation.accept(client);
        } catch (ServiceException ex) {
            throw ex;
        } catch (NotFoundException ex) {
            throw exception(DOCKER_CONTAINER_NOT_EXISTS, containerId);
        } catch (DockerException ex) {
            throw exception(ENVIRONMENT_DOCKER_CONNECTION_FAIL, StrUtil.subPre(ex.getMessage(), 512));
        } catch (Exception ex) {
            throw exception(ENVIRONMENT_DOCKER_CONNECTION_FAIL, StrUtil.subPre(ex.getMessage(), 512));
        }
    }

    private void operateComposeProject(EnvironmentDO environment, String projectName, Predicate<Container> filter,
                                       ComposeContainerOperation operation) {
        DockerEnvironmentConfig config = requireConfig(environment);
        try (DockerClient client = dockerClientFactory.createClient(config)) {
            List<Container> containers = listComposeContainers(client, projectName);
            if (containers.isEmpty()) {
                throw exception(DOCKER_COMPOSE_PROJECT_NOT_EXISTS, projectName);
            }
            for (Container container : containers) {
                if (filter.test(container)) {
                    operation.accept(client, container);
                }
            }
        } catch (ServiceException ex) {
            throw ex;
        } catch (DockerException ex) {
            throw exception(ENVIRONMENT_DOCKER_CONNECTION_FAIL, StrUtil.subPre(ex.getMessage(), 512));
        } catch (Exception ex) {
            throw exception(ENVIRONMENT_DOCKER_CONNECTION_FAIL, StrUtil.subPre(ex.getMessage(), 512));
        }
    }

    private InspectContainerResponse inspectContainer(DockerClient client, String containerId) {
        try {
            return client.inspectContainerCmd(containerId).exec();
        } catch (NotFoundException ex) {
            throw exception(DOCKER_CONTAINER_NOT_EXISTS, containerId);
        }
    }

    private boolean containsKeyword(EnvironmentDockerImageRespVO image, String keyword) {
        return image.getRepoTags().stream().anyMatch(tag -> StrUtil.containsIgnoreCase(tag, keyword))
                || image.getRepoDigests().stream().anyMatch(digest -> StrUtil.containsIgnoreCase(digest, keyword))
                || StrUtil.containsIgnoreCase(image.getId(), keyword);
    }

    private boolean imageMatchesContainer(Image image, Container container) {
        if (StrUtil.equals(image.getId(), container.getImageId())) {
            return true;
        }
        String normalizedImageId = removeSha256Prefix(image.getId());
        String normalizedContainerImageId = removeSha256Prefix(container.getImageId());
        if (StrUtil.isNotBlank(normalizedImageId) && StrUtil.equals(normalizedImageId, normalizedContainerImageId)) {
            return true;
        }
        return formatArray(image.getRepoTags()).contains(container.getImage());
    }

    private boolean isDanglingImage(List<String> repoTags) {
        return repoTags.isEmpty() || repoTags.stream().allMatch(tag -> "<none>:<none>".equals(tag));
    }

    private boolean isComposeContainer(Container container) {
        return StrUtil.isNotBlank(label(container, COMPOSE_LABEL_PROJECT));
    }

    private String label(Container container, String labelKey) {
        return label(container.getLabels(), labelKey);
    }

    private String label(Map<String, String> labels, String labelKey) {
        return labels == null ? null : labels.get(labelKey);
    }

    private List<String> distinctLabels(List<Container> containers, String labelKey) {
        return containers.stream()
                .map(container -> label(container, labelKey))
                .filter(StrUtil::isNotBlank)
                .distinct()
                .sorted()
                .toList();
    }

    private int countContainers(List<Container> containers, Predicate<Container> predicate) {
        return (int) containers.stream().filter(predicate).count();
    }

    private boolean isAbnormalContainer(Container container) {
        return CONTAINER_STATE_RESTARTING.equals(container.getState())
                || CONTAINER_STATE_PAUSED.equals(container.getState())
                || StrUtil.containsIgnoreCase(container.getStatus(), "unhealthy");
    }

    private String resolveComposeStatus(List<Container> containers, Integer runningCount, Integer abnormalCount) {
        if (containers.isEmpty()) {
            return COMPOSE_STATUS_STOPPED;
        }
        if (abnormalCount != null && abnormalCount > 0) {
            return COMPOSE_STATUS_ABNORMAL;
        }
        if (runningCount != null && runningCount == containers.size()) {
            return COMPOSE_STATUS_RUNNING;
        }
        if (runningCount == null || runningCount == 0) {
            return COMPOSE_STATUS_STOPPED;
        }
        return COMPOSE_STATUS_PARTIAL;
    }

    private List<String> formatArray(String[] values) {
        if (values == null || values.length == 0) {
            return List.of();
        }
        return Arrays.stream(values)
                .filter(StrUtil::isNotBlank)
                .distinct()
                .sorted()
                .toList();
    }

    private String removeSha256Prefix(String value) {
        return StrUtil.removePrefix(value, "sha256:");
    }

    private List<EnvironmentDockerImageRespVO> pageList(List<EnvironmentDockerImageRespVO> list, Integer pageNo,
                                                        Integer pageSize) {
        if (list.isEmpty()) {
            return List.of();
        }
        int safePageNo = pageNo == null || pageNo < 1 ? 1 : pageNo;
        int safePageSize = pageSize == null || pageSize < 1 ? 10 : pageSize;
        int fromIndex = (safePageNo - 1) * safePageSize;
        if (fromIndex >= list.size()) {
            return List.of();
        }
        int toIndex = Math.min(fromIndex + safePageSize, list.size());
        return list.subList(fromIndex, toIndex);
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

    @FunctionalInterface
    private interface ComposeContainerOperation {

        void accept(DockerClient client, Container container);

    }

    private static final class DockerImageCacheEntry {

        private final String configKey;
        private final List<Image> images;
        private final List<Container> containers;
        private final LocalDateTime expiresAt;

        private DockerImageCacheEntry(String configKey, List<Image> images, List<Container> containers,
                                      LocalDateTime expiresAt) {
            this.configKey = configKey;
            this.images = Collections.unmodifiableList(images == null ? List.of() : images);
            this.containers = Collections.unmodifiableList(containers == null ? List.of() : containers);
            this.expiresAt = expiresAt;
        }

        private String getConfigKey() {
            return configKey;
        }

        private List<Image> getImages() {
            return images;
        }

        private List<Container> getContainers() {
            return containers;
        }

        private boolean isExpired() {
            return !LocalDateTime.now().isBefore(expiresAt);
        }

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
