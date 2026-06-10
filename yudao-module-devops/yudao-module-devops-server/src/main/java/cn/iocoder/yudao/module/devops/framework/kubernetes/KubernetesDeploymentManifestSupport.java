package cn.iocoder.yudao.module.devops.framework.kubernetes;

import cn.hutool.core.util.StrUtil;
import cn.iocoder.yudao.module.devops.service.deployment.context.ContainerDeployConfigContext;
import io.fabric8.kubernetes.api.model.Container;
import io.fabric8.kubernetes.api.model.KubernetesResource;
import io.fabric8.kubernetes.api.model.apps.Deployment;
import io.fabric8.kubernetes.client.utils.Serialization;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.Map;

import static cn.iocoder.yudao.framework.common.exception.util.ServiceExceptionUtil.exception;
import static cn.iocoder.yudao.module.devops.enums.ErrorCodeConstants.DEPLOYMENT_MANIFEST_CONTAINER_NOT_EXISTS;
import static cn.iocoder.yudao.module.devops.enums.ErrorCodeConstants.DEPLOYMENT_MANIFEST_INVALID;
import static cn.iocoder.yudao.module.devops.enums.ErrorCodeConstants.DEPLOYMENT_MANIFEST_KIND_NOT_SUPPORTED;
import static cn.iocoder.yudao.module.devops.enums.ErrorCodeConstants.DEPLOYMENT_MANIFEST_NAME_REQUIRED;
import static cn.iocoder.yudao.module.devops.enums.ErrorCodeConstants.DEPLOYMENT_MANIFEST_REQUIRED;

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

    public void validateDeployment(Deployment deployment, String containerName) {
        if (deployment.getMetadata() == null || StrUtil.isBlank(deployment.getMetadata().getName())) {
            throw exception(DEPLOYMENT_MANIFEST_NAME_REQUIRED);
        }
        findContainer(deployment, containerName);
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
