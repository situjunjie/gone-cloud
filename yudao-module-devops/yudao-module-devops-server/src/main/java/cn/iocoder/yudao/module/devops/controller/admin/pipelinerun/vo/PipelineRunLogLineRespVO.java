package cn.iocoder.yudao.module.devops.controller.admin.pipelinerun.vo;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;

import java.time.LocalDateTime;

@Schema(description = "管理后台 - DevOps 流水线运行行级日志 Response VO")
@Data
public class PipelineRunLogLineRespVO {

    private Long id;
    private Long pipelineRunId;
    private Long runLogId;
    private String stageId;
    private String jobId;
    private String stepId;
    private Long lineNo;
    private String streamType;
    private String content;
    private LocalDateTime createTime;

}
