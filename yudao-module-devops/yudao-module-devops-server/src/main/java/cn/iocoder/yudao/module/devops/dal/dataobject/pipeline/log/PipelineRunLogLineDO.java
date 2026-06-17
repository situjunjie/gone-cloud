package cn.iocoder.yudao.module.devops.dal.dataobject.pipeline.log;

import cn.iocoder.yudao.framework.tenant.core.db.TenantBaseDO;
import com.baomidou.mybatisplus.annotation.KeySequence;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;
import lombok.EqualsAndHashCode;
import lombok.ToString;

/**
 * DevOps 流水线运行行级日志 DO。
 */
@TableName("dev_pipeline_run_log_line")
@KeySequence("dev_pipeline_run_log_line_seq")
@Data
@EqualsAndHashCode(callSuper = true)
@ToString(callSuper = true)
public class PipelineRunLogLineDO extends TenantBaseDO {

    @TableId
    private Long id;
    /**
     * 流水线运行编号。
     */
    private Long pipelineRunId;
    /**
     * 流水线运行日志编号。
     */
    private Long runLogId;
    /**
     * 阶段编号。
     */
    private String stageId;
    /**
     * 任务编号。
     */
    private String jobId;
    /**
     * 步骤编号。
     */
    private String stepId;
    /**
     * 行号，同一个 runLogId 内单调递增。
     */
    private Long lineNo;
    /**
     * 输出流类型，stdout / stderr。
     */
    private String streamType;
    /**
     * 日志内容。
     */
    private String content;

}
