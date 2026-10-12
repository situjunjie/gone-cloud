package cn.iocoder.yudao.module.devops.service.deployment;

import cn.hutool.core.util.StrUtil;
import cn.iocoder.yudao.framework.common.util.json.JsonUtils;
import cn.iocoder.yudao.module.devops.dal.dataobject.deployment.DeploymentOrderDO;
import cn.iocoder.yudao.module.devops.enums.DeploymentModeEnum;
import cn.iocoder.yudao.module.devops.service.deployment.context.ContainerDeployConfigContext;
import cn.iocoder.yudao.module.devops.service.deployment.context.DeploymentOrderResultContext;
import cn.iocoder.yudao.module.devops.service.deployment.context.KubernetesDeploymentHealthResult;
import io.fabric8.kubernetes.api.model.Container;
import io.fabric8.kubernetes.api.model.ContainerState;
import io.fabric8.kubernetes.api.model.ContainerStateRunning;
import io.fabric8.kubernetes.api.model.ContainerStateTerminated;
import io.fabric8.kubernetes.api.model.ContainerStateWaiting;
import io.fabric8.kubernetes.api.model.ContainerStatus;
import io.fabric8.kubernetes.api.model.Event;
import io.fabric8.kubernetes.api.model.EndpointAddress;
import io.fabric8.kubernetes.api.model.EndpointSubset;
import io.fabric8.kubernetes.api.model.Endpoints;
import io.fabric8.kubernetes.api.model.OwnerReference;
import io.fabric8.kubernetes.api.model.Pod;
import io.fabric8.kubernetes.api.model.PodCondition;
import io.fabric8.kubernetes.api.model.Service;
import io.fabric8.kubernetes.api.model.ServiceBuilder;
import io.fabric8.kubernetes.api.model.apps.Deployment;
import io.fabric8.kubernetes.api.model.apps.DeploymentCondition;
import io.fabric8.kubernetes.api.model.apps.ReplicaSet;
import io.fabric8.kubernetes.client.KubernetesClient;
import org.springframework.stereotype.Component;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;

/**
 * 收集某个部署单对应的 Kubernetes 实时状态。中间过程不持久化，部署结束时可将该快照写入部署单结果。
 */
@Component
public class KubernetesDeploymentStatusCollector {

    public static final String DEPLOYMENT_ORDER_ID_KEY = "gone.devops/deployment-order-id";
    public static final String PIPELINE_RUN_ID_KEY = "gone.devops/pipeline-run-id";
    public static final String DEPLOYMENT_REVISION_ANNOTATION = "deployment.kubernetes.io/revision";

    private static final int MAX_PODS = 50;
    private static final int MAX_EVENTS = 100;
    private static final int MAX_MESSAGE_LENGTH = 1000;

    public Map<String, Object> collect(KubernetesClient client, DeploymentOrderDO order) {
        Deployment deployment = client.apps().deployments().inNamespace(order.getNamespace())
                .withName(order.getWorkloadName()).get();
        return collect(client, order, deployment);
    }

    public Map<String, Object> collect(KubernetesClient client, DeploymentOrderDO order, Deployment deployment) {
        return collect(client, order, deployment, resolveAppliedServices(client, order));
    }

    public KubernetesDeploymentHealthResult evaluateHealth(KubernetesClient client, DeploymentOrderDO order,
                                                            Deployment deployment, List<Service> services) {
        Map<String, Object> snapshot = collect(client, order, deployment, services);
        Map<?, ?> health = snapshot.get("health") instanceof Map<?, ?> value ? value : Collections.emptyMap();
        return new KubernetesDeploymentHealthResult(Boolean.TRUE.equals(health.get("ready")),
                Boolean.TRUE.equals(health.get("terminal")), String.valueOf(health.get("reason")),
                String.valueOf(health.get("message")), snapshot);
    }

