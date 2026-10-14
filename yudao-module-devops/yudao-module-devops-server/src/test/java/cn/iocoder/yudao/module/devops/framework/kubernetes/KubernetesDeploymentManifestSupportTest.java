package cn.iocoder.yudao.module.devops.framework.kubernetes;

import cn.iocoder.yudao.framework.common.exception.ServiceException;
import cn.iocoder.yudao.module.devops.service.deployment.context.ContainerDeployConfigContext;
import io.fabric8.kubernetes.api.model.HasMetadata;
import io.fabric8.kubernetes.api.model.Service;
import io.fabric8.kubernetes.api.model.apps.Deployment;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;

import static cn.iocoder.yudao.framework.test.core.util.AssertUtils.assertServiceException;
import static cn.iocoder.yudao.module.devops.enums.ErrorCodeConstants.DEPLOYMENT_MANIFEST_KIND_NOT_SUPPORTED;
import static cn.iocoder.yudao.module.devops.enums.ErrorCodeConstants.DEPLOYMENT_NODE_PARAM_INVALID;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * {@link KubernetesDeploymentManifestSupport} 的单元测试。
 */
public class KubernetesDeploymentManifestSupportTest {

    private final KubernetesDeploymentManifestSupport support = new KubernetesDeploymentManifestSupport();

    @Test
    public void testRenderManifest_replacesVariablesAndKeepsUnknownPlaceholder() {
        String yaml = "kind: ConfigMap\nmetadata:\n  name: demo\ndata:\n"
                + "  host: ${DB_HOST}\n  image: ${IMAGE}\n  untouched: ${UNKNOWN}\n  regex: ${DB_HOST}-${IMAGE}\n";

        String rendered = support.renderManifest(yaml, Map.of("DB_HOST", "db.prod", "IMAGE", "registry/demo:sha"));

        assertTrue(rendered.contains("host: db.prod"));
        assertTrue(rendered.contains("image: registry/demo:sha"));
        assertTrue(rendered.contains("regex: db.prod-registry/demo:sha"));
        assertTrue(rendered.contains("untouched: ${UNKNOWN}"));
    }

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
    public void testPrepareSupportedResources_deploymentOnly() {
        ContainerDeployConfigContext config = config(deploymentManifestYaml("gone-api", "app"));

        List<HasMetadata> resources = support.prepareSupportedResources(config);

        assertEquals(1, resources.size());
        assertTrue(support.findServices(resources).isEmpty());
    }

    @Test
    public void testPrepareSupportedResources_serviceWithoutSelector() {
        String service = serviceManifestYaml().replace("  selector:\n    app: gone-api\n", "");
        ContainerDeployConfigContext config = config(service + "---\n" + deploymentManifestYaml("gone-api", "app"));

        assertParamInvalid(config, "非空 selector");
    }

    @Test
    public void testPrepareSupportedResources_externalNameService() {
        String service = serviceManifestYaml().replace("spec:\n", "spec:\n  type: ExternalName\n  externalName: example.com\n");
        ContainerDeployConfigContext config = config(service + "---\n" + deploymentManifestYaml("gone-api", "app"));

        assertParamInvalid(config, "ExternalName");
    }

    @Test
    public void testPrepareSupportedResources_serviceSelectorMismatch() {
        String service = serviceManifestYaml().replace("app: gone-api", "app: other");
        ContainerDeployConfigContext config = config(service + "---\n" + deploymentManifestYaml("gone-api", "app"));

        assertParamInvalid(config, "selector 与 Deployment");
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

    @Test
    public void testOrchestrationResourcesAndNamespace() {
        String yaml = "apiVersion: v1\nkind: Secret\nmetadata:\n  name: credentials\nstringData:\n  password: sensitive-value\n"
                + "---\n" + configMapManifestYaml()
                + "---\napiVersion: v1\nkind: PersistentVolumeClaim\nmetadata:\n  name: storage\n"
                + "---\n" + serviceManifestYaml().replace("app: gone-api", "app: external")
                + "---\n" + deploymentManifestYaml("gone-api", "app")
                + "---\napiVersion: networking.k8s.io/v1\nkind: Ingress\nmetadata:\n  name: ingress\n";
        ContainerDeployConfigContext config = config(yaml);
        config.setDeployMode("ORCHESTRATION");
        List<HasMetadata> resources = support.prepareSupportedResources(config);
        assertEquals(List.of("Secret", "ConfigMap", "PersistentVolumeClaim", "Service", "Deployment", "Ingress"),
                resources.stream().map(HasMetadata::getKind).toList());
        assertTrue(resources.stream().allMatch(resource -> "prod".equals(resource.getMetadata().getNamespace())));
        assertEquals("registry.example.com/gone-api:sha", support.findSingleDeployment(resources)
                .getSpec().getTemplate().getSpec().getContainers().get(0).getImage());
        assertEquals(6, support.parseSupportedResources(support.redactSecrets(yaml), "ORCHESTRATION").size());
        assertFalse(support.redactSecrets(yaml).contains("sensitive-value"));
        assertFalse(support.redactSecrets("kind: Secret\nstringData: [bad").contains("bad"));
        assertServiceException(() -> support.parseSupportedResources(
                "apiVersion: v1\nkind: Namespace\nmetadata:\n  name: other\n", "ORCHESTRATION"),
                DEPLOYMENT_MANIFEST_KIND_NOT_SUPPORTED);
    }

    @Test
    public void testRedactSecretDataAndLastAppliedAnnotation() {
        String yaml = "apiVersion: v1\nkind: Secret\nmetadata:\n  name: credentials\n  annotations:\n"
                + "    kubectl.kubernetes.io/last-applied-configuration: encoded-secret\n"
                + "data:\n  token: c2VjcmV0\nstringData:\n  password: clear-secret\n";
        String redacted = support.redactSecrets(yaml);
        assertFalse(redacted.contains("c2VjcmV0"));
        assertFalse(redacted.contains("clear-secret"));
        assertFalse(redacted.contains("encoded-secret"));
        assertEquals("credentials", support.parseSupportedResources(redacted, "ORCHESTRATION").get(0).getMetadata().getName());
    }

    @Test
    public void testOrchestrationResourceNameRequired() {
        ContainerDeployConfigContext config = config("apiVersion: v1\nkind: ConfigMap\nmetadata: {}\n---\n"
                + deploymentManifestYaml("gone-api", "app"));
        config.setDeployMode("ORCHESTRATION");
        assertServiceException(() -> support.prepareSupportedResources(config),
                cn.iocoder.yudao.module.devops.enums.ErrorCodeConstants.DEPLOYMENT_MANIFEST_NAME_REQUIRED);
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

    private ContainerDeployConfigContext config(String manifestYaml) {
        ContainerDeployConfigContext config = new ContainerDeployConfigContext();
        config.setNamespace("prod");
        config.setRenderedManifestYaml(manifestYaml);
        config.setContainerName("app");
        config.setImage("registry.example.com/gone-api:sha");
        config.setReplicas(1);
        return config;
    }

    private void assertParamInvalid(ContainerDeployConfigContext config, String message) {
        ServiceException exception = assertThrows(ServiceException.class,
                () -> support.prepareSupportedResources(config));
        assertEquals(DEPLOYMENT_NODE_PARAM_INVALID.getCode(), exception.getCode());
        assertTrue(exception.getMessage().contains(message));
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
