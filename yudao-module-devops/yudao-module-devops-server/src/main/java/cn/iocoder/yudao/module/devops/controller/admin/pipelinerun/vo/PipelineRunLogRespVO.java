package cn.iocoder.yudao.module.devops.controller.admin.pipelinerun.vo;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;

import java.time.LocalDateTime;
import java.util.Map;

@Schema(description = "管理后台 - DevOps 流水线运行日志 Response VO")
@Data
public class PipelineRunLogRespVO {

    private Long id;
    private Long pipelineRunId;
    private Long parentId;
    private String stageId;
    private String stageName;
    private String jobId;
    private String jobName;
    private String stepId;
    private String stepType;
    private String stepName;
    private String nodeId;
    private String nodeType;
    private String nodeName;
    private String logLevel;
    private String status;
    private Integer sort;
    private Integer attempt;
    private LocalDateTime startedAt;
    private LocalDateTime finishedAt;
    private Long durationMillis;
    private String runtimeType;
    private String executorGroup;
    private String executorImage;
    private String runtimeId;
    private String runtimeName;
    private String workspacePath;
    private String summary;
    private Map<String, Object> context;
    private Map<String, Object> result;
    private String logFileUrl;
    private Boolean logTruncated;
    private String errorMessage;

}
