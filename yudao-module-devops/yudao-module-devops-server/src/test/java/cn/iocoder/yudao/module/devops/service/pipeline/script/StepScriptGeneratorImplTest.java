package cn.iocoder.yudao.module.devops.service.pipeline.script;

import cn.iocoder.yudao.module.devops.framework.pipeline.PipelineSpec;
import cn.iocoder.yudao.module.devops.service.pipeline.PipelineNodeRegistryServiceImpl;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;

import java.util.LinkedHashMap;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * {@link StepScriptGeneratorImpl} 的单元测试。
 *
 * <p>断言各 BUILD 类节点生成的纯 shell 片段（快照式），并校验：
 * 不含 Jenkins DSL / Groovy 外壳、凭据通过 env 引用而非内联、用户参数经 shell 转义。
 */
public class StepScriptGeneratorImplTest {

    private StepScriptGeneratorImpl generator;

    @BeforeEach
    public void setUp() {
        PipelineNodeRegistryServiceImpl nodeRegistryService = new PipelineNodeRegistryServiceImpl();
        generator = new StepScriptGeneratorImpl();
        ReflectionTestUtils.setField(generator, "pipelineNodeRegistryService", nodeRegistryService);
    }

    @Test
    public void testSupports() {
        assertTrue(generator.supports(PipelineNodeRegistryServiceImpl.TYPE_CHECKOUT));
        assertTrue(generator.supports(PipelineNodeRegistryServiceImpl.TYPE_DOCKER_BUILD_PUSH));
        assertTrue(generator.supports(PipelineNodeRegistryServiceImpl.TYPE_EXECUTE_SHELL));
        // 平台类节点不归本生成器处理
        assertFalse(generator.supports(PipelineNodeRegistryServiceImpl.TYPE_APPROVAL));
        assertFalse(generator.supports(PipelineNodeRegistryServiceImpl.TYPE_CONTAINER_DEPLOY));
        // SSH_PUBLISH 去留待 ST-5 定夺，当前不声明支持
        assertFalse(generator.supports(PipelineNodeRegistryServiceImpl.TYPE_SSH_PUBLISH));
    }

    @Test
    public void testUnsupportedTypeThrows() {
        PipelineSpec.Node node = node("x", PipelineNodeRegistryServiceImpl.TYPE_APPROVAL, Map.of());
        assertThrows(IllegalArgumentException.class, () -> generator.generate(node));
    }

    @Test
    public void testCheckout_default() {
        PipelineSpec.Node node = node("checkout", PipelineNodeRegistryServiceImpl.TYPE_CHECKOUT, Map.of());

        String script = generator.generate(node);

        assertTrue(script.contains("git clone --branch \"$BRANCH_NAME\" \"$REPO_URL\" ."));
        assertTrue(script.contains("if [ -n \"$COMMIT_SHA\" ]; then"));
        assertTrue(script.contains("git checkout \"$COMMIT_SHA\""));
        assertTrue(script.contains("rm -rf ./*"));
        // 不含 Jenkins 包装
        assertFalse(script.contains("goneDevops"));
        assertFalse(script.contains("sh '"));
    }

    @Test
    public void testCheckout_shallowWithSubdirectory() {
        PipelineSpec.Node node = node("checkout", PipelineNodeRegistryServiceImpl.TYPE_CHECKOUT,
                Map.of("shallowClone", true, "checkoutSubdirectory", "src", "cleanBeforeCheckout", false));

        String script = generator.generate(node);

        assertTrue(script.contains("git clone --depth 1 --branch \"$BRANCH_NAME\" \"$REPO_URL\" 'src'"));
        assertTrue(script.contains("cd 'src'"));
        assertTrue(script.contains("git fetch --depth 1 origin \"$COMMIT_SHA\""));
        assertFalse(script.contains("rm -rf"));
    }

    @Test
    public void testUnitTest_fromTemplate() {
        PipelineSpec.Node node = node("ut", PipelineNodeRegistryServiceImpl.TYPE_UNIT_TEST,
                Map.of("commandTemplateKey", "maven_test"));

        String script = generator.generate(node);

        assertTrue(script.contains("mvn test"));
        assertFalse(script.contains("goneDevopsUnitTest"));
    }