    private Map<String, Object> collect(KubernetesClient client, DeploymentOrderDO order, Deployment deployment,
                                        List<Service> services) {
        Map<String, Object> status = new LinkedHashMap<>();
        status.put("observedAt", LocalDateTime.now());
        status.put("available", deployment != null);
        if (deployment == null) {
            status.put("ready", false);
            status.put("message", "Deployment 不存在");
            status.put("pods", Collections.emptyList());
            status.put("services", Collections.emptyList());
            status.put("health", health(false, false, "DEPLOYMENT_NOT_FOUND", "Deployment 不存在"));
            status.put("events", Collections.emptyList());
            return status;
        }
        if (isWorkloadReplaced(order, deployment)) {
            status.put("ready", false);
            status.put("correlationMode", "WORKLOAD_UID_MISMATCH");
            status.put("message", "同名 Deployment 已被替换，当前资源不属于本部署单");
            status.put("deployment", buildDeploymentSummary(deployment));
            status.put("pods", Collections.emptyList());
            status.put("services", Collections.emptyList());
            status.put("health", health(false, true, "WORKLOAD_UID_MISMATCH",
                    "同名 Deployment 已被替换，当前资源不属于本部署单"));
            status.put("events", Collections.emptyList());
            return status;
        }

        Map<String, Object> deploymentSummary = buildDeploymentSummary(deployment);
        status.putAll(deploymentSummary); // 兼容原 liveStatus 顶层 rollout 字段
        status.put("deployment", deploymentSummary);
        status.put("ready", isRolloutReady(deployment));

        boolean hasOrderLabel = hasDeploymentOrderLabel(deployment, order.getId());
        if (!hasOrderLabel && usesStructuredResult(order)) {
            String currentOrderId = readDeploymentOrderId(deployment);
            boolean superseded = StrUtil.isNotBlank(currentOrderId)
                    && !String.valueOf(order.getId()).equals(currentOrderId);
            status.put("correlationMode", superseded ? "SUPERSEDED" : "DEPLOYMENT_NOT_APPLIED");
            status.put("message", superseded ? "Deployment 已由后续部署单更新，请查看本部署单的最终快照"
                    : "本部署单尚未成功提交 Deployment，未关联现有 Pod");
            status.put("replicaSet", Collections.emptyMap());
            status.put("pods", Collections.emptyList());
            status.put("services", Collections.emptyList());
            status.put("health", health(false, superseded, superseded ? "DEPLOYMENT_SUPERSEDED"
                    : "DEPLOYMENT_NOT_APPLIED", String.valueOf(status.get("message"))));
            status.put("events", Collections.emptyList());
            return status;
        }
        List<ReplicaSet> replicaSets = listOwnedReplicaSets(client, order, deployment);
        ReplicaSet targetReplicaSet = selectTargetReplicaSet(replicaSets, order.getTargetRevision(), order.getId(),
                hasOrderLabel);
        status.put("replicaSet", buildReplicaSetSummary(targetReplicaSet));

        List<Pod> pods = listDeploymentPods(client, order, deployment, targetReplicaSet, hasOrderLabel);
        status.put("correlationMode", hasOrderLabel ? "DEPLOYMENT_ORDER_LABEL" : "LEGACY_SELECTOR");
        status.put("pods", pods.stream().limit(MAX_PODS).map(pod -> buildPodSummary(order, pod)).toList());

        Set<String> involvedUids = collectInvolvedUids(deployment, targetReplicaSet, pods);
        List<Map<String, Object>> events = buildEventSummaries(client, order, involvedUids);
        status.put("events", events);
        evaluateHealthGate(client, order, deployment, targetReplicaSet, pods, services, status);
        Map<?, ?> health = status.get("health") instanceof Map<?, ?> value ? value : Collections.emptyMap();
        if (!Boolean.TRUE.equals(health.get("ready"))) {
            Map<String, Object> diagnosis = diagnoseIssue(pods, events, deployment);
            if (diagnosis != null) {
                status.put("diagnosis", diagnosis);
                if (Boolean.TRUE.equals(health.get("terminal"))) {
                    status.put("failure", diagnosis);
                }
            }
        }
        return status;
    }

    private boolean isWorkloadReplaced(DeploymentOrderDO order, Deployment deployment) {
        return StrUtil.isNotBlank(order.getWorkloadUid()) && deployment.getMetadata() != null
                && StrUtil.isNotBlank(deployment.getMetadata().getUid())
                && !order.getWorkloadUid().equals(deployment.getMetadata().getUid());
    }

    private Map<String, Object> buildDeploymentSummary(Deployment deployment) {
        Map<String, Object> summary = new LinkedHashMap<>();
        summary.put("name", deployment.getMetadata() == null ? null : deployment.getMetadata().getName());
        summary.put("uid", deployment.getMetadata() == null ? null : deployment.getMetadata().getUid());
        summary.put("generation", deployment.getMetadata() == null ? null : deployment.getMetadata().getGeneration());
        summary.put("observedGeneration", deployment.getStatus() == null ? null : deployment.getStatus().getObservedGeneration());
        summary.put("desiredReplicas", deployment.getSpec() == null ? null : deployment.getSpec().getReplicas());
        summary.put("replicas", deployment.getStatus() == null ? null : deployment.getStatus().getReplicas());
        summary.put("updatedReplicas", deployment.getStatus() == null ? null : deployment.getStatus().getUpdatedReplicas());
        summary.put("availableReplicas", deployment.getStatus() == null ? null : deployment.getStatus().getAvailableReplicas());
        summary.put("readyReplicas", deployment.getStatus() == null ? null : deployment.getStatus().getReadyReplicas());
        summary.put("unavailableReplicas", deployment.getStatus() == null ? null : deployment.getStatus().getUnavailableReplicas());
        summary.put("revision", readRevision(deployment));
        summary.put("conditions", buildDeploymentConditions(deployment.getStatus() == null
                ? null : deployment.getStatus().getConditions()));
        return summary;
    }

    private List<Map<String, Object>> buildDeploymentConditions(List<DeploymentCondition> conditions) {
        if (conditions == null) {
            return Collections.emptyList();
        }
        return conditions.stream().map(condition -> {
            Map<String, Object> item = new LinkedHashMap<>();
            item.put("type", condition.getType());
            item.put("status", condition.getStatus());
            item.put("reason", condition.getReason());
            item.put("message", truncate(condition.getMessage()));
            item.put("lastUpdateTime", condition.getLastUpdateTime());
            item.put("lastTransitionTime", condition.getLastTransitionTime());
            return item;
        }).toList();
    }

    private boolean hasDeploymentOrderLabel(Deployment deployment, Long orderId) {
        Map<String, String> labels = deployment.getSpec() == null || deployment.getSpec().getTemplate() == null
                || deployment.getSpec().getTemplate().getMetadata() == null
                ? null : deployment.getSpec().getTemplate().getMetadata().getLabels();
        return labels != null && Objects.equals(labels.get(DEPLOYMENT_ORDER_ID_KEY), String.valueOf(orderId));
    }

