package cn.iocoder.yudao.module.devops.dal.dataobject.pipeline.job;

import cn.iocoder.yudao.framework.tenant.core.db.TenantBaseDO;
import com.baomidou.mybatisplus.annotation.KeySequence;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;
import lombok.EqualsAndHashCode;
import lombok.ToString;

import java.time.Duration;
import java.time.LocalDateTime;

/**
 * DevOps 流水线运行任务 DO。
 */
@TableName("dev_pipeline_run_job")
@KeySequence("dev_pipeline_run_job_seq")
@Data
@EqualsAndHashCode(callSuper = true)
@ToString(callSuper = true)
public class PipelineRunJobDO extends TenantBaseDO {

    @TableId
    private Long id;
    /**
     * 流水线运行编号。
     */
    private Long pipelineRunId;
    /**
     * 阶段编号。
     */
    private String stageId;
    /**
     * 阶段名称。
     */
    private String stageName;
    /**
     * 任务编号。
     */
    private String jobId;
    /**
     * 任务名称。
     */
    private String jobName;
    /**
     * 任务状态。
     */
    private String status;
    /**
     * 依赖任务 JSON。
     */
    private String needsJson;
    /**
     * 执行次数。
     */
    private Integer attempt = 1;
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
     * 执行耗时，毫秒。
     */
    private Long durationMillis;
    /**
     * 运行时类型。
     */
    private String runtimeType;
    /**
     * 执行资源池。
     */
    private String executorGroup;
    /**
     * 执行容器镜像。
     */
    private String executorImage;
    /**
     * 运行时实例编号。
     */
    private String runtimeId;
    /**
     * 运行时实例名称。
     */
    private String runtimeName;
    /**
     * 工作目录路径。
     */
    private String workspacePath;
    /**
     * 保留字段：调度 worker 编号。
     */
    private String workerId;
    /**
     * 保留字段：租约过期时间。
     */
    private LocalDateTime leaseUntil;
    /**
     * 摘要。
     */
    private String summary;
    /**
     * 错误信息。
     */
    private String errorMessage;

    public void setStartedAt(LocalDateTime startedAt) {
        this.startedAt = startedAt;
        refreshDurationMillis();
    }

    public void setFinishedAt(LocalDateTime finishedAt) {
        this.finishedAt = finishedAt;
        refreshDurationMillis();
    }

    private void refreshDurationMillis() {
        if (startedAt == null || finishedAt == null) {
            this.durationMillis = null;
            return;
        }
        this.durationMillis = Math.max(0, Duration.between(startedAt, finishedAt).toMillis());
    }

}
