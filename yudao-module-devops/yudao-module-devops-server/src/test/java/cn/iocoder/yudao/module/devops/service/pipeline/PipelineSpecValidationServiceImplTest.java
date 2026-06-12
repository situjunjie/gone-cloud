package cn.iocoder.yudao.module.devops.service.pipeline;

import cn.iocoder.yudao.framework.common.util.json.JsonUtils;
import cn.iocoder.yudao.module.devops.controller.admin.pipeline.vo.PipelineValidationRespVO;
import cn.iocoder.yudao.module.devops.framework.kubernetes.KubernetesDeploymentManifestSupport;
import cn.iocoder.yudao.module.devops.framework.pipeline.PipelineSpec;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * {@link PipelineSpecValidationServiceImpl} 的单元测试。
 */
public class PipelineSpecValidationServiceImplTest {

    private PipelineSpecValidationServiceImpl validationService;

    @BeforeEach
    public void setUp() {
        validationService = new PipelineSpecValidationServiceImpl();
        ReflectionTestUtils.setField(validationService, "pipelineNodeRegistryService",
                new PipelineNodeRegistryServiceImpl());
        ReflectionTestUtils.setField(validationService, "kubernetesDeploymentManifestSupport",
                new KubernetesDeploymentManifestSupport());
    }

    @Test
    public void testValidate_success() {
        // 准备参数
        String specJson = JsonUtils.toJsonString(buildValidSpec());

        // 调用
        PipelineValidationRespVO validation = validationService.validate(specJson);

        // 断言
        assertTrue(validation.getValid());
        assertTrue(validation.getErrors().isEmpty());
    }

    @Test
    public void testValidate_approvalParamsRequired() {
        // 准备参数
        PipelineSpec spec = buildValidSpec();
        PipelineSpec.Node approval = new PipelineSpec.Node();
        approval.setId("approval");
        approval.setType(PipelineNodeRegistryServiceImpl.TYPE_APPROVAL);
        approval.setName("审批");
        approval.setParams(Map.of());
        spec.getNodes().add(approval);
        spec.getEdges().add(edge("report_artifacts", "approval"));

        // 调用
        PipelineValidationRespVO validation = validationService.validate(JsonUtils.toJsonString(spec));

        // 断言
        assertFalse(validation.getValid());
        assertTrue(validation.getErrors().stream().anyMatch(error -> "approval".equals(error.getNodeId())
                && "params.processDefinitionKey".equals(error.getField())));
    }

    @Test
    public void testValidate_approvalSuccessBeforeContainerDeploy() {
        // 准备参数
        PipelineSpec spec = buildValidSpec();
        spec.getNodes().add(node("approval", PipelineNodeRegistryServiceImpl.TYPE_APPROVAL,
                Map.of("processDefinitionKey", "devops_deploy_approval")));
        spec.getNodes().add(node("deploy", PipelineNodeRegistryServiceImpl.TYPE_CONTAINER_DEPLOY,
                Map.of("infraType", "K8S", "deployMode", "RAW_MANIFEST",
                        "manifestYaml", deploymentManifestYaml("gone-server", "server"),
                        "containerName", "server", "image", "${APP_KEY}:${COMMIT_SHA}")));
        spec.getEdges().add(edge("report_artifacts", "approval"));
        spec.getEdges().add(edge("approval", "deploy"));

        // 调用
        PipelineValidationRespVO validation = validationService.validate(JsonUtils.toJsonString(spec));

        // 断言
        assertTrue(validation.getValid());
    }

    @Test
    public void testValidate_commandTemplateRequired() {
        // 准备参数
        PipelineSpec spec = buildValidSpec();
        spec.getNodes().get(1).setParams(Map.of());

        // 调用
        PipelineValidationRespVO validation = validationService.validate(JsonUtils.toJsonString(spec));

        // 断言
        assertFalse(validation.getValid());
        assertTrue(validation.getErrors().stream().anyMatch(error -> "COMMAND_TEMPLATE_REQUIRED".equals(error.getCode())));
    }

