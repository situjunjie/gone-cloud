package cn.iocoder.yudao.module.devops.framework.kubernetes;

import cn.hutool.core.util.StrUtil;
import cn.iocoder.yudao.framework.common.util.json.JsonUtils;
import cn.iocoder.yudao.module.devops.controller.admin.environment.vo.EnvironmentConnectionCheckRespVO;
import cn.iocoder.yudao.module.devops.controller.admin.environment.vo.EnvironmentKubernetesDashboardRespVO;
import cn.iocoder.yudao.module.devops.controller.admin.environment.vo.EnvironmentKubernetesConfigReqVO;
import cn.iocoder.yudao.module.devops.controller.admin.environment.vo.EnvironmentKubernetesDeploymentRespVO;
import cn.iocoder.yudao.module.devops.controller.admin.environment.vo.EnvironmentKubernetesNamespaceRespVO;
import cn.iocoder.yudao.module.devops.controller.admin.environment.vo.EnvironmentKubernetesPodRespVO;
import cn.iocoder.yudao.module.devops.controller.admin.environment.vo.EnvironmentSaveReqVO;
import cn.iocoder.yudao.module.devops.controller.admin.environment.vo.EnvironmentKubernetesServicePortRespVO;
import cn.iocoder.yudao.module.devops.controller.admin.environment.vo.EnvironmentKubernetesServiceRespVO;
import cn.iocoder.yudao.module.devops.dal.dataobject.environment.EnvironmentDO;
import cn.iocoder.yudao.module.devops.enums.EnvironmentInfraTypeEnum;
import cn.iocoder.yudao.module.devops.framework.infra.EnvironmentConnector;
import io.fabric8.kubernetes.api.model.Container;
import io.fabric8.kubernetes.api.model.ContainerStatus;
import io.fabric8.kubernetes.api.model.LoadBalancerIngress;
import io.fabric8.kubernetes.api.model.Namespace;
import io.fabric8.kubernetes.client.KubernetesClient;
import io.fabric8.kubernetes.client.KubernetesClientException;
import io.fabric8.kubernetes.api.model.Pod;
import io.fabric8.kubernetes.api.model.Service;
import io.fabric8.kubernetes.api.model.ServicePort;
import io.fabric8.kubernetes.api.model.apps.Deployment;
import io.fabric8.kubernetes.api.model.IntOrString;
import jakarta.annotation.Resource;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import static cn.iocoder.yudao.framework.common.exception.util.ServiceExceptionUtil.exception;
import static cn.iocoder.yudao.module.devops.enums.ErrorCodeConstants.ENVIRONMENT_KUBECONFIG_REQUIRED;
import static cn.iocoder.yudao.module.devops.enums.ErrorCodeConstants.ENVIRONMENT_KUBERNETES_CONNECTION_FAIL;
import static cn.iocoder.yudao.module.devops.enums.ErrorCodeConstants.ENVIRONMENT_KUBERNETES_NAMESPACE_REQUIRED;

/**
 * Kubernetes 环境连接器。
 */
@Component
public class KubernetesEnvironmentConnector implements EnvironmentConnector {

    private static final String POD_PHASE_RUNNING = "Running";

    @Resource
    private KubernetesClientFactory kubernetesClientFactory;

    @Override
    public String getInfraType() {
        return EnvironmentInfraTypeEnum.K8S.getInfraType();
    }

    @Override
    public String buildInfraConfig(EnvironmentSaveReqVO reqVO, EnvironmentDO oldEnvironment) {
        EnvironmentKubernetesConfigReqVO kubernetesConfig = reqVO.getKubernetesConfig();
        KubernetesEnvironmentConfig oldConfig = parseOldConfig(oldEnvironment);
        String kubeconfig = kubernetesConfig == null ? null : kubernetesConfig.getKubeconfig();
        if (StrUtil.isBlank(kubeconfig) && oldConfig != null) {
            kubeconfig = oldConfig.getKubeconfig();
        }
        if (StrUtil.isBlank(kubeconfig)) {
            throw exception(ENVIRONMENT_KUBECONFIG_REQUIRED);
        }

        String namespace = kubernetesConfig == null ? null : kubernetesConfig.getNamespace();
        if (StrUtil.isBlank(namespace) && oldConfig != null) {
            namespace = oldConfig.getNamespace();
        }
        if (StrUtil.isBlank(namespace)) {
            throw exception(ENVIRONMENT_KUBERNETES_NAMESPACE_REQUIRED);
        }

        KubernetesEnvironmentConfig config = new KubernetesEnvironmentConfig();
        config.setKubeconfig(kubeconfig);
        config.setNamespace(namespace);
        if (kubernetesConfig != null && StrUtil.isNotBlank(kubernetesConfig.getKubeconfig())) {
            validateConfig(config);
        }
        return JsonUtils.toJsonString(config);
    }

