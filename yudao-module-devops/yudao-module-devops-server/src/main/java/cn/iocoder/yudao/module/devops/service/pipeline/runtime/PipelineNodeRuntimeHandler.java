package cn.iocoder.yudao.module.devops.service.pipeline.runtime;

public interface PipelineNodeRuntimeHandler {

    String getNodeType();

    void onStarted(PipelineNodeCallbackContext context);

    void onCompleted(PipelineNodeCallbackContext context);

    void onFailed(PipelineNodeCallbackContext context);

}
