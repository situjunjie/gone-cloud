package cn.iocoder.yudao.module.devops.service.pipeline.execution;

import cn.hutool.core.util.StrUtil;
import cn.iocoder.yudao.module.devops.framework.pipeline.PipelineSpec;
import cn.iocoder.yudao.module.devops.service.pipeline.execution.handler.PipelineStepContext;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * 流水线变量解析器，支持 YAML 参数中的简单 ${VAR} 占位符替换。
 */
public final class PipelineVariableResolver {

    private static final Pattern PLACEHOLDER_PATTERN = Pattern.compile("\\$\\{([A-Za-z_][A-Za-z0-9_]*)}");

    private PipelineVariableResolver() {
    }

    public static Map<String, Object> resolveStepWith(PipelineStepContext ctx) {
        Map<String, Object> with = ctx.getStep() == null ? null : ctx.getStep().getWith();
        if (with == null) {
            return new LinkedHashMap<>();
        }
        return resolveMap(with, buildVariables(ctx));
    }

    public static PipelineSpec.ExecutableStep resolveStep(PipelineStepContext ctx) {
        PipelineSpec.ExecutableStep source = ctx.getStep();
        PipelineSpec.ExecutableStep step = new PipelineSpec.ExecutableStep();
        step.setStageId(source.getStageId());
        step.setStageName(source.getStageName());
        step.setJobId(source.getJobId());
        step.setJobName(source.getJobName());
        step.setRunsOn(source.getRunsOn());
        step.setStepId(source.getStepId());
        step.setName(source.getName());
        step.setStep(source.getStep());
        step.setEnabled(source.getEnabled());
        step.setWith(resolveStepWith(ctx));
        step.setTimeoutSeconds(source.getTimeoutSeconds());
        step.setRetryTimes(source.getRetryTimes());
        step.setFailStrategy(source.getFailStrategy());
        return step;
    }

    public static String resolveText(String text, Map<String, Object> variables) {
        if (StrUtil.isBlank(text) || variables == null || variables.isEmpty()) {
            return text;
        }
        Matcher matcher = PLACEHOLDER_PATTERN.matcher(text);
        StringBuffer buffer = new StringBuffer();
        while (matcher.find()) {
            Object value = variables.get(matcher.group(1));
            matcher.appendReplacement(buffer, Matcher.quoteReplacement(value == null ? matcher.group() : String.valueOf(value)));
        }
        matcher.appendTail(buffer);
        return buffer.toString();
    }

    public static boolean containsPlaceholder(String text) {
        return StrUtil.isNotBlank(text) && PLACEHOLDER_PATTERN.matcher(text).find();
    }

    private static Map<String, Object> buildVariables(PipelineStepContext ctx) {
        Map<String, Object> variables = new LinkedHashMap<>();
        if (ctx.getSharedState() != null) {
            ctx.getSharedState().forEach((key, value) -> putVariable(variables, key, value));
        }
        if (ctx.getRun() != null) {
            putVariable(variables, "runId", ctx.getRun().getId());
            putVariable(variables, "definitionId", ctx.getRun().getDefinitionId());
            putVariable(variables, "definitionVersionId", ctx.getRun().getDefinitionVersionId());
            putVariable(variables, "appId", ctx.getRun().getAppId());
            putVariable(variables, "applicationEnvId", ctx.getRun().getApplicationEnvId());
            putVariable(variables, "changeId", ctx.getRun().getChangeId());
            putVariable(variables, "changeEnvId", ctx.getRun().getChangeEnvId());
            putVariable(variables, "branchName", ctx.getRun().getBranchName());
            putVariable(variables, "commitSha", ctx.getRun().getCommitSha());
            putVariable(variables, "triggerType", ctx.getRun().getTriggerType());
            putVariable(variables, "triggerUserId", ctx.getRun().getTriggerUserId());
        }
        if (ctx.getJob() != null) {
            putVariable(variables, "stageId", ctx.getJob().getStageId());
            putVariable(variables, "stageName", ctx.getJob().getStageName());
            putVariable(variables, "jobId", ctx.getJob().getJobId());
            putVariable(variables, "jobName", ctx.getJob().getName());
        }
        if (ctx.getStep() != null) {
            putVariable(variables, "stepId", ctx.getStep().getStepId());
            putVariable(variables, "stepName", ctx.getStep().getName());
            putVariable(variables, "stepType", ctx.getStep().getStep());
        }
        return variables;
    }

    @SuppressWarnings("unchecked")
    private static Object resolveValue(Object value, Map<String, Object> variables) {
        if (value instanceof String str) {
            return resolveText(str, variables);
        }
        if (value instanceof Map<?, ?> map) {
            return resolveMap((Map<?, ?>) map, variables);
        }
        if (value instanceof List<?> list) {
            List<Object> resolved = new ArrayList<>(list.size());
            for (Object item : list) {
                resolved.add(resolveValue(item, variables));
            }
            return resolved;
        }
        return value;
    }

    private static Map<String, Object> resolveMap(Map<?, ?> source, Map<String, Object> variables) {
        Map<String, Object> resolved = new LinkedHashMap<>();
        source.forEach((key, value) -> {
            if (key != null) {
                resolved.put(String.valueOf(key), resolveValue(value, variables));
            }
        });
        return resolved;
    }

    private static void putVariable(Map<String, Object> variables, String key, Object value) {
        if (StrUtil.isBlank(key) || value == null) {
            return;
        }
        variables.put(key, value);
        variables.put(toEnvKey(key), value);
    }

    private static String toEnvKey(String key) {
        if (key.matches("[A-Z][A-Z0-9_]*")) {
            return key;
        }
        StringBuilder builder = new StringBuilder();
        for (int i = 0; i < key.length(); i++) {
            char ch = key.charAt(i);
            if (Character.isUpperCase(ch) && i > 0) {
                builder.append('_');
            } else if (ch == '-' || ch == '.') {
                builder.append('_');
                continue;
            }
            builder.append(Character.toUpperCase(ch));
        }
        return builder.toString();
    }

}
