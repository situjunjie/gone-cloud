package cn.iocoder.yudao.module.devops.service.pipeline;

import cn.hutool.core.collection.CollUtil;
import cn.hutool.core.util.StrUtil;
import cn.iocoder.yudao.framework.common.util.json.JsonUtils;
import cn.iocoder.yudao.module.devops.controller.admin.pipeline.vo.PipelineNodeTypeRespVO;
import cn.iocoder.yudao.module.devops.controller.admin.pipeline.vo.PipelineValidationMessageRespVO;
import cn.iocoder.yudao.module.devops.controller.admin.pipeline.vo.PipelineValidationRespVO;
import cn.iocoder.yudao.module.devops.framework.pipeline.PipelineSpec;
import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.dataformat.yaml.YAMLFactory;
import jakarta.annotation.Resource;
import org.springframework.stereotype.Service;

import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
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
    private static final String FAIL_STRATEGY_FAIL_FAST = "failFast";
    private static final String LOCAL_DOCKER_GROUP = "local-docker/default";
    private static final String SOURCE_TYPE_GITLAB = "gitlab";
    private static final ObjectMapper YAML_OBJECT_MAPPER = new ObjectMapper(new YAMLFactory())
            .configure(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES, true);
    private static final ObjectMapper JSON_OBJECT_MAPPER = new ObjectMapper()
            .configure(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES, true);

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
                try {
                    return JSON_OBJECT_MAPPER.readValue(specText, PipelineSpec.class);
                } catch (Exception jsonEx) {
                    throw new RuntimeException(jsonEx);
                }
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
            addError(validation, "spec", null, "SPEC_REQUIRED", "流水线 YAML 不能为空");
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
        Set<String> jobIds = new LinkedHashSet<>();
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
            validateJobs(stageId, stage, jobIds, stepIds, validation);
        }
        validateJobNeeds(spec, validation);
    }

    private void validateSources(PipelineSpec spec, PipelineValidationRespVO validation) {
        if (spec.getSources().size() > 1) {
            addError(validation, "sources", null, "SOURCE_COUNT_UNSUPPORTED", "当前版本最多支持一个代码源");
        }
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
            } else if (!SOURCE_TYPE_GITLAB.equalsIgnoreCase(source.getType())) {
                addError(validation, "sources." + sourceId + ".type", null,
                        "SOURCE_TYPE_UNSUPPORTED", "当前版本仅支持 gitlab 代码源");
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

    private void validateJobs(String stageId, PipelineSpec.Stage stage, Set<String> jobIds, Set<String> stepIds,
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
            if (!jobIds.add(jobId)) {
                addError(validation, jobField, jobId, "JOB_ID_DUPLICATE", "任务编号重复");
            }
            validateRunsOn(job, jobField, validation);
            validateFailStrategy(jobField + ".failStrategy", jobId, job.getFailStrategy(), validation);
            if (job.getSteps() == null || job.getSteps().isEmpty()) {
                addError(validation, jobField + ".steps", null, "STEP_REQUIRED", "任务至少需要一个步骤");
                continue;
            }
            validateSteps(stageId, jobId, job, stepIds, validation);
        }
    }

    private void validateRunsOn(PipelineSpec.Job job, String jobField, PipelineValidationRespVO validation) {
        if (!requiresJobRuntime(job)) {
            return;
        }
        if (job.getRunsOn() == null) {
            addError(validation, jobField + ".runsOn.group", null, "RUNS_ON_GROUP_REQUIRED", "运行集群不能为空");
            addError(validation, jobField + ".runsOn.container", null, "RUNS_ON_CONTAINER_REQUIRED", "运行容器不能为空");
            return;
        }
        if (StrUtil.isBlank(job.getRunsOn().getGroup())) {
            addError(validation, jobField + ".runsOn.group", null, "RUNS_ON_GROUP_REQUIRED", "运行集群不能为空");
        } else if (!LOCAL_DOCKER_GROUP.equals(job.getRunsOn().getGroup())) {
            addError(validation, jobField + ".runsOn.group", null, "RUNS_ON_GROUP_UNSUPPORTED",
                    "当前版本仅支持 local-docker/default");
        }
        if (StrUtil.isBlank(job.getRunsOn().getContainer())) {
            addError(validation, jobField + ".runsOn.container", null, "RUNS_ON_CONTAINER_REQUIRED", "运行容器不能为空");
        }
    }

    private boolean requiresJobRuntime(PipelineSpec.Job job) {
        if (job.getSteps() == null || job.getSteps().isEmpty()) {
            return false;
        }
        for (PipelineSpec.Step step : job.getSteps().values()) {
            if (step == null || StrUtil.isBlank(step.getStep())) {
                continue;
            }
            PipelineNodeTypeRespVO nodeType = pipelineNodeRegistryService.getNodeType(step.getStep());
            if (nodeType == null) {
                continue;
            }
            if (!PipelineNodeRegistryServiceImpl.isPlatformNode(step.getStep())) {
                return true;
            }
        }
        return false;
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
            if (!isSupportedStep(step.getStep())) {
                addError(validation, stepField + ".step", stepId, "STEP_TYPE_UNSUPPORTED",
                        "当前版本仅支持 Command、CodeMerge、APPROVAL、K8sDeploy、K8sImageUpgrade 和 PrivateRegistryDockerBuild 步骤");
                continue;
            }
            validateFailStrategy(stepField + ".failStrategy", stepId, step.getFailStrategy(), validation);
            validateStepParams(stageId, jobId, stepId, step, validation);
        }
    }

    private void validateJobNeeds(PipelineSpec spec, PipelineValidationRespVO validation) {
        Map<String, String> jobFields = new LinkedHashMap<>();
        Map<String, List<String>> needsGraph = new LinkedHashMap<>();
        for (Map.Entry<String, PipelineSpec.Stage> stageEntry : spec.getStages().entrySet()) {
            PipelineSpec.Stage stage = stageEntry.getValue();
            if (stage == null || stage.getJobs() == null) {
                continue;
            }
            for (Map.Entry<String, PipelineSpec.Job> jobEntry : stage.getJobs().entrySet()) {
                String jobId = jobEntry.getKey();
                PipelineSpec.Job job = jobEntry.getValue();
                if (StrUtil.isBlank(jobId) || job == null) {
                    continue;
                }
                String jobField = "stages." + stageEntry.getKey() + ".jobs." + jobId;
                jobFields.putIfAbsent(jobId, jobField);
                needsGraph.putIfAbsent(jobId, job.getNeeds() == null ? List.of() : job.getNeeds());
            }
        }
        for (Map.Entry<String, List<String>> entry : needsGraph.entrySet()) {
            String jobId = entry.getKey();
            for (String need : entry.getValue()) {
                String needsField = jobFields.get(jobId) + ".needs";
                if (StrUtil.isBlank(need) || !needsGraph.containsKey(need)) {
                    addError(validation, needsField, jobId, "JOB_NEEDS_NOT_FOUND", "依赖任务不存在：" + need);
                    continue;
                }
                if (jobId.equals(need)) {
                    addError(validation, needsField, jobId, "JOB_NEEDS_SELF", "任务不能依赖自身");
                }
            }
        }
        Set<String> visiting = new HashSet<>();
        Set<String> visited = new HashSet<>();
        for (String jobId : needsGraph.keySet()) {
            if (hasCycle(jobId, needsGraph, visiting, visited)) {
                addError(validation, jobFields.get(jobId) + ".needs", jobId, "JOB_NEEDS_CYCLE", "任务依赖存在循环");
                return;
            }
        }
    }

    private boolean hasCycle(String jobId, Map<String, List<String>> needsGraph,
                             Set<String> visiting, Set<String> visited) {
        if (visited.contains(jobId)) {
            return false;
        }
        if (!visiting.add(jobId)) {
            return true;
        }
        for (String need : needsGraph.getOrDefault(jobId, List.of())) {
            if (!needsGraph.containsKey(need)) {
                continue;
            }
            if (hasCycle(need, needsGraph, visiting, visited)) {
                return true;
            }
        }
        visiting.remove(jobId);
        visited.add(jobId);
        return false;
    }

    private void validateFailStrategy(String field, String refId, String failStrategy,
                                      PipelineValidationRespVO validation) {
        if (StrUtil.isBlank(failStrategy) || FAIL_STRATEGY_FAIL_FAST.equals(failStrategy)) {
            return;
        }
        addError(validation, field, refId, "FAIL_STRATEGY_UNSUPPORTED", "当前版本仅支持 failFast");
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
            case PipelineNodeRegistryServiceImpl.TYPE_COMMAND ->
                    validateRequiredString(stepId, step, validation, "run", "PARAM_REQUIRED", "命令步骤必须配置 run");
            case PipelineNodeRegistryServiceImpl.TYPE_CODE_MERGE -> validateCodeMergeParams(stepId, step, validation);
            case PipelineNodeRegistryServiceImpl.TYPE_APPROVAL -> validateRequiredString(stepId, step, validation,
                    "processDefinitionKey", "PARAM_REQUIRED", "审批步骤必须配置 processDefinitionKey");
            case PipelineNodeRegistryServiceImpl.TYPE_K8S_DEPLOY -> validateK8sDeployParams(stepId, step, validation);
            case PipelineNodeRegistryServiceImpl.TYPE_K8S_IMAGE_UPGRADE ->
                    validateK8sImageUpgradeParams(stepId, step, validation);
            case PipelineNodeRegistryServiceImpl.TYPE_PRIVATE_REGISTRY_DOCKER_BUILD ->
                    validatePrivateRegistryDockerBuildParams(stepId, step, validation);
            default -> {
                // Unsupported types are reported earlier.
            }
        }
    }

    private boolean isSupportedStep(String stepType) {
        return PipelineNodeRegistryServiceImpl.TYPE_COMMAND.equals(stepType)
                || PipelineNodeRegistryServiceImpl.TYPE_CODE_MERGE.equals(stepType)
                || PipelineNodeRegistryServiceImpl.TYPE_APPROVAL.equals(stepType)
                || PipelineNodeRegistryServiceImpl.TYPE_K8S_DEPLOY.equals(stepType)
                || PipelineNodeRegistryServiceImpl.TYPE_K8S_IMAGE_UPGRADE.equals(stepType)
                || PipelineNodeRegistryServiceImpl.TYPE_PRIVATE_REGISTRY_DOCKER_BUILD.equals(stepType);
    }

    private void validatePrivateRegistryDockerBuildParams(String stepId, PipelineSpec.Step step,
                                                          PipelineValidationRespVO validation) {
        validateRequiredString(stepId, step, validation, "artifact", "PARAM_REQUIRED",
                "镜像构建必须配置 artifact");
        validateRequiredString(stepId, step, validation, "image", "PARAM_REQUIRED",
                "镜像构建必须配置 image");
        validateRequiredString(stepId, step, validation, "dockerfilePath", "PARAM_REQUIRED",
                "镜像构建必须配置 dockerfilePath");
        if (step.getWith() == null || !(step.getWith().get("certificate") instanceof Map<?, ?> certificate)) {
            addError(validation, "with.certificate", stepId, "PARAM_REQUIRED", "镜像构建必须配置 certificate");
            return;
        }
        Object typeValue = certificate.get("type");
        String type = typeValue == null ? null : String.valueOf(typeValue);
        if (StrUtil.isBlank(type)) {
            addError(validation, "with.certificate.type", stepId, "PARAM_REQUIRED",
                    "镜像构建必须配置 certificate.type");
            return;
        }
        if ("serviceConnection".equals(type)) {
            addError(validation, "with.certificate.type", stepId, "PARAM_VALUE_UNSUPPORTED",
                    "当前版本不支持 serviceConnection，请使用 usernamePassword");
            return;
        }
        if (!"usernamePassword".equals(type)) {
            addError(validation, "with.certificate.type", stepId, "PARAM_VALUE_UNSUPPORTED",
                    "certificate.type 当前仅支持 usernamePassword");
            return;
        }
        validateRequiredMapString(stepId, certificate, "username", "with.certificate.username", validation,
                "镜像构建必须配置 certificate.username");
        validateRequiredMapString(stepId, certificate, "password", "with.certificate.password", validation,
                "镜像构建必须配置 certificate.password");
        validateOptionalBoolean(stepId, step, validation, "noCache");
        validateOptionalVariables(stepId, step, validation);
        String buildkitVersion = param(step, "buildkitVersion");
        if (StrUtil.isNotBlank(buildkitVersion)
                && !Set.of("v0.8.0", "v0.9.0", "v0.11.6").contains(buildkitVersion)) {
            addError(validation, "with.buildkitVersion", stepId, "PARAM_VALUE_UNSUPPORTED",
                    "buildkitVersion 仅支持 v0.8.0、v0.9.0、v0.11.6");
        }
    }

    private void validateK8sDeployParams(String stepId, PipelineSpec.Step step, PipelineValidationRespVO validation) {
        validateRequiredString(stepId, step, validation, "deployMode", "PARAM_REQUIRED",
                "K8s 集群部署必须配置 deployMode");
        validateRequiredString(stepId, step, validation, "manifestYaml", "PARAM_REQUIRED",
                "K8s 集群部署必须配置 manifestYaml");
        validateRequiredString(stepId, step, validation, "containerName", "PARAM_REQUIRED",
                "K8s 集群部署必须配置 containerName");
        validateRequiredString(stepId, step, validation, "image", "PARAM_REQUIRED",
                "K8s 集群部署必须配置 image");
        String deployMode = param(step, "deployMode");
        if (StrUtil.isNotBlank(deployMode) && !"RAW_MANIFEST".equals(deployMode)) {
            addError(validation, "with.deployMode", stepId, "PARAM_VALUE_UNSUPPORTED",
                    "deployMode 当前仅支持 RAW_MANIFEST");
        }
        validateOptionalInteger(stepId, step, validation, "replicas");
        validateOptionalInteger(stepId, step, validation, "rolloutTimeoutSeconds");
    }

    private void validateK8sImageUpgradeParams(String stepId, PipelineSpec.Step step,
                                               PipelineValidationRespVO validation) {
        validateRequiredString(stepId, step, validation, "workloadKind", "PARAM_REQUIRED",
                "K8s 镜像版本升级必须配置 workloadKind");
        validateRequiredString(stepId, step, validation, "workloadName", "PARAM_REQUIRED",
                "K8s 镜像版本升级必须配置 workloadName");
        validateRequiredString(stepId, step, validation, "containerName", "PARAM_REQUIRED",
                "K8s 镜像版本升级必须配置 containerName");
        validateRequiredString(stepId, step, validation, "image", "PARAM_REQUIRED",
                "K8s 镜像版本升级必须配置 image");
        String workloadKind = param(step, "workloadKind");
        if (StrUtil.isNotBlank(workloadKind) && !"Deployment".equalsIgnoreCase(workloadKind)) {
            addError(validation, "with.workloadKind", stepId, "PARAM_VALUE_UNSUPPORTED",
                    "workloadKind 当前仅支持 Deployment");
        }
        validateOptionalInteger(stepId, step, validation, "replicas");
        validateOptionalInteger(stepId, step, validation, "rolloutTimeoutSeconds");
    }

    private void validateCodeMergeParams(String stepId, PipelineSpec.Step step, PipelineValidationRespVO validation) {
        validateRequiredString(stepId, step, validation, "baseBranch", "PARAM_REQUIRED", "代码合并必须配置 baseBranch");
        validateRequiredString(stepId, step, validation, "targetBranch", "PARAM_REQUIRED", "代码合并必须配置 targetBranch");
        Object branchesFromSubmit = step.getWith() == null ? null : step.getWith().get("branchesFromSubmit");
        if (branchesFromSubmit != null && !(branchesFromSubmit instanceof Boolean)) {
            addError(validation, "with.branchesFromSubmit", stepId, "PARAM_TYPE_INVALID",
                    "branchesFromSubmit 必须是布尔值");
        }
        Object branches = step.getWith() == null ? null : step.getWith().get("branches");
        if (branches != null && !(branches instanceof List<?>)) {
            addError(validation, "with.branches", stepId, "PARAM_TYPE_INVALID", "branches 必须是字符串数组");
            return;
        }
        if (branches instanceof List<?> branchList) {
            for (Object branch : branchList) {
                if (!(branch instanceof String str) || StrUtil.isBlank(str)) {
                    addError(validation, "with.branches", stepId, "PARAM_TYPE_INVALID", "branches 只能包含非空字符串");
                    return;
                }
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

    private void validateOptionalInteger(String stepId, PipelineSpec.Step step, PipelineValidationRespVO validation,
                                         String paramName) {
        if (step.getWith() == null || !step.getWith().containsKey(paramName)) {
            return;
        }
        Object value = step.getWith().get(paramName);
        if (value == null || StrUtil.isBlank(String.valueOf(value))) {
            return;
        }
        try {
            Integer.parseInt(String.valueOf(value));
        } catch (NumberFormatException ex) {
            addError(validation, "with." + paramName, stepId, "PARAM_TYPE_INVALID",
                    paramName + " 必须是整数");
        }
    }

    private void validateOptionalBoolean(String stepId, PipelineSpec.Step step, PipelineValidationRespVO validation,
                                         String paramName) {
        if (step.getWith() == null || !step.getWith().containsKey(paramName)) {
            return;
        }
        Object value = step.getWith().get(paramName);
        if (value != null && !(value instanceof Boolean)) {
            addError(validation, "with." + paramName, stepId, "PARAM_TYPE_INVALID",
                    paramName + " 必须是布尔值");
        }
    }

    private void validateOptionalVariables(String stepId, PipelineSpec.Step step, PipelineValidationRespVO validation) {
        if (step.getWith() == null || !step.getWith().containsKey("variables")) {
            return;
        }
        Object variables = step.getWith().get("variables");
        if (variables == null) {
            return;
        }
        if (!(variables instanceof List<?> variableList)) {
            addError(validation, "with.variables", stepId, "PARAM_TYPE_INVALID", "variables 必须是数组");
            return;
        }
        for (Object variable : variableList) {
            if (!(variable instanceof Map<?, ?> variableMap)
                    || StrUtil.isBlank(stringValue(variableMap.get("key")))) {
                addError(validation, "with.variables", stepId, "PARAM_TYPE_INVALID",
                        "variables 每一项必须包含非空 key");
                return;
            }
        }
    }

    private void validateRequiredMapString(String stepId, Map<?, ?> map, String key, String field,
                                           PipelineValidationRespVO validation, String message) {
        if (StrUtil.isBlank(stringValue(map.get(key)))) {
            addError(validation, field, stepId, "PARAM_REQUIRED", message);
        }
    }

    private String stringValue(Object value) {
        return value == null ? null : String.valueOf(value);
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
