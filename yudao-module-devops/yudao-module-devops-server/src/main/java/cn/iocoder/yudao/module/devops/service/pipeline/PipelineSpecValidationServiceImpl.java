package cn.iocoder.yudao.module.devops.service.pipeline;

import cn.hutool.core.collection.CollUtil;
import cn.hutool.core.util.StrUtil;
import cn.iocoder.yudao.framework.common.util.json.JsonUtils;
import cn.iocoder.yudao.module.devops.controller.admin.pipeline.vo.PipelineCommandTemplateRespVO;
import cn.iocoder.yudao.module.devops.controller.admin.pipeline.vo.PipelineNodeTypeRespVO;
import cn.iocoder.yudao.module.devops.controller.admin.pipeline.vo.PipelineValidationMessageRespVO;
import cn.iocoder.yudao.module.devops.controller.admin.pipeline.vo.PipelineValidationRespVO;
import cn.iocoder.yudao.module.devops.framework.pipeline.PipelineSpec;
import jakarta.annotation.Resource;
import org.springframework.stereotype.Service;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Queue;
import java.util.Set;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

/**
 * DevOps 流水线 DSL 校验服务。
 */
@Service
public class PipelineSpecValidationServiceImpl implements PipelineSpecValidationService {

    private static final Pattern NODE_ID_PATTERN = Pattern.compile("[a-zA-Z][a-zA-Z0-9_-]{0,63}");
    private static final Pattern ENV_KEY_PATTERN = Pattern.compile("[A-Za-z_][A-Za-z0-9_]*");
    private static final String PARAM_COMMAND_TEMPLATE_KEY = "commandTemplateKey";

    @Resource
    private PipelineNodeRegistryService pipelineNodeRegistryService;

    @Override
    public PipelineValidationRespVO validate(String specJson) {
        PipelineValidationRespVO validation = new PipelineValidationRespVO();
        PipelineSpec spec = parseSpec(specJson, validation);
        if (spec != null) {
            validate(spec, validation);
        }
        validation.setValid(CollUtil.isEmpty(validation.getErrors()));
        return validation;
    }

    @Override
    public PipelineValidationRespVO validate(PipelineSpec spec) {
        PipelineValidationRespVO validation = new PipelineValidationRespVO();
        validate(spec, validation);
        validation.setValid(CollUtil.isEmpty(validation.getErrors()));
        return validation;
    }

    @Override
    public PipelineSpec parseSpec(String specJson, PipelineValidationRespVO validation) {
        if (StrUtil.isBlank(specJson)) {
            addError(validation, "specJson", null, "SPEC_JSON_REQUIRED", "流水线 DSL JSON 不能为空");
            return null;
        }
        try {
            PipelineSpec spec = JsonUtils.parseObject(specJson, PipelineSpec.class);
            if (spec == null) {
                addError(validation, "specJson", null, "SPEC_JSON_INVALID", "流水线 DSL JSON 无效");
            }
            return spec;
        } catch (RuntimeException ex) {
            addError(validation, "specJson", null, "SPEC_JSON_INVALID", "流水线 DSL JSON 格式错误");
            return null;
        }
    }

    @Override
    public List<PipelineSpec.Node> sortNodes(PipelineSpec spec) {
        Map<String, PipelineSpec.Node> nodeMap = spec.getNodes().stream()
                .collect(Collectors.toMap(PipelineSpec.Node::getId, node -> node, (a, b) -> a, LinkedHashMap::new));
        Map<String, List<String>> outgoing = new HashMap<>();
        Map<String, Integer> inDegree = new HashMap<>();
        nodeMap.keySet().forEach(nodeId -> inDegree.put(nodeId, 0));
        for (PipelineSpec.Edge edge : spec.getEdges()) {
            outgoing.computeIfAbsent(edge.getSource(), key -> new ArrayList<>()).add(edge.getTarget());
            inDegree.put(edge.getTarget(), inDegree.getOrDefault(edge.getTarget(), 0) + 1);
        }

        Queue<String> queue = new ArrayDeque<>();
        inDegree.forEach((nodeId, degree) -> {
            if (degree == 0) {
                queue.add(nodeId);
            }
        });
        List<PipelineSpec.Node> sorted = new ArrayList<>();
        while (!queue.isEmpty()) {
            String nodeId = queue.poll();
            sorted.add(nodeMap.get(nodeId));
            for (String target : outgoing.getOrDefault(nodeId, List.of())) {
                int degree = inDegree.get(target) - 1;
                inDegree.put(target, degree);
                if (degree == 0) {
                    queue.add(target);
                }
            }
        }
        return sorted;
    }

