package cn.iocoder.yudao.module.devops.service.applicationorchestration;

import cn.hutool.core.util.StrUtil;
import cn.iocoder.yudao.module.devops.controller.admin.applicationorchestration.vo.ApplicationOrchestrationComponentVO;
import cn.iocoder.yudao.module.devops.controller.admin.applicationorchestration.vo.ApplicationOrchestrationConfigVO;
import cn.iocoder.yudao.module.devops.controller.admin.applicationorchestration.vo.ApplicationOrchestrationResourceSummaryRespVO;
import cn.iocoder.yudao.module.devops.controller.admin.applicationorchestration.vo.ApplicationOrchestrationValidationMessageRespVO;
import cn.iocoder.yudao.module.devops.enums.ApplicationOrchestrationComponentTemplateEnum;
import io.fabric8.kubernetes.api.model.HasMetadata;
import io.fabric8.kubernetes.api.model.KubernetesResource;
import io.fabric8.kubernetes.client.utils.Serialization;
import org.springframework.stereotype.Service;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.regex.Pattern;

@Service
public class ApplicationOrchestrationValidationServiceImpl implements ApplicationOrchestrationValidationService {

    private static final Pattern DNS_LABEL = Pattern.compile("[a-z0-9]([-a-z0-9]*[a-z0-9])?");
    private static final Set<String> SUPPORTED_KINDS = Set.of("Secret", "ConfigMap", "PersistentVolumeClaim",
            "Service", "Deployment", "Ingress");

    @Override
    public ValidationResult validate(ApplicationOrchestrationConfigVO config) {
        ApplicationOrchestrationConfigVO normalized = copyConfig(config);
        List<ApplicationOrchestrationValidationMessageRespVO> errors = new ArrayList<>();
        List<ApplicationOrchestrationResourceSummaryRespVO> resources = new ArrayList<>();
        if (normalized.getComponents() == null) {
            normalized.setComponents(new ArrayList<>());
        }
        Map<String, ApplicationOrchestrationComponentVO> byId = new LinkedHashMap<>();
        Set<String> names = new HashSet<>();
        // 第一遍：归一化顺序，收集组件标识与名称,校验唯一性,构建完整的 byId 索引
        for (int i = 0; i < normalized.getComponents().size(); i++) {
            ApplicationOrchestrationComponentVO component = normalized.getComponents().get(i);
            String id = component == null ? null : component.getId();
            if (component == null) {
                add(errors, null, "components[" + i + "]", "COMPONENT_REQUIRED", "组件不能为空");
                continue;
            }
            component.setApplyOrder(i + 1);
            if (component.getDependsOn() == null) component.setDependsOn(new ArrayList<>());
            if (StrUtil.isBlank(id)) {
                add(errors, null, "id", "COMPONENT_ID_REQUIRED", "组件标识不能为空");
            } else if (byId.putIfAbsent(id, component) != null) {
                add(errors, id, "id", "COMPONENT_ID_DUPLICATE", "组件标识重复");
            }
            if (StrUtil.isBlank(component.getName())) {
                add(errors, id, "name", "COMPONENT_NAME_REQUIRED", "组件名称不能为空");
            } else if (!DNS_LABEL.matcher(component.getName()).matches() || component.getName().length() > 63) {
                add(errors, id, "name", "COMPONENT_NAME_INVALID", "组件名称必须符合 Kubernetes DNS label 规范");
            } else if (!names.add(component.getName())) {
                add(errors, id, "name", "COMPONENT_NAME_DUPLICATE", "组件名称重复");
            }
        }
        // 第二遍：基于完整索引校验依赖引用/逆序,以及逐组件的 YAML
        for (ApplicationOrchestrationComponentVO component : normalized.getComponents()) {
            if (component == null) {
                continue;
            }
            String id = component.getId();
            for (String dependency : component.getDependsOn()) {
                if (StrUtil.isBlank(dependency) || dependency.equals(id)) {
                    add(errors, id, "dependsOn", "DEPENDENCY_INVALID", "依赖不能为空或自引用");
                } else if (!byId.containsKey(dependency)) {
                    add(errors, id, "dependsOn", "DEPENDENCY_NOT_EXISTS", "依赖组件不存在：" + dependency);
                } else if (byId.get(dependency).getApplyOrder() >= component.getApplyOrder()) {
                    add(errors, id, "dependsOn", "DEPENDENCY_ORDER_INVALID", "依赖组件必须排在当前组件之前");
                }
            }
            validateYaml(component, errors, resources);
        }
        detectCycle(normalized.getComponents(), byId, errors);
        return new ValidationResult(normalized, resources, errors);
    }

