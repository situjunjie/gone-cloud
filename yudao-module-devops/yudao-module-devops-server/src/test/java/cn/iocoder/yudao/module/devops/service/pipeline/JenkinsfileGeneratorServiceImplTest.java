package cn.iocoder.yudao.module.devops.service.pipeline;

import cn.iocoder.yudao.module.devops.framework.pipeline.PipelineSpec;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;

import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * {@link JenkinsfileGeneratorServiceImpl} 的单元测试。
 */
public class JenkinsfileGeneratorServiceImplTest {

    private JenkinsfileGeneratorServiceImpl generatorService;

    @BeforeEach
    public void setUp() {
        PipelineNodeRegistryServiceImpl nodeRegistryService = new PipelineNodeRegistryServiceImpl();
        PipelineSpecValidationServiceImpl validationService = new PipelineSpecValidationServiceImpl();
        ReflectionTestUtils.setField(validationService, "pipelineNodeRegistryService", nodeRegistryService);
        generatorService = new JenkinsfileGeneratorServiceImpl();
        ReflectionTestUtils.setField(generatorService, "pipelineNodeRegistryService", nodeRegistryService);
        ReflectionTestUtils.setField(generatorService, "pipelineSpecValidationService", validationService);
    }

    @Test
    public void testGenerate() {
        // 调用
        String jenkinsfile = generatorService.generate(buildSpec());

        // 断言
        assertTrue(jenkinsfile.contains("stage('checkout')"));
        assertTrue(jenkinsfile.contains("string(name: 'CALLBACK_URL')"));
        assertTrue(jenkinsfile.contains("password(name: 'CALLBACK_TOKEN')"));
        assertTrue(jenkinsfile.contains("goneDevopsCallback(callbackUrl: params.CALLBACK_URL"));
        assertTrue(jenkinsfile.contains("action: 'STARTED'"));
        assertTrue(jenkinsfile.contains("action: 'COMPLETED'"));
        assertTrue(jenkinsfile.contains("action: 'FAILED'"));
        assertTrue(jenkinsfile.contains("goneDevopsUnitTest(command: 'mvn test')"));
        assertTrue(jenkinsfile.contains("archiveArtifacts artifacts: '**/target/*.jar'"));
        assertTrue(jenkinsfile.contains("goneDevopsReportArtifacts"));
        assertTrue(jenkinsfile.contains("post {\n    always {\n      cleanWs(deleteDirs: true, disableDeferredWipeout: true, notFailBuild: true)"));
    }

    @Test
    public void testGenerateJenkinsCompatibleNodes() {
        // 准备参数
        PipelineSpec spec = new PipelineSpec();
        spec.setNodes(List.of(
                node("maven", PipelineNodeRegistryServiceImpl.TYPE_MAVEN_BUILD_JAR,
                        Map.of("workingDir", "yudao-server", "goals", "clean package", "profiles", "prod",
                                "skipTests", true, "artifactPattern", "**/target/*.jar", "agentLabel", "linux",
                                "toolJdk", "jdk17", "toolMaven", "maven3", "env", Map.of("MAVEN_OPTS", "-Xmx1g"))),
                node("npm", PipelineNodeRegistryServiceImpl.TYPE_NPM_BUILD,
                        Map.of("workingDir", "frontend", "packageManager", "npm", "installCommand", "npm ci",
                                "buildCommand", "npm run build", "distPattern", "dist/**")),
                node("docker", PipelineNodeRegistryServiceImpl.TYPE_DOCKER_BUILD_PUSH,
                        Map.of("imageName", "${APP_KEY}", "imageTagExpression", "${COMMIT_SHA}",
                                "dockerfile", "Dockerfile", "context", ".", "registryUrl", "https://registry.example.com",
                                "registryCredentialsId", "docker-registry", "push", true, "pushLatest", true)),
                node("artifact", PipelineNodeRegistryServiceImpl.TYPE_ARTIFACT_UPLOAD,
                        Map.of("artifactPattern", "**/target/*.jar", "fingerprint", true,
                                "allowEmptyArchive", false, "onlyIfSuccessful", true))
        ));
        spec.setEdges(List.of(edge("maven", "npm"), edge("npm", "docker"), edge("docker", "artifact")));

        // 调用
        String jenkinsfile = generatorService.generate(spec);

        // 断言
        assertTrue(jenkinsfile.contains("stage('maven')"));
        assertTrue(jenkinsfile.contains("agent { label 'linux' }"));
        assertTrue(jenkinsfile.contains("jdk 'jdk17'"));
        assertTrue(jenkinsfile.contains("maven 'maven3'"));
        assertTrue(jenkinsfile.contains("MAVEN_OPTS = '-Xmx1g'"));
        assertTrue(jenkinsfile.contains("goneDevopsMavenBuildJar(workingDir: 'yudao-server'"));
        assertTrue(jenkinsfile.contains("goneDevopsNpmBuild(workingDir: 'frontend'"));
        assertTrue(jenkinsfile.contains("goneDevopsDockerBuildPush(imageName: '${APP_KEY}'"));
        assertTrue(jenkinsfile.contains("registryUrl: 'https://registry.example.com'"));
        assertTrue(jenkinsfile.contains("archiveArtifacts artifacts: '**/target/*.jar', fingerprint: true"));
    }

    @Test
    public void testGenerateMockNode() {
        // 准备参数
        PipelineSpec spec = new PipelineSpec();
        spec.setNodes(List.of(node("mock", PipelineNodeRegistryServiceImpl.TYPE_MOCK,
                Map.of("message", "hello"))));

        // 调用
        String jenkinsfile = generatorService.generate(spec);

        // 断言
        assertTrue(jenkinsfile.contains("stage('mock')"));
        assertTrue(jenkinsfile.contains("echo 'MOCK node: hello'"));
        assertTrue(jenkinsfile.contains("nodeId: 'mock'"));
        assertTrue(jenkinsfile.contains("nodeType: 'MOCK'"));
    }

    private PipelineSpec buildSpec() {
        PipelineSpec spec = new PipelineSpec();
        spec.setNodes(List.of(
                node("checkout", PipelineNodeRegistryServiceImpl.TYPE_CHECKOUT, Map.of()),
                node("unit_test", PipelineNodeRegistryServiceImpl.TYPE_UNIT_TEST,
                        Map.of("commandTemplateKey", "maven_test")),
                node("build_artifact", PipelineNodeRegistryServiceImpl.TYPE_BUILD_ARTIFACT,
                        Map.of("commandTemplateKey", "maven_package_skip_tests", "artifactPattern", "**/target/*.jar")),
                node("report_artifacts", PipelineNodeRegistryServiceImpl.TYPE_REPORT_ARTIFACTS, Map.of())
        ));
        spec.setEdges(List.of(edge("checkout", "unit_test"),
                edge("unit_test", "build_artifact"),
                edge("build_artifact", "report_artifacts")));
        return spec;
    }

    private PipelineSpec.Node node(String id, String type, Map<String, Object> params) {
        PipelineSpec.Node node = new PipelineSpec.Node();
        node.setId(id);
        node.setType(type);
        node.setName(id);
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
