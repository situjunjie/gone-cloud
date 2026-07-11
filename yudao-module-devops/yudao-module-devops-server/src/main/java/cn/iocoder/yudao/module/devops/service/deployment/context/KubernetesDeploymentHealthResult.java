package cn.iocoder.yudao.module.devops.service.deployment.context;

import lombok.AllArgsConstructor;
import lombok.Data;

import java.util.Map;

/**
 * Kubernetes Deployment 本次发布健康门禁结果。
 */
@Data
@AllArgsConstructor
public class KubernetesDeploymentHealthResult {

    private boolean ready;
    private boolean terminal;
    private String reason;
    private String message;
    private Map<String, Object> snapshot;

}
