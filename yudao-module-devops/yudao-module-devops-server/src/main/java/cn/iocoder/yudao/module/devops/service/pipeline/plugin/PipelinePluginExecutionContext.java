package cn.iocoder.yudao.module.devops.service.pipeline.plugin;

import lombok.Builder;
import lombok.Value;

import java.time.LocalDateTime;

/**
 * 流水线插件执行上下文快照。
 */
@Value
@Builder
public class PipelinePluginExecutionContext {

    /** 租户编号。 */
    Long tenantId;
    /** 流水线运行编号。 */
    Long runId;
    /** 流水线定义编号。 */
    Long definitionId;
    /** 流水线名称。 */
    String pipelineName;
    /** 触发人用户编号。 */
    Long triggerUserId;
    /** 操作人展示名称。 */
    String operator;
    /** 阶段编号。 */
    String stageId;
    /** 阶段名称。 */
    String stageName;
    /** 任务编号。 */
    String jobId;
    /** 任务名称。 */
    String jobName;
    /** 任务终态。 */
    String status;
    /** 任务开始时间。 */
    LocalDateTime startedAt;
    /** 任务结束时间。 */
    LocalDateTime finishedAt;
    /** 任务耗时，毫秒。 */
    Long durationMillis;

}