    private void validateYaml(ApplicationOrchestrationComponentVO component,
                               List<ApplicationOrchestrationValidationMessageRespVO> errors,
                               List<ApplicationOrchestrationResourceSummaryRespVO> resources) {
        if (StrUtil.isBlank(component.getYaml())) return;
        List<String> docs = splitDocuments(component.getYaml());
        if (docs.size() != 1) {
            add(errors, component.getId(), "yaml", "YAML_MULTI_DOCUMENT", "每个组件只能包含一个 Kubernetes 资源");
            return;
        }
        try {
            KubernetesResource resource = Serialization.unmarshal(docs.get(0));
            if (!(resource instanceof HasMetadata metadata)) {
                add(errors, component.getId(), "yaml", "YAML_KIND_UNSUPPORTED", "Kubernetes 资源类型不支持");
                return;
            }
            String kind = metadata.getKind();
            if (!SUPPORTED_KINDS.contains(kind)) {
                add(errors, component.getId(), "kind", "KIND_UNSUPPORTED", "不支持的 Kubernetes 资源类型：" + kind);
            }
            if (StrUtil.isBlank(metadata.getApiVersion())) add(errors, component.getId(), "apiVersion", "API_VERSION_REQUIRED", "apiVersion 不能为空");
            if (StrUtil.isBlank(kind)) add(errors, component.getId(), "kind", "KIND_REQUIRED", "kind 不能为空");
            String metadataName = metadata.getMetadata() == null ? null : metadata.getMetadata().getName();
            if (StrUtil.isBlank(metadataName)) add(errors, component.getId(), "metadata.name", "METADATA_NAME_REQUIRED", "metadata.name 不能为空");
            ApplicationOrchestrationComponentTemplateEnum template = ApplicationOrchestrationComponentTemplateEnum.valueOfKey(component.getTemplateKey());
            if (template == null) add(errors, component.getId(), "templateKey", "TEMPLATE_INVALID", "模板类型不支持");
            else if (template.getKind() != null && !template.getKind().equals(kind)) add(errors, component.getId(), "kind", "KIND_MISMATCH", "kind 与模板类型不一致");
            resources.add(new ApplicationOrchestrationResourceSummaryRespVO(component.getId(), component.getApplyOrder(), metadata.getApiVersion(), kind, metadataName));
        } catch (Exception ex) {
            add(errors, component.getId(), "yaml", "YAML_INVALID", "YAML 解析失败：" + StrUtil.subPre(String.valueOf(ex.getMessage()), 256));
        }
    }

    private void detectCycle(List<ApplicationOrchestrationComponentVO> components, Map<String, ApplicationOrchestrationComponentVO> byId,
                             List<ApplicationOrchestrationValidationMessageRespVO> errors) {
        Map<String, Integer> state = new HashMap<>();
        for (ApplicationOrchestrationComponentVO component : components) {
            if (component != null && visit(component.getId(), byId, state)) add(errors, component.getId(), "dependsOn", "DEPENDENCY_CYCLE", "依赖关系不能成环");
        }
    }

    private boolean visit(String id, Map<String, ApplicationOrchestrationComponentVO> byId, Map<String, Integer> state) {
        if (id == null || !byId.containsKey(id)) return false;
        Integer current = state.get(id);
        if (current != null) return current == 1;
        state.put(id, 1);
        for (String dependency : byId.get(id).getDependsOn()) if (visit(dependency, byId, state)) return true;
        state.put(id, 2);
        return false;
    }

    private ApplicationOrchestrationConfigVO copyConfig(ApplicationOrchestrationConfigVO config) {
        ApplicationOrchestrationConfigVO copy = new ApplicationOrchestrationConfigVO();
        copy.setSchemaVersion(StrUtil.blankToDefault(config == null ? null : config.getSchemaVersion(), "1.0"));
        List<ApplicationOrchestrationComponentVO> components = new ArrayList<>();
        if (config != null && config.getComponents() != null) for (ApplicationOrchestrationComponentVO source : config.getComponents()) {
            if (source == null) { components.add(null); continue; }
            ApplicationOrchestrationComponentVO target = new ApplicationOrchestrationComponentVO();
            target.setId(source.getId()); target.setName(source.getName()); target.setTemplateKey(source.getTemplateKey()); target.setKind(source.getKind());
            target.setApplyOrder(source.getApplyOrder()); target.setEnabled(source.getEnabled() == null ? Boolean.TRUE : source.getEnabled()); target.setYaml(source.getYaml());
            target.setDependsOn(source.getDependsOn() == null ? new ArrayList<>() : new ArrayList<>(source.getDependsOn())); components.add(target);
        }
        copy.setComponents(components);
        return copy;
    }

    private static void add(List<ApplicationOrchestrationValidationMessageRespVO> errors, String componentId, String field, String code, String message) {
        errors.add(new ApplicationOrchestrationValidationMessageRespVO(componentId, field, code, message));
    }

    private static List<String> splitDocuments(String yaml) {
        List<String> docs = new ArrayList<>(); StringBuilder current = new StringBuilder();
        for (String line : yaml.replace("\r\n", "\n").split("\n", -1)) {
            if (line.trim().equals("---") || line.trim().startsWith("--- ")) { if (current.toString().lines().anyMatch(s -> StrUtil.isNotBlank(s) && !s.trim().startsWith("#"))) docs.add(current.toString()); current.setLength(0); }
            else current.append(line).append('\n');
        }
        if (current.toString().lines().anyMatch(s -> StrUtil.isNotBlank(s) && !s.trim().startsWith("#"))) docs.add(current.toString());
        return docs;
    }
}