    @Test
    public void testValidate_jenkinsNodeParamsRequired() {
        // 准备参数
        PipelineSpec spec = new PipelineSpec();
        spec.setNodes(List.of(
                node("maven", PipelineNodeRegistryServiceImpl.TYPE_MAVEN_BUILD_JAR,
                        Map.of("workingDir", ".", "artifactPattern", "**/target/*.jar")),
                node("npm", PipelineNodeRegistryServiceImpl.TYPE_NPM_BUILD,
                        Map.of("workingDir", ".", "packageManager", "npm", "installCommand", "npm ci",
                                "distPattern", "dist/**")),
                node("docker", PipelineNodeRegistryServiceImpl.TYPE_DOCKER_BUILD_PUSH,
                        Map.of("imageName", "${APP_KEY}", "imageTagExpression", "${COMMIT_SHA}", "context", ".")),
                node("artifact", PipelineNodeRegistryServiceImpl.TYPE_ARTIFACT_UPLOAD, Map.of()),
                node("shell", PipelineNodeRegistryServiceImpl.TYPE_EXECUTE_SHELL, Map.of("workingDir", ".")),
                node("ssh", PipelineNodeRegistryServiceImpl.TYPE_SSH_PUBLISH, Map.of("execTimeoutMillis", "bad"))
        ));
        spec.setEdges(List.of(edge("maven", "npm"), edge("npm", "docker"), edge("docker", "artifact"),
                edge("artifact", "shell"), edge("shell", "ssh")));

        // 调用
        PipelineValidationRespVO validation = validationService.validate(JsonUtils.toJsonString(spec));

        // 断言
        assertFalse(validation.getValid());
        assertTrue(validation.getErrors().stream().anyMatch(error -> "maven".equals(error.getNodeId())
                && "params.goals".equals(error.getField())));
        assertTrue(validation.getErrors().stream().anyMatch(error -> "npm".equals(error.getNodeId())
                && "params.buildCommand".equals(error.getField())));
        assertTrue(validation.getErrors().stream().anyMatch(error -> "docker".equals(error.getNodeId())
                && "params.dockerfile".equals(error.getField())));
        assertTrue(validation.getErrors().stream().anyMatch(error -> "artifact".equals(error.getNodeId())
                && "params.artifactPattern".equals(error.getField())));
        assertTrue(validation.getErrors().stream().anyMatch(error -> "shell".equals(error.getNodeId())
                && "params.script".equals(error.getField())));
        assertTrue(validation.getErrors().stream().anyMatch(error -> "ssh".equals(error.getNodeId())
                && "params.sourceFiles".equals(error.getField())));
        assertTrue(validation.getErrors().stream().anyMatch(error -> "ssh".equals(error.getNodeId())
                && "params.execTimeoutMillis".equals(error.getField())));
    }

    @Test
    public void testValidate_commonEnvKeyInvalid() {
        // 准备参数
        PipelineSpec spec = buildValidSpec();
        spec.getNodes().get(0).setParams(Map.of("env", Map.of("bad-key", "value")));

        // 调用
        PipelineValidationRespVO validation = validationService.validate(JsonUtils.toJsonString(spec));

        // 断言
        assertFalse(validation.getValid());
        assertTrue(validation.getErrors().stream().anyMatch(error -> "PARAM_ENV_KEY_INVALID".equals(error.getCode())));
    }

    @Test
    public void testValidate_containerDeploySuccess() {
        // 准备参数
        PipelineSpec spec = buildValidSpec();
        spec.getNodes().add(node("deploy", PipelineNodeRegistryServiceImpl.TYPE_CONTAINER_DEPLOY,
                Map.of("infraType", "K8S", "deployMode", "RAW_MANIFEST",
                        "manifestYaml", deploymentManifestYaml("gone-server", "server"),
                        "containerName", "server", "image", "${APP_KEY}:${COMMIT_SHA}",
                        "rolloutTimeoutSeconds", 300)));
        spec.getEdges().add(edge("report_artifacts", "deploy"));

        // 调用
        PipelineValidationRespVO validation = validationService.validate(JsonUtils.toJsonString(spec));

        // 断言
        assertTrue(validation.getValid());
    }

    @Test
    public void testValidate_containerDeployMustBeTerminal() {
        // 准备参数
        PipelineSpec spec = buildValidSpec();
        spec.getNodes().add(node("deploy", PipelineNodeRegistryServiceImpl.TYPE_CONTAINER_DEPLOY,
                Map.of("infraType", "K8S", "deployMode", "RAW_MANIFEST",
                        "manifestYaml", deploymentManifestYaml("gone-server", "server"),
                        "containerName", "server", "image", "${APP_KEY}:${COMMIT_SHA}")));
        spec.getEdges().add(edge("unit_test", "deploy"));
        spec.getEdges().add(edge("deploy", "build_artifact"));
        spec.getEdges().removeIf(edge -> "unit_test".equals(edge.getSource()) && "build_artifact".equals(edge.getTarget()));

        // 调用
        PipelineValidationRespVO validation = validationService.validate(JsonUtils.toJsonString(spec));

        // 断言
        assertFalse(validation.getValid());
        assertTrue(validation.getErrors().stream()
                .anyMatch(error -> "CONTAINER_DEPLOY_NOT_TERMINAL".equals(error.getCode())));
    }

