package cn.iocoder.yudao.module.devops.service.pipeline.execution.handler;

import cn.iocoder.yudao.module.devops.dal.dataobject.pipeline.PipelineDefinitionVersionDO;
import cn.iocoder.yudao.module.devops.dal.dataobject.pipeline.PipelineRunDO;
import cn.iocoder.yudao.module.devops.framework.pipeline.PipelineSpec;
import lombok.Builder;
import lombok.Data;

import java.nio.file.Path;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * 流水线节点处理上下文。
 *
 * <p>承载 {@link PipelineNodeHandler#handle} 所需的运行时数据：run / version / 当前 node /
 * 工作区 / 共享状态，使 handler 可写 RunLog、读元数据、传递数据。
 */
@Data
@Builder
public class PipelineNodeContext {

    /**
     * 流水线运行。
     */
    private PipelineRunDO run;

    /**
     * 流水线定义版本(含 specJson)。
     */
    private PipelineDefinitionVersionDO version;

    /**
     * 当前处理的节点。
     */
    private PipelineSpec.Node node;

    /**
     * 本地工作区路径(平台侧合并/推送用)。代码合并节点用该字段。
     */
    private Path localWorkspace;

    /**
     * 远程构建机工作区路径字符串(BUILD 节点用,作为 ExecContext.workingDir)。
     * 同一 run 的多个 BUILD 节点复用同一远程目录,下游可见上游产物。
     */
    private String remoteBuildWorkspace;

    /**
     * 共享状态(跨节点传递)。handler 可写入中间结果供后续节点读取。
     */
    @Builder.Default
    private Map<String, Object> sharedState = new ConcurrentHashMap<>();

    /**
     * 触发人用户编号。
     */
    private Long userId;

}
