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

    public static final String TYPE_CODE_MERGE = "CodeMerge";
    public static final String TYPE_CODE_MERGE_LEGACY = "CODE_MERGE";
    public static final String TYPE_APPROVAL = "APPROVAL";
    public static final String TYPE_K8S_DEPLOY = "K8sDeploy";
    public static final String TYPE_K8S_IMAGE_UPGRADE = "K8sImageUpgrade";
    public static final String TYPE_EXECUTE_SHELL = "EXECUTE_SHELL";
    public static final String TYPE_COMMAND = "Command";
    public static final String TYPE_SETUP_JAVA = "SetupJava";
    public static final String TYPE_SETUP_MAVEN_SETTINGS = "SetupMavenSettings";
    public static final String TYPE_UNIT_TEST_REPORT = "UnitTestReport";
    public static final String TYPE_ARTIFACT_UPLOAD = "ArtifactUpload";
    public static final String TYPE_JAVA_P3C_SCAN = "JavaP3CScan";
    public static final String TYPE_PRIVATE_REGISTRY_DOCKER_BUILD = "PrivateRegistryDockerBuild";
    public static final String TYPE_DOCKER_IMAGE_EXPORT_OBJECT_STORAGE = "DockerImageExportObjectStorage";
    public static final String TYPE_DOCKER_IMAGE_ARCHIVE_IMPORT = "DockerImageArchiveImport";

    private final Map<String, PipelineNodeTypeRespVO> nodeTypeMap = new LinkedHashMap<>();
    private final Map<String, PipelineCommandTemplateRespVO> commandTemplateMap = new LinkedHashMap<>();

    public PipelineNodeRegistryServiceImpl() {
        registerNode(TYPE_CODE_MERGE, "代码合并", "PLATFORM", "git-merge", true, null,
                mapOf("baseBranch", "${SOURCE_BRANCH}", "targetBranch", "${BRANCH_NAME}",
                        "pushOnSuccess", true, "branches", new ArrayList<>(), "branchesFromSubmit", true),
                schemaOf(List.of(), mapOf(
                        "baseBranch", stringParam("基础分支", "${SOURCE_BRANCH}"),
                        "targetBranch", stringParam("部署分支", "${BRANCH_NAME}"),
                        "pushOnSuccess", mapOf("type", "boolean", "title", "合并成功后推送", "default", true),
                        "branches", stringArrayParam("待合并分支", List.of()),
                        "branchesFromSubmit", mapOf("type", "boolean", "title", "使用发布提交分支", "default", true))));
        registerNode(TYPE_APPROVAL, "审批", "GATE", "shield-check", true, null,
                mapOf("processDefinitionKey", ""),
                schemaOf(List.of("processDefinitionKey"), mapOf(
                        "processDefinitionKey", stringParam("流程定义标识", ""))));
        registerNode(TYPE_K8S_DEPLOY, "K8s 集群部署", "DEPLOY", "ship", true, null,
                mapOf("deployMode", "RAW_MANIFEST", "manifestYaml", "", "containerName", "", "image", "",
                        "replicas", null, "rolloutTimeoutSeconds", 300),
                schemaOf(List.of("deployMode", "manifestYaml", "containerName", "image"), mapOf(
                        "deployMode", enumParam("部署模式", "RAW_MANIFEST", List.of("RAW_MANIFEST")),
                        "manifestYaml", textAreaParam("Deployment YAML", ""),
                        "containerName", stringParam("容器名称", ""),
                        "image", stringParam("目标镜像", ""),
                        "replicas", integerParam("副本数", null),
                        "rolloutTimeoutSeconds", integerParam("Rollout 超时秒数", 300))));
        registerNode(TYPE_K8S_IMAGE_UPGRADE, "K8s 镜像版本升级", "DEPLOY", "refresh-cw", true, null,
                mapOf("workloadKind", "Deployment", "workloadName", "", "containerName", "", "image", "",
                        "replicas", null, "rolloutTimeoutSeconds", 300),
                schemaOf(List.of("workloadKind", "workloadName", "containerName", "image"), mapOf(
                        "workloadKind", enumParam("工作负载类型", "Deployment", List.of("Deployment")),
                        "workloadName", stringParam("Deployment 名称", ""),
                        "containerName", stringParam("容器名称", ""),
                        "image", stringParam("目标镜像", ""),
                        "replicas", integerParam("副本数", null),
                        "rolloutTimeoutSeconds", integerParam("Rollout 超时秒数", 300))));
        registerNode(TYPE_EXECUTE_SHELL, "执行 Shell", "BUILD", "terminal", true, null,
                mapOf("script", "", "shellType", "bash", "env", new ArrayList<>()),
                schemaOf(List.of("script"), mapOf(
                        "script", textAreaParam("Shell 脚本", ""),
                        "shellType", enumParam("Shell 类型", "bash", List.of("bash", "zsh", "sh")),
                        "env", arrayParam("环境变量", List.of()))));
        registerNode(TYPE_COMMAND, "执行命令", "BUILD", "terminal", true, null,
                mapOf("run", "", "shellType", "bash", "env", new ArrayList<>()),
                schemaOf(List.of("run"), mapOf(
                        "run", textAreaParam("执行命令", ""),
                        "shellType", enumParam("Shell 类型", "bash", List.of("bash", "zsh", "sh")),
                        "env", arrayParam("环境变量", List.of()))));
        registerNode(TYPE_PRIVATE_REGISTRY_DOCKER_BUILD, "镜像构建并推送至自定义镜像仓库", "PLATFORM", "container", true, null,
                mapOf("artifact", "", "image", "", "certificate", mapOf(
                                "type", "usernamePassword", "username", "", "password", ""),
                        "dockerfilePath", "Dockerfile", "contextPath", "", "noCache", false,
                        "variables", new ArrayList<>(), "buildkitVersion", "v0.8.0"),
                schemaOf(List.of("artifact", "image", "certificate", "dockerfilePath"), mapOf(
                        "artifact", stringParam("制品名称", ""),
                        "image", stringParam("镜像地址", ""),
                        "certificate", objectParam("镜像仓库凭证", mapOf(
                                "type", enumParam("凭证类型", "usernamePassword",
                                        List.of("usernamePassword")),
                                "username", stringParam("用户名", ""),
                                "password", passwordParam("密码", ""))),
                        "dockerfilePath", stringParam("Dockerfile 路径", "Dockerfile"),
                        "contextPath", stringParam("构建上下文", ""),
                        "noCache", mapOf("type", "boolean", "title", "不使用缓存", "default", false),
                        "variables", arrayParam("构建参数", List.of()),
                        "buildkitVersion", enumParam("BuildKit 版本", "v0.8.0",
                                List.of("v0.8.0", "v0.9.0", "v0.11.6")))));
        registerNode(TYPE_DOCKER_IMAGE_EXPORT_OBJECT_STORAGE, "导出镜像并上传对象存储", "BUILD", "archive", true, null,
                mapOf("image", "", "archiveFormat", "docker-archive", "compression", "none",
                        "outputFileName", "", "registryTlsVerify", true, "registryCertificate", mapOf(
                                "type", "usernamePassword", "username", "", "password", ""),
                        "storage", mapOf(
                                "type", "s3", "endpoint", "", "path", "", "region", "",
                                "forcePathStyle", false, "certificate", mapOf(
                                        "type", "accessKey", "accessKeyId", "", "accessKeySecret", "")),
                        "overwrite", false),
                schemaOf(List.of("image", "registryCertificate", "storage"), mapOf(
                        "image", stringParam("镜像地址", ""),
                        "archiveFormat", enumParam("归档格式", "docker-archive",
                                List.of("docker-archive", "oci-archive")),
                        "compression", enumParam("压缩方式", "none", List.of("none", "gzip", "zstd")),
                        "outputFileName", stringParam("输出文件名", ""),
                        "registryTlsVerify", mapOf("type", "boolean", "title", "校验镜像仓库 TLS", "default", true),
                        "registryCertificate", objectParam("镜像仓库凭证", mapOf(
                                "type", enumParam("凭证类型", "usernamePassword",
                                        List.of("usernamePassword")),
                                "username", stringParam("用户名", ""),
                                "password", passwordParam("密码", ""))),
                        "storage", objectParam("对象存储上传配置", mapOf(
                                "type", enumParam("存储类型", "s3", List.of("s3")),
                                "endpoint", stringParam("Endpoint", ""),
                                "path", stringParam("对象路径", ""),
                                "region", stringParam("Region", ""),
                                "forcePathStyle", mapOf("type", "boolean", "title", "使用 Path Style", "default", false),
                                "certificate", objectParam("对象存储凭证", mapOf(
                                        "type", enumParam("凭证类型", "accessKey", List.of("accessKey")),
                                        "accessKeyId", stringParam("AccessKey ID", ""),
                                        "accessKeySecret", passwordParam("AccessKey Secret", ""))))),
                        "overwrite", mapOf("type", "boolean", "title", "覆盖已存在对象", "default", false))));
        registerNode(TYPE_DOCKER_IMAGE_ARCHIVE_IMPORT, "导入镜像包到镜像仓库", "BUILD", "upload-cloud", true, null,
                mapOf("fileUrl", "${FILE_URL}", "image", "", "archiveFormat", "docker-archive",
                        "compression", "none", "registryTlsVerify", true, "certificate", mapOf(
                                "type", "usernamePassword", "username", "", "password", "")),
                schemaOf(List.of("fileUrl", "image", "certificate"), mapOf(
                        "fileUrl", stringParam("镜像包文件 URL", "${FILE_URL}"),
                        "image", stringParam("目标镜像地址", ""),
                        "archiveFormat", enumParam("归档格式", "docker-archive",
                                List.of("docker-archive", "oci-archive")),
                        "compression", enumParam("压缩方式", "none", List.of("none", "gzip", "zstd")),
                        "registryTlsVerify", mapOf("type", "boolean", "title", "校验镜像仓库 TLS", "default", true),
                        "certificate", objectParam("镜像仓库凭证", mapOf(
                                "type", enumParam("凭证类型", "usernamePassword",
                                        List.of("usernamePassword")),
                                "username", stringParam("用户名", ""),
                                "password", passwordParam("密码", ""))))));
        registerNode(TYPE_SETUP_JAVA, "安装 Java 环境", "BUILD", "package", true, null,
                mapOf("jdkVersion", "", "mavenVersion", ""),
                schemaOf(List.of(), mapOf(
                        "jdkVersion", stringParam("JDK 版本", ""),
                        "mavenVersion", stringParam("Maven 版本", ""))));
        registerNode(TYPE_SETUP_MAVEN_SETTINGS, "配置 Maven Settings", "BUILD", "settings", true, null,
                mapOf("mavenSettingXmlPath", ""),
                schemaOf(List.of(), mapOf(
                        "mavenSettingXmlPath", stringParam("Maven Settings 路径", ""))));
        registerNode(TYPE_UNIT_TEST_REPORT, "单元测试报告", "REPORT", "file-text", true, null,
                mapOf("reportPath", "", "reporter", "", "failOnError", true),
                schemaOf(List.of("reportPath"), mapOf(
                        "reportPath", stringParam("报告路径", ""),
                        "reporter", stringParam("报告类型", ""),
                        "failOnError", mapOf("type", "boolean", "title", "失败时中断", "default", true))));
        registerNode(TYPE_ARTIFACT_UPLOAD, "构建物上传", "BUILD", "upload", true, null,
                mapOf("uploadType", "", "filePath", new ArrayList<>(), "version", ""),
                schemaOf(List.of(), mapOf(
                        "uploadType", stringParam("上传类型", ""),
                        "filePath", arrayParam("文件路径", List.of()),
                        "version", stringParam("版本", ""))));
        registerNode(TYPE_JAVA_P3C_SCAN, "Java 代码规约扫描", "SCAN", "scan-line", true, null,
                mapOf("incrementalScan", false, "customRule", false, "jdkVersion", "", "mavenVersion", "", "ruleSet", ""),
                schemaOf(List.of(), mapOf(
                        "jdkVersion", stringParam("JDK 版本", ""),
                        "mavenVersion", stringParam("Maven 版本", ""),
                        "ruleSet", textAreaParam("规则集", ""))));

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
        return TYPE_CODE_MERGE.equals(nodeType) || TYPE_CODE_MERGE_LEGACY.equals(nodeType)
                || TYPE_APPROVAL.equals(nodeType) || TYPE_K8S_DEPLOY.equals(nodeType)
                || TYPE_K8S_IMAGE_UPGRADE.equals(nodeType)
                || TYPE_PRIVATE_REGISTRY_DOCKER_BUILD.equals(nodeType);
    }

    public static boolean isCommandNode(String nodeType) {
        return TYPE_EXECUTE_SHELL.equals(nodeType) || TYPE_COMMAND.equals(nodeType);
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
        properties.put("env", objectParam("环境变量"));
        return mapOf("type", "object", "required", required, "properties", properties);
    }

    private static Map<String, Object> stringParam(String title, String defaultValue) {
        return mapOf("type", "string", "title", title, "default", defaultValue);
    }

    private static Map<String, Object> textAreaParam(String title, String defaultValue) {
        return mapOf("type", "string", "title", title, "default", defaultValue, "x-component", "textarea");
    }

    private static Map<String, Object> objectParam(String title) {
        return mapOf("type", "object", "title", title, "default", mapOf());
    }

    private static Map<String, Object> objectParam(String title, Map<String, Object> properties) {
        return mapOf("type", "object", "title", title, "properties", properties, "default", mapOf());
    }

    private static Map<String, Object> enumParam(String title, String defaultValue, List<String> values) {
        return mapOf("type", "string", "title", title, "default", defaultValue, "enum", values);
    }

    private static Map<String, Object> passwordParam(String title, String defaultValue) {
        return mapOf("type", "string", "title", title, "default", defaultValue, "x-component", "password");
    }

    private static Map<String, Object> integerParam(String title, Integer defaultValue) {
        return mapOf("type", "integer", "title", title, "default", defaultValue);
    }

    private static Map<String, Object> arrayParam(String title, List<?> defaultValue) {
        return mapOf("type", "array", "title", title, "default", defaultValue,
                "items", mapOf("type", "object", "properties", mapOf(
                        "key", stringParam("变量名", ""),
                        "value", stringParam("变量值", ""))));
    }

    private static Map<String, Object> stringArrayParam(String title, List<String> defaultValue) {
        return mapOf("type", "array", "title", title, "default", defaultValue,
                "items", mapOf("type", "string"));
    }

}
