package cn.iocoder.yudao.module.devops.dal.dataobject.pipeline.log;

import cn.iocoder.yudao.framework.tenant.core.db.TenantBaseDO;
import com.baomidou.mybatisplus.annotation.KeySequence;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;
import lombok.EqualsAndHashCode;
import lombok.ToString;

import java.time.LocalDateTime;

/**
 * DevOps 流水线运行日志 DO。
 */
@TableName("dev_pipeline_run_log")
@KeySequence("dev_pipeline_run_log_seq")
@Data
@EqualsAndHashCode(callSuper = true)
@ToString(callSuper = true)
public class PipelineRunLogDO extends TenantBaseDO {

    @TableId
    private Long id;
    /**
     * 流水线运行编号。
     */
    private Long pipelineRunId;
    /**
     * 父日志编号。
     */
    private Long parentId;
    /**
     * 节点编号。
     */
    private String nodeId;
    /**
     * 节点类型。
     */
    private String nodeType;
    /**
     * 节点名称。
     */
    private String nodeName;
    /**
     * 日志层级。枚举 {@code PipelineRunLogLevelEnum}。
     */
    private String logLevel;
    /**
     * 状态。枚举 {@code PipelineRunLogStatusEnum}。
     */
    private String status;
    /**
     * 排序。
     */
    private Integer sort;
    /**
     * 开始时间。
     */
    private LocalDateTime startedAt;
    /**
     * 结束时间。
     */
    private LocalDateTime finishedAt;
    /**
     * 摘要。
     */
    private String summary;
    /**
     * 运行上下文 JSON。
     */
    private String contextJson;
    /**
     * 运行结果 JSON。
     */
    private String resultJson;
    /**
     * 错误信息。
     */
    private String errorMessage;

}
