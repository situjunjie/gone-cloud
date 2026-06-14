package cn.iocoder.yudao.module.devops.service.pipeline;

import cn.iocoder.yudao.module.devops.controller.admin.pipeline.vo.PipelineNodeTypeRespVO;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

/**
 * {@link PipelineNodeRegistryServiceImpl} 的单元测试。
 */
public class PipelineNodeRegistryServiceImplTest {

    @Test
    public void testGetConfigurableNodeTypes_onlyEnabled() {
        // 准备参数
        PipelineNodeRegistryServiceImpl service = new PipelineNodeRegistryServiceImpl();

        // 调用
        List<PipelineNodeTypeRespVO> nodeTypes = service.getConfigurableNodeTypes();

        // 断言：只返回启用节点
        assertFalse(nodeTypes.isEmpty());
        assertTrue(nodeTypes.stream().allMatch(nodeType -> Boolean.TRUE.equals(nodeType.getEnabled())));
        assertTrue(nodeTypes.stream().anyMatch(nodeType -> PipelineNodeRegistryServiceImpl.TYPE_CODE_MERGE.equals(nodeType.getType())));
        assertTrue(nodeTypes.stream().anyMatch(nodeType -> PipelineNodeRegistryServiceImpl.TYPE_APPROVAL.equals(nodeType.getType())));
        assertTrue(nodeTypes.stream().anyMatch(nodeType -> PipelineNodeRegistryServiceImpl.TYPE_EXECUTE_SHELL.equals(nodeType.getType())));
        assertEquals(3, nodeTypes.size());
    }

    @Test
    public void testGetNodeType_approval() {
        // 准备参数
        PipelineNodeRegistryServiceImpl service = new PipelineNodeRegistryServiceImpl();

        // 调用
        PipelineNodeTypeRespVO nodeType = service.getNodeType(PipelineNodeRegistryServiceImpl.TYPE_APPROVAL);

        // 断言
        assertNotNull(nodeType);
        assertTrue(Boolean.TRUE.equals(nodeType.getEnabled()));
        assertEquals("审批", nodeType.getName());
        assertEquals("GATE", nodeType.getCategory());
        Map<String, Object> propertyMap = getPropertyMap(nodeType);
        assertTrue(propertyMap.containsKey("processDefinitionKey"));
    }


    @Test
    public void testGetNodeType_codeMerge() {
        // 准备参数
        PipelineNodeRegistryServiceImpl service = new PipelineNodeRegistryServiceImpl();

        // 调用
        PipelineNodeTypeRespVO nodeType = service.getNodeType(PipelineNodeRegistryServiceImpl.TYPE_CODE_MERGE);

        // 断言
        assertNotNull(nodeType);
        assertTrue(Boolean.TRUE.equals(nodeType.getEnabled()));
        assertEquals("代码合并", nodeType.getName());
        assertEquals("PLATFORM", nodeType.getCategory());
        Map<String, Object> propertyMap = getPropertyMap(nodeType);
        assertTrue(propertyMap.isEmpty()); // 无参数
    }

    @Test
    public void testGetNodeType_executeShell() {
        // 准备参数
        PipelineNodeRegistryServiceImpl service = new PipelineNodeRegistryServiceImpl();

        // 调用
        PipelineNodeTypeRespVO nodeType = service.getNodeType(PipelineNodeRegistryServiceImpl.TYPE_EXECUTE_SHELL);

        // 断言
        assertNotNull(nodeType);
        assertTrue(Boolean.TRUE.equals(nodeType.getEnabled()));
        assertEquals("执行 Shell", nodeType.getName());
        assertEquals("BUILD", nodeType.getCategory());
        Map<String, Object> propertyMap = getPropertyMap(nodeType);
        assertTrue(propertyMap.containsKey("script"));
        assertTrue(propertyMap.containsKey("shellType"));
        assertTrue(propertyMap.containsKey("env"));
        assertEquals("textarea", propertyMap.get("script") instanceof Map<?, ?> scriptParam
                ? scriptParam.get("x-component") : null);
    }

    @SuppressWarnings("unchecked")
    private Map<String, Object> getPropertyMap(PipelineNodeTypeRespVO nodeType) {
        Map<String, Object> schema = nodeType.getParamSchema();
        assertNotNull(schema);
        Object properties = schema.get("properties");
        return properties instanceof Map<?, ?> map ? (Map<String, Object>) map : Map.of();
    }

}
