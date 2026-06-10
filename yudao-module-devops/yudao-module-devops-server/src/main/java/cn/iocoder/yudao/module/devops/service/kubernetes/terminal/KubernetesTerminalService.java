package cn.iocoder.yudao.module.devops.service.kubernetes.terminal;

/**
 * Kubernetes Pod 终端服务。
 */
public interface KubernetesTerminalService {

    KubernetesTerminalSession openTerminal(Long environmentId, String namespace, String podName, String containerName);

}