    @Test
    public void testValidate_containerDeployDuplicate() {
        // 准备参数
        PipelineSpec spec = buildValidSpec();
        spec.getNodes().add(node("deploy1", PipelineNodeRegistryServiceImpl.TYPE_CONTAINER_DEPLOY,
                Map.of("infraType", "K8S", "deployMode", "RAW_MANIFEST",
                        "manifestYaml", deploymentManifestYaml("gone-server", "server"),
                        "containerName", "server", "image", "${APP_KEY}:${COMMIT_SHA}")));
        spec.getNodes().add(node("deploy2", PipelineNodeRegistryServiceImpl.TYPE_CONTAINER_DEPLOY,
                Map.of("infraType", "K8S", "deployMode", "RAW_MANIFEST",
                        "manifestYaml", deploymentManifestYaml("gone-server", "server"),
                        "containerName", "server", "image", "${APP_KEY}:${COMMIT_SHA}")));
        spec.getEdges().add(edge("report_artifacts", "deploy1"));
        spec.getEdges().add(edge("deploy1", "deploy2"));

        // 调用
        PipelineValidationRespVO validation = validationService.validate(JsonUtils.toJsonString(spec));

        // 断言
        assertFalse(validation.getValid());
        assertTrue(validation.getErrors().stream()
                .anyMatch(error -> "CONTAINER_DEPLOY_DUPLICATE".equals(error.getCode())));
    }

    @Test
    public void testValidate_containerDeployParamsRequired() {
        // 准备参数
        PipelineSpec spec = new PipelineSpec();
        spec.setNodes(List.of(node("deploy", PipelineNodeRegistryServiceImpl.TYPE_CONTAINER_DEPLOY,
                Map.of("infraType", "HOST", "deployMode", "PATCH_IMAGE", "replicas", "bad"))));

        // 调用
        PipelineValidationRespVO validation = validationService.validate(JsonUtils.toJsonString(spec));

        // 断言
        assertFalse(validation.getValid());
        assertTrue(validation.getErrors().stream().anyMatch(error -> "params.manifestYaml".equals(error.getField())));
        assertTrue(validation.getErrors().stream().anyMatch(error -> "params.containerName".equals(error.getField())));
        assertTrue(validation.getErrors().stream().anyMatch(error -> "params.image".equals(error.getField())));
        assertTrue(validation.getErrors().stream().anyMatch(error -> "params.infraType".equals(error.getField())));
        assertTrue(validation.getErrors().stream().anyMatch(error -> "params.deployMode".equals(error.getField())));
        assertTrue(validation.getErrors().stream().anyMatch(error -> "params.replicas".equals(error.getField())));
    }

    @Test
    public void testValidate_containerDeployManifestInvalid() {
        PipelineSpec spec = buildValidSpec();
        spec.getNodes().add(node("deploy", PipelineNodeRegistryServiceImpl.TYPE_CONTAINER_DEPLOY,
                Map.of("infraType", "K8S", "deployMode", "RAW_MANIFEST",
                        "manifestYaml", "bad: [yaml",
                        "containerName", "server", "image", "${APP_KEY}:${COMMIT_SHA}")));
        spec.getEdges().add(edge("report_artifacts", "deploy"));

        PipelineValidationRespVO validation = validationService.validate(JsonUtils.toJsonString(spec));

        assertFalse(validation.getValid());
        assertTrue(validation.getErrors().stream().anyMatch(error -> "params.manifestYaml".equals(error.getField())));
    }

    @Test
    public void testValidate_containerDeployManifestAllowsDocumentStart() {
        PipelineSpec spec = buildValidSpec();
        spec.getNodes().add(node("deploy", PipelineNodeRegistryServiceImpl.TYPE_CONTAINER_DEPLOY,
                Map.of("infraType", "K8S", "deployMode", "RAW_MANIFEST",
                        "manifestYaml", "---\n" + deploymentManifestYaml("gone-server", "server"),
                        "containerName", "server", "image", "${APP_KEY}:${COMMIT_SHA}")));
        spec.getEdges().add(edge("report_artifacts", "deploy"));

        PipelineValidationRespVO validation = validationService.validate(JsonUtils.toJsonString(spec));

        assertTrue(validation.getValid());
    }

