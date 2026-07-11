package cn.iocoder.yudao.module.devops.service.deployment;

import cn.iocoder.yudao.framework.test.core.ut.BaseMockitoUnitTest;
import cn.iocoder.yudao.module.devops.dal.dataobject.deployment.DeploymentOrderDO;
import io.fabric8.kubernetes.api.model.ContainerStateBuilder;
import io.fabric8.kubernetes.api.model.EventBuilder;
import io.fabric8.kubernetes.api.model.EventListBuilder;
import io.fabric8.kubernetes.api.model.Pod;
import io.fabric8.kubernetes.api.model.PodBuilder;
import io.fabric8.kubernetes.api.model.PodList;
import io.fabric8.kubernetes.api.model.PodListBuilder;
import io.fabric8.kubernetes.api.model.apps.Deployment;
import io.fabric8.kubernetes.api.model.apps.DeploymentBuilder;
import io.fabric8.kubernetes.api.model.apps.ReplicaSet;
import io.fabric8.kubernetes.api.model.apps.ReplicaSetBuilder;
import io.fabric8.kubernetes.api.model.apps.ReplicaSetList;
import io.fabric8.kubernetes.api.model.apps.ReplicaSetListBuilder;
import io.fabric8.kubernetes.client.KubernetesClient;
import io.fabric8.kubernetes.client.dsl.AppsAPIGroupDSL;
import io.fabric8.kubernetes.client.dsl.MixedOperation;
import io.fabric8.kubernetes.client.dsl.NonNamespaceOperation;
import io.fabric8.kubernetes.client.dsl.PodResource;
import io.fabric8.kubernetes.client.dsl.Resource;
import io.fabric8.kubernetes.client.dsl.RollableScalableResource;
import io.fabric8.kubernetes.client.dsl.V1APIGroupDSL;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class KubernetesDeploymentStatusCollectorTest extends BaseMockitoUnitTest {

    private final KubernetesDeploymentStatusCollector collector = new KubernetesDeploymentStatusCollector();

    @Test
    @SuppressWarnings("unchecked")
    void testCollect_filtersOldPodAndDiagnosesImagePullBackOff() {
        DeploymentOrderDO order = new DeploymentOrderDO();
        order.setId(100L);
        order.setNamespace("prod");
        order.setWorkloadName("gone-api");
        order.setWorkloadUid("deployment-uid");
        order.setTargetRevision("9");
        order.setContainerName("app");

        Deployment deployment = new DeploymentBuilder()
                .withNewMetadata().withName("gone-api").withNamespace("prod").withUid("deployment-uid")
                .withGeneration(10L).addToAnnotations("deployment.kubernetes.io/revision", "9").endMetadata()
                .withNewSpec().withReplicas(1)
                .withNewSelector().addToMatchLabels("app", "gone-api").endSelector()
                .withNewTemplate().withNewMetadata()
                .addToLabels(KubernetesDeploymentStatusCollector.DEPLOYMENT_ORDER_ID_KEY, "100")
                .endMetadata().withNewSpec().addNewContainer().withName("app").withImage("registry/gone:bad")
                .endContainer().endSpec().endTemplate().endSpec()
                .withNewStatus().withObservedGeneration(10L).withUpdatedReplicas(1).withAvailableReplicas(0)
                .withReadyReplicas(0).endStatus().build();
        ReplicaSet targetReplicaSet = new ReplicaSetBuilder()
                .withNewMetadata().withName("gone-api-new").withUid("rs-new").withCreationTimestamp("2026-07-11T10:00:00Z")
                .addToAnnotations("deployment.kubernetes.io/revision", "9")
                .addNewOwnerReference().withKind("Deployment").withUid("deployment-uid").endOwnerReference()
                .endMetadata().withNewStatus().withReplicas(1).withReadyReplicas(0).endStatus().build();
        Pod targetPod = new PodBuilder()
                .withNewMetadata().withName("gone-api-new-abc").withUid("pod-new")
                .addNewOwnerReference().withKind("ReplicaSet").withUid("rs-new").endOwnerReference().endMetadata()
                .withNewSpec().addNewContainer().withName("app").withImage("registry/gone:bad").endContainer().endSpec()
                .withNewStatus().withPhase("Pending")
                .addNewContainerStatus().withName("app").withImage("registry/gone:bad").withReady(false)
                .withRestartCount(2)
                .withState(new ContainerStateBuilder().withNewWaiting().withReason("ImagePullBackOff")
                        .withMessage("Back-off pulling image registry/gone:bad").endWaiting().build())
                .withLastState(new ContainerStateBuilder().withNewTerminated().withReason("Error").withExitCode(1)
                        .withStartedAt("2026-07-11T09:59:00Z").withFinishedAt("2026-07-11T09:59:02Z")
                        .endTerminated().build())
                .endContainerStatus().endStatus().build();
        Pod oldPod = new PodBuilder().withNewMetadata().withName("gone-api-old-abc").withUid("pod-old")
                .addNewOwnerReference().withKind("ReplicaSet").withUid("rs-old").endOwnerReference()
                .endMetadata().build();

        KubernetesClient client = mock(KubernetesClient.class);
        AppsAPIGroupDSL apps = mock(AppsAPIGroupDSL.class);
        MixedOperation<ReplicaSet, ReplicaSetList, RollableScalableResource<ReplicaSet>> replicaSetOperation = mock(MixedOperation.class);
        NonNamespaceOperation<ReplicaSet, ReplicaSetList, RollableScalableResource<ReplicaSet>> namespacedReplicaSets =
                mock(NonNamespaceOperation.class);
        MixedOperation<Pod, PodList, PodResource> podOperation = mock(MixedOperation.class);
        NonNamespaceOperation<Pod, PodList, PodResource> namespacedPods = mock(NonNamespaceOperation.class);
        V1APIGroupDSL v1 = mock(V1APIGroupDSL.class);
        MixedOperation<io.fabric8.kubernetes.api.model.Event, io.fabric8.kubernetes.api.model.EventList,
                Resource<io.fabric8.kubernetes.api.model.Event>> eventOperation = mock(MixedOperation.class);
        NonNamespaceOperation<io.fabric8.kubernetes.api.model.Event, io.fabric8.kubernetes.api.model.EventList,
                Resource<io.fabric8.kubernetes.api.model.Event>> namespacedEvents = mock(NonNamespaceOperation.class);
        when(client.apps()).thenReturn(apps);
        when(apps.replicaSets()).thenReturn(replicaSetOperation);
        when(replicaSetOperation.inNamespace(eq("prod"))).thenReturn(namespacedReplicaSets);
        when(namespacedReplicaSets.list()).thenReturn(new ReplicaSetListBuilder().withItems(targetReplicaSet).build());
        when(client.pods()).thenReturn(podOperation);
        when(podOperation.inNamespace(eq("prod"))).thenReturn(namespacedPods);
        when(namespacedPods.withLabels(eq(Map.of(KubernetesDeploymentStatusCollector.DEPLOYMENT_ORDER_ID_KEY, "100"))))
                .thenReturn(namespacedPods);
        when(namespacedPods.list()).thenReturn(new PodListBuilder().withItems(targetPod, oldPod).build());
        when(client.v1()).thenReturn(v1);
        when(v1.events()).thenReturn(eventOperation);
        when(eventOperation.inNamespace(eq("prod"))).thenReturn(namespacedEvents);
        when(namespacedEvents.list()).thenReturn(new EventListBuilder().addToItems(new EventBuilder()
                .withType("Warning").withReason("Failed").withMessage("Failed to pull image")
                .withNewInvolvedObject().withKind("Pod").withName("gone-api-new-abc").withUid("pod-new")
                .endInvolvedObject().build()).build());

        Map<String, Object> result = collector.collect(client, order, deployment);

        assertEquals("DEPLOYMENT_ORDER_LABEL", result.get("correlationMode"));
        List<Map<String, Object>> pods = (List<Map<String, Object>>) result.get("pods");
        assertEquals(1, pods.size());
        assertEquals("gone-api-new-abc", pods.get(0).get("name"));
        Map<String, Object> container = (Map<String, Object>) pods.get(0).get("targetContainer");
        assertEquals("WAITING", container.get("state"));
        assertEquals("ImagePullBackOff", container.get("reason"));
        assertNotNull(container.get("lastState"));
        Map<String, Object> failure = (Map<String, Object>) result.get("failure");
        assertEquals("ImagePullBackOff", failure.get("reason"));
        assertEquals(1, ((List<?>) result.get("events")).size());
    }

}
