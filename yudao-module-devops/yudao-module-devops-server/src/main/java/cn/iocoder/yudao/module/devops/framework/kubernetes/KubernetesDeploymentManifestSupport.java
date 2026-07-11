package cn.iocoder.yudao.module.devops.framework.kubernetes;

import cn.hutool.core.util.StrUtil;
import cn.iocoder.yudao.module.devops.service.deployment.context.ContainerDeployConfigContext;
import io.fabric8.kubernetes.api.model.Container;
import io.fabric8.kubernetes.api.model.HasMetadata;
import io.fabric8.kubernetes.api.model.KubernetesResource;
import io.fabric8.kubernetes.api.model.Service;
import io.fabric8.kubernetes.api.model.apps.Deployment;
import io.fabric8.kubernetes.client.utils.Serialization;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Objects;

import static cn.iocoder.yudao.framework.common.exception.util.ServiceExceptionUtil.exception;
import static cn.iocoder.yudao.module.devops.enums.ErrorCodeConstants.DEPLOYMENT_MANIFEST_CONTAINER_NOT_EXISTS;
import static cn.iocoder.yudao.module.devops.enums.ErrorCodeConstants.DEPLOYMENT_MANIFEST_INVALID;
import static cn.iocoder.yudao.module.devops.enums.ErrorCodeConstants.DEPLOYMENT_MANIFEST_KIND_NOT_SUPPORTED;
import static cn.iocoder.yudao.module.devops.enums.ErrorCodeConstants.DEPLOYMENT_MANIFEST_NAME_REQUIRED;
import static cn.iocoder.yudao.module.devops.enums.ErrorCodeConstants.DEPLOYMENT_MANIFEST_REQUIRED;
import static cn.iocoder.yudao.module.devops.enums.ErrorCodeConstants.DEPLOYMENT_NODE_PARAM_INVALID;

/**
 * Kubernetes Deployment manifest 支持工具。
 */
@Component
public class KubernetesDeploymentManifestSupport {

    public String renderManifest(String manifestYaml, Map<String, String> variables) {
        String rendered = manifestYaml;
        for (Map.Entry<String, String> entry : variables.entrySet()) {
            rendered = rendered.replace("${" + entry.getKey() + "}", StrUtil.blankToDefault(entry.getValue(), ""));
        }
        return rendered;
    }

    public Deployment parseDeployment(String manifestYaml) {
        if (StrUtil.isBlank(manifestYaml)) {
            throw exception(DEPLOYMENT_MANIFEST_REQUIRED);
        }
        if (containsMultipleDocuments(manifestYaml)) {
            throw exception(DEPLOYMENT_MANIFEST_KIND_NOT_SUPPORTED);
        }
        try {
            KubernetesResource resource = Serialization.unmarshal(manifestYaml);
            if (!(resource instanceof Deployment deployment)) {
                throw exception(DEPLOYMENT_MANIFEST_KIND_NOT_SUPPORTED);
            }
            return deployment;
        } catch (Exception ex) {
            if (ex instanceof cn.iocoder.yudao.framework.common.exception.ServiceException serviceException) {
                throw serviceException;
            }
            throw exception(DEPLOYMENT_MANIFEST_INVALID, StrUtil.subPre(ex.getMessage(), 512));
        }
    }

    public Deployment parseDeploymentForValidation(String manifestYaml) {
        return parseDeployment(manifestYaml);
    }

    public List<HasMetadata> parseSupportedResources(String manifestYaml) {
        if (StrUtil.isBlank(manifestYaml)) {
            throw exception(DEPLOYMENT_MANIFEST_REQUIRED);
        }
        List<String> documents = splitDocuments(manifestYaml);
        if (documents.isEmpty()) {
            throw exception(DEPLOYMENT_MANIFEST_REQUIRED);
        }
        List<HasMetadata> resources = new ArrayList<>(documents.size());
        for (String document : documents) {
            try {
                KubernetesResource resource = Serialization.unmarshal(document);
                if (!(resource instanceof HasMetadata metadata) || !isSupportedResource(metadata)) {
                    throw exception(DEPLOYMENT_MANIFEST_KIND_NOT_SUPPORTED);
                }
                resources.add(metadata);
            } catch (Exception ex) {
                if (ex instanceof cn.iocoder.yudao.framework.common.exception.ServiceException serviceException) {
                    throw serviceException;
                }
                throw exception(DEPLOYMENT_MANIFEST_INVALID, StrUtil.subPre(ex.getMessage(), 512));
            }
        }
        return resources;
    }