    private String readDeploymentOrderId(Deployment deployment) {
        return deployment.getMetadata() == null || deployment.getMetadata().getAnnotations() == null ? null
                : deployment.getMetadata().getAnnotations().get(DEPLOYMENT_ORDER_ID_KEY);
    }

    private boolean usesStructuredResult(DeploymentOrderDO order) {
        if (StrUtil.isBlank(order.getResultJson())) {
            return false;
        }
        try {
            return DeploymentOrderResultContext.SCHEMA_VERSION.equals(
                    JsonUtils.parseMap(order.getResultJson()).get("schemaVersion"));
        } catch (Exception ignored) {
            return false;
        }
    }

    private List<ReplicaSet> listOwnedReplicaSets(KubernetesClient client, DeploymentOrderDO order,
                                                   Deployment deployment) {
        List<ReplicaSet> items;
        try {
            items = client.apps().replicaSets().inNamespace(order.getNamespace()).list().getItems();
        } catch (Exception ignored) {
            return Collections.emptyList();
        }
        if (items == null || deployment.getMetadata() == null) {
            return Collections.emptyList();
        }
        String deploymentUid = deployment.getMetadata().getUid();
        return items.stream().filter(replicaSet -> isOwnedBy(replicaSet.getMetadata() == null
                ? null : replicaSet.getMetadata().getOwnerReferences(), deploymentUid, "Deployment")).toList();
    }

    private ReplicaSet selectTargetReplicaSet(List<ReplicaSet> replicaSets, String targetRevision, Long orderId,
                                              boolean requireOrderLabel) {
        List<ReplicaSet> targetReplicaSets = requireOrderLabel ? replicaSets.stream()
                .filter(item -> hasReplicaSetOrderLabel(item, orderId)).toList() : replicaSets;
        Comparator<ReplicaSet> byCreationTimestamp = Comparator.comparing(this::readCreationTimestamp,
                Comparator.nullsFirst(Comparator.naturalOrder()));
        if (StrUtil.isNotBlank(targetRevision)) {
            ReplicaSet target = targetReplicaSets.stream().filter(item -> targetRevision.equals(readRevision(item)))
                    .max(byCreationTimestamp).orElse(null);
            if (target != null) {
                return target;
            }
        }
        return targetReplicaSets.stream().max(byCreationTimestamp).orElse(null);
    }

    private boolean hasReplicaSetOrderLabel(ReplicaSet replicaSet, Long orderId) {
        Map<String, String> metadataLabels = replicaSet.getMetadata() == null
                ? null : replicaSet.getMetadata().getLabels();
        Map<String, String> templateLabels = replicaSet.getSpec() == null
                || replicaSet.getSpec().getTemplate() == null
                || replicaSet.getSpec().getTemplate().getMetadata() == null
                ? null : replicaSet.getSpec().getTemplate().getMetadata().getLabels();
        String expected = String.valueOf(orderId);
        return metadataLabels != null && Objects.equals(expected, metadataLabels.get(DEPLOYMENT_ORDER_ID_KEY))
                || templateLabels != null && Objects.equals(expected, templateLabels.get(DEPLOYMENT_ORDER_ID_KEY));
    }

    private String readCreationTimestamp(ReplicaSet replicaSet) {
        return replicaSet.getMetadata() == null ? null : replicaSet.getMetadata().getCreationTimestamp();
    }

    private Map<String, Object> buildReplicaSetSummary(ReplicaSet replicaSet) {
        if (replicaSet == null) {
            return Collections.emptyMap();
        }
        Map<String, Object> summary = new LinkedHashMap<>();
        summary.put("name", replicaSet.getMetadata() == null ? null : replicaSet.getMetadata().getName());
        summary.put("uid", replicaSet.getMetadata() == null ? null : replicaSet.getMetadata().getUid());
        summary.put("revision", readRevision(replicaSet));
        summary.put("replicas", replicaSet.getStatus() == null ? null : replicaSet.getStatus().getReplicas());
        summary.put("readyReplicas", replicaSet.getStatus() == null ? null : replicaSet.getStatus().getReadyReplicas());
        summary.put("availableReplicas", replicaSet.getStatus() == null ? null : replicaSet.getStatus().getAvailableReplicas());
        summary.put("fullyLabeledReplicas", replicaSet.getStatus() == null ? null : replicaSet.getStatus().getFullyLabeledReplicas());
        summary.put("conditions", replicaSet.getStatus() == null ? Collections.emptyList()
                : replicaSet.getStatus().getConditions());
        return summary;
    }

    private List<Pod> listDeploymentPods(KubernetesClient client, DeploymentOrderDO order, Deployment deployment,
                                         ReplicaSet targetReplicaSet, boolean hasOrderLabel) {
        List<Pod> items;
        if (hasOrderLabel) {
            items = client.pods().inNamespace(order.getNamespace())
                    .withLabels(Map.of(DEPLOYMENT_ORDER_ID_KEY, String.valueOf(order.getId()))).list().getItems();
        } else {
            Map<String, String> matchLabels = deployment.getSpec() == null || deployment.getSpec().getSelector() == null
                    ? null : deployment.getSpec().getSelector().getMatchLabels();
            if (matchLabels == null || matchLabels.isEmpty()) {
                return Collections.emptyList();
            }
            items = client.pods().inNamespace(order.getNamespace()).withLabels(matchLabels).list().getItems();
        }
        if (items == null) {
            return Collections.emptyList();
        }
        String replicaSetUid = targetReplicaSet == null || targetReplicaSet.getMetadata() == null
                ? null : targetReplicaSet.getMetadata().getUid();
        return items.stream()
                .filter(pod -> replicaSetUid == null || isOwnedBy(pod.getMetadata() == null
                        ? null : pod.getMetadata().getOwnerReferences(), replicaSetUid, "ReplicaSet"))
                .sorted(Comparator.comparing(pod -> pod.getMetadata() == null ? "" : pod.getMetadata().getName()))
                .limit(MAX_PODS)
                .toList();
    }