    private void validate(PipelineSpec spec, PipelineValidationRespVO validation) {
        if (spec == null) {
            addError(validation, "spec", null, "SPEC_REQUIRED", "流水线 DSL 不能为空");
            return;
        }
        if (spec.getNodes() == null) {
            spec.setNodes(new ArrayList<>());
        }
        if (spec.getEdges() == null) {
            spec.setEdges(new ArrayList<>());
        }
        if (CollUtil.isEmpty(spec.getNodes())) {
            addError(validation, "nodes", null, "NODE_REQUIRED", "流水线至少需要一个节点");
            return;
        }
        validateNodes(spec, validation);
        validateEdges(spec, validation);
        validateTopology(spec, validation);
    }

    private void validateNodes(PipelineSpec spec, PipelineValidationRespVO validation) {
        Set<String> nodeIds = new HashSet<>();
        for (PipelineSpec.Node node : spec.getNodes()) {
            if (node == null || StrUtil.isBlank(node.getId())) {
                addError(validation, "nodes", null, "NODE_ID_REQUIRED", "节点编号不能为空");
                continue;
            }
            if (!NODE_ID_PATTERN.matcher(node.getId()).matches()) {
                addError(validation, "nodes", node.getId(), "NODE_ID_INVALID",
                        "节点编号只能包含字母、数字、中划线和下划线，且必须以字母开头");
            }
            if (!nodeIds.add(node.getId())) {
                addError(validation, "nodes", node.getId(), "NODE_ID_DUPLICATE", "节点编号重复");
            }
            PipelineNodeTypeRespVO nodeType = pipelineNodeRegistryService.getNodeType(node.getType());
            if (nodeType == null) {
                addError(validation, "nodes", node.getId(), "NODE_TYPE_NOT_SUPPORTED",
                        "不支持的节点类型：" + node.getType());
                continue;
            }
            if (!Boolean.TRUE.equals(nodeType.getEnabled())) {
                addError(validation, "nodes", node.getId(), "NODE_TYPE_DISABLED",
                        "节点类型暂未开放：" + nodeType.getName());
            }
            validateCommandTemplate(node, validation);
            validateCommonJenkinsParams(node, validation);
            validateJenkinsNodeParams(node, validation);
        }
    }

    private void validateCommandTemplate(PipelineSpec.Node node, PipelineValidationRespVO validation) {
        if (!PipelineNodeRegistryServiceImpl.TYPE_UNIT_TEST.equals(node.getType())
                && !PipelineNodeRegistryServiceImpl.TYPE_BUILD_ARTIFACT.equals(node.getType())
                && !PipelineNodeRegistryServiceImpl.TYPE_BUILD_IMAGE.equals(node.getType())) {
            return;
        }
        Object templateKey = node.getParams() == null ? null : node.getParams().get(PARAM_COMMAND_TEMPLATE_KEY);
        if (templateKey == null || StrUtil.isBlank(String.valueOf(templateKey))) {
            addError(validation, "params.commandTemplateKey", node.getId(), "COMMAND_TEMPLATE_REQUIRED",
                    "构建/测试节点必须选择命令模板");
            return;
        }
        PipelineCommandTemplateRespVO template = pipelineNodeRegistryService.getCommandTemplate(String.valueOf(templateKey));
        if (template == null || !Boolean.TRUE.equals(template.getEnabled())) {
            addError(validation, "params.commandTemplateKey", node.getId(), "COMMAND_TEMPLATE_NOT_EXISTS",
                    "命令模板不存在或已停用");
            return;
        }
        if (!node.getType().equals(template.getNodeType())) {
            addError(validation, "params.commandTemplateKey", node.getId(), "COMMAND_TEMPLATE_TYPE_MISMATCH",
                    "命令模板与节点类型不匹配");
        }
    }

