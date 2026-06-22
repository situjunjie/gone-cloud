package cn.iocoder.yudao.module.devops.service.pipeline.execution.handler;

/**
 * 流水线节点处理器(责任链上一环的处理策略)。
 *
 * <p>整条流水线是一条**责任链**：拓扑排序后的有序节点逐环处理。每环的具体行为由 {@link #supports(String)}
 * 解析到对应的 {@link PipelineNodeHandler} bean（策略/注册表模式），{@link #handle(PipelineNodeContext)}
 * 处理本环并返回 {@link NodeOutcome} 驱动链流转。
 *
 * <p>实现约定：
 * <ul>
 *     <li>{@code CodeMergeNodeHandler} → 调 {@code GitWorkspaceService} 合并+推送；冲突 → SUSPEND。</li>
 *     <li>{@code BuildNodeHandler} → 执行 Shell 脚本；退出码 0 → CONTINUE 否则 FAIL。</li>
 * </ul>
 *
 * <p>注意：handler 不可阻塞线程等人工。人工闸口(审批/合并冲突)返回 SUSPEND,由外部事件重入引擎续跑。
 *
 * <p>新流水线执行扩展请优先实现 {@link PipelineStepHandler}，当前执行引擎已基于 step handler 运行。
 */
public interface PipelineNodeHandler {

    /**
     * 该节点类型是否由本 handler 处理。
     *
     * @param nodeType 节点类型
     * @return 是否支持
     */
    boolean supports(String nodeType);

    /**
     * 处理当前节点,返回链流转信号。
     *
     * @param ctx 节点处理上下文
     * @return 流转信号(CONTINUE / SUSPEND / FAIL)
     */
    NodeOutcome handle(PipelineNodeContext ctx);

}