    @Test
    public void testValidate_containerDeployManifestRejectsMultipleDocuments() {
        PipelineSpec spec = buildValidSpec();
        spec.getNodes().add(node("deploy", PipelineNodeRegistryServiceImpl.TYPE_CONTAINER_DEPLOY,
                Map.of("infraType", "K8S", "deployMode", "RAW_MANIFEST",
                        "manifestYaml", deploymentManifestYaml("gone-server", "server") + "\n---\nkind: Service",
                        "containerName", "server", "image", "${APP_KEY}:${COMMIT_SHA}")));
        spec.getEdges().add(edge("report_artifacts", "deploy"));

        PipelineValidationRespVO validation = validationService.validate(JsonUtils.toJsonString(spec));

        assertFalse(validation.getValid());
        assertTrue(validation.getErrors().stream().anyMatch(error -> "params.manifestYaml".equals(error.getField())));
    }

    @Test
    public void testValidate_containerDeployManifestContainerMissing() {
        PipelineSpec spec = buildValidSpec();
        spec.getNodes().add(node("deploy", PipelineNodeRegistryServiceImpl.TYPE_CONTAINER_DEPLOY,
                Map.of("infraType", "K8S", "deployMode", "RAW_MANIFEST",
                        "manifestYaml", deploymentManifestYaml("gone-server", "server"),
                        "containerName", "worker", "image", "${APP_KEY}:${COMMIT_SHA}")));
        spec.getEdges().add(edge("report_artifacts", "deploy"));

        PipelineValidationRespVO validation = validationService.validate(JsonUtils.toJsonString(spec));

        assertFalse(validation.getValid());
        assertTrue(validation.getErrors().stream().anyMatch(error -> "params.manifestYaml".equals(error.getField())));
    }

    @Test
    public void testValidate_nullNode() {
        // 准备参数
        PipelineSpec spec = buildValidSpec();
        spec.getNodes().add(null);

        // 调用
        PipelineValidationRespVO validation = validationService.validate(JsonUtils.toJsonString(spec));

        // 断言
        assertFalse(validation.getValid());
        assertTrue(validation.getErrors().stream().anyMatch(error -> "NODE_ID_REQUIRED".equals(error.getCode())));
    }

    @Test
    public void testSortNodes() {
        // 调用
        List<PipelineSpec.Node> nodes = validationService.sortNodes(buildValidSpec());

        // 断言
        assertEquals("checkout", nodes.get(0).getId());
        assertEquals("report_artifacts", nodes.get(3).getId());
    }

    private PipelineSpec buildValidSpec() {
        PipelineSpec spec = new PipelineSpec();
        spec.setDslVersion("1.0");
        spec.setExecutionMode("SEQUENTIAL");
        spec.setNodes(new ArrayList<>(List.of(
                node("checkout", PipelineNodeRegistryServiceImpl.TYPE_CHECKOUT, Map.of()),
                node("unit_test", PipelineNodeRegistryServiceImpl.TYPE_UNIT_TEST,
                        Map.of("commandTemplateKey", "maven_test", "reportPattern", "**/surefire-reports/*.xml")),
                node("build_artifact", PipelineNodeRegistryServiceImpl.TYPE_BUILD_ARTIFACT,
                        Map.of("commandTemplateKey", "maven_package_skip_tests", "artifactPattern", "**/target/*.jar")),
                node("report_artifacts", PipelineNodeRegistryServiceImpl.TYPE_REPORT_ARTIFACTS, Map.of())
        )));
        spec.setEdges(new ArrayList<>(List.of(
                edge("checkout", "unit_test"),
                edge("unit_test", "build_artifact"),
                edge("build_artifact", "report_artifacts")
        )));
        return spec;
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
                          image: demo:latest
                """.formatted(name, name, name, containerName);
    }

    private PipelineSpec.Node node(String id, String type, Map<String, Object> params) {
        PipelineSpec.Node node = new PipelineSpec.Node();
        node.setId(id);
        node.setType(type);
        node.setName(id);
        node.setEnabled(true);
        node.setParams(params);
        return node;
    }

    private PipelineSpec.Edge edge(String source, String target) {
        PipelineSpec.Edge edge = new PipelineSpec.Edge();
        edge.setSource(source);
        edge.setTarget(target);
        return edge;
    }

}
