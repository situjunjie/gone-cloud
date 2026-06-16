package cn.iocoder.yudao.module.devops.service.pipeline.execution.handler;

import cn.hutool.core.util.StrUtil;
import cn.iocoder.yudao.module.devops.dal.dataobject.pipeline.log.PipelineRunLogDO;
import cn.iocoder.yudao.module.devops.service.pipeline.PipelineNodeRegistryServiceImpl;
import cn.iocoder.yudao.module.devops.service.pipeline.execution.CodeMergeExecutionStatus;
import cn.iocoder.yudao.module.devops.service.pipeline.execution.CodeMergeService;
import jakarta.annotation.Resource;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/**
 * CodeMerge 步骤处理器。
 */
@Slf4j
@Component
public class CodeMergeStepHandler implements PipelineStepHandler {

    @Resource
    private CodeMergeService codeMergeService;
    @Resource
    private PipelineStepLogHelper logHelper;

    @Override
    public boolean supports(String stepType) {
        return PipelineNodeRegistryServiceImpl.TYPE_CODE_MERGE.equals(stepType);
    }

    @Override
    public StepRuntimeRequirement runtimeRequirement() {
        return StepRuntimeRequirement.PLATFORM;
    }

    @Override
    public StepResult handle(PipelineStepContext ctx) {
        try {
            PipelineRunLogDO runLog = logHelper.getOrCreateLog(ctx);
            CodeMergeExecutionStatus status = codeMergeService.executeCodeMergeStep(ctx.getRun(), runLog,
                    parseBranches(ctx), getBoolean(ctx, "branchesFromSubmit", true),
                    resolveString(ctx, "baseBranch"), resolveString(ctx, "targetBranch"),
                    getBoolean(ctx, "pushOnSuccess", true), ctx.getUserId());
            if (status == CodeMergeExecutionStatus.SUCCESS) {
                String branch = ctx.getRun().getBranchName();
                String commitSha = ctx.getRun().getCommitSha();
                return StepResult.builder()
                        .type(StepResultType.CONTINUE)
                        .summary("代码合并完成")
                        .outputs(Map.of(
                                "mergedBranch", StrUtil.blankToDefault(branch, ""),
                                "mergedCommitSha", StrUtil.blankToDefault(commitSha, ""),
                                "branchName", StrUtil.blankToDefault(branch, ""),
                                "commitSha", StrUtil.blankToDefault(commitSha, "")))
                        .build();
            }
            if (status == CodeMergeExecutionStatus.SUSPEND) {
                return StepResult.suspend("代码合并冲突，等待处理");
            }
            return StepResult.fail("代码合并失败", "代码合并执行失败");
        } catch (Exception ex) {
            log.error("[CodeMergeStepHandler][runId({}) stepId({}) 执行异常]",
                    ctx.getRun().getId(), ctx.getStep().getStepId(), ex);
            return StepResult.fail("代码合并失败", ex.getMessage());
        }
    }

    private List<String> parseBranches(PipelineStepContext ctx) {
        Object value = ctx.getStep().getWith() == null ? null : ctx.getStep().getWith().get("branches");
        if (!(value instanceof List<?> list)) {
            return List.of();
        }
        List<String> branches = new ArrayList<>();
        for (Object item : list) {
            if (item instanceof String branch && StrUtil.isNotBlank(branch)) {
                branches.add(branch);
            }
        }
        return branches;
    }

    private boolean getBoolean(PipelineStepContext ctx, String key, boolean defaultValue) {
        Object value = ctx.getStep().getWith() == null ? null : ctx.getStep().getWith().get(key);
        return value instanceof Boolean bool ? bool : defaultValue;
    }

    private String resolveString(PipelineStepContext ctx, String key) {
        Object value = ctx.getStep().getWith() == null ? null : ctx.getStep().getWith().get(key);
        if (value == null) {
            return null;
        }
        String text = String.valueOf(value);
        for (Map.Entry<String, Object> entry : ctx.getSharedState().entrySet()) {
            if (entry.getValue() instanceof String str) {
                text = text.replace("${" + toEnvKey(entry.getKey()) + "}", str)
                        .replace("${" + entry.getKey() + "}", str);
            }
        }
        return text;
    }

    private String toEnvKey(String key) {
        StringBuilder builder = new StringBuilder();
        for (int i = 0; i < key.length(); i++) {
            char ch = key.charAt(i);
            if (Character.isUpperCase(ch) && i > 0) {
                builder.append('_');
            }
            builder.append(Character.toUpperCase(ch));
        }
        return builder.toString();
    }

}
