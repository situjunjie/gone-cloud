package cn.iocoder.yudao.module.devops.service.pipeline;

import cn.iocoder.yudao.module.devops.controller.admin.pipeline.vo.PipelineNodeTypeRespVO;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
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

    @Test
    public void testGetNodeTypes_jenkinsSchemas() {
        // 准备参数
        PipelineNodeRegistryServiceImpl service = new PipelineNodeRegistryServiceImpl();

        // 调用
        PipelineNodeTypeRespVO mavenNode = service.getNodeType(PipelineNodeRegistryServiceImpl.TYPE_MAVEN_BUILD_JAR);
        PipelineNodeTypeRespVO npmNode = service.getNodeType(PipelineNodeRegistryServiceImpl.TYPE_NPM_BUILD);
        PipelineNodeTypeRespVO dockerNode = service.getNodeType(PipelineNodeRegistryServiceImpl.TYPE_DOCKER_BUILD_PUSH);
        PipelineNodeTypeRespVO artifactNode = service.getNodeType(PipelineNodeRegistryServiceImpl.TYPE_ARTIFACT_UPLOAD);

        // 断言
        assertJenkinsSchema(mavenNode, "workingDir", "goals", "artifactPattern");
        assertJenkinsSchema(npmNode, "installCommand", "buildCommand", "distPattern");
        assertJenkinsSchema(dockerNode, "imageName", "dockerfile", "context");
        assertJenkinsSchema(artifactNode, "artifactPattern", "fingerprint", "allowEmptyArchive");
    }

    @SuppressWarnings("unchecked")
    private void assertJenkinsSchema(PipelineNodeTypeRespVO nodeType, String... properties) {
        assertNotNull(nodeType);
        assertTrue(Boolean.TRUE.equals(nodeType.getEnabled()));
        Map<String, Object> schema = nodeType.getParamSchema();
        assertNotNull(schema);
        Map<String, Object> propertyMap = (Map<String, Object>) schema.get("properties");
        for (String property : properties) {
            assertTrue(propertyMap.containsKey(property));
        }
    }

}
