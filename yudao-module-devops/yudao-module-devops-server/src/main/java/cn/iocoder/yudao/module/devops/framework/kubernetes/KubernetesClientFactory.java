package cn.iocoder.yudao.module.devops.framework.kubernetes;

import cn.hutool.core.util.StrUtil;
import io.fabric8.kubernetes.client.Config;
import io.fabric8.kubernetes.client.KubernetesClient;
import io.fabric8.kubernetes.client.KubernetesClientBuilder;
import org.springframework.stereotype.Component;

import static cn.iocoder.yudao.framework.common.exception.util.ServiceExceptionUtil.exception;
import static cn.iocoder.yudao.module.devops.enums.ErrorCodeConstants.ENVIRONMENT_KUBECONFIG_REQUIRED;
import static cn.iocoder.yudao.module.devops.enums.ErrorCodeConstants.ENVIRONMENT_KUBERNETES_CONFIG_INVALID;

/**
 * Kubernetes 客户端工厂。
 */
@Component
public class KubernetesClientFactory {

    public KubernetesClient create(String kubeconfig) {
        if (StrUtil.isBlank(kubeconfig)) {
            throw exception(ENVIRONMENT_KUBECONFIG_REQUIRED);
        }
        try {
            Config config = Config.fromKubeconfig(kubeconfig);
            return new KubernetesClientBuilder().withConfig(config).build();
        } catch (Exception ex) {
            throw exception(ENVIRONMENT_KUBERNETES_CONFIG_INVALID, StrUtil.subPre(ex.getMessage(), 512));
        }
    }

}
