package cn.iocoder.yudao.module.devops.service.pipeline;

import cn.iocoder.yudao.module.devops.controller.admin.pipeline.vo.PipelineCommandTemplateRespVO;
import cn.iocoder.yudao.module.devops.controller.admin.pipeline.vo.PipelineNodeTypeRespVO;
import cn.iocoder.yudao.module.devops.enums.DeploymentModeEnum;
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

    private static final String DEFAULT_DEPLOYMENT_MANIFEST_YAML = """
            apiVersion: apps/v1
            kind: Deployment
            metadata:
              name: ${APP_KEY}
              namespace: ${NAMESPACE}
            spec:
              replicas: 1
              selector:
                matchLabels:
                  app: ${APP_KEY}
              template:
                metadata:
                  labels:
                    app: ${APP_KEY}
                spec:
                  containers:
                    - name: app
                      image: ${IMAGE}
                      ports:
                        - containerPort: 8080
            """;

    public static final String TYPE_CHECKOUT = "CHECKOUT";
    public static final String TYPE_UNIT_TEST = "UNIT_TEST";
    public static final String TYPE_BUILD_ARTIFACT = "BUILD_ARTIFACT";
    public static final String TYPE_BUILD_IMAGE = "BUILD_IMAGE";
    public static final String TYPE_REPORT_ARTIFACTS = "REPORT_ARTIFACTS";
    public static final String TYPE_MAVEN_BUILD_JAR = "MAVEN_BUILD_JAR";
    public static final String TYPE_NPM_BUILD = "NPM_BUILD";
    public static final String TYPE_DOCKER_BUILD_PUSH = "DOCKER_BUILD_PUSH";
    public static final String TYPE_ARTIFACT_UPLOAD = "ARTIFACT_UPLOAD";
    public static final String TYPE_EXECUTE_SHELL = "EXECUTE_SHELL";
    public static final String TYPE_SSH_PUBLISH = "SSH_PUBLISH";
    public static final String TYPE_MOCK = "MOCK";
    public static final String TYPE_APPROVAL = "APPROVAL";
    public static final String TYPE_DEPLOY_K8S = "DEPLOY_K8S";
    public static final String TYPE_CONTAINER_DEPLOY = "CONTAINER_DEPLOY";

    private final Map<String, PipelineNodeTypeRespVO> nodeTypeMap = new LinkedHashMap<>();
    private final Map<String, PipelineCommandTemplateRespVO> commandTemplateMap = new LinkedHashMap<>();

    public PipelineNodeRegistryServiceImpl() {
        registerNode(TYPE_CHECKOUT, "拉取代码", "JENKINS", "git-branch", true, null,
                mapOf("cleanBeforeCheckout", true, "checkoutSubdirectory", "", "shallowClone", false),
                schemaOf(List.of(), mapOf(
                        "cleanBeforeCheckout", booleanParam("构建前清理工作区", true),
                        "checkoutSubdirectory", stringParam("检出子目录", ""),
                        "shallowClone", booleanParam("浅克隆", false))));
        registerNode(TYPE_UNIT_TEST, "单元测试", "JENKINS", "test-tube", true, null,
                mapOf("commandTemplateKey", "maven_test", "reportPattern", "**/surefire-reports/*.xml"),
                schemaOf(List.of("commandTemplateKey"), mapOf(
                        "commandTemplateKey", stringParam("命令模板", "maven_test"),
                        "reportPattern", stringParam("测试报告匹配", "**/surefire-reports/*.xml"))));
        registerNode(TYPE_BUILD_ARTIFACT, "构建制品", "JENKINS", "package", true, null,
                mapOf("commandTemplateKey", "maven_package_skip_tests", "artifactPattern", "**/target/*.jar"),
                schemaOf(List.of("commandTemplateKey", "artifactPattern"), mapOf(
                        "commandTemplateKey", stringParam("命令模板", "maven_package_skip_tests"),
                        "artifactPattern", stringParam("制品匹配", "**/target/*.jar"))));
        registerNode(TYPE_BUILD_IMAGE, "构建镜像", "JENKINS", "container", true, null,
                mapOf("commandTemplateKey", "docker_build", "dockerfile", "Dockerfile", "context", "."),
                schemaOf(List.of("commandTemplateKey", "dockerfile", "context"), mapOf(
                        "commandTemplateKey", stringParam("命令模板", "docker_build"),
                        "dockerfile", stringParam("Dockerfile 路径", "Dockerfile"),
                        "context", stringParam("构建上下文", "."))));
        registerNode(TYPE_REPORT_ARTIFACTS, "上报产物", "JENKINS", "upload-cloud", true, null,
                mapOf("artifactPattern", "**/target/*.jar", "fingerprint", true, "allowEmptyArchive", false,
                        "onlyIfSuccessful", true),
                artifactUploadSchema());
        registerNode(TYPE_MAVEN_BUILD_JAR, "Maven Jar 构建", "JENKINS", "package", true, null,
                mapOf("workingDir", ".", "goals", "clean package", "profiles", "", "skipTests", true,
                        "mavenOptions", "", "settingsConfigId", "", "artifactPattern", "**/target/*.jar"),
                schemaOf(List.of("workingDir", "goals", "artifactPattern"), mapOf(
                        "workingDir", stringParam("工作目录", "."),
                        "goals", stringParam("Maven Goals", "clean package"),
                        "profiles", stringParam("Maven Profiles", ""),
                        "skipTests", booleanParam("跳过测试", true),
                        "mavenOptions", stringParam("Maven Options", ""),
                        "settingsConfigId", stringParam("Maven settings 配置 ID", ""),
                        "artifactPattern", stringParam("制品匹配", "**/target/*.jar"))));
        registerNode(TYPE_NPM_BUILD, "NPM 构建", "JENKINS", "box", true, null,
                mapOf("workingDir", ".", "packageManager", "npm", "installCommand", "npm ci",
                        "buildCommand", "npm run build", "nodeVersionTool", "", "distPattern", "dist/**",
                        "cacheEnabled", false),
                schemaOf(List.of("workingDir", "packageManager", "installCommand", "buildCommand", "distPattern"),
                        mapOf("workingDir", stringParam("工作目录", "."),
                                "packageManager", enumParam("包管理器", "npm", List.of("npm", "pnpm", "yarn")),
                                "installCommand", stringParam("安装命令", "npm ci"),
                                "buildCommand", stringParam("构建命令", "npm run build"),
                                "nodeVersionTool", stringParam("Jenkins NodeJS 工具名", ""),
                                "distPattern", stringParam("构建产物匹配", "dist/**"),
                                "cacheEnabled", booleanParam("启用依赖缓存", false))));
        registerNode(TYPE_DOCKER_BUILD_PUSH, "Docker 构建推送", "JENKINS", "container", true, null,
                mapOf("imageName", "${APP_KEY}", "imageTagExpression", "${COMMIT_SHA}", "dockerfile", "Dockerfile",
                        "context", ".", "buildArgs", mapOf(), "registryUrl", "", "registryCredentialsId", "",
                        "push", true, "pushLatest", false),
                schemaOf(List.of("imageName", "imageTagExpression", "dockerfile", "context"), mapOf(
                        "imageName", stringParam("镜像名称", "${APP_KEY}"),
                        "imageTagExpression", stringParam("镜像标签表达式", "${COMMIT_SHA}"),
                        "dockerfile", stringParam("Dockerfile 路径", "Dockerfile"),
                        "context", stringParam("构建上下文", "."),
                        "buildArgs", objectParam("构建参数"),
                        "registryUrl", stringParam("镜像仓库地址", ""),
                        "registryCredentialsId", stringParam("Jenkins 仓库凭据 ID", ""),
                        "push", booleanParam("推送镜像", true),
                        "pushLatest", booleanParam("推送 latest 标签", false))));
        registerNode(TYPE_ARTIFACT_UPLOAD, "制品归档", "JENKINS", "upload-cloud", true, null,
                mapOf("artifactPattern", "**/target/*.jar", "fingerprint", true, "allowEmptyArchive", false,
                        "onlyIfSuccessful", true, "stashName", ""),
                artifactUploadSchema());
        registerNode(TYPE_EXECUTE_SHELL, "执行 Shell", "JENKINS", "terminal", true, null,
                mapOf("workingDir", ".", "script", "echo hello"),
                schemaOf(List.of("script"), mapOf(
                        "workingDir", stringParam("工作目录", "."),
                        "script", textAreaParam("Shell 脚本", "echo hello"))));
        registerNode(TYPE_SSH_PUBLISH, "SSH 发布", "JENKINS", "send", true, null,
                mapOf("configName", "", "sourceFiles", "", "removePrefix", "", "remoteDirectory", "",
                        "execCommand", "", "execTimeoutMillis", 120000, "verbose", true),
                schemaOf(List.of("configName"), mapOf(
                        "configName", stringParam("Jenkins SSH Server 名称", ""),
                        "sourceFiles", stringParam("发送文件", ""),
                        "removePrefix", stringParam("移除路径前缀", ""),
                        "remoteDirectory", stringParam("远端目录", ""),
                        "execCommand", textAreaParam("远端执行命令", ""),
                        "execTimeoutMillis", integerParam("命令超时毫秒", 120000),
                        "verbose", booleanParam("输出详细日志", true))));
        registerNode(TYPE_MOCK, "Mock 节点", "JENKINS", "play-circle", true, null,
                mapOf("message", "mock node"), schemaOf(List.of(), mapOf("message", stringParam("消息", "mock node"))));
        registerNode(TYPE_APPROVAL, "审批", "PLATFORM", "check-circle", true, null,
                mapOf("processDefinitionKey", ""),
                platformSchemaOf(List.of("processDefinitionKey"), mapOf(
                        "processDefinitionKey", stringParam("BPM 流程定义 Key", ""))));
        registerNode(TYPE_DEPLOY_K8S, "部署 K8S", "PLATFORM", "rocket", false,
                "后续阶段开放：平台部署", mapOf(), schemaOf());
        registerNode(TYPE_CONTAINER_DEPLOY, "容器部署", "PLATFORM", "rocket", true, null,
                mapOf("infraType", "K8S", "deployMode", DeploymentModeEnum.RAW_MANIFEST.getMode(),
                        "manifestYaml", DEFAULT_DEPLOYMENT_MANIFEST_YAML,
                        "containerName", "app", "image", "${APP_KEY}:${COMMIT_SHA}",
                        "replicas", null, "rolloutTimeoutSeconds", 300),
                platformSchemaOf(List.of("infraType", "deployMode", "manifestYaml", "containerName", "image"),
                        mapOf("infraType", enumParam("基础设施类型", "K8S", List.of("K8S")),
                                "deployMode", enumParam("部署模式", DeploymentModeEnum.RAW_MANIFEST.getMode(),
                                        List.of(DeploymentModeEnum.RAW_MANIFEST.getMode())),
                                "manifestYaml", textAreaParam("Deployment YAML", DEFAULT_DEPLOYMENT_MANIFEST_YAML),
                                "containerName", stringParam("目标容器名称", "app"),
                                "image", stringParam("镜像地址或表达式", "${APP_KEY}:${COMMIT_SHA}"),
                                "replicas", integerParam("副本数（为空则使用 YAML 配置）", null),
                                "rolloutTimeoutSeconds", integerParam("Rollout 超时秒数", 300))));

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

        attachCommandTemplatesToNodeTypes();
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
    public PipelineNodeTypeRespVO getNodeType(String type) {
        return nodeTypeMap.get(type);
    }

    @Override
    public PipelineCommandTemplateRespVO getCommandTemplate(String templateKey) {
        return commandTemplateMap.get(templateKey);
    }

    public static boolean isPlatformNode(String nodeType) {
        return TYPE_APPROVAL.equals(nodeType) || TYPE_DEPLOY_K8S.equals(nodeType)
                || TYPE_CONTAINER_DEPLOY.equals(nodeType);
    }

    public static boolean isJenkinsExecutableNode(String nodeType) {
        return !isPlatformNode(nodeType);
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

    private void attachCommandTemplatesToNodeTypes() {
        for (PipelineNodeTypeRespVO nodeType : nodeTypeMap.values()) {
            nodeType.setCommandTemplates(commandTemplateMap.values().stream()
                    .filter(template -> nodeType.getType().equals(template.getNodeType()))
                    .toList());
        }
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

    private static Map<String, Object> schemaOf(List<String> required, Map<String, Object> properties) {
        properties.put("agentLabel", stringParam("Jenkins Agent 标签", ""));
        properties.put("toolJdk", remoteSelectParam("Jenkins JDK 工具名", "",
                "/devops/pipeline/jenkins-tools?type=JDK"));
        properties.put("toolMaven", remoteSelectParam("Jenkins Maven 工具名", "",
                "/devops/pipeline/jenkins-tools?type=MAVEN"));
        properties.put("env", objectParam("环境变量"));
        return mapOf("type", "object", "required", required, "properties", properties);
    }

    private static Map<String, Object> platformSchemaOf(List<String> required, Map<String, Object> properties) {
        return mapOf("type", "object", "required", required, "properties", properties);
    }

    private static Map<String, Object> artifactUploadSchema() {
        return schemaOf(List.of("artifactPattern"), mapOf(
                "artifactPattern", stringParam("制品匹配", "**/target/*.jar"),
                "fingerprint", booleanParam("记录指纹", true),
                "allowEmptyArchive", booleanParam("允许空归档", false),
                "onlyIfSuccessful", booleanParam("仅成功时归档", true),
                "stashName", stringParam("Stash 名称", "")));
    }

    private static Map<String, Object> stringParam(String title, String defaultValue) {
        return mapOf("type", "string", "title", title, "default", defaultValue);
    }

    private static Map<String, Object> textAreaParam(String title, String defaultValue) {
        return mapOf("type", "string", "title", title, "default", defaultValue, "x-component", "textarea");
    }

    private static Map<String, Object> booleanParam(String title, Boolean defaultValue) {
        return mapOf("type", "boolean", "title", title, "default", defaultValue);
    }

    private static Map<String, Object> integerParam(String title, Integer defaultValue) {
        return mapOf("type", "integer", "title", title, "default", defaultValue);
    }

    private static Map<String, Object> objectParam(String title) {
        return mapOf("type", "object", "title", title, "default", mapOf());
    }

    private static Map<String, Object> enumParam(String title, String defaultValue, List<String> values) {
        return mapOf("type", "string", "title", title, "default", defaultValue, "enum", values);
    }

    private static Map<String, Object> remoteSelectParam(String title, String defaultValue, String url) {
        return mapOf("type", "string", "title", title, "default", defaultValue,
                "x-component", "select",
                "x-optionSource", mapOf("type", "remote", "url", url, "labelField", "name", "valueField", "name"));
    }

}
