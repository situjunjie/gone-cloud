package cn.iocoder.yudao.module.devops.service.pipeline.runtime;

import cn.iocoder.yudao.module.devops.service.pipeline.PipelineNodeRegistryServiceImpl;
import jakarta.annotation.Resource;
import org.springframework.stereotype.Component;

import java.util.Set;

/**
 * Jenkins 执行节点处理器：平台侧只记录节点生命周期，具体执行在 Jenkinsfile 中完成。
 */
@Component
public class JenkinsPipelineNodeRuntimeHandler implements PipelineNodeRuntimeHandler {

    private static final Set<String> SUPPORTED_NODE_TYPES = Set.of(
            PipelineNodeRegistryServiceImpl.TYPE_CHECKOUT,
            PipelineNodeRegistryServiceImpl.TYPE_UNIT_TEST,
            PipelineNodeRegistryServiceImpl.TYPE_BUILD_ARTIFACT,
            PipelineNodeRegistryServiceImpl.TYPE_BUILD_IMAGE,
            PipelineNodeRegistryServiceImpl.TYPE_REPORT_ARTIFACTS);

    @Resource
    private PipelineNodeRuntimeSupport runtimeSupport;

    @Override
    public boolean supports(String nodeType) {
        return SUPPORTED_NODE_TYPES.contains(nodeType);
    }

    @Override
    public String getNodeType() {
        return "JENKINS";
    }

    @Override
    public void onStarted(PipelineNodeCallbackContext context) {
        runtimeSupport.markStarted(context);
    }

    @Override
    public void onCompleted(PipelineNodeCallbackContext context) {
        runtimeSupport.markCompleted(context);
    }

    @Override
    public void onFailed(PipelineNodeCallbackContext context) {
        runtimeSupport.markFailed(context);
    }

}
