package cn.iocoder.yudao.module.devops.service.pipeline;

import cn.iocoder.yudao.module.devops.controller.admin.pipeline.vo.PipelineNodeTypeRespVO;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

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

        // 断言
        assertFalse(nodeTypes.isEmpty());
        assertTrue(nodeTypes.stream().allMatch(nodeType -> Boolean.TRUE.equals(nodeType.getEnabled())));
        assertTrue(nodeTypes.stream().anyMatch(nodeType -> PipelineNodeRegistryServiceImpl.TYPE_MOCK.equals(nodeType.getType())));
        assertFalse(nodeTypes.stream().anyMatch(nodeType -> PipelineNodeRegistryServiceImpl.TYPE_APPROVAL.equals(nodeType.getType())));
        assertFalse(nodeTypes.stream().anyMatch(nodeType -> PipelineNodeRegistryServiceImpl.TYPE_DEPLOY_K8S.equals(nodeType.getType())));
    }

}
