package cn.iocoder.yudao.module.devops.service.pipeline.execution.handler;

import cn.iocoder.yudao.framework.common.util.json.JsonUtils;
import cn.iocoder.yudao.module.devops.dal.dataobject.pipeline.log.PipelineRunLogDO;
import cn.iocoder.yudao.module.devops.framework.pipeline.PipelineSpec;
import cn.iocoder.yudao.module.devops.service.pipeline.PipelineNodeRegistryServiceImpl;
import jakarta.annotation.Resource;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Set;

/**
 * 流水线 YAML 内置步骤占位处理器。
 *
 * <p>这些步骤当前先记录配置并标记成功，避免配置结构落地时阻塞整条流水线。
 * 需要真实执行时按 step 类型替换为专用 handler。
 */
@Slf4j
@Component
public class PipelineBuiltinStepHandler implements PipelineNodeHandler {

    private static final Set<String> SUPPORTED_TYPES = Set.of(
            PipelineNodeRegistryServiceImpl.TYPE_SETUP_JAVA,
            PipelineNodeRegistryServiceImpl.TYPE_SETUP_MAVEN_SETTINGS,
            PipelineNodeRegistryServiceImpl.TYPE_UNIT_TEST_REPORT,
            PipelineNodeRegistryServiceImpl.TYPE_ARTIFACT_UPLOAD,
            PipelineNodeRegistryServiceImpl.TYPE_JAVA_P3C_SCAN
    );

    @Resource
    private PipelineNodeLogHelper logHelper;

    @Override
    public boolean supports(String nodeType) {
        return SUPPORTED_TYPES.contains(nodeType);
    }

    @Override
    public NodeOutcome handle(PipelineNodeContext ctx) {
        PipelineRunLogDO runLog = logHelper.getOrCreateLog(ctx);
        if ("SUCCESS".equals(runLog.getStatus())) {
            return NodeOutcome.CONTINUE;
        }
        PipelineSpec.Node node = ctx.getNode();
        logHelper.markStarted(runLog, "处理流水线内置步骤");
        Map<String, Object> result = new LinkedHashMap<>();
        result.put("step", node.getType());
        result.put("with", node.getParams());
        result.put("message", "当前步骤已按流水线 YAML 结构识别，执行实现待接入");
        runLog.setResultJson(JsonUtils.toJsonString(result));
        logHelper.markSuccess(runLog, "流水线内置步骤已识别");
        log.info("[PipelineBuiltinStepHandler][runId({}) nodeId({}) step({}) 已识别]",
                ctx.getRun().getId(), node.getId(), node.getType());
        return NodeOutcome.CONTINUE;
    }

}