    @Test
    public void testBuildArtifact_fromTemplate() {
        PipelineSpec.Node node = node("ba", PipelineNodeRegistryServiceImpl.TYPE_BUILD_ARTIFACT,
                Map.of("commandTemplateKey", "maven_package_skip_tests"));

        String script = generator.generate(node);

        assertTrue(script.contains("mvn -DskipTests package"));
    }

    @Test
    public void testBuildImage_compat() {
        PipelineSpec.Node node = node("bi", PipelineNodeRegistryServiceImpl.TYPE_BUILD_IMAGE,
                Map.of("dockerfile", "build/Dockerfile", "context", "app"));

        String script = generator.generate(node);

        assertTrue(script.contains("docker build -f 'build/Dockerfile' -t \"$APP_KEY:$IMAGE_TAG\" 'app'"));
    }

    @Test
    public void testMavenBuildJar() {
        Map<String, Object> params = new LinkedHashMap<>();
        params.put("workingDir", "yudao-server");
        params.put("goals", "clean package");
        params.put("profiles", "prod");
        params.put("skipTests", true);
        params.put("mavenOptions", "-Xmx1g");
        PipelineSpec.Node node = node("mvn", PipelineNodeRegistryServiceImpl.TYPE_MAVEN_BUILD_JAR, params);

        String script = generator.generate(node);

        assertTrue(script.contains("cd 'yudao-server'"));
        assertTrue(script.contains("export MAVEN_OPTS='-Xmx1g'"));
        assertTrue(script.contains("mvn clean package -P 'prod' -DskipTests"));
    }

    @Test
    public void testMavenBuildJar_noSkipNoProfile() {
        Map<String, Object> params = new LinkedHashMap<>();
        params.put("workingDir", ".");
        params.put("goals", "clean install");
        params.put("skipTests", false);
        PipelineSpec.Node node = node("mvn", PipelineNodeRegistryServiceImpl.TYPE_MAVEN_BUILD_JAR, params);

        String script = generator.generate(node);

        assertFalse(script.contains("cd "));
        assertFalse(script.contains("-DskipTests"));
        assertFalse(script.contains("-P "));
        assertTrue(script.contains("mvn clean install"));
    }

    @Test
    public void testNpmBuild() {
        Map<String, Object> params = new LinkedHashMap<>();
        params.put("workingDir", "frontend");
        params.put("packageManager", "pnpm");
        params.put("installCommand", "pnpm install");
        params.put("buildCommand", "pnpm build");
        PipelineSpec.Node node = node("npm", PipelineNodeRegistryServiceImpl.TYPE_NPM_BUILD, params);

        String script = generator.generate(node);

        assertTrue(script.contains("cd 'frontend'"));
        assertTrue(script.contains("pnpm install"));
        assertTrue(script.contains("pnpm build"));
    }

    @Test
    public void testDockerBuildPush_pushWithRegistry() {
        Map<String, Object> params = new LinkedHashMap<>();
        params.put("imageName", "${APP_KEY}");
        params.put("imageTagExpression", "${COMMIT_SHA}");
        params.put("dockerfile", "Dockerfile");
        params.put("context", ".");
        params.put("push", true);
        params.put("pushLatest", true);
        params.put("buildArgs", Map.of("VERSION", "1.0"));
        PipelineSpec.Node node = node("docker", PipelineNodeRegistryServiceImpl.TYPE_DOCKER_BUILD_PUSH, params);

        String script = generator.generate(node);

        // 占位符转 shell 变量引用
        assertTrue(script.contains("IMAGE_REF=\"${DOCKER_REGISTRY:+$DOCKER_REGISTRY/}$APP_KEY:$COMMIT_SHA\""));
        assertTrue(script.contains("docker build -f 'Dockerfile' -t \"$IMAGE_REF\" --build-arg 'VERSION=1.0' '.'"));
        assertTrue(script.contains("docker tag \"$IMAGE_REF\" \"$IMAGE_REF_LATEST\""));
        // 凭据通过 env 引用，不内联明文
        assertTrue(script.contains("echo \"$DOCKER_REGISTRY_PASSWORD\" | docker login \"$DOCKER_REGISTRY\" -u \"$DOCKER_REGISTRY_USERNAME\" --password-stdin"));
        assertTrue(script.contains("docker push \"$IMAGE_REF\""));
        assertTrue(script.contains("docker push \"$IMAGE_REF_LATEST\""));
        assertFalse(script.contains("credentialsId"));
        assertFalse(script.contains("registryCredentialsId"));
    }

