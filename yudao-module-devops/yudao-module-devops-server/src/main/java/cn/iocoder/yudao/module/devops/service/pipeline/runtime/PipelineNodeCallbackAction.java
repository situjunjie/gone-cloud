package cn.iocoder.yudao.module.devops.service.pipeline.runtime;

/**
 * Jenkins 节点生命周期回调动作。
 */
public interface PipelineNodeCallbackAction {

    String STARTED = "STARTED";
    String COMPLETED = "COMPLETED";
    String FAILED = "FAILED";

}
