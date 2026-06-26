package cn.iocoder.yudao.module.devops.framework.kubernetes;

import cn.iocoder.yudao.framework.common.exception.ServiceException;
import cn.iocoder.yudao.module.devops.service.deployment.context.ContainerDeployConfigContext;
import io.fabric8.kubernetes.api.model.HasMetadata;
import io.fabric8.kubernetes.api.model.Service;
import io.fabric8.kubernetes.api.model.apps.Deployment;
import org.junit.jupiter.api.Test;

import java.util.List;

import static cn.iocoder.yudao.framework.test.core.util.AssertUtils.assertServiceException;
import static cn.iocoder.yudao.module.devops.enums.ErrorCodeConstants.DEPLOYMENT_MANIFEST_KIND_NOT_SUPPORTED;
import static cn.iocoder.yudao.module.devops.enums.ErrorCodeConstants.DEPLOYMENT_NODE_PARAM_INVALID;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * {@link KubernetesDeploymentManifestSupport} 的单元测试。
 */
public class KubernetesDeploymentManifestSupportTest {

    private final KubernetesDeploymentManifestSupport support = new KubernetesDeploymentManifestSupport();

    @Test
    public void testParseSupportedResources_serviceThenDeployment() {
        List<HasMetadata> resources = support.parseSupportedResources(serviceManifestYaml() + "---\n"
                + deploymentManifestYaml("gone-api", "app"));

        assertEquals(2, resources.size());
        assertInstanceOf(Service.class, resources.get(0));
        assertInstanceOf(Deployment.class, resources.get(1));
        assertEquals("gone-api", support.readDeploymentName(support.findSingleDeployment(resources)));
    }

    @Test
    public void testPrepareSupportedResources_setNamespaceAndDeploymentOverrides() {
        ContainerDeployConfigContext config = new ContainerDeployConfigContext();
        config.setNamespace("prod");
        config.setRenderedManifestYaml(serviceManifestYaml() + "---\n" + deploymentManifestYaml("gone-api", "app"));
        config.setContainerName("app");
        config.setImage("registry.example.com/gone-api:sha");
        config.setReplicas(3);

        List<HasMetadata> resources = support.prepareSupportedResources(config);

        assertEquals("prod", resources.get(0).getMetadata().getNamespace());
        Deployment deployment = support.findSingleDeployment(resources);
        assertEquals("prod", deployment.getMetadata().getNamespace());
        assertEquals(3, deployment.getSpec().getReplicas());
        assertEquals("registry.example.com/gone-api:sha",
                deployment.getSpec().getTemplate().getSpec().getContainers().get(0).getImage());
    }

    @Test
    public void testFindSingleDeployment_missingDeployment() {
        List<HasMetadata> resources = support.parseSupportedResources(serviceManifestYaml());

        ServiceException exception = assertThrows(ServiceException.class, () -> support.findSingleDeployment(resources));
        assertEquals(DEPLOYMENT_NODE_PARAM_INVALID.getCode(), exception.getCode());
        assertTrue(exception.getMessage().contains("必须包含一个 Deployment"));
    }

    @Test
    public void testFindSingleDeployment_multipleDeployments() {
        List<HasMetadata> resources = support.parseSupportedResources(deploymentManifestYaml("gone-api", "app")
                + "---\n" + deploymentManifestYaml("gone-worker", "app"));

        ServiceException exception = assertThrows(ServiceException.class, () -> support.findSingleDeployment(resources));
        assertEquals(DEPLOYMENT_NODE_PARAM_INVALID.getCode(), exception.getCode());
        assertTrue(exception.getMessage().contains("仅支持一个 Deployment"));
    }

    @Test
    public void testParseSupportedResources_unsupportedKind() {
        assertServiceException(() -> support.parseSupportedResources(configMapManifestYaml()),
                DEPLOYMENT_MANIFEST_KIND_NOT_SUPPORTED);
    }

    private String serviceManifestYaml() {
        return """
                apiVersion: v1
                kind: Service
                metadata:
                  name: gone-api
                spec:
                  selector:
                    app: gone-api
                  ports:
                    - port: 80
                      targetPort: 8080
                """;
    }

    private String deploymentManifestYaml(String name, String containerName) {
        return """
                apiVersion: apps/v1
                kind: Deployment
                metadata:
                  name: %s
                spec:
                  replicas: 1
                  selector:
                    matchLabels:
                      app: %s
                  template:
                    metadata:
                      labels:
                        app: %s
                    spec:
                      containers:
                        - name: %s
                          image: ${IMAGE}
                """.formatted(name, name, name, containerName);
    }

    private String configMapManifestYaml() {
        return """
                apiVersion: v1
                kind: ConfigMap
                metadata:
                  name: gone-api
                data:
                  app: gone-api
                """;
    }

}
