package cn.iocoder.yudao.module.devops.controller.admin.pipelinerun.vo;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;

/**
 * 管理后台 - DevOps 流水线运行日志流完成 Response VO。
 */
@Schema(description = "管理后台 - DevOps 流水线运行日志流完成 Response VO")
@Data
public class PipelineRunLogStreamCompleteRespVO {

    /**
     * 流水线运行编号。
     */
    private Long pipelineRunId;
    /**
     * 步骤运行日志编号。
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
     * 步骤状态。
     */
    private String status;
    /**
     * 是否终态事件。
     */
    private Boolean terminal;
    /**
     * 持久化日志文件地址。
     */
    private String logFileUrl;
    /**
     * 日志归档状态。
     */
    private String logArchiveStatus;

}
