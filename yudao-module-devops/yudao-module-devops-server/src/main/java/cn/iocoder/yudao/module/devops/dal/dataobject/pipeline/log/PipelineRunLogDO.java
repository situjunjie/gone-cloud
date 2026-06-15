package cn.iocoder.yudao.module.devops.dal.dataobject.pipeline.log;

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
     * 步骤编号。
     */
    private String stepId;
    /**
     * 步骤类型。
     */
    private String stepType;
    /**
     * 步骤名称。
     */
    private String stepName;
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
     * 执行次数。
     */
    private Integer attempt = 1;
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
     * 运行时类型。LOCAL / DOCKER。
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
     * 运行时实例编号，例如容器编号。
     */
    private String runtimeId;
    /**
     * 运行时实例名称，例如容器名称。
     */
    private String runtimeName;
    /**
     * 工作目录路径。
     */
    private String workspacePath;
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
     * 完整日志文件地址。
     */
    private String logFileUrl;
    /**
     * 日志是否截断。
     */
    private Boolean logTruncated = false;
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

    public String getNodeId() {
        return stepId;
    }

    public void setNodeId(String nodeId) {
        this.stepId = nodeId;
    }

    public String getNodeType() {
        return stepType;
    }

    public void setNodeType(String nodeType) {
        this.stepType = nodeType;
    }

    public String getNodeName() {
        return stepName;
    }

    public void setNodeName(String nodeName) {
        this.stepName = nodeName;
    }

    private void refreshDurationMillis() {
        if (startedAt == null || finishedAt == null) {
            this.durationMillis = null;
            return;
        }
        this.durationMillis = Math.max(0, Duration.between(startedAt, finishedAt).toMillis());
    }

}