    @Override
    public EnvironmentConnectionCheckRespVO checkConnection(EnvironmentDO environment) {
        try (KubernetesClient client = createClient(environment)) {
            int namespaceCount = client.namespaces().list().getItems().size();
            EnvironmentConnectionCheckRespVO respVO = new EnvironmentConnectionCheckRespVO();
            respVO.setInfraType(getInfraType());
            respVO.setNamespaceCount(namespaceCount);
            respVO.setMessage(StrUtil.format("连接成功，Namespace 数量：{}", namespaceCount));
            return respVO;
        } catch (KubernetesClientException ex) {
            throw exception(ENVIRONMENT_KUBERNETES_CONNECTION_FAIL, StrUtil.subPre(ex.getMessage(), 512));
        }
    }

    public List<EnvironmentKubernetesNamespaceRespVO> listNamespaces(EnvironmentDO environment) {
        try (KubernetesClient client = createClient(environment)) {
            return client.namespaces().list().getItems().stream()
                    .map(this::convertNamespace)
                    .toList();
        } catch (KubernetesClientException ex) {
            throw exception(ENVIRONMENT_KUBERNETES_CONNECTION_FAIL, StrUtil.subPre(ex.getMessage(), 512));
        }
    }

    public EnvironmentKubernetesDashboardRespVO getDashboard(EnvironmentDO environment) {
        KubernetesEnvironmentConfig config = parseConfig(environment);
        String namespace = requireNamespace(config);
        try (KubernetesClient client = createClient(config)) {
            List<EnvironmentKubernetesPodRespVO> pods = client.pods().inNamespace(namespace).list().getItems().stream()
                    .map(this::convertPod)
                    .toList();
            EnvironmentKubernetesDashboardRespVO respVO = new EnvironmentKubernetesDashboardRespVO();
            respVO.setEnvironmentId(environment.getId());
            respVO.setEnvKey(environment.getEnvKey());
            respVO.setEnvName(environment.getEnvName());
            respVO.setEnvStage(environment.getEnvStage());
            respVO.setNamespace(namespace);
            respVO.setServiceCount(client.services().inNamespace(namespace).list().getItems().size());
            respVO.setDeploymentCount(client.apps().deployments().inNamespace(namespace).list().getItems().size());
            respVO.setPodCount(pods.size());
            respVO.setRunningPodCount((int) pods.stream().filter(pod -> POD_PHASE_RUNNING.equals(pod.getPhase())).count());
            respVO.setAbnormalPodCount((int) pods.stream().filter(this::isAbnormalPod).count());
            return respVO;
        } catch (KubernetesClientException ex) {
            throw exception(ENVIRONMENT_KUBERNETES_CONNECTION_FAIL, StrUtil.subPre(ex.getMessage(), 512));
        }
    }

    public List<EnvironmentKubernetesPodRespVO> listPods(EnvironmentDO environment) {
        KubernetesEnvironmentConfig config = parseConfig(environment);
        String namespace = requireNamespace(config);
        try (KubernetesClient client = createClient(config)) {
            return client.pods().inNamespace(namespace).list().getItems().stream()
                    .map(this::convertPod)
                    .toList();
        } catch (KubernetesClientException ex) {
            throw exception(ENVIRONMENT_KUBERNETES_CONNECTION_FAIL, StrUtil.subPre(ex.getMessage(), 512));
        }
    }

    public List<EnvironmentKubernetesDeploymentRespVO> listDeployments(EnvironmentDO environment) {
        KubernetesEnvironmentConfig config = parseConfig(environment);
        String namespace = requireNamespace(config);
        try (KubernetesClient client = createClient(config)) {
            return client.apps().deployments().inNamespace(namespace).list().getItems().stream()
                    .map(this::convertDeployment)
                    .toList();
        } catch (KubernetesClientException ex) {
            throw exception(ENVIRONMENT_KUBERNETES_CONNECTION_FAIL, StrUtil.subPre(ex.getMessage(), 512));
        }
    }

