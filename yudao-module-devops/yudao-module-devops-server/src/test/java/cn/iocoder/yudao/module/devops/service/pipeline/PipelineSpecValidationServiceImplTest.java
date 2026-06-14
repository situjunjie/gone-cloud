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

import static org.junit.jupiter.api.Assertions.*;

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
    public void testValidate_codeMerge() {
        // 准备参数
        PipelineSpec spec = new PipelineSpec();
        spec.setNodes(List.of(
                node("code_merge", PipelineNodeRegistryServiceImpl.TYPE_CODE_MERGE, Map.of()),
                node("shell", PipelineNodeRegistryServiceImpl.TYPE_EXECUTE_SHELL,
                        Map.of("script", "echo hello"))
        ));
        spec.setEdges(List.of(edge("code_merge", "shell")));

        // 调用
        PipelineValidationRespVO validation = validationService.validate(JsonUtils.toJsonString(spec));

        // 断言
        assertTrue(validation.getValid());
    }

    @Test
    public void testValidate_executeShellScriptRequired() {
        // 准备参数
        PipelineSpec spec = new PipelineSpec();
        spec.setNodes(List.of(
                node("shell", PipelineNodeRegistryServiceImpl.TYPE_EXECUTE_SHELL, Map.of())
        ));
        spec.setEdges(List.of());

        // 调用
        PipelineValidationRespVO validation = validationService.validate(JsonUtils.toJsonString(spec));

        // 断言
        assertFalse(validation.getValid());
        assertTrue(validation.getErrors().stream().anyMatch(error -> "shell".equals(error.getNodeId())
                && "params.script".equals(error.getField())));
    }

    @Test
    public void testValidate_approval() {
        // 准备参数
        PipelineSpec spec = buildValidSpec();
        spec.getNodes().add(node("approval", PipelineNodeRegistryServiceImpl.TYPE_APPROVAL,
                Map.of("processDefinitionKey", "devops_deploy_approval")));
        spec.getEdges().add(edge("shell", "approval"));

        // 调用
        PipelineValidationRespVO validation = validationService.validate(JsonUtils.toJsonString(spec));

        // 断言
        assertTrue(validation.getValid());
    }

    @Test
    public void testValidate_approvalProcessDefinitionKeyRequired() {
        // 准备参数
        PipelineSpec spec = buildValidSpec();
        spec.getNodes().add(node("approval", PipelineNodeRegistryServiceImpl.TYPE_APPROVAL, Map.of()));
        spec.getEdges().add(edge("shell", "approval"));

        // 调用
        PipelineValidationRespVO validation = validationService.validate(JsonUtils.toJsonString(spec));

        // 断言
        assertFalse(validation.getValid());
        assertTrue(validation.getErrors().stream().anyMatch(error -> "approval".equals(error.getNodeId())
                && "params.processDefinitionKey".equals(error.getField())));
    }

    @Test
    public void testValidate_topologySuccess() {
        // 准备参数
        PipelineSpec spec = buildValidSpec();

        // 调用
        List<PipelineSpec.Node> sorted = validationService.sortNodes(spec);

        // 断言
        assertEquals(2, sorted.size());
        assertEquals("code_merge", sorted.get(0).getId());
        assertEquals("shell", sorted.get(1).getId());
    }

    @Test
    public void testValidate_topologyCycle() {
        // 准备参数
        PipelineSpec spec = new PipelineSpec();
        spec.setNodes(List.of(
                node("a", PipelineNodeRegistryServiceImpl.TYPE_EXECUTE_SHELL, Map.of("script", "echo a")),
                node("b", PipelineNodeRegistryServiceImpl.TYPE_EXECUTE_SHELL, Map.of("script", "echo b"))
        ));
        spec.setEdges(List.of(edge("a", "b"), edge("b", "a")));

        // 调用
        PipelineValidationRespVO validation = validationService.validate(JsonUtils.toJsonString(spec));

        // 断言
        assertFalse(validation.getValid());
        assertTrue(validation.getErrors().stream().anyMatch(error -> "GRAPH_HAS_CYCLE".equals(error.getCode())));
    }

    private PipelineSpec buildValidSpec() {
        PipelineSpec spec = new PipelineSpec();
        spec.setNodes(new ArrayList<>(List.of(
                node("code_merge", PipelineNodeRegistryServiceImpl.TYPE_CODE_MERGE, Map.of()),
                node("shell", PipelineNodeRegistryServiceImpl.TYPE_EXECUTE_SHELL,
                        Map.of("script", "echo hello"))
        )));
        spec.setEdges(new ArrayList<>(List.of(
                edge("code_merge", "shell")
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
