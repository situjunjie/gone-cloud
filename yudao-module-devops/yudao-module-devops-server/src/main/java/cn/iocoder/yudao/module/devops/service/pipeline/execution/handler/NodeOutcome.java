package cn.iocoder.yudao.module.devops.service.pipeline.execution.handler;

/**
 * 节点处理结果,驱动责任链流转。
 *
 * <p>流水线即责任链：每个节点是链上一环,{@link PipelineNodeHandler#handle} 返回本枚举,
 * 引擎据此流转到下一环、挂起待外部事件、或中止。
 */
public enum NodeOutcome {

    /**
     * 继续 → 下一环。
     *
     * <p>构建类节点(同步阻塞跑完)、平台节点调用下游 Service 并直接完成均返回此值。
     */
    CONTINUE,

    /**
     * 挂起 → 持久化位置并停止,待外部事件重入。
     *
     * <p>审批节点触发 BPM 后挂起,等审批完成事件推进;代码合并节点遇冲突挂起,等人工解决续跑。
     * 引擎**不阻塞线程等**,挂起后直接返回。外部事件发生时重入引擎,幂等跳过已完成节点从挂起处续跑。
     */
    SUSPEND,

    /**
     * 失败 → run 置 FAILED。
     *
     * <p>构建节点脚本退出码非零、平台节点下游 Service 校验失败或执行失败均返回此值。
     */
    FAIL

}
