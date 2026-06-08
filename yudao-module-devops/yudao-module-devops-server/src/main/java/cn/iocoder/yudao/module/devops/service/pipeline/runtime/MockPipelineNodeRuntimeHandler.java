package cn.iocoder.yudao.module.devops.service.pipeline.runtime;

import cn.iocoder.yudao.module.devops.service.pipeline.PipelineNodeRegistryServiceImpl;
import jakarta.annotation.Resource;
import org.springframework.stereotype.Component;

/**
 * Mock 节点处理器：只更新节点生命周期状态，不执行额外平台逻辑。
 */
@Component
public class MockPipelineNodeRuntimeHandler implements PipelineNodeRuntimeHandler {

    @Resource
    private PipelineNodeRuntimeSupport runtimeSupport;

    @Override
    public String getNodeType() {
        return PipelineNodeRegistryServiceImpl.TYPE_MOCK;
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
