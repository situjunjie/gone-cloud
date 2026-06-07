package cn.iocoder.yudao.module.devops.service.pipeline;

import cn.iocoder.yudao.framework.common.util.json.JsonUtils;
import cn.iocoder.yudao.module.devops.controller.admin.pipeline.vo.PipelineValidationRespVO;
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
    public void testValidate_disabledNode() {
        // 准备参数
        PipelineSpec spec = buildValidSpec();
        PipelineSpec.Node approval = new PipelineSpec.Node();
        approval.setId("approval");
        approval.setType(PipelineNodeRegistryServiceImpl.TYPE_APPROVAL);
        approval.setName("审批");
        spec.getNodes().add(approval);
        spec.getEdges().add(edge("report_artifacts", "approval"));

        // 调用
        PipelineValidationRespVO validation = validationService.validate(JsonUtils.toJsonString(spec));

        // 断言
        assertFalse(validation.getValid());
        assertTrue(validation.getErrors().stream().anyMatch(error -> "NODE_TYPE_DISABLED".equals(error.getCode())));
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
