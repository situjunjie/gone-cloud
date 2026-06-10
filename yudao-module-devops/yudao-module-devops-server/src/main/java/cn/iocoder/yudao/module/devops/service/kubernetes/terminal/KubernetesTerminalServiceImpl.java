package cn.iocoder.yudao.module.devops.service.kubernetes.terminal;

import cn.hutool.core.util.StrUtil;
import cn.iocoder.yudao.framework.common.util.json.JsonUtils;
import cn.iocoder.yudao.module.devops.dal.dataobject.environment.EnvironmentDO;
import cn.iocoder.yudao.module.devops.dal.mysql.environment.EnvironmentMapper;
import cn.iocoder.yudao.module.devops.enums.EnvironmentInfraTypeEnum;
import cn.iocoder.yudao.module.devops.framework.kubernetes.KubernetesClientFactory;
import cn.iocoder.yudao.module.devops.framework.kubernetes.KubernetesEnvironmentConfig;
import io.fabric8.kubernetes.api.model.Container;
import io.fabric8.kubernetes.api.model.Pod;
import io.fabric8.kubernetes.client.KubernetesClient;
import io.fabric8.kubernetes.client.KubernetesClientException;
import io.fabric8.kubernetes.client.dsl.ExecWatch;
import jakarta.annotation.Resource;
import org.springframework.stereotype.Service;
import org.springframework.validation.annotation.Validated;

import java.util.List;

import static cn.iocoder.yudao.framework.common.exception.util.ServiceExceptionUtil.exception;
import static cn.iocoder.yudao.module.devops.enums.ErrorCodeConstants.ENVIRONMENT_INFRA_TYPE_NOT_SUPPORTED;
import static cn.iocoder.yudao.module.devops.enums.ErrorCodeConstants.ENVIRONMENT_KUBECONFIG_REQUIRED;
import static cn.iocoder.yudao.module.devops.enums.ErrorCodeConstants.ENVIRONMENT_KUBERNETES_CONNECTION_FAIL;
import static cn.iocoder.yudao.module.devops.enums.ErrorCodeConstants.ENVIRONMENT_NOT_EXISTS;
import static cn.iocoder.yudao.module.devops.enums.ErrorCodeConstants.KUBERNETES_POD_CONTAINER_NOT_EXISTS;
import static cn.iocoder.yudao.module.devops.enums.ErrorCodeConstants.KUBERNETES_POD_CONTAINER_REQUIRED;
import static cn.iocoder.yudao.module.devops.enums.ErrorCodeConstants.KUBERNETES_POD_NOT_EXISTS;
import static cn.iocoder.yudao.module.devops.enums.ErrorCodeConstants.KUBERNETES_POD_NOT_RUNNING;
import static cn.iocoder.yudao.module.devops.enums.ErrorCodeConstants.KUBERNETES_TERMINAL_EXEC_FAIL;

/**
 * Kubernetes Pod 终端服务实现。
 */
@Service
@Validated
public class KubernetesTerminalServiceImpl implements KubernetesTerminalService {

    private static final String POD_PHASE_RUNNING = "Running";
    private static final String INTERACTIVE_SHELL_COMMAND = "if command -v bash >/dev/null 2>&1; then exec bash -il; "
            + "elif command -v ash >/dev/null 2>&1; then exec ash -i; else exec sh -i; fi";

    @Resource
    private EnvironmentMapper environmentMapper;
    @Resource
    private KubernetesClientFactory kubernetesClientFactory;

    @Override
    public KubernetesTerminalSession openTerminal(Long environmentId, String namespace, String podName, String containerName) {
        EnvironmentDO environment = validateEnvironment(environmentId);
        KubernetesClient client = createClient(environment);
        try {
            Pod pod = client.pods().inNamespace(namespace).withName(podName).get();
            validatePod(pod, podName);
            String resolvedContainerName = resolveContainerName(pod, containerName);
            ExecWatch execWatch = client.pods().inNamespace(namespace).withName(podName)
                    .inContainer(resolvedContainerName)
                    .redirectingInput()
                    .redirectingOutput()
                    .redirectingError()
                    .withTTY()
                    .exec("/bin/sh", "-c", INTERACTIVE_SHELL_COMMAND);
            return new KubernetesTerminalSession(client, execWatch);
        } catch (KubernetesClientException ex) {
            client.close();
            throw exception(KUBERNETES_TERMINAL_EXEC_FAIL, StrUtil.subPre(ex.getMessage(), 512));
        } catch (RuntimeException ex) {
            client.close();
            throw ex;
        }
    }

    private EnvironmentDO validateEnvironment(Long environmentId) {
        EnvironmentDO environment = environmentMapper.selectById(environmentId);
        if (environment == null) {
            throw exception(ENVIRONMENT_NOT_EXISTS);
        }
        if (!EnvironmentInfraTypeEnum.K8S.getInfraType().equals(environment.getInfraType())) {
            throw exception(ENVIRONMENT_INFRA_TYPE_NOT_SUPPORTED);
        }
        return environment;
    }

    private KubernetesClient createClient(EnvironmentDO environment) {
        KubernetesEnvironmentConfig config = JsonUtils.parseObject(environment.getInfraConfig(), KubernetesEnvironmentConfig.class);
        if (config == null || StrUtil.isBlank(config.getKubeconfig())) {
            throw exception(ENVIRONMENT_KUBECONFIG_REQUIRED);
        }
        try {
            return kubernetesClientFactory.create(config.getKubeconfig());
        } catch (KubernetesClientException ex) {
            throw exception(ENVIRONMENT_KUBERNETES_CONNECTION_FAIL, StrUtil.subPre(ex.getMessage(), 512));
        }
    }

    private void validatePod(Pod pod, String podName) {
        if (pod == null) {
            throw exception(KUBERNETES_POD_NOT_EXISTS, podName);
        }
        String phase = pod.getStatus() == null ? null : pod.getStatus().getPhase();
        if (!POD_PHASE_RUNNING.equals(phase)) {
            throw exception(KUBERNETES_POD_NOT_RUNNING, StrUtil.blankToDefault(phase, "Unknown"));
        }
    }

    private String resolveContainerName(Pod pod, String containerName) {
        List<Container> containers = pod.getSpec() == null ? null : pod.getSpec().getContainers();
        if (containers == null || containers.isEmpty()) {
            throw exception(KUBERNETES_POD_CONTAINER_NOT_EXISTS, containerName);
        }
        if (StrUtil.isBlank(containerName)) {
            if (containers.size() == 1) {
                return containers.get(0).getName();
            }
            throw exception(KUBERNETES_POD_CONTAINER_REQUIRED);
        }
        boolean exists = containers.stream().anyMatch(container -> containerName.equals(container.getName()));
        if (!exists) {
            throw exception(KUBERNETES_POD_CONTAINER_NOT_EXISTS, containerName);
        }
        return containerName;
    }

}
