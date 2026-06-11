package cn.iocoder.yudao.module.devops.service.pipeline.execution.context;

import lombok.Data;

import java.time.LocalDateTime;
import java.util.Map;

/**
 * 流水线审批节点运行上下文。
 */
@Data
public class PipelineApprovalContext {

    public static final String STATUS_WAITING = "WAITING";
    public static final String STATUS_APPROVED = "APPROVED";
    public static final String STATUS_REJECTED = "REJECTED";
    public static final String STATUS_CANCELED = "CANCELED";

    private String processDefinitionKey;
    private String processInstanceId;
    private String businessKey;
    private Map<String, Object> variables;
    private Long startedBy;
    private LocalDateTime startedAt;
    private String status;
    private Integer bpmStatus;
    private String reason;
    private LocalDateTime finishedAt;

}