    public Deployment findSingleDeployment(List<HasMetadata> resources) {
        List<Deployment> deployments = resources.stream()
                .filter(Deployment.class::isInstance)
                .map(Deployment.class::cast)
                .toList();
        if (deployments.isEmpty()) {
            throw exception(DEPLOYMENT_NODE_PARAM_INVALID, "manifestYaml 必须包含一个 Deployment");
        }
        if (deployments.size() > 1) {
            throw exception(DEPLOYMENT_NODE_PARAM_INVALID, "manifestYaml 仅支持一个 Deployment");
        }
        return deployments.get(0);
    }

    public void validateDeployment(Deployment deployment, String containerName) {
        if (deployment.getMetadata() == null || StrUtil.isBlank(deployment.getMetadata().getName())) {
            throw exception(DEPLOYMENT_MANIFEST_NAME_REQUIRED);
        }
        findContainer(deployment, containerName);
    }

    public List<HasMetadata> prepareSupportedResources(ContainerDeployConfigContext config) {
        List<HasMetadata> resources = parseSupportedResources(config.getRenderedManifestYaml());
        Deployment deployment = findSingleDeployment(resources);
        validateDeployment(deployment, config.getContainerName());
        for (HasMetadata resource : resources) {
            if (resource.getMetadata() == null) {
                throw exception(DEPLOYMENT_MANIFEST_NAME_REQUIRED);
            }
            resource.getMetadata().setNamespace(config.getNamespace());
            if (resource instanceof Deployment resourceDeployment) {
                Container container = findContainer(resourceDeployment, config.getContainerName());
                container.setImage(config.getImage());
                if (config.getReplicas() != null) {
                    resourceDeployment.getSpec().setReplicas(config.getReplicas());
                }
            }
        }
        validateServices(resources, deployment);
        return resources;
    }

    public List<Service> findServices(List<HasMetadata> resources) {
        return resources.stream().filter(Service.class::isInstance).map(Service.class::cast).toList();
    }

    private void validateServices(List<HasMetadata> resources, Deployment deployment) {
        List<Service> services = findServices(resources);
        if (services.isEmpty()) {
            return;
        }
        int desiredReplicas = deployment.getSpec() == null || deployment.getSpec().getReplicas() == null
                ? 1 : deployment.getSpec().getReplicas();
        if (desiredReplicas == 0) {
            throw exception(DEPLOYMENT_NODE_PARAM_INVALID,
                    "manifestYaml 包含 Service 时 Deployment replicas 必须大于 0");
        }
        Map<String, String> podLabels = deployment.getSpec() == null
                || deployment.getSpec().getTemplate() == null
                || deployment.getSpec().getTemplate().getMetadata() == null
                ? null : deployment.getSpec().getTemplate().getMetadata().getLabels();
        for (Service service : services) {
            String serviceName = service.getMetadata() == null ? null : service.getMetadata().getName();
            if (StrUtil.isBlank(serviceName)) {
                throw exception(DEPLOYMENT_MANIFEST_NAME_REQUIRED);
            }
            if (service.getSpec() != null && "ExternalName".equals(service.getSpec().getType())) {
                throw exception(DEPLOYMENT_NODE_PARAM_INVALID,
                        "Service " + serviceName + " 类型 ExternalName 不支持 Pod 健康门禁");
            }
            Map<String, String> selector = service.getSpec() == null ? null : service.getSpec().getSelector();
            if (selector == null || selector.isEmpty()) {
                throw exception(DEPLOYMENT_NODE_PARAM_INVALID,
                        "Service " + serviceName + " 必须配置非空 selector");
            }
            if (podLabels == null || !selector.entrySet().stream()
                    .allMatch(entry -> Objects.equals(podLabels.get(entry.getKey()), entry.getValue()))) {
                throw exception(DEPLOYMENT_NODE_PARAM_INVALID,
                        "Service " + serviceName + " selector 与 Deployment Pod Template labels 不匹配");
            }
        }
    }