    public List<EnvironmentKubernetesServiceRespVO> listServices(EnvironmentDO environment) {
        KubernetesEnvironmentConfig config = parseConfig(environment);
        String namespace = requireNamespace(config);
        try (KubernetesClient client = createClient(config)) {
            return client.services().inNamespace(namespace).list().getItems().stream()
                    .map(this::convertService)
                    .toList();
        } catch (KubernetesClientException ex) {
            throw exception(ENVIRONMENT_KUBERNETES_CONNECTION_FAIL, StrUtil.subPre(ex.getMessage(), 512));
        }
    }

    private void validateConfig(KubernetesEnvironmentConfig config) {
        try (KubernetesClient ignored = kubernetesClientFactory.create(config.getKubeconfig())) {
            // 构造客户端即可完成 kubeconfig 基础解析校验，避免保存明显无效的配置。
        }
    }

    private KubernetesEnvironmentConfig parseOldConfig(EnvironmentDO oldEnvironment) {
        if (oldEnvironment == null || !getInfraType().equals(oldEnvironment.getInfraType())
                || StrUtil.isBlank(oldEnvironment.getInfraConfig())) {
            return null;
        }
        return JsonUtils.parseObject(oldEnvironment.getInfraConfig(), KubernetesEnvironmentConfig.class);
    }

    private KubernetesClient createClient(EnvironmentDO environment) {
        return createClient(parseConfig(environment));
    }

    private KubernetesClient createClient(KubernetesEnvironmentConfig config) {
        if (config == null || StrUtil.isBlank(config.getKubeconfig())) {
            throw exception(ENVIRONMENT_KUBECONFIG_REQUIRED);
        }
        return kubernetesClientFactory.create(config.getKubeconfig());
    }

    private KubernetesEnvironmentConfig parseConfig(EnvironmentDO environment) {
        return JsonUtils.parseObject(environment.getInfraConfig(), KubernetesEnvironmentConfig.class);
    }

    private String requireNamespace(KubernetesEnvironmentConfig config) {
        if (config == null || StrUtil.isBlank(config.getNamespace())) {
            throw exception(ENVIRONMENT_KUBERNETES_NAMESPACE_REQUIRED);
        }
        return config.getNamespace();
    }

    private EnvironmentKubernetesNamespaceRespVO convertNamespace(Namespace namespace) {
        EnvironmentKubernetesNamespaceRespVO respVO = new EnvironmentKubernetesNamespaceRespVO();
        respVO.setName(namespace.getMetadata().getName());
        respVO.setCreationTimestamp(namespace.getMetadata().getCreationTimestamp());
        respVO.setStatus(namespace.getStatus() == null ? null : namespace.getStatus().getPhase());
        return respVO;
    }

    EnvironmentKubernetesPodRespVO convertPod(Pod pod) {
        EnvironmentKubernetesPodRespVO respVO = new EnvironmentKubernetesPodRespVO();
        respVO.setName(pod.getMetadata().getName());
        respVO.setNamespace(pod.getMetadata().getNamespace());
        respVO.setCreationTimestamp(pod.getMetadata().getCreationTimestamp());
        respVO.setPhase(pod.getStatus() == null ? null : pod.getStatus().getPhase());
        respVO.setNodeName(pod.getSpec() == null ? null : pod.getSpec().getNodeName());
        respVO.setPodIp(pod.getStatus() == null ? null : pod.getStatus().getPodIP());
        List<Container> containers = pod.getSpec() == null || pod.getSpec().getContainers() == null
                ? List.of() : pod.getSpec().getContainers();
        respVO.setTotalContainerCount(containers.size());
        respVO.setContainerNames(containers.stream().map(Container::getName).toList());
        List<ContainerStatus> statuses = pod.getStatus() == null || pod.getStatus().getContainerStatuses() == null
                ? List.of() : pod.getStatus().getContainerStatuses();
        respVO.setReadyContainerCount((int) statuses.stream().filter(status -> Boolean.TRUE.equals(status.getReady())).count());
        respVO.setRestartCount(statuses.stream()
                .mapToInt(status -> status.getRestartCount() == null ? 0 : status.getRestartCount())
                .sum());
        respVO.setTerminalEnabled(POD_PHASE_RUNNING.equals(respVO.getPhase()));
        return respVO;
    }

