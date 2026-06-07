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