    public Deployment prepareDeployment(ContainerDeployConfigContext config) {
        Deployment deployment = parseDeployment(config.getRenderedManifestYaml());
        validateDeployment(deployment, config.getContainerName());
        if (deployment.getMetadata() == null) {
            throw exception(DEPLOYMENT_MANIFEST_NAME_REQUIRED);
        }
        deployment.getMetadata().setNamespace(config.getNamespace());
        Container container = findContainer(deployment, config.getContainerName());
        container.setImage(config.getImage());
        if (config.getReplicas() != null) {
            deployment.getSpec().setReplicas(config.getReplicas());
        }
        return deployment;
    }

    public Container findContainer(Deployment deployment, String containerName) {
        List<Container> containers = deployment.getSpec() == null
                || deployment.getSpec().getTemplate() == null
                || deployment.getSpec().getTemplate().getSpec() == null
                ? null : deployment.getSpec().getTemplate().getSpec().getContainers();
        if (containers == null || containers.isEmpty()) {
            throw exception(DEPLOYMENT_MANIFEST_CONTAINER_NOT_EXISTS, containerName);
        }
        return containers.stream()
                .filter(container -> containerName.equals(container.getName()))
                .findFirst()
                .orElseThrow(() -> exception(DEPLOYMENT_MANIFEST_CONTAINER_NOT_EXISTS, containerName));
    }

    public String readDeploymentName(Deployment deployment) {
        if (deployment.getMetadata() == null || StrUtil.isBlank(deployment.getMetadata().getName())) {
            throw exception(DEPLOYMENT_MANIFEST_NAME_REQUIRED);
        }
        return deployment.getMetadata().getName();
    }

    private boolean isSupportedResource(HasMetadata resource) {
        return resource instanceof Service || resource instanceof Deployment;
    }

    private List<String> splitDocuments(String manifestYaml) {
        String normalized = manifestYaml.replace("\r\n", "\n");
        List<String> documents = new ArrayList<>();
        StringBuilder current = new StringBuilder();
        for (String line : normalized.split("\n", -1)) {
            if (isDocumentSeparator(line.trim())) {
                addDocumentIfPresent(documents, current);
                current.setLength(0);
                continue;
            }
            current.append(line).append('\n');
        }
        addDocumentIfPresent(documents, current);
        return documents;
    }

    private void addDocumentIfPresent(List<String> documents, StringBuilder current) {
        if (hasDocumentContent(current)) {
            documents.add(current.toString());
        }
    }

    private boolean hasDocumentContent(StringBuilder document) {
        for (String line : document.toString().split("\n")) {
            String trimmed = line.trim();
            if (StrUtil.isNotBlank(trimmed) && !trimmed.startsWith("#")) {
                return true;
            }
        }
        return false;
    }

    private boolean containsMultipleDocuments(String manifestYaml) {
        String normalized = manifestYaml.replace("\r\n", "\n");
        boolean seenContent = false;
        boolean seenDocumentStart = false;
        for (String line : normalized.split("\n")) {
            String trimmed = line.trim();
            if (isDocumentSeparator(trimmed)) {
                if (!seenContent && !seenDocumentStart) {
                    seenDocumentStart = true;
                    continue;
                }
                return true;
            }
            if (StrUtil.isNotBlank(trimmed) && !trimmed.startsWith("#")) {
                seenContent = true;
            }
        }
        return false;
    }

    private boolean isDocumentSeparator(String trimmedLine) {
        return "---".equals(trimmedLine) || trimmedLine.startsWith("--- ");
    }

}