    private boolean isOwnedBy(List<OwnerReference> ownerReferences, String ownerUid, String ownerKind) {
        return StrUtil.isNotBlank(ownerUid) && ownerReferences != null && ownerReferences.stream()
                .anyMatch(owner -> ownerUid.equals(owner.getUid()) && ownerKind.equals(owner.getKind()));
    }

    private Map<String, Object> buildPodSummary(DeploymentOrderDO order, Pod pod) {
        Map<String, Object> summary = new LinkedHashMap<>();
        summary.put("name", pod.getMetadata() == null ? null : pod.getMetadata().getName());
        summary.put("uid", pod.getMetadata() == null ? null : pod.getMetadata().getUid());
        summary.put("namespace", pod.getMetadata() == null ? null : pod.getMetadata().getNamespace());
        summary.put("creationTimestamp", pod.getMetadata() == null ? null : pod.getMetadata().getCreationTimestamp());
        summary.put("phase", pod.getStatus() == null ? null : pod.getStatus().getPhase());
        summary.put("reason", pod.getStatus() == null ? null : pod.getStatus().getReason());
        summary.put("message", pod.getStatus() == null ? null : truncate(pod.getStatus().getMessage()));
        summary.put("ready", isPodReady(pod));
        summary.put("readyContainers", countReadyContainers(pod));
        summary.put("totalContainers", countTotalContainers(pod));
        summary.put("restartCount", countRestartCount(pod));
        summary.put("podIp", pod.getStatus() == null ? null : pod.getStatus().getPodIP());
        summary.put("hostIp", pod.getStatus() == null ? null : pod.getStatus().getHostIP());
        summary.put("nodeName", pod.getSpec() == null ? null : pod.getSpec().getNodeName());
        summary.put("startTime", pod.getStatus() == null ? null : pod.getStatus().getStartTime());
        summary.put("conditions", buildPodConditions(pod.getStatus() == null ? null : pod.getStatus().getConditions()));
        List<Map<String, Object>> initContainers = buildContainerSummaries(pod.getStatus() == null
                ? null : pod.getStatus().getInitContainerStatuses());
        List<Map<String, Object>> containers = buildContainerSummaries(pod.getStatus() == null
                ? null : pod.getStatus().getContainerStatuses());
        summary.put("initContainers", initContainers);
        summary.put("containers", containers);
        summary.put("targetContainer", containers.stream()
                .filter(item -> order.getContainerName().equals(item.get("name"))).findFirst().orElse(Collections.emptyMap()));
        return summary;
    }

    private List<Map<String, Object>> buildPodConditions(List<PodCondition> conditions) {
        if (conditions == null) {
            return Collections.emptyList();
        }
        return conditions.stream().map(condition -> {
            Map<String, Object> item = new LinkedHashMap<>();
            item.put("type", condition.getType());
            item.put("status", condition.getStatus());
            item.put("reason", condition.getReason());
            item.put("message", truncate(condition.getMessage()));
            item.put("lastProbeTime", condition.getLastProbeTime());
            item.put("lastTransitionTime", condition.getLastTransitionTime());
            return item;
        }).toList();
    }

    private List<Map<String, Object>> buildContainerSummaries(List<ContainerStatus> statuses) {
        if (statuses == null) {
            return Collections.emptyList();
        }
        return statuses.stream().map(this::buildContainerSummary).toList();
    }

    private Map<String, Object> buildContainerSummary(ContainerStatus status) {
        Map<String, Object> summary = new LinkedHashMap<>();
        summary.put("name", status.getName());
        summary.put("image", status.getImage());
        summary.put("imageId", status.getImageID());
        summary.put("containerId", status.getContainerID());
        summary.put("ready", status.getReady());
        summary.put("started", status.getStarted());
        summary.put("restartCount", status.getRestartCount());
        summary.put("state", resolveContainerStateType(status.getState()));
        summary.put("stateDetail", buildContainerStateSummary(status.getState()));
        summary.put("lastState", buildContainerStateSummary(status.getLastState()));
        String stateType = resolveContainerStateType(status.getState());
        summary.put("stateType", stateType);
        summary.put("reason", resolveContainerStateReason(status.getState()));
        summary.put("message", resolveContainerStateMessage(status.getState()));
        return summary;
    }