    EnvironmentKubernetesDeploymentRespVO convertDeployment(Deployment deployment) {
        EnvironmentKubernetesDeploymentRespVO respVO = new EnvironmentKubernetesDeploymentRespVO();
        respVO.setName(deployment.getMetadata().getName());
        respVO.setNamespace(deployment.getMetadata().getNamespace());
        respVO.setCreationTimestamp(deployment.getMetadata().getCreationTimestamp());
        respVO.setReplicas(deployment.getSpec() == null ? null : deployment.getSpec().getReplicas());
        respVO.setReadyReplicas(deployment.getStatus() == null ? null : deployment.getStatus().getReadyReplicas());
        respVO.setAvailableReplicas(deployment.getStatus() == null ? null : deployment.getStatus().getAvailableReplicas());
        respVO.setUpdatedReplicas(deployment.getStatus() == null ? null : deployment.getStatus().getUpdatedReplicas());
        List<Container> containers = deployment.getSpec() == null
                || deployment.getSpec().getTemplate() == null
                || deployment.getSpec().getTemplate().getSpec() == null
                || deployment.getSpec().getTemplate().getSpec().getContainers() == null
                ? List.of() : deployment.getSpec().getTemplate().getSpec().getContainers();
        respVO.setImages(containers.stream().map(Container::getImage).toList());
        return respVO;
    }

    EnvironmentKubernetesServiceRespVO convertService(Service service) {
        EnvironmentKubernetesServiceRespVO respVO = new EnvironmentKubernetesServiceRespVO();
        respVO.setName(service.getMetadata().getName());
        respVO.setNamespace(service.getMetadata().getNamespace());
        respVO.setCreationTimestamp(service.getMetadata().getCreationTimestamp());
        respVO.setType(service.getSpec() == null ? null : service.getSpec().getType());
        respVO.setClusterIp(service.getSpec() == null ? null : service.getSpec().getClusterIP());
        respVO.setExternalIps(getExternalIps(service));
        respVO.setPorts(service.getSpec() == null || service.getSpec().getPorts() == null
                ? List.of() : service.getSpec().getPorts().stream().map(this::convertServicePort).toList());
        respVO.setSelector(service.getSpec() == null || service.getSpec().getSelector() == null
                ? Map.of() : service.getSpec().getSelector());
        return respVO;
    }

    private EnvironmentKubernetesServicePortRespVO convertServicePort(ServicePort port) {
        EnvironmentKubernetesServicePortRespVO respVO = new EnvironmentKubernetesServicePortRespVO();
        respVO.setName(port.getName());
        respVO.setProtocol(port.getProtocol());
        respVO.setPort(port.getPort());
        respVO.setTargetPort(formatIntOrString(port.getTargetPort()));
        respVO.setNodePort(port.getNodePort());
        return respVO;
    }

    private String formatIntOrString(IntOrString value) {
        if (value == null) {
            return null;
        }
        if (value.getIntVal() != null) {
            return value.getIntVal().toString();
        }
        return value.getStrVal();
    }

    private List<String> getExternalIps(Service service) {
        List<String> externalIps = new ArrayList<>();
        if (service.getSpec() != null && service.getSpec().getExternalIPs() != null) {
            externalIps.addAll(service.getSpec().getExternalIPs());
        }
        if (service.getStatus() == null || service.getStatus().getLoadBalancer() == null
                || service.getStatus().getLoadBalancer().getIngress() == null) {
            return externalIps;
        }
        for (LoadBalancerIngress ingress : service.getStatus().getLoadBalancer().getIngress()) {
            if (StrUtil.isNotBlank(ingress.getIp())) {
                externalIps.add(ingress.getIp());
            } else if (StrUtil.isNotBlank(ingress.getHostname())) {
                externalIps.add(ingress.getHostname());
            }
        }
        return externalIps;
    }

    private boolean isAbnormalPod(EnvironmentKubernetesPodRespVO pod) {
        if (!POD_PHASE_RUNNING.equals(pod.getPhase())) {
            return true;
        }
        return pod.getTotalContainerCount() != null && pod.getReadyContainerCount() != null
                && pod.getReadyContainerCount() < pod.getTotalContainerCount();
    }

}
