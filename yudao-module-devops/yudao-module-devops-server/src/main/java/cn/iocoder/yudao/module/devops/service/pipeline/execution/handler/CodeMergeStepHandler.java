package cn.iocoder.yudao.module.devops.service.pipeline.execution.handler;

import cn.hutool.core.util.StrUtil;
import cn.iocoder.yudao.module.devops.dal.dataobject.pipeline.log.PipelineRunLogDO;
import cn.iocoder.yudao.module.devops.service.pipeline.PipelineNodeRegistryServiceImpl;
import cn.iocoder.yudao.module.devops.service.pipeline.execution.CodeMergeExecutionStatus;
import cn.iocoder.yudao.module.devops.service.pipeline.execution.CodeMergeService;
import cn.iocoder.yudao.module.devops.service.pipeline.execution.PipelineExecutionContext;
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
    public boolean sourceWorkspaceRequired() {
        return true;  // 需要源码工作区
    }

    @Override
    public StepResult handle(PipelineExecutionContext ctx) {
        try {
            PipelineRunLogDO runLog = logHelper.getOrCreateLog(ctx);
            CodeMergeExecutionStatus status = codeMergeService.executeCodeMergeStep(ctx.getRun(), runLog,
                    parseBranches(ctx), getBoolean(ctx, "branchesFromSubmit", true),
                    resolveString(ctx, "baseBranch"), resolveString(ctx, "targetBranch"),
                    getBoolean(ctx, "pushOnSuccess", true), ctx.getWorkspace(), ctx.getUserId());
            if (status == CodeMergeExecutionStatus.SUCCESS) {
                String branch = ctx.getRun().getBranchName();
                String commitSha = ctx.getRun().getCommitSha();
                Map<String, Object> outputs = Map.of(
                        "mergedBranch", StrUtil.blankToDefault(branch, ""),
                        "mergedCommitSha", StrUtil.blankToDefault(commitSha, ""),
                        "branchName", StrUtil.blankToDefault(branch, ""),
                        "commitSha", StrUtil.blankToDefault(commitSha, ""));
                ctx.getSharedState().putAll(outputs);
                ctx.setSourcePrepared(true);
                ctx.setSourcePreparedByCodeMerge(true);
                return StepResult.builder()
                        .type(StepResultType.CONTINUE)
                        .summary("代码合并完成")
                        .outputs(outputs)
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

    private List<String> parseBranches(PipelineExecutionContext ctx) {
        Object value = ctx.getResolvedWith().get("branches");
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

    @Override
    public void cancel(PipelineExecutionContext ctx) {
        codeMergeService.cancelCodeMergeIfActive(ctx.getRun(), ctx.getStep().getStepId(), ctx.getUserId());
    }

    private boolean getBoolean(PipelineExecutionContext ctx, String key, boolean defaultValue) {
        Object value = ctx.getResolvedWith().get(key);
        if (value instanceof Boolean bool) {
            return bool;
        }
        if (value instanceof String str && StrUtil.isNotBlank(str)) {
            if ("true".equalsIgnoreCase(str) || "false".equalsIgnoreCase(str)) {
                return Boolean.parseBoolean(str);
            }
        }
        return defaultValue;
    }

    private String resolveString(PipelineExecutionContext ctx, String key) {
        Object value = ctx.getResolvedWith().get(key);
        return value == null ? null : String.valueOf(value);
    }

}