    private Map<String, Object> buildContainerStateSummary(ContainerState state) {
        if (state == null) {
            return Collections.emptyMap();
        }
        Map<String, Object> summary = new LinkedHashMap<>();
        ContainerStateWaiting waiting = state.getWaiting();
        ContainerStateRunning running = state.getRunning();
        ContainerStateTerminated terminated = state.getTerminated();
        if (waiting != null) {
            summary.put("type", "WAITING");
            summary.put("reason", waiting.getReason());
            summary.put("message", truncate(waiting.getMessage()));
        } else if (running != null) {
            summary.put("type", "RUNNING");
            summary.put("startedAt", running.getStartedAt());
        } else if (terminated != null) {
            summary.put("type", "TERMINATED");
            summary.put("reason", terminated.getReason());
            summary.put("message", truncate(terminated.getMessage()));
            summary.put("exitCode", terminated.getExitCode());
            summary.put("signal", terminated.getSignal());
            summary.put("startedAt", terminated.getStartedAt());
            summary.put("finishedAt", terminated.getFinishedAt());
            summary.put("containerId", terminated.getContainerID());
        }
        return summary;
    }

    private String resolveContainerStateType(ContainerState state) {
        Map<String, Object> summary = buildContainerStateSummary(state);
        Object type = summary.get("type");
        return type == null ? null : String.valueOf(type);
    }

    private String resolveContainerStateReason(ContainerState state) {
        Map<String, Object> summary = buildContainerStateSummary(state);
        Object reason = summary.get("reason");
        return reason == null ? null : String.valueOf(reason);
    }

    private String resolveContainerStateMessage(ContainerState state) {
        Map<String, Object> summary = buildContainerStateSummary(state);
        Object message = summary.get("message");
        return message == null ? null : String.valueOf(message);
    }

    private boolean isPodReady(Pod pod) {
        return pod.getStatus() != null && pod.getStatus().getConditions() != null
                && pod.getStatus().getConditions().stream().anyMatch(condition -> "Ready".equals(condition.getType())
                && "True".equals(condition.getStatus()));
    }

    private int countReadyContainers(Pod pod) {
        return pod.getStatus() == null || pod.getStatus().getContainerStatuses() == null ? 0
                : (int) pod.getStatus().getContainerStatuses().stream()
                .filter(status -> Boolean.TRUE.equals(status.getReady())).count();
    }

    private int countTotalContainers(Pod pod) {
        return pod.getSpec() != null && pod.getSpec().getContainers() != null ? pod.getSpec().getContainers().size()
                : pod.getStatus() != null && pod.getStatus().getContainerStatuses() != null
                ? pod.getStatus().getContainerStatuses().size() : 0;
    }

    private int countRestartCount(Pod pod) {
        if (pod.getStatus() == null) {
            return 0;
        }
        List<ContainerStatus> statuses = new ArrayList<>();
        if (pod.getStatus().getInitContainerStatuses() != null) {
            statuses.addAll(pod.getStatus().getInitContainerStatuses());
        }
        if (pod.getStatus().getContainerStatuses() != null) {
            statuses.addAll(pod.getStatus().getContainerStatuses());
        }
        return statuses.stream().map(ContainerStatus::getRestartCount).filter(Objects::nonNull)
                .mapToInt(Integer::intValue).sum();
    }

    private void evaluateHealthGate(KubernetesClient client, DeploymentOrderDO order, Deployment deployment,
                                    ReplicaSet replicaSet, List<Pod> pods, List<Service> services,
                                    Map<String, Object> snapshot) {
        int desired = deployment.getSpec() == null || deployment.getSpec().getReplicas() == null
                ? 1 : deployment.getSpec().getReplicas();
        List<Pod> activePods = pods.stream().filter(this::isNonTerminating).toList();
        List<Map<String, Object>> serviceSummaries = buildServiceSummaries(client, order, services, activePods);
        if (hasProgressDeadlineExceeded(deployment)) {
            setHealth(snapshot, false, true, "PROGRESS_DEADLINE_EXCEEDED",
                    "Deployment 已超过 progress deadline", serviceSummaries);
            return;
        }
        if (!isRolloutReady(deployment)) {
            setHealth(snapshot, false, false, "DEPLOYMENT_ROLLOUT_NOT_READY",
                    "Deployment rollout 尚未完成", serviceSummaries);
            return;
        }
        if (replicaSet == null) {
            setHealth(snapshot, false, false, "TARGET_REPLICA_SET_NOT_FOUND",
                    "本部署单对应的目标 ReplicaSet 尚未创建", serviceSummaries);
            return;
        }
        Integer replicaSetDesired = replicaSet.getSpec() == null ? null : replicaSet.getSpec().getReplicas();
        Integer replicaSetReady = replicaSet.getStatus() == null ? null : replicaSet.getStatus().getReadyReplicas();
        if (!Objects.equals(replicaSetDesired, desired) || !Objects.equals(replicaSetReady, desired)) {
            setHealth(snapshot, false, false, "TARGET_REPLICA_SET_NOT_READY",
                    "目标 ReplicaSet 副本尚未全部就绪", serviceSummaries);
            return;
        }
        if (activePods.size() != desired) {
            setHealth(snapshot, false, false, "TARGET_POD_COUNT_NOT_READY",
                    "目标 Pod 数量 " + activePods.size() + "/" + desired, serviceSummaries);
            return;
        }
        for (Pod pod : activePods) {
            String podName = pod.getMetadata() == null ? "未知 Pod" : pod.getMetadata().getName();
            if (pod.getStatus() == null || !"Running".equals(pod.getStatus().getPhase()) || !isPodReady(pod)) {
                setHealth(snapshot, false, false, "TARGET_POD_NOT_READY", podName + " 尚未 Running/Ready",
                        serviceSummaries);
                return;
            }
            String containerMessage = validatePodContainers(order, pod);
            if (containerMessage != null) {
                setHealth(snapshot, false, false, "TARGET_CONTAINER_NOT_READY", podName + "：" + containerMessage,
                        serviceSummaries);
                return;
            }
        }
        Map<String, Object> failedService = serviceSummaries.stream()
                .filter(item -> !Boolean.TRUE.equals(item.get("ready"))).findFirst().orElse(null);
        if (failedService != null) {
            setHealth(snapshot, false, false, "SERVICE_ENDPOINTS_NOT_READY",
                    "Service " + failedService.get("name") + " 尚未覆盖全部目标 Pod", serviceSummaries);
            return;
        }
        setHealth(snapshot, true, false, "READY", services == null || services.isEmpty()
                ? "Deployment、Pod 和容器已全部健康" : "Deployment、Pod、容器和 Service Endpoints 已全部健康",
                serviceSummaries);
    }

