package cn.iocoder.yudao.module.devops.service.pipeline.execution.handler;

import cn.hutool.core.collection.CollUtil;
import cn.iocoder.yudao.framework.common.exception.ServiceException;
import cn.iocoder.yudao.framework.common.util.json.JsonUtils;
import cn.iocoder.yudao.module.devops.dal.dataobject.pipeline.PipelineRunDO;
import cn.iocoder.yudao.module.devops.service.change.ChangeService;
import cn.iocoder.yudao.module.devops.service.pipeline.PipelineNodeRegistryServiceImpl;
import cn.iocoder.yudao.module.devops.service.pipeline.execution.context.PipelineRunChangeSnapshotContext;
import jakarta.annotation.Resource;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * 发布收尾步骤处理器。
 */
@Slf4j
@Component
public class ChangePublishFinalizeStepHandler implements PipelineStepHandler {

    @Resource
    private ChangeService changeService;

    @Override
    public boolean supports(String stepType) {
        return PipelineNodeRegistryServiceImpl.TYPE_CHANGE_PUBLISH_FINALIZE.equals(stepType);
    }

    @Override
    public StepRuntimeRequirement runtimeRequirement() {
        return StepRuntimeRequirement.PLATFORM;
    }

    @Override
    public StepResult handle(PipelineStepContext ctx) {
        try {
            List<Long> changeIds = resolveChangeIds(ctx.getRun());
            if (CollUtil.isEmpty(changeIds)) {
                return StepResult.continueWith("无变更需要发布收尾");
            }
            for (Long changeId : changeIds) {
                changeService.finalizePublishedChange(changeId);
            }
            Map<String, Object> outputs = new LinkedHashMap<>();
            outputs.put("finalizedChangeCount", changeIds.size());
            outputs.put("finalizedChangeIds", changeIds);
            return StepResult.builder()
                    .type(StepResultType.CONTINUE)
                    .summary("发布收尾完成")
                    .outputs(outputs)
                    .build();
        } catch (ServiceException ex) {
            log.error("[ChangePublishFinalizeStepHandler][runId({}) stepId({}) 发布收尾失败 code({}) message({})]",
                    ctx.getRun().getId(), ctx.getStep().getStepId(), ex.getCode(), ex.getMessage());
            return StepResult.builder()
                    .type(StepResultType.FAIL)
                    .summary("发布收尾失败")
                    .errorCode(String.valueOf(ex.getCode()))
                    .errorMessage(ex.getMessage())
                    .build();
        } catch (Exception ex) {
            log.error("[ChangePublishFinalizeStepHandler][runId({}) stepId({}) 发布收尾异常]",
                    ctx.getRun().getId(), ctx.getStep().getStepId(), ex);
            return StepResult.fail("发布收尾失败", ex.getMessage());
        }
    }

    private List<Long> resolveChangeIds(PipelineRunDO run) {
        List<PipelineRunChangeSnapshotContext> snapshots = JsonUtils.parseArray(run.getChangeSnapshotJson(),
                PipelineRunChangeSnapshotContext.class);
        if (CollUtil.isNotEmpty(snapshots)) {
            return snapshots.stream()
                    .map(PipelineRunChangeSnapshotContext::getChangeId)
                    .filter(java.util.Objects::nonNull)
                    .toList();
        }
        if (run.getChangeId() == null) {
            return List.of();
        }
        List<Long> changeIds = new ArrayList<>();
        changeIds.add(run.getChangeId());
        return changeIds;
    }

}
