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
    private String nodeId;
    private String nodeType;
    private String nodeName;
    private String logLevel;
    private String status;
    private Integer sort;
    private LocalDateTime startedAt;
    private LocalDateTime finishedAt;
    private String summary;
    private Map<String, Object> context;
    private Map<String, Object> result;
    private String errorMessage;

}