    private boolean hasProgressDeadlineExceeded(Deployment deployment) {
        return deployment.getStatus() != null && deployment.getStatus().getConditions() != null
                && deployment.getStatus().getConditions().stream().anyMatch(condition ->
                "ProgressDeadlineExceeded".equals(condition.getReason()));
    }

    private boolean isNonTerminating(Pod pod) {
        return pod.getMetadata() == null || pod.getMetadata().getDeletionTimestamp() == null;
    }

    private String validatePodContainers(DeploymentOrderDO order, Pod pod) {
        List<Container> containers = pod.getSpec() == null ? null : pod.getSpec().getContainers();
        List<ContainerStatus> statuses = pod.getStatus() == null ? null : pod.getStatus().getContainerStatuses();
        if (containers == null || statuses == null || containers.size() != statuses.size()) {
            return "普通容器状态尚未完整上报";
        }
        Map<String, ContainerStatus> statusByName = statuses.stream()
                .collect(java.util.stream.Collectors.toMap(ContainerStatus::getName, item -> item, (left, right) -> left));
        for (Container container : containers) {
            ContainerStatus status = statusByName.get(container.getName());
            if (status == null || !Boolean.TRUE.equals(status.getReady()) || status.getState() == null
                    || status.getState().getRunning() == null) {
                return "容器 " + container.getName() + " 尚未 Running/Ready";
            }
            if (Objects.equals(container.getName(), order.getContainerName())
                    && !Objects.equals(container.getImage(), order.getImage())) {
                return "目标容器镜像不是本部署单目标镜像";
            }
        }
        List<Container> initContainers = pod.getSpec() == null ? null : pod.getSpec().getInitContainers();
        if (initContainers == null || initContainers.isEmpty()) {
            return null;
        }
        List<ContainerStatus> initStatuses = pod.getStatus().getInitContainerStatuses();
        if (initStatuses == null || initStatuses.size() != initContainers.size()) {
            return "Init Container 状态尚未完整上报";
        }
        Map<String, ContainerStatus> initStatusByName = initStatuses.stream()
                .collect(java.util.stream.Collectors.toMap(ContainerStatus::getName, item -> item, (left, right) -> left));
        for (Container initContainer : initContainers) {
            ContainerStatus status = initStatusByName.get(initContainer.getName());
            boolean completed = status != null && status.getState() != null
                    && status.getState().getTerminated() != null
                    && Objects.equals(status.getState().getTerminated().getExitCode(), 0);
            boolean runningSidecar = status != null && Boolean.TRUE.equals(status.getReady())
                    && status.getState() != null && status.getState().getRunning() != null;
            if (!completed && !runningSidecar) {
                return "Init Container " + initContainer.getName() + " 尚未成功完成";
            }
        }
        return null;
    }

    private List<Map<String, Object>> buildServiceSummaries(KubernetesClient client, DeploymentOrderDO order,
                                                             List<Service> services, List<Pod> pods) {
        if (services == null || services.isEmpty()) {
            return Collections.emptyList();
        }
        Set<String> targetPodUids = pods.stream().map(pod -> pod.getMetadata() == null
                ? null : pod.getMetadata().getUid()).filter(StrUtil::isNotBlank).collect(java.util.stream.Collectors.toSet());
        Map<String, String> podUidByIp = pods.stream().filter(pod -> pod.getMetadata() != null
                        && StrUtil.isNotBlank(pod.getMetadata().getUid()) && pod.getStatus() != null
                        && StrUtil.isNotBlank(pod.getStatus().getPodIP()))
                .collect(java.util.stream.Collectors.toMap(pod -> pod.getStatus().getPodIP(),
                        pod -> pod.getMetadata().getUid(), (left, right) -> left));
        List<Map<String, Object>> summaries = new ArrayList<>();
        for (Service service : services) {
            String name = service.getMetadata() == null ? null : service.getMetadata().getName();
            Endpoints endpoints;
            try {
                endpoints = client.endpoints().inNamespace(order.getNamespace()).withName(name).get();
            } catch (Exception ignored) {
                endpoints = null;
            }
            Set<String> readyPodUids = collectEndpointPodUids(endpoints, true, podUidByIp);
            Set<String> notReadyPodUids = collectEndpointPodUids(endpoints, false, podUidByIp);
            Set<String> missingPodUids = new HashSet<>(targetPodUids);
            missingPodUids.removeAll(readyPodUids);
            Map<String, Object> summary = new LinkedHashMap<>();
            summary.put("name", name);
            summary.put("uid", service.getMetadata() == null ? null : service.getMetadata().getUid());
            summary.put("ready", endpoints != null && !targetPodUids.isEmpty() && missingPodUids.isEmpty());
            summary.put("readyPodUids", readyPodUids);
            summary.put("notReadyPodUids", notReadyPodUids);
            summary.put("missingPodUids", missingPodUids);
            summaries.add(summary);
        }
        return summaries;
    }

