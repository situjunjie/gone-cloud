package cn.iocoder.yudao.module.devops.framework.kubernetes;

import lombok.Data;

/**
 * Kubernetes 环境连接配置。
 */
@Data
public class KubernetesEnvironmentConfig {

    /**
     * kubeconfig 文件内容。
     */
    private String kubeconfig;

}