    @Test
    public void testDockerBuildPush_noPush() {
        Map<String, Object> params = new LinkedHashMap<>();
        params.put("imageName", "${APP_KEY}");
        params.put("imageTagExpression", "${COMMIT_SHA}");
        params.put("push", false);
        PipelineSpec.Node node = node("docker", PipelineNodeRegistryServiceImpl.TYPE_DOCKER_BUILD_PUSH, params);

        String script = generator.generate(node);

        assertTrue(script.contains("docker build"));
        assertFalse(script.contains("docker push"));
        assertFalse(script.contains("docker login"));
    }

    @Test
    public void testArtifactUpload() {
        PipelineSpec.Node node = node("au", PipelineNodeRegistryServiceImpl.TYPE_ARTIFACT_UPLOAD,
                Map.of("artifactPattern", "**/target/*.jar"));

        String script = generator.generate(node);

        assertTrue(script.contains("ls -1 '**/target/*.jar' 2>/dev/null || true"));
        assertFalse(script.contains("archiveArtifacts"));
    }

    @Test
    public void testReportArtifacts() {
        PipelineSpec.Node node = node("ra", PipelineNodeRegistryServiceImpl.TYPE_REPORT_ARTIFACTS, Map.of());

        String script = generator.generate(node);

        assertTrue(script.contains("ls -1 '**/target/*.jar'"));
        assertFalse(script.contains("goneDevopsReportArtifacts"));
    }

    @Test
    public void testExportOfflineImage_onlyDockerSave() {
        Map<String, Object> params = new LinkedHashMap<>();
        params.put("imageName", "${APP_KEY}");
        params.put("imageTag", "${COMMIT_SHA}");
        params.put("ossEndpoint", "https://oss.example.com");
        params.put("ossBucket", "my-bucket");
        PipelineSpec.Node node = node("export", PipelineNodeRegistryServiceImpl.TYPE_EXPORT_OFFLINE_IMAGE, params);

        String script = generator.generate(node);

        assertTrue(script.contains("IMAGE_REF=\"$APP_KEY:$COMMIT_SHA\""));
        assertTrue(script.contains("docker save -o \"$OFFLINE_IMAGE_TAR\" \"$IMAGE_REF\""));
        // OSS 上传由平台侧处理，脚本不引用 oss 配置/凭据
        assertFalse(script.contains("oss"));
        assertFalse(script.contains("OSS"));
        assertFalse(script.contains("goneDevopsExportOfflineImage"));
    }

    @Test
    public void testExecuteShell_withWorkingDir() {
        PipelineSpec.Node node = node("shell", PipelineNodeRegistryServiceImpl.TYPE_EXECUTE_SHELL,
                Map.of("workingDir", "scripts", "script", "echo 'hello'\npwd"));

        String script = generator.generate(node);

        assertTrue(script.contains("cd 'scripts'"));
        assertTrue(script.contains("echo 'hello'\npwd"));
        assertFalse(script.contains("sh(label"));
    }

    @Test
    public void testMock() {
        PipelineSpec.Node node = node("mock", PipelineNodeRegistryServiceImpl.TYPE_MOCK,
                Map.of("message", "hello"));

        String script = generator.generate(node);

        assertEquals("echo 'MOCK node: hello'\n", script);
    }

    @Test
    public void testShellInjectionEscaped() {
        // 用户参数含单引号，必须被 shell 转义，防注入
        PipelineSpec.Node node = node("bi", PipelineNodeRegistryServiceImpl.TYPE_BUILD_IMAGE,
                Map.of("dockerfile", "a'; rm -rf /; '", "context", "."));

        String script = generator.generate(node);

        // 单引号被 '\'' 收尾续接，恶意内容被困在单引号内无法逃逸执行
        assertTrue(script.contains("-f 'a'\\''; rm -rf /; '\\''' -t"));
    }

    private PipelineSpec.Node node(String id, String type, Map<String, Object> params) {
        PipelineSpec.Node node = new PipelineSpec.Node();
        node.setId(id);
        node.setType(type);
        node.setName(id);
        node.setParams(params);
        return node;
    }

}
