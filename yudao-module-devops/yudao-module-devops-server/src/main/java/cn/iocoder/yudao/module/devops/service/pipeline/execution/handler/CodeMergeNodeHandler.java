package cn.iocoder.yudao.module.devops.service.pipeline.execution.handler;

import cn.hutool.core.util.StrUtil;
import cn.iocoder.yudao.module.devops.service.pipeline.PipelineNodeRegistryServiceImpl;
import cn.iocoder.yudao.module.devops.service.pipeline.execution.CodeMergeExecutionStatus;
import cn.iocoder.yudao.module.devops.service.pipeline.execution.CodeMergeService;
import jakarta.annotation.Resource;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

/**
 * 代码合并节点处理器。
 */
@Slf4j
@Component
public class CodeMergeNodeHandler implements PipelineNodeHandler {

    @Resource
    private CodeMergeService codeMergeService;

    @Override
    public boolean supports(String nodeType) {
        return PipelineNodeRegistryServiceImpl.TYPE_CODE_MERGE.equals(nodeType)
                || PipelineNodeRegistryServiceImpl.TYPE_CODE_MERGE_LEGACY.equals(nodeType);
    }

    @Override
    public NodeOutcome handle(PipelineNodeContext ctx) {
        try {
            CodeMergeExecutionStatus status = codeMergeService.executeCodeMergeNode(ctx.getRun(), ctx.getStep().getStepId(),
                    ctx.getStep().getName(), ctx.getUserId());
            if (status == CodeMergeExecutionStatus.SUCCESS) {
                putIfNotBlank(ctx, "branchName", ctx.getRun().getBranchName());
                putIfNotBlank(ctx, "commitSha", ctx.getRun().getCommitSha());
                return NodeOutcome.CONTINUE;
            }
            if (status == CodeMergeExecutionStatus.SUSPEND) {
                return NodeOutcome.SUSPEND;
            }
            log.error("[CodeMergeNodeHandler][runId({}) stepId({}) 代码合并失败]",
                    ctx.getRun().getId(), ctx.getStep().getStepId());
            return NodeOutcome.FAIL;
        } catch (Exception ex) {
            log.error("[CodeMergeNodeHandler][runId({}) stepId({}) 代码合并异常]",
                    ctx.getRun().getId(), ctx.getStep().getStepId(), ex);
            return NodeOutcome.FAIL;
        }
    }

    private void putIfNotBlank(PipelineNodeContext ctx, String key, String value) {
        if (StrUtil.isNotBlank(value)) {
            ctx.getSharedState().put(key, value);
        }
    }

}
