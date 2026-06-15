package cn.iocoder.yudao.module.devops.service.pipeline;

import cn.hutool.core.collection.CollUtil;
import cn.hutool.core.util.StrUtil;
import cn.iocoder.yudao.framework.common.util.json.JsonUtils;
import cn.iocoder.yudao.module.devops.controller.admin.pipeline.vo.PipelineNodeTypeRespVO;
import cn.iocoder.yudao.module.devops.controller.admin.pipeline.vo.PipelineValidationMessageRespVO;
import cn.iocoder.yudao.module.devops.controller.admin.pipeline.vo.PipelineValidationRespVO;
import cn.iocoder.yudao.module.devops.framework.pipeline.PipelineSpec;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.dataformat.yaml.YAMLFactory;
import jakarta.annotation.Resource;
import org.springframework.stereotype.Service;

import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.regex.Pattern;

/**
 * DevOps 流水线 DSL 校验服务。
 */
@Service
public class PipelineSpecValidationServiceImpl implements PipelineSpecValidationService {

    private static final Pattern NODE_ID_PATTERN = Pattern.compile("[a-zA-Z][a-zA-Z0-9_-]{0,63}");
    private static final Pattern ENV_KEY_PATTERN = Pattern.compile("[A-Za-z_][A-Za-z0-9_]*");
    private static final ObjectMapper YAML_OBJECT_MAPPER = new ObjectMapper(new YAMLFactory())
            .configure(com.fasterxml.jackson.databind.DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES, false);

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
            addError(validation, "specJson", null, "SPEC_REQUIRED", "流水线 YAML 不能为空");
            return null;
        }
        try {
            PipelineSpec spec = parseJsonOrYaml(specJson);
            if (spec == null) {
                addError(validation, "specJson", null, "SPEC_INVALID", "流水线 YAML 无效");
            }
            return spec;
        } catch (RuntimeException ex) {
            addError(validation, "specJson", null, "SPEC_INVALID", "流水线 YAML 格式错误");
            return null;
        }
    }

    private PipelineSpec parseJsonOrYaml(String specText) {
        try {
            String trimmed = StrUtil.trim(specText);
            if (StrUtil.startWithAny(trimmed, "{", "[")) {
                return JsonUtils.parseObject(specText, PipelineSpec.class);
            }
            try {
                return YAML_OBJECT_MAPPER.readValue(specText, PipelineSpec.class);
            } catch (Exception yamlEx) {
                throw new RuntimeException(yamlEx);
            }
        } catch (RuntimeException ex) {
            try {
                return YAML_OBJECT_MAPPER.readValue(specText, PipelineSpec.class);
            } catch (Exception yamlEx) {
                throw new RuntimeException(yamlEx);
            }
        }
    }

    @Override
    public List<PipelineSpec.ExecutableStep> sortExecutableSteps(PipelineSpec spec) {
        if (spec == null) {
            return List.of();
        }
        return spec.toExecutableSteps();
    }

    private void validate(PipelineSpec spec, PipelineValidationRespVO validation) {
        if (spec == null) {
            addError(validation, "spec", null, "SPEC_REQUIRED", "流水线 DSL 不能为空");
            return;
        }
        if (spec.getSources() == null) {
            spec.setSources(new LinkedHashMap<>());
        }
        if (spec.getStages() == null) {
            spec.setStages(new LinkedHashMap<>());
        }
        validateSources(spec, validation);
        if (spec.getStages().isEmpty()) {
            addError(validation, "stages", null, "STAGE_REQUIRED", "流水线至少需要一个阶段");
            return;
        }
        Set<String> stepIds = new HashSet<>();
        for (Map.Entry<String, PipelineSpec.Stage> stageEntry : spec.getStages().entrySet()) {
            String stageId = stageEntry.getKey();
            PipelineSpec.Stage stage = stageEntry.getValue();
            if (!validateNamedEntry("stages", stageId, "STAGE_ID_REQUIRED", "STAGE_ID_INVALID",
                    "阶段编号不能为空", "阶段编号只能包含字母、数字、中划线和下划线，且必须以字母开头", validation)) {
                continue;
            }
            if (stage == null) {
                addError(validation, "stages." + stageId, null, "STAGE_REQUIRED", "阶段配置不能为空");
                continue;
            }
            if (stage.getJobs() == null || stage.getJobs().isEmpty()) {
                addError(validation, "stages." + stageId + ".jobs", null, "JOB_REQUIRED", "阶段至少需要一个任务");
                continue;
            }
            validateJobs(stageId, stage, stepIds, validation);
        }
    }

    private void validateSources(PipelineSpec spec, PipelineValidationRespVO validation) {
        for (Map.Entry<String, PipelineSpec.Source> sourceEntry : spec.getSources().entrySet()) {
            String sourceId = sourceEntry.getKey();
            PipelineSpec.Source source = sourceEntry.getValue();
            if (!validateNamedEntry("sources", sourceId, "SOURCE_ID_REQUIRED", "SOURCE_ID_INVALID",
                    "代码源编号不能为空", "代码源编号只能包含字母、数字、中划线和下划线，且必须以字母开头", validation)) {
                continue;
            }
            if (source == null) {
                addError(validation, "sources." + sourceId, null, "SOURCE_REQUIRED", "代码源配置不能为空");
                continue;
            }
            if (StrUtil.isBlank(source.getType())) {
                addError(validation, "sources." + sourceId + ".type", null, "SOURCE_TYPE_REQUIRED", "代码源类型不能为空");
            }
            if (StrUtil.isBlank(source.getEndpoint())) {
                addError(validation, "sources." + sourceId + ".endpoint", null,
                        "SOURCE_ENDPOINT_REQUIRED", "代码源地址不能为空");
            }
            if (StrUtil.isBlank(source.getBranch())) {
                addError(validation, "sources." + sourceId + ".branch", null,
                        "SOURCE_BRANCH_REQUIRED", "代码源分支不能为空");
            }
        }
    }

    private void validateJobs(String stageId, PipelineSpec.Stage stage, Set<String> stepIds,
                              PipelineValidationRespVO validation) {
        for (Map.Entry<String, PipelineSpec.Job> jobEntry : stage.getJobs().entrySet()) {
            String jobId = jobEntry.getKey();
            PipelineSpec.Job job = jobEntry.getValue();
            String jobField = "stages." + stageId + ".jobs." + jobId;
            if (!validateNamedEntry("stages." + stageId + ".jobs", jobId, "JOB_ID_REQUIRED", "JOB_ID_INVALID",
                    "任务编号不能为空", "任务编号只能包含字母、数字、中划线和下划线，且必须以字母开头", validation)) {
                continue;
            }
            if (job == null) {
                addError(validation, jobField, null, "JOB_REQUIRED", "任务配置不能为空");
                continue;
            }
            validateRunsOn(job, jobField, validation);
            if (job.getSteps() == null || job.getSteps().isEmpty()) {
                addError(validation, jobField + ".steps", null, "STEP_REQUIRED", "任务至少需要一个步骤");
                continue;
            }
            validateSteps(stageId, jobId, job, stepIds, validation);
        }
    }

    private void validateRunsOn(PipelineSpec.Job job, String jobField, PipelineValidationRespVO validation) {
        if (job.getRunsOn() == null) {
            return;
        }
        if (StrUtil.isBlank(job.getRunsOn().getGroup())) {
            addError(validation, jobField + ".runsOn.group", null, "RUNS_ON_GROUP_REQUIRED", "运行集群不能为空");
        }
        if (StrUtil.isBlank(job.getRunsOn().getContainer())) {
            addError(validation, jobField + ".runsOn.container", null, "RUNS_ON_CONTAINER_REQUIRED", "运行容器不能为空");
        }
    }

    private void validateSteps(String stageId, String jobId, PipelineSpec.Job job, Set<String> stepIds,
                               PipelineValidationRespVO validation) {
        for (Map.Entry<String, PipelineSpec.Step> stepEntry : job.getSteps().entrySet()) {
            String stepId = stepEntry.getKey();
            PipelineSpec.Step step = stepEntry.getValue();
            String stepField = "stages." + stageId + ".jobs." + jobId + ".steps." + stepId;
            if (!validateNamedEntry("stages." + stageId + ".jobs." + jobId + ".steps", stepId,
                    "STEP_ID_REQUIRED", "STEP_ID_INVALID", "步骤编号不能为空",
                    "步骤编号只能包含字母、数字、中划线和下划线，且必须以字母开头", validation)) {
                continue;
            }
            if (!stepIds.add(stepId)) {
                addError(validation, stepField, stepId, "STEP_ID_DUPLICATE", "步骤编号重复");
            }
            if (step == null) {
                addError(validation, stepField, stepId, "STEP_REQUIRED", "步骤配置不能为空");
                continue;
            }
            PipelineNodeTypeRespVO nodeType = pipelineNodeRegistryService.getNodeType(step.getStep());
            if (nodeType == null) {
                addError(validation, stepField + ".step", stepId, "STEP_TYPE_NOT_SUPPORTED",
                        "不支持的步骤类型：" + step.getStep());
                continue;
            }
            if (!Boolean.TRUE.equals(nodeType.getEnabled())) {
                addError(validation, stepField + ".step", stepId, "STEP_TYPE_DISABLED",
                        "步骤类型暂未开放：" + nodeType.getName());
            }
            validateStepParams(stageId, jobId, stepId, step, validation);
        }
    }

    private boolean validateNamedEntry(String field, String id, String requiredCode, String invalidCode,
                                       String requiredMessage, String invalidMessage,
                                       PipelineValidationRespVO validation) {
        if (StrUtil.isBlank(id)) {
            addError(validation, field, null, requiredCode, requiredMessage);
            return false;
        }
        if (!NODE_ID_PATTERN.matcher(id).matches()) {
            addError(validation, field, id, invalidCode, invalidMessage);
            return false;
        }
        return true;
    }

    private void validateStepParams(String stageId, String jobId, String stepId, PipelineSpec.Step step,
                                    PipelineValidationRespVO validation) {
        validateCommonParams(stepId, step, validation);
        validateStepTypeParams(stageId, jobId, stepId, step, validation);
    }

    @SuppressWarnings("unchecked")
    private void validateCommonParams(String stepId, PipelineSpec.Step step, PipelineValidationRespVO validation) {
        validateOptionalMap(stepId, step, validation, "env");
        if (step.getWith() == null || !(step.getWith().get("env") instanceof Map<?, ?> envMap)) {
            return;
        }
        for (Object key : envMap.keySet()) {
            if (key == null || !ENV_KEY_PATTERN.matcher(String.valueOf(key)).matches()) {
                addError(validation, "with.env", stepId, "PARAM_ENV_KEY_INVALID",
                        "环境变量名称格式不正确：" + key);
            }
        }
    }

    private void validateStepTypeParams(String stageId, String jobId, String stepId, PipelineSpec.Step step,
                                       PipelineValidationRespVO validation) {
        switch (step.getStep()) {
            case PipelineNodeRegistryServiceImpl.TYPE_CODE_MERGE -> {
                // 代码合并节点无参数校验
            }
            case PipelineNodeRegistryServiceImpl.TYPE_APPROVAL ->
                    validateRequiredString(stepId, step, validation, "processDefinitionKey",
                            "PARAM_REQUIRED", "审批节点必须配置流程定义标识");
            case PipelineNodeRegistryServiceImpl.TYPE_EXECUTE_SHELL ->
                    validateRequiredString(stepId, step, validation, "script", "PARAM_REQUIRED", "执行 Shell 节点必须配置脚本");
            case PipelineNodeRegistryServiceImpl.TYPE_COMMAND ->
                    validateRequiredString(stepId, step, validation, "run", "PARAM_REQUIRED", "命令步骤必须配置 run");
            default -> {
                // Other node types either have no required params or are validated elsewhere.
            }
        }
    }

    private void validateRequiredString(String stepId, PipelineSpec.Step step, PipelineValidationRespVO validation, String paramName,
                                        String code, String message) {
        if (StrUtil.isBlank(param(step, paramName))) {
            addError(validation, "with." + paramName, stepId, code, message);
        }
    }

    private String param(PipelineSpec.Step step, String paramName) {
        Object value = step.getWith() == null ? null : step.getWith().get(paramName);
        return value == null ? null : String.valueOf(value);
    }

    private void validateOptionalMap(String stepId, PipelineSpec.Step step, PipelineValidationRespVO validation, String paramName) {
        if (step.getWith() == null || !step.getWith().containsKey(paramName)) {
            return;
        }
        Object value = step.getWith().get(paramName);
        if (value != null && !(value instanceof Map<?, ?>)) {
            addError(validation, "with." + paramName, stepId, "PARAM_TYPE_INVALID",
                    "参数必须是对象：" + paramName);
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