    @SuppressWarnings("unchecked")
    private void validateCommonJenkinsParams(PipelineSpec.Node node, PipelineValidationRespVO validation) {
        validateOptionalMap(node, validation, "env");
        if (node.getParams() == null || !(node.getParams().get("env") instanceof Map<?, ?> envMap)) {
            return;
        }
        for (Object key : envMap.keySet()) {
            if (key == null || !ENV_KEY_PATTERN.matcher(String.valueOf(key)).matches()) {
                addError(validation, "params.env", node.getId(), "PARAM_ENV_KEY_INVALID",
                        "环境变量名称格式不正确：" + key);
            }
        }
    }

    private void validateJenkinsNodeParams(PipelineSpec.Node node, PipelineValidationRespVO validation) {
        switch (node.getType()) {
            case PipelineNodeRegistryServiceImpl.TYPE_MAVEN_BUILD_JAR -> {
                validateRequiredString(node, validation, "workingDir", "PARAM_REQUIRED", "Maven 构建节点必须配置工作目录");
                validateRequiredString(node, validation, "goals", "PARAM_REQUIRED", "Maven 构建节点必须配置 goals");
                validateRequiredString(node, validation, "artifactPattern", "PARAM_REQUIRED", "Maven 构建节点必须配置制品匹配");
                validateOptionalBoolean(node, validation, "skipTests");
            }
            case PipelineNodeRegistryServiceImpl.TYPE_NPM_BUILD -> {
                validateRequiredString(node, validation, "workingDir", "PARAM_REQUIRED", "NPM 构建节点必须配置工作目录");
                validateRequiredString(node, validation, "packageManager", "PARAM_REQUIRED", "NPM 构建节点必须配置包管理器");
                validateRequiredString(node, validation, "installCommand", "PARAM_REQUIRED", "NPM 构建节点必须配置安装命令");
                validateRequiredString(node, validation, "buildCommand", "PARAM_REQUIRED", "NPM 构建节点必须配置构建命令");
                validateRequiredString(node, validation, "distPattern", "PARAM_REQUIRED", "NPM 构建节点必须配置产物匹配");
                validateOptionalBoolean(node, validation, "cacheEnabled");
            }
            case PipelineNodeRegistryServiceImpl.TYPE_DOCKER_BUILD_PUSH -> {
                validateRequiredString(node, validation, "imageName", "PARAM_REQUIRED", "Docker 节点必须配置镜像名称");
                validateRequiredString(node, validation, "imageTagExpression", "PARAM_REQUIRED", "Docker 节点必须配置镜像标签");
                validateRequiredString(node, validation, "dockerfile", "PARAM_REQUIRED", "Docker 节点必须配置 Dockerfile");
                validateRequiredString(node, validation, "context", "PARAM_REQUIRED", "Docker 节点必须配置构建上下文");
                validateOptionalBoolean(node, validation, "push");
                validateOptionalBoolean(node, validation, "pushLatest");
            }
            case PipelineNodeRegistryServiceImpl.TYPE_ARTIFACT_UPLOAD -> {
                validateRequiredString(node, validation, "artifactPattern", "PARAM_REQUIRED", "制品归档节点必须配置制品匹配");
                validateOptionalBoolean(node, validation, "fingerprint");
                validateOptionalBoolean(node, validation, "allowEmptyArchive");
                validateOptionalBoolean(node, validation, "onlyIfSuccessful");
            }
            case PipelineNodeRegistryServiceImpl.TYPE_EXECUTE_SHELL ->
                    validateRequiredString(node, validation, "script", "PARAM_REQUIRED", "执行 Shell 节点必须配置脚本");
            case PipelineNodeRegistryServiceImpl.TYPE_SSH_PUBLISH -> validateSshPublishParams(node, validation);
            case PipelineNodeRegistryServiceImpl.TYPE_CONTAINER_DEPLOY -> validateContainerDeployParams(node, validation);
            case PipelineNodeRegistryServiceImpl.TYPE_REPORT_ARTIFACTS -> {
                validateOptionalBoolean(node, validation, "fingerprint");
                validateOptionalBoolean(node, validation, "allowEmptyArchive");
                validateOptionalBoolean(node, validation, "onlyIfSuccessful");
            }
            default -> {
                // Other node types either have no required Jenkins params or are validated by command template checks.
            }
        }
    }

