package cn.iocoder.yudao.module.devops.service.pipeline.execution.context;

import lombok.Data;

import java.time.LocalDateTime;

@Data
public class CodeMergeItemContext {

    public static final String STATUS_PENDING = "PENDING";
    public static final String STATUS_RUNNING = "RUNNING";
    public static final String STATUS_SUCCESS = "SUCCESS";
    public static final String STATUS_CONFLICTING = "CONFLICTING";
    public static final String STATUS_FAILED = "FAILED";

    private Long changeId;
    private String changeKey;
    private String branchName;
    private String commitSha;
    private String status;
    private String mergeCommitSha;
    private LocalDateTime startedAt;
    private LocalDateTime finishedAt;
    private String errorMessage;

}
