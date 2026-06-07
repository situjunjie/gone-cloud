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
        assertTrue(jenkinsfile.contains("stage('checkout__CHECKOUT')"));
        assertTrue(jenkinsfile.contains("goneDevopsUnitTest(command: 'mvn test')"));
        assertTrue(jenkinsfile.contains("archiveArtifacts artifacts: '**/target/*.jar'"));
        assertTrue(jenkinsfile.contains("goneDevopsReportArtifacts"));
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