    private void validateSshPublishParams(PipelineSpec.Node node, PipelineValidationRespVO validation) {
        validateRequiredString(node, validation, "configName", "PARAM_REQUIRED", "SSH 发布节点必须配置 Jenkins SSH Server 名称");
        String sourceFiles = param(node, "sourceFiles");
        String execCommand = param(node, "execCommand");
        if (StrUtil.isBlank(sourceFiles) && StrUtil.isBlank(execCommand)) {
            addError(validation, "params.sourceFiles", node.getId(), "PARAM_REQUIRED",
                    "SSH 发布节点必须配置发送文件或远端执行命令");
        }
        validateOptionalBoolean(node, validation, "verbose");
        validateOptionalInteger(node, validation, "execTimeoutMillis");
    }

    private void validateContainerDeployParams(PipelineSpec.Node node, PipelineValidationRespVO validation) {
        validateRequiredString(node, validation, "infraType", "PARAM_REQUIRED", "容器部署节点必须配置基础设施类型");
        validateRequiredString(node, validation, "workloadKind", "PARAM_REQUIRED", "容器部署节点必须配置工作负载类型");
        validateRequiredString(node, validation, "deploymentName", "PARAM_REQUIRED", "容器部署节点必须配置 Deployment 名称");
        validateRequiredString(node, validation, "containerName", "PARAM_REQUIRED", "容器部署节点必须配置容器名称");
        validateRequiredString(node, validation, "image", "PARAM_REQUIRED", "容器部署节点必须配置镜像地址");
        if (!"K8S".equals(param(node, "infraType"))) {
            addError(validation, "params.infraType", node.getId(), "PARAM_VALUE_INVALID",
                    "容器部署节点当前仅支持 K8S");
        }
        if (!"DEPLOYMENT".equals(param(node, "workloadKind"))) {
            addError(validation, "params.workloadKind", node.getId(), "PARAM_VALUE_INVALID",
                    "容器部署节点当前仅支持 Deployment");
        }
        validateOptionalInteger(node, validation, "replicas");
        validateOptionalInteger(node, validation, "rolloutTimeoutSeconds");
    }

    private void validateRequiredString(PipelineSpec.Node node, PipelineValidationRespVO validation, String paramName,
                                        String code, String message) {
        if (StrUtil.isBlank(param(node, paramName))) {
            addError(validation, "params." + paramName, node.getId(), code, message);
        }
    }

    private String param(PipelineSpec.Node node, String paramName) {
        Object value = node.getParams() == null ? null : node.getParams().get(paramName);
        return value == null ? null : String.valueOf(value);
    }

    private void validateOptionalBoolean(PipelineSpec.Node node, PipelineValidationRespVO validation, String paramName) {
        if (node.getParams() == null || !node.getParams().containsKey(paramName)) {
            return;
        }
        Object value = node.getParams().get(paramName);
        if (value != null && !(value instanceof Boolean)) {
            addError(validation, "params." + paramName, node.getId(), "PARAM_TYPE_INVALID",
                    "参数必须是布尔值：" + paramName);
        }
    }

    private void validateOptionalInteger(PipelineSpec.Node node, PipelineValidationRespVO validation, String paramName) {
        if (node.getParams() == null || !node.getParams().containsKey(paramName)) {
            return;
        }
        Object value = node.getParams().get(paramName);
        if (value == null || value instanceof Number) {
            return;
        }
        try {
            Integer.parseInt(String.valueOf(value));
        } catch (NumberFormatException ex) {
            addError(validation, "params." + paramName, node.getId(), "PARAM_TYPE_INVALID",
                    "参数必须是整数：" + paramName);
        }
    }

    private void validateOptionalMap(PipelineSpec.Node node, PipelineValidationRespVO validation, String paramName) {
        if (node.getParams() == null || !node.getParams().containsKey(paramName)) {
            return;
        }
        Object value = node.getParams().get(paramName);
        if (value != null && !(value instanceof Map<?, ?>)) {
            addError(validation, "params." + paramName, node.getId(), "PARAM_TYPE_INVALID",
                    "参数必须是对象：" + paramName);
        }
    }

