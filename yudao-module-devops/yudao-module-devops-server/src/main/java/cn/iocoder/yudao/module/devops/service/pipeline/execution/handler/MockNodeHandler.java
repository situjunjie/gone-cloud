package cn.iocoder.yudao.module.devops.service.pipeline.execution.handler;

import cn.iocoder.yudao.module.devops.dal.dataobject.pipeline.log.PipelineRunLogDO;
import cn.iocoder.yudao.module.devops.framework.pipeline.PipelineSpec;
import cn.iocoder.yudao.module.devops.service.pipeline.PipelineNodeRegistryServiceImpl;
import jakarta.annotation.Resource;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

/**
 * Mock 节点处理器(测试用)。
 *
 * <p>读取节点参数 {@code shouldFail} 决定返回 SUCCESS/FAIL,用于流水线测试与演示。
 */
@Slf4j
@Component
public class MockNodeHandler implements PipelineNodeHandler {

    @Resource
    private PipelineNodeLogHelper logHelper;

    @Override
    public boolean supports(String nodeType) {
        return PipelineNodeRegistryServiceImpl.TYPE_MOCK.equals(nodeType);
    }

    @Override
    public NodeOutcome handle(PipelineNodeContext ctx) {
        PipelineRunLogDO runLog = logHelper.getOrCreateLog(ctx);
        PipelineSpec.Node node = ctx.getNode();

        // 已成功完成则跳过(幂等重入)
        if ("SUCCESS".equals(runLog.getStatus())) {
            log.info("[MockNodeHandler][runId({}) nodeId({}) 已成功,跳过]",
                    ctx.getRun().getId(), node.getId());
            return NodeOutcome.CONTINUE;
        }

        boolean shouldFail = false;
        if (node.getParams() != null && node.getParams().get("shouldFail") instanceof Boolean fail) {
            shouldFail = fail;
        }

        logHelper.markStarted(runLog, "执行 Mock 节点");

        if (shouldFail) {
            log.info("[MockNodeHandler][runId({}) nodeId({}) Mock 失败]",
                    ctx.getRun().getId(), node.getId());
            logHelper.markFailed(runLog, "Mock 节点模拟失败", "shouldFail=true");
            return NodeOutcome.FAIL;
        } else {
            log.info("[MockNodeHandler][runId({}) nodeId({}) Mock 成功]",
                    ctx.getRun().getId(), node.getId());
            logHelper.markSuccess(runLog, "Mock 节点执行成功");
            return NodeOutcome.CONTINUE;
        }
    }

}