    private Set<String> collectEndpointPodUids(Endpoints endpoints, boolean ready, Map<String, String> podUidByIp) {
        if (endpoints == null || endpoints.getSubsets() == null) {
            return Collections.emptySet();
        }
        Set<String> podUids = new HashSet<>();
        for (EndpointSubset subset : endpoints.getSubsets()) {
            List<EndpointAddress> addresses = ready ? subset.getAddresses() : subset.getNotReadyAddresses();
            if (addresses == null) {
                continue;
            }
            for (EndpointAddress address : addresses) {
                String uid = address.getTargetRef() != null && "Pod".equals(address.getTargetRef().getKind())
                        ? address.getTargetRef().getUid() : podUidByIp.get(address.getIp());
                addUid(podUids, uid);
            }
        }
        return podUids;
    }

    private void setHealth(Map<String, Object> snapshot, boolean ready, boolean terminal, String reason,
                           String message, List<Map<String, Object>> services) {
        snapshot.put("ready", ready);
        snapshot.put("services", services);
        snapshot.put("health", health(ready, terminal, reason, message));
    }

    private Map<String, Object> health(boolean ready, boolean terminal, String reason, String message) {
        Map<String, Object> health = new LinkedHashMap<>();
        health.put("ready", ready);
        health.put("terminal", terminal);
        health.put("reason", reason);
        health.put("message", message);
        return health;
    }

    private List<Service> resolveAppliedServices(KubernetesClient client, DeploymentOrderDO order) {
        if (StrUtil.isBlank(order.getResultJson())) {
            return Collections.emptyList();
        }
        try {
            ContainerDeployConfigContext config = JsonUtils.parseObject(order.getConfigJson(), ContainerDeployConfigContext.class);
            if (config != null && DeploymentModeEnum.ORCHESTRATION.getMode().equals(config.getDeployMode())) {
                return Collections.emptyList();
            }
            DeploymentOrderResultContext result = JsonUtils.parseObject(order.getResultJson(),
                    DeploymentOrderResultContext.class);
            if (result == null || result.getApplyResults() == null) {
                return Collections.emptyList();
            }
            return result.getApplyResults().stream().filter(item -> "Service".equals(item.getKind())
                            && "APPLIED".equals(item.getStatus()) && StrUtil.isNotBlank(item.getName()))
                    .map(item -> {
                        Service service = null;
                        try {
                            service = client.services().inNamespace(order.getNamespace()).withName(item.getName()).get();
                        } catch (Exception ignored) {
                            // 保留 apply 记录中的 Service 身份，Endpoints 健康门禁仍应显示为未就绪。
                        }
                        return service != null ? service : new ServiceBuilder().withNewMetadata()
                                .withName(item.getName()).withNamespace(order.getNamespace()).withUid(item.getUid())
                                .endMetadata().build();
                    }).toList();
        } catch (Exception ignored) {
            return Collections.emptyList();
        }
    }

    private Set<String> collectInvolvedUids(Deployment deployment, ReplicaSet replicaSet, List<Pod> pods) {
        Set<String> uids = new HashSet<>();
        addUid(uids, deployment.getMetadata() == null ? null : deployment.getMetadata().getUid());
        addUid(uids, replicaSet == null || replicaSet.getMetadata() == null ? null : replicaSet.getMetadata().getUid());
        pods.forEach(pod -> addUid(uids, pod.getMetadata() == null ? null : pod.getMetadata().getUid()));
        return uids;
    }

    private void addUid(Set<String> uids, String uid) {
        if (StrUtil.isNotBlank(uid)) {
            uids.add(uid);
        }
    }

    private List<Map<String, Object>> buildEventSummaries(KubernetesClient client, DeploymentOrderDO order,
                                                           Set<String> involvedUids) {
        if (involvedUids.isEmpty()) {
            return Collections.emptyList();
        }
        List<Event> items;
        try {
            items = client.v1().events().inNamespace(order.getNamespace()).list().getItems();
        } catch (Exception ignored) {
            return Collections.emptyList();
        }
        if (items == null) {
            return Collections.emptyList();
        }
        List<Event> relatedEvents = items.stream()
                .filter(event -> event.getInvolvedObject() != null
                        && involvedUids.contains(event.getInvolvedObject().getUid()))
                .sorted(Comparator.comparing(this::readEventTimestamp,
                        Comparator.nullsLast(Comparator.reverseOrder())))
                .toList();
        Map<String, Map<String, Object>> deduplicated = new LinkedHashMap<>();
        for (Event event : relatedEvents) {
            String fingerprint = buildEventFingerprint(event);
            deduplicated.putIfAbsent(fingerprint, buildEventSummary(event));
            if (deduplicated.size() >= MAX_EVENTS) {
                break;
            }
        }
        return new ArrayList<>(deduplicated.values());
    }

    private String buildEventFingerprint(Event event) {
        String involvedUid = event.getInvolvedObject() == null ? "" : event.getInvolvedObject().getUid();
        return String.join("|", StrUtil.blankToDefault(involvedUid, ""),
                StrUtil.blankToDefault(event.getType(), ""), StrUtil.blankToDefault(event.getReason(), ""),
                StrUtil.blankToDefault(event.getMessage(), ""));
    }

