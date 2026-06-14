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

    public static final String TYPE_CODE_MERGE = "CODE_MERGE";
    public static final String TYPE_EXECUTE_SHELL = "EXECUTE_SHELL";

    private final Map<String, PipelineNodeTypeRespVO> nodeTypeMap = new LinkedHashMap<>();
    private final Map<String, PipelineCommandTemplateRespVO> commandTemplateMap = new LinkedHashMap<>();

    public PipelineNodeRegistryServiceImpl() {
        registerNode(TYPE_CODE_MERGE, "代码合并", "PLATFORM", "git-merge", true, null,
                mapOf(), // 无参数
                schemaOf());
        registerNode(TYPE_EXECUTE_SHELL, "执行 Shell", "BUILD", "terminal", true, null,
                mapOf("script", "", "shellType", "bash", "env", new ArrayList<>()),
                schemaOf(List.of("script"), mapOf(
                        "script", textAreaParam("Shell 脚本", ""),
                        "shellType", enumParam("Shell 类型", "bash", List.of("bash", "zsh", "sh")),
                        "env", arrayParam("环境变量", List.of()))));

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
        return TYPE_CODE_MERGE.equals(nodeType);
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

    private static Map<String, Object> enumParam(String title, String defaultValue, List<String> values) {
        return mapOf("type", "string", "title", title, "default", defaultValue, "enum", values);
    }

    private static Map<String, Object> arrayParam(String title, List<?> defaultValue) {
        return mapOf("type", "array", "title", title, "default", defaultValue,
                "items", mapOf("type", "object", "properties", mapOf(
                        "key", stringParam("变量名", ""),
                        "value", stringParam("变量值", ""))));
    }

}
