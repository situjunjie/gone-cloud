package cn.iocoder.yudao.module.devops.service.pipeline;

import cn.iocoder.yudao.module.devops.controller.admin.pipeline.vo.PipelineNodeTypeRespVO;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertEquals;
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
        assertTrue(nodeTypes.stream().anyMatch(nodeType -> PipelineNodeRegistryServiceImpl.TYPE_CONTAINER_DEPLOY.equals(nodeType.getType())));
        assertTrue(nodeTypes.stream().anyMatch(nodeType -> PipelineNodeRegistryServiceImpl.TYPE_APPROVAL.equals(nodeType.getType())));
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
        PipelineNodeTypeRespVO exportOfflineImageNode = service.getNodeType(PipelineNodeRegistryServiceImpl.TYPE_EXPORT_OFFLINE_IMAGE);
        PipelineNodeTypeRespVO shellNode = service.getNodeType(PipelineNodeRegistryServiceImpl.TYPE_EXECUTE_SHELL);
        PipelineNodeTypeRespVO sshNode = service.getNodeType(PipelineNodeRegistryServiceImpl.TYPE_SSH_PUBLISH);

        // 断言
        assertJenkinsSchema(mavenNode, "workingDir", "goals", "artifactPattern");
        assertJenkinsSchema(npmNode, "installCommand", "buildCommand", "distPattern");
        assertJenkinsSchema(dockerNode, "imageName", "dockerfile", "context");
        assertJenkinsSchema(artifactNode, "artifactPattern", "fingerprint", "allowEmptyArchive");
        assertJenkinsSchema(exportOfflineImageNode, "imageName", "imageTag", "ossEndpoint", "ossBucket", "ossPath", "ossCredentialsId");
        assertEquals("导出离线镜像", exportOfflineImageNode.getName());
        assertEquals("JENKINS", exportOfflineImageNode.getCategory());
        assertJenkinsSchema(shellNode, "workingDir", "script");
        assertEquals("执行 Shell", shellNode.getName());
        assertEquals("textarea", getPropertyMap(shellNode).get("script") instanceof Map<?, ?> scriptParam
                ? scriptParam.get("x-component") : null);
        assertJenkinsSchema(sshNode, "configName", "sourceFiles", "remoteDirectory", "execCommand", "execTimeoutMillis");
        assertEquals("SSH 发布", sshNode.getName());
        assertEquals("textarea", getPropertyMap(sshNode).get("execCommand") instanceof Map<?, ?> execCommandParam
                ? execCommandParam.get("x-component") : null);
    }

    @Test
    public void testGetNodeTypes_jenkinsToolParamsUseRemoteOptions() {
        // 准备参数
        PipelineNodeRegistryServiceImpl service = new PipelineNodeRegistryServiceImpl();

        // 调用
        PipelineNodeTypeRespVO mavenNode = service.getNodeType(PipelineNodeRegistryServiceImpl.TYPE_MAVEN_BUILD_JAR);

        // 断言
        Map<String, Object> propertyMap = getPropertyMap(mavenNode);
        assertRemoteToolOption(propertyMap.get("toolJdk"), "/devops/pipeline/jenkins-tools?type=JDK");
        assertRemoteToolOption(propertyMap.get("toolMaven"), "/devops/pipeline/jenkins-tools?type=MAVEN");
    }

    @Test
    public void testGetConfigurableNodeTypes_includeCommandTemplates() {
        // 准备参数
        PipelineNodeRegistryServiceImpl service = new PipelineNodeRegistryServiceImpl();

        // 调用
        PipelineNodeTypeRespVO unitTestNode = service.getNodeType(PipelineNodeRegistryServiceImpl.TYPE_UNIT_TEST);
        PipelineNodeTypeRespVO mavenBuildNode = service.getNodeType(PipelineNodeRegistryServiceImpl.TYPE_MAVEN_BUILD_JAR);

        // 断言
        assertNotNull(unitTestNode.getCommandTemplates());
        assertTrue(unitTestNode.getCommandTemplates().stream()
                .anyMatch(template -> "maven_test".equals(template.getTemplateKey())
                        && PipelineNodeRegistryServiceImpl.TYPE_UNIT_TEST.equals(template.getNodeType())));
        assertTrue(unitTestNode.getCommandTemplates().stream()
                .anyMatch(template -> "npm_test".equals(template.getTemplateKey())
                        && PipelineNodeRegistryServiceImpl.TYPE_UNIT_TEST.equals(template.getNodeType())));
        assertNotNull(mavenBuildNode.getCommandTemplates());
        assertTrue(mavenBuildNode.getCommandTemplates().isEmpty());
    }

    @Test
    public void testGetNodeTypes_containerDeploySchema() {
        // 准备参数
        PipelineNodeRegistryServiceImpl service = new PipelineNodeRegistryServiceImpl();

        // 调用
        PipelineNodeTypeRespVO nodeType = service.getNodeType(PipelineNodeRegistryServiceImpl.TYPE_CONTAINER_DEPLOY);

        // 断言
        assertNotNull(nodeType);
        assertTrue(Boolean.TRUE.equals(nodeType.getEnabled()));
        assertEquals("容器部署", nodeType.getName());
        Map<String, Object> propertyMap = getPropertyMap(nodeType);
        assertTrue(propertyMap.containsKey("infraType"));
        assertTrue(propertyMap.containsKey("deployMode"));
        assertTrue(propertyMap.containsKey("manifestYaml"));
        assertTrue(propertyMap.containsKey("containerName"));
        assertTrue(propertyMap.containsKey("image"));
        assertTrue(propertyMap.containsKey("replicas"));
        assertTrue(propertyMap.containsKey("rolloutTimeoutSeconds"));
        assertFalse(propertyMap.containsKey("workloadKind"));
        assertFalse(propertyMap.containsKey("deploymentName"));
        assertFalse(propertyMap.containsKey("namespace"));
        assertFalse(propertyMap.containsKey("agentLabel"));
        assertEquals("textarea", propertyMap.get("manifestYaml") instanceof Map<?, ?> manifestYamlParam
                ? manifestYamlParam.get("x-component") : null);
    }

    @Test
    public void testGetNodeTypes_approvalSchema() {
        // 准备参数
        PipelineNodeRegistryServiceImpl service = new PipelineNodeRegistryServiceImpl();

        // 调用
        PipelineNodeTypeRespVO nodeType = service.getNodeType(PipelineNodeRegistryServiceImpl.TYPE_APPROVAL);

        // 断言
        assertNotNull(nodeType);
        assertTrue(Boolean.TRUE.equals(nodeType.getEnabled()));
        assertEquals("审批", nodeType.getName());
        assertEquals("PLATFORM", nodeType.getCategory());
        Map<String, Object> propertyMap = getPropertyMap(nodeType);
        assertTrue(propertyMap.containsKey("processDefinitionKey"));
        assertFalse(propertyMap.containsKey("startUserSelectAssignees"));
        assertFalse(propertyMap.containsKey("agentLabel"));
        assertFalse(propertyMap.containsKey("toolJdk"));
        assertFalse(propertyMap.containsKey("toolMaven"));
    }

    @SuppressWarnings("unchecked")
    private void assertJenkinsSchema(PipelineNodeTypeRespVO nodeType, String... properties) {
        assertNotNull(nodeType);
        assertTrue(Boolean.TRUE.equals(nodeType.getEnabled()));
        Map<String, Object> propertyMap = getPropertyMap(nodeType);
        for (String property : properties) {
            assertTrue(propertyMap.containsKey(property));
        }
    }

    @SuppressWarnings("unchecked")
    private Map<String, Object> getPropertyMap(PipelineNodeTypeRespVO nodeType) {
        Map<String, Object> schema = nodeType.getParamSchema();
        assertNotNull(schema);
        return (Map<String, Object>) schema.get("properties");
    }

    @SuppressWarnings("unchecked")
    private void assertRemoteToolOption(Object property, String url) {
        assertTrue(property instanceof Map<?, ?>);
        Map<String, Object> param = (Map<String, Object>) property;
        assertEquals("select", param.get("x-component"));
        Map<String, Object> optionSource = (Map<String, Object>) param.get("x-optionSource");
        assertEquals("remote", optionSource.get("type"));
        assertEquals(url, optionSource.get("url"));
        assertEquals("name", optionSource.get("labelField"));
        assertEquals("name", optionSource.get("valueField"));
    }

}
