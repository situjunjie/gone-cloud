package cn.iocoder.yudao.module.devops.service.pipeline;

import cn.iocoder.yudao.module.devops.controller.admin.pipeline.vo.PipelineCommandTemplateRespVO;
import cn.iocoder.yudao.module.devops.controller.admin.pipeline.vo.PipelineNodeTypeRespVO;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * DevOps 流水线节点与命令模板注册表。
 */
@Service
public class PipelineNodeRegistryServiceImpl implements PipelineNodeRegistryService {

    public static final String TYPE_CHECKOUT = "CHECKOUT";
    public static final String TYPE_UNIT_TEST = "UNIT_TEST";
    public static final String TYPE_BUILD_ARTIFACT = "BUILD_ARTIFACT";
    public static final String TYPE_BUILD_IMAGE = "BUILD_IMAGE";
    public static final String TYPE_REPORT_ARTIFACTS = "REPORT_ARTIFACTS";
    public static final String TYPE_MOCK = "MOCK";
    public static final String TYPE_APPROVAL = "APPROVAL";
    public static final String TYPE_DEPLOY_K8S = "DEPLOY_K8S";

    private final Map<String, PipelineNodeTypeRespVO> nodeTypeMap = new LinkedHashMap<>();
    private final Map<String, PipelineCommandTemplateRespVO> commandTemplateMap = new LinkedHashMap<>();

    public PipelineNodeRegistryServiceImpl() {
        registerNode(TYPE_CHECKOUT, "拉取代码", "JENKINS", "git-branch", true, null,
                mapOf(), schemaOf());
        registerNode(TYPE_UNIT_TEST, "单元测试", "JENKINS", "test-tube", true, null,
                mapOf("commandTemplateKey", "maven_test", "reportPattern", "**/surefire-reports/*.xml"),
                schemaOf());
        registerNode(TYPE_BUILD_ARTIFACT, "构建制品", "JENKINS", "package", true, null,
                mapOf("commandTemplateKey", "maven_package_skip_tests", "artifactPattern", "**/target/*.jar"),
                schemaOf());
        registerNode(TYPE_BUILD_IMAGE, "构建镜像", "JENKINS", "container", true, null,
                mapOf("commandTemplateKey", "docker_build", "dockerfile", "Dockerfile", "context", "."),
                schemaOf());
        registerNode(TYPE_REPORT_ARTIFACTS, "上报产物", "JENKINS", "upload-cloud", true, null,
                mapOf(), schemaOf());
        registerNode(TYPE_MOCK, "Mock 节点", "JENKINS", "play-circle", true, null,
                mapOf("message", "mock node"), schemaOf());
        registerNode(TYPE_APPROVAL, "审批", "PLATFORM", "check-circle", false,
                "后续阶段开放：平台审批", mapOf(), schemaOf());
        registerNode(TYPE_DEPLOY_K8S, "部署 K8S", "PLATFORM", "rocket", false,
                "后续阶段开放：平台部署", mapOf(), schemaOf());

        registerTemplate("maven_test", "Maven 单元测试", TYPE_UNIT_TEST, "mvn test",
                null, "**/surefire-reports/*.xml", "执行 Maven 单元测试");
        registerTemplate("maven_package_skip_tests", "Maven 打包（跳过测试）", TYPE_BUILD_ARTIFACT,
                "mvn -DskipTests package", "**/target/*.jar", null, "执行 Maven 打包并归档 jar");
        registerTemplate("npm_test", "NPM 测试", TYPE_UNIT_TEST, "npm run test",
                null, "junit.xml", "执行 npm 测试命令");
        registerTemplate("npm_build", "NPM 构建", TYPE_BUILD_ARTIFACT, "npm run build",
                "dist/**", null, "执行 npm 构建命令");
        registerTemplate("docker_build", "Docker 镜像构建", TYPE_BUILD_IMAGE, "docker build",
                null, null, "使用 Dockerfile 构建镜像");
    }

    @Override
    public List<PipelineNodeTypeRespVO> getNodeTypes() {
        return new ArrayList<>(nodeTypeMap.values());
    }

    @Override
    public List<PipelineNodeTypeRespVO> getConfigurableNodeTypes() {
        return nodeTypeMap.values().stream()
                .filter(nodeType -> Boolean.TRUE.equals(nodeType.getEnabled()))
                .toList();
    }

    @Override
    public List<PipelineCommandTemplateRespVO> getCommandTemplates() {
        return new ArrayList<>(commandTemplateMap.values());
    }

    @Override
    public PipelineNodeTypeRespVO getNodeType(String type) {
        return nodeTypeMap.get(type);
    }

    @Override
    public PipelineCommandTemplateRespVO getCommandTemplate(String templateKey) {
        return commandTemplateMap.get(templateKey);
    }

    private void registerNode(String type, String name, String category, String icon, boolean enabled,
                              String disabledReason, Map<String, Object> defaultParams,
                              Map<String, Object> paramSchema) {
        PipelineNodeTypeRespVO nodeType = new PipelineNodeTypeRespVO();
        nodeType.setType(type);
        nodeType.setName(name);
        nodeType.setCategory(category);
        nodeType.setIcon(icon);
        nodeType.setEnabled(enabled);
        nodeType.setDisabledReason(disabledReason);
        nodeType.setDefaultName(name);
        nodeType.setDefaultParams(defaultParams);
        nodeType.setParamSchema(paramSchema);
        nodeTypeMap.put(type, nodeType);
    }

    private void registerTemplate(String templateKey, String templateName, String nodeType, String command,
                                  String artifactPattern, String reportPattern, String description) {
        PipelineCommandTemplateRespVO template = new PipelineCommandTemplateRespVO();
        template.setTemplateKey(templateKey);
        template.setTemplateName(templateName);
        template.setNodeType(nodeType);
        template.setCommand(command);
        template.setArtifactPattern(artifactPattern);
        template.setReportPattern(reportPattern);
        template.setDescription(description);
        template.setEnabled(true);
        commandTemplateMap.put(templateKey, template);
    }

    private static Map<String, Object> mapOf(Object... values) {
        Map<String, Object> map = new LinkedHashMap<>();
        for (int i = 0; i + 1 < values.length; i += 2) {
            map.put(String.valueOf(values[i]), values[i + 1]);
        }
        return map;
    }

    private static Map<String, Object> schemaOf() {
        return mapOf("type", "object");
    }

}
