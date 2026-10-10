package cn.iocoder.yudao.module.devops.framework.pipeline.runtime;

import cn.iocoder.yudao.framework.tenant.core.util.TenantUtils;
import cn.iocoder.yudao.module.devops.dal.dataobject.environment.EnvironmentDO;
import cn.iocoder.yudao.module.devops.framework.config.DevopsConfig;
import cn.iocoder.yudao.module.devops.framework.kubernetes.KubernetesClientFactory;
import cn.iocoder.yudao.module.devops.framework.kubernetes.KubernetesEnvironmentConfig;
import cn.iocoder.yudao.module.devops.service.environment.EnvironmentService;
import io.fabric8.kubernetes.api.model.Pod;
import io.fabric8.kubernetes.api.model.PersistentVolumeClaim;
import io.fabric8.kubernetes.client.KubernetesClient;
import jakarta.annotation.Resource;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.Map;

/**
 * K8s 构建 Pod 清理任务。
 *
 * <p>定期清理带有清理标记的 Pod（通常是失败的 Pod 用于调试保留一段时间后清理）。
 * <p>正常完成的 Pod 会在任务结束时立即删除，无需定时清理。
 */
@Slf4j
@Component
public class K8sBuildPodCleanupJob {

    @Resource
    private KubernetesClientFactory kubernetesClientFactory;

    @Resource
    private EnvironmentService environmentService;

    @Resource
    private DevopsConfig devopsConfig;

    /**
     * 每15分钟执行一次清理任务。
     */
    @Scheduled(cron = "0 */15 * * * ?")
    public void cleanupMarkedPods() {
        // 调度线程没有租户上下文，而构建集群是全局配置，环境查询必须忽略租户
        TenantUtils.executeIgnore(this::doCleanupMarkedPods);
    }

    private void doCleanupMarkedPods() {
        Long environmentId = devopsConfig.getBuild().getCluster().getEnvironmentId();
        if (environmentId == null) {
            log.debug("[cleanupMarkedPods] 构建集群未配置，跳过清理");
            return;
        }

        try {
            EnvironmentDO environment = environmentService.getEnvironment(environmentId);
            if (environment == null) {
                log.warn("[cleanupMarkedPods] 构建集群环境不存在: {}", environmentId);
                return;
            }

            KubernetesEnvironmentConfig envConfig = parseK8sConfig(environment);
            String namespace = devopsConfig.getBuild().getCluster().getNamespace();

            cleanupPods(envConfig, namespace);

        } catch (Exception ex) {
            log.error("[cleanupMarkedPods] 清理标记 Pod 失败", ex);
        }
    }

    /**
     * 清理带有清理标记的 Pod。
     */
    private void cleanupPods(KubernetesEnvironmentConfig envConfig, String namespace) {
        try (KubernetesClient client = kubernetesClientFactory.create(envConfig.getKubeconfig())) {
            // 获取所有构建 Pod
            List<Pod> pods = client.pods()
                    .inNamespace(namespace)
                    .withLabel("app", "pipeline-build")
                    .list()
                    .getItems();

            log.info("[cleanupPods][namespace({}) 检查标记 Pod，当前数量: {}]", namespace, pods.size());

            long now = System.currentTimeMillis();
            int deletedCount = 0;
            int skippedCount = 0;

            for (Pod pod : pods) {
                try {
                    if (shouldDeletePod(pod, now)) {
                        String podName = pod.getMetadata().getName();
                        log.info("[cleanupPods][namespace({}) 删除已到期的 Pod: {}]", namespace, podName);

                        client.pods()
                                .inNamespace(namespace)
                                .withName(podName)
                                .delete();

                        deletedCount++;
                    } else {
                        skippedCount++;
                    }
                } catch (Exception ex) {
                    log.error("[cleanupPods][namespace({}) 删除 Pod 失败: {}]",
                            namespace, pod.getMetadata().getName(), ex);
                }
            }

            if (deletedCount > 0) {
                log.info("[cleanupPods][namespace({}) 清理完成，删除: {}, 保留: {}]",
                        namespace, deletedCount, skippedCount);
            }
            cleanupWorkspaceClaims(client, namespace, now);

        } catch (Exception ex) {
            log.error("[cleanupPods][namespace({}) 清理 Pod 失败]", namespace, ex);
            throw ex;
        }
    }

    private void cleanupWorkspaceClaims(KubernetesClient client, String namespace, long now) {
        List<PersistentVolumeClaim> claims = client.persistentVolumeClaims().inNamespace(namespace)
                .withLabel("app", "pipeline-build").list().getItems();
        for (PersistentVolumeClaim claim : claims) {
            if (claim.getMetadata() == null || claim.getMetadata().getLabels() == null) {
                continue;
            }
            String runId = claim.getMetadata().getLabels().get("pipeline-run-id");
            if (!K8sPipelineJobRuntimeManager.ownsDynamicWorkspace(claim, runId)
                    || !cleanupDue(claim.getMetadata().getAnnotations(), now)) {
                continue;
            }
            try {
                // Listing after Pod deletion also covers terminating Pods and retained failures.
                if (client.pods().inNamespace(namespace).withLabel("app", "pipeline-build")
                        .withLabel("pipeline-run-id", runId).list().getItems().isEmpty()) {
                    client.persistentVolumeClaims().inNamespace(namespace)
                            .withName(claim.getMetadata().getName()).delete();
                }
            } catch (RuntimeException ex) {
                log.warn("[cleanupWorkspaceClaims][runId({}) PVC cleanup failed]", runId);
            }
        }
    }

    private boolean cleanupDue(Map<String, String> annotations, long now) {
        if (annotations == null || !annotations.containsKey("devops.cleanup-after")) {
            return false;
        }
        try {
            return Long.parseLong(annotations.get("devops.cleanup-after")) <= now;
        } catch (NumberFormatException ignored) {
            return false;
        }
    }

    /**
     * 判断 Pod 是否应该被删除。
     */
    private boolean shouldDeletePod(Pod pod, long now) {
        if (pod == null || pod.getMetadata() == null) {
            return false;
        }
        // Never reap a live build unless the runtime explicitly supplied a deadline.
        // Suspended/blocked Pods intentionally stay Running until the run reaches a terminal state.
        Map<String, String> annotations = pod.getMetadata().getAnnotations();
        if (annotations == null) {
            return false;
        }

        // 检查是否有清理标记
        String cleanupAfter = annotations.get("devops.cleanup-after");
        if (cleanupAfter == null) {
            return false;
        }

        try {
            long cleanupTime = Long.parseLong(cleanupAfter);
            if (now >= cleanupTime) {
                log.debug("[shouldDeletePod] Pod 已到清理时间: {}, scheduled: {}, now: {}",
                        pod.getMetadata().getName(), cleanupTime, now);
                return true;
            }
        } catch (NumberFormatException ex) {
            log.warn("[shouldDeletePod] Pod 清理时间格式错误: {}", pod.getMetadata().getName());
        }

        return false;
    }

    /**
     * 解析 K8s 环境配置。
     */
    private KubernetesEnvironmentConfig parseK8sConfig(EnvironmentDO environment) {
        return cn.iocoder.yudao.framework.common.util.json.JsonUtils.parseObject(
                environment.getInfraConfig(),
                KubernetesEnvironmentConfig.class
        );
    }

}