    private String readEventTimestamp(Event event) {
        if (event.getEventTime() != null) {
            return event.getEventTime().getTime();
        }
        if (event.getLastTimestamp() != null) {
            return event.getLastTimestamp();
        }
        return event.getMetadata() == null ? null : event.getMetadata().getCreationTimestamp();
    }

    private Map<String, Object> buildEventSummary(Event event) {
        Map<String, Object> summary = new LinkedHashMap<>();
        summary.put("type", event.getType());
        summary.put("reason", event.getReason());
        summary.put("message", truncate(event.getMessage()));
        summary.put("count", event.getCount());
        summary.put("firstTimestamp", event.getFirstTimestamp());
        summary.put("lastTimestamp", readEventTimestamp(event));
        summary.put("action", event.getAction());
        if (event.getInvolvedObject() != null) {
            summary.put("resourceKind", event.getInvolvedObject().getKind());
            summary.put("resourceName", event.getInvolvedObject().getName());
            summary.put("resourceUid", event.getInvolvedObject().getUid());
        }
        return summary;
    }

    private Map<String, Object> diagnoseIssue(List<Pod> pods, List<Map<String, Object>> events,
                                              Deployment deployment) {
        List<String> priorityReasons = List.of("ErrImagePull", "ImagePullBackOff", "CrashLoopBackOff");
        for (String reason : priorityReasons) {
            for (Pod pod : pods) {
                Map<String, Object> failure = findContainerFailure(pod, reason);
                if (failure != null) {
                    return failure;
                }
            }
        }
        List<String> eventReasons = List.of("FailedScheduling", "FailedMount", "Unhealthy", "Failed", "BackOff");
        for (String reason : eventReasons) {
            Map<String, Object> event = events.stream().filter(item -> reason.equals(item.get("reason"))).findFirst().orElse(null);
            if (event != null) {
                return failure(reason, String.valueOf(event.get("message")), event.get("resourceName"), null);
            }
        }
        if (deployment.getStatus() != null && deployment.getStatus().getConditions() != null) {
            DeploymentCondition exceeded = deployment.getStatus().getConditions().stream()
                    .filter(condition -> "ProgressDeadlineExceeded".equals(condition.getReason())).findFirst().orElse(null);
            if (exceeded != null) {
                return failure("ProgressDeadlineExceeded", exceeded.getMessage(),
                        deployment.getMetadata() == null ? null : deployment.getMetadata().getName(), null);
            }
        }
        return null;
    }

    private Map<String, Object> findContainerFailure(Pod pod, String expectedReason) {
        if (pod.getStatus() == null) {
            return null;
        }
        List<ContainerStatus> statuses = new ArrayList<>();
        if (pod.getStatus().getInitContainerStatuses() != null) {
            statuses.addAll(pod.getStatus().getInitContainerStatuses());
        }
        if (pod.getStatus().getContainerStatuses() != null) {
            statuses.addAll(pod.getStatus().getContainerStatuses());
        }
        for (ContainerStatus status : statuses) {
            if (status.getState() != null && status.getState().getWaiting() != null
                    && expectedReason.equals(status.getState().getWaiting().getReason())) {
                String podName = pod.getMetadata() == null ? null : pod.getMetadata().getName();
                return failure(expectedReason, status.getState().getWaiting().getMessage(), podName, status.getName());
            }
        }
        return null;
    }

    private Map<String, Object> failure(String reason, String message, Object resourceName, String containerName) {
        Map<String, Object> failure = new LinkedHashMap<>();
        failure.put("reason", reason);
        failure.put("message", truncate(message));
        failure.put("resourceName", resourceName);
        failure.put("containerName", containerName);
        return failure;
    }

    private boolean isRolloutReady(Deployment deployment) {
        if (deployment.getSpec() == null || deployment.getStatus() == null || deployment.getMetadata() == null) {
            return false;
        }
        int desired = deployment.getSpec().getReplicas() == null ? 1 : deployment.getSpec().getReplicas();
        return deployment.getStatus().getObservedGeneration() != null && deployment.getMetadata().getGeneration() != null
                && deployment.getStatus().getObservedGeneration() >= deployment.getMetadata().getGeneration()
                && Objects.equals(deployment.getStatus().getUpdatedReplicas(), desired)
                && Objects.equals(deployment.getStatus().getAvailableReplicas(), desired)
                && (deployment.getStatus().getConditions() == null || deployment.getStatus().getConditions().stream()
                .noneMatch(condition -> "ProgressDeadlineExceeded".equals(condition.getReason())));
    }

    private String readRevision(Deployment deployment) {
        return deployment.getMetadata() == null || deployment.getMetadata().getAnnotations() == null ? null
                : deployment.getMetadata().getAnnotations().get(DEPLOYMENT_REVISION_ANNOTATION);
    }

    private String readRevision(ReplicaSet replicaSet) {
        return replicaSet.getMetadata() == null || replicaSet.getMetadata().getAnnotations() == null ? null
                : replicaSet.getMetadata().getAnnotations().get(DEPLOYMENT_REVISION_ANNOTATION);
    }

    private String truncate(String message) {
        return StrUtil.subPre(message, MAX_MESSAGE_LENGTH);
    }

}
