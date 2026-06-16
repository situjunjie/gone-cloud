package cn.iocoder.yudao.module.devops.service.pipeline.execution.handler;

import cn.iocoder.yudao.module.devops.dal.dataobject.pipeline.PipelineDefinitionVersionDO;
import cn.iocoder.yudao.module.devops.dal.dataobject.pipeline.PipelineRunDO;
import cn.iocoder.yudao.module.devops.dal.dataobject.pipeline.job.PipelineRunJobDO;
import cn.iocoder.yudao.module.devops.framework.pipeline.PipelineSpec;
import lombok.Builder;
import lombok.Data;

import java.nio.file.Path;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * 流水线步骤处理上下文。
 */
@Data
@Builder
public class PipelineStepContext {

    /**
     * 流水线运行记录。
     */
    private PipelineRunDO run;
    /**
     * 流水线定义版本记录。
     */
    private PipelineDefinitionVersionDO version;
    /**
     * 流水线任务运行记录。
     */
    private PipelineRunJobDO jobRun;
    /**
     * 可执行任务定义。
     */
    private PipelineSpec.ExecutableJob job;
    /**
     * 可执行步骤定义。
     */
    private PipelineSpec.ExecutableStep step;
    /**
     * 步骤工作目录。
     */
    private Path workspace;
    /**
     * 同一任务内的共享状态。
     */
    @Builder.Default
    private Map<String, Object> sharedState = new ConcurrentHashMap<>();
    /**
     * 操作用户编号。
     */
    private Long userId;

}