    private void validateEdges(PipelineSpec spec, PipelineValidationRespVO validation) {
        Set<String> nodeIds = spec.getNodes().stream().filter(node -> node != null)
                .map(PipelineSpec.Node::getId)
                .filter(StrUtil::isNotBlank).collect(Collectors.toSet());
        for (PipelineSpec.Edge edge : spec.getEdges()) {
            if (edge == null || StrUtil.isBlank(edge.getSource()) || StrUtil.isBlank(edge.getTarget())) {
                addError(validation, "edges", null, "EDGE_INVALID", "连线 source/target 不能为空");
                continue;
            }
            if (!nodeIds.contains(edge.getSource())) {
                addError(validation, "edges", edge.getSource(), "EDGE_SOURCE_NOT_EXISTS", "连线起点节点不存在");
            }
            if (!nodeIds.contains(edge.getTarget())) {
                addError(validation, "edges", edge.getTarget(), "EDGE_TARGET_NOT_EXISTS", "连线终点节点不存在");
            }
        }
    }

    private void validateTopology(PipelineSpec spec, PipelineValidationRespVO validation) {
        if (CollUtil.isNotEmpty(validation.getErrors())) {
            return;
        }
        Map<String, Integer> inDegree = new HashMap<>();
        Map<String, Integer> outDegree = new HashMap<>();
        for (PipelineSpec.Node node : spec.getNodes()) {
            inDegree.put(node.getId(), 0);
            outDegree.put(node.getId(), 0);
        }
        for (PipelineSpec.Edge edge : spec.getEdges()) {
            outDegree.put(edge.getSource(), outDegree.get(edge.getSource()) + 1);
            inDegree.put(edge.getTarget(), inDegree.get(edge.getTarget()) + 1);
        }
        long startCount = inDegree.values().stream().filter(degree -> degree == 0).count();
        long terminalCount = outDegree.values().stream().filter(degree -> degree == 0).count();
        if (startCount != 1) {
            addError(validation, "nodes", null, "START_NODE_INVALID", "流水线必须且只能有一个开始节点");
        }
        if (terminalCount != 1) {
            addError(validation, "nodes", null, "TERMINAL_NODE_INVALID", "流水线必须且只能有一个结束节点");
        }
        if (sortNodes(spec).size() != spec.getNodes().size()) {
            addError(validation, "edges", null, "GRAPH_HAS_CYCLE", "流水线节点不能形成环路");
            return;
        }
        validatePhaseATopology(spec, validation);
    }

    private void validatePhaseATopology(PipelineSpec spec, PipelineValidationRespVO validation) {
        List<PipelineSpec.Node> sortedNodes = sortNodes(spec);
        List<PipelineSpec.Node> containerDeployNodes = sortedNodes.stream()
                .filter(node -> PipelineNodeRegistryServiceImpl.TYPE_CONTAINER_DEPLOY.equals(node.getType()))
                .toList();
        if (containerDeployNodes.isEmpty()) {
            return;
        }
        if (containerDeployNodes.size() > 1) {
            addError(validation, "nodes", null, "CONTAINER_DEPLOY_DUPLICATE",
                    "MVP 阶段最多只允许一个容器部署节点");
            return;
        }
        PipelineSpec.Node containerDeployNode = containerDeployNodes.get(0);
        PipelineSpec.Node terminalNode = sortedNodes.get(sortedNodes.size() - 1);
        if (!Objects.equals(containerDeployNode.getId(), terminalNode.getId())) {
            addError(validation, "nodes", containerDeployNode.getId(), "CONTAINER_DEPLOY_NOT_TERMINAL",
                    "MVP 阶段容器部署节点必须是最后一个执行节点");
        }
    }

    private void addError(PipelineValidationRespVO validation, String field, String nodeId, String code, String message) {
        PipelineValidationMessageRespVO error = new PipelineValidationMessageRespVO();
        error.setField(field);
        error.setNodeId(nodeId);
        error.setCode(code);
        error.setMessage(message);
        validation.getErrors().add(error);
    }

}
