package cn.iocoder.yudao.module.devops.service.pipeline.execution.context;

import lombok.Data;

@Data
public class CodeMergeConflictContext {

    public static final String STATUS_UNRESOLVED = "UNRESOLVED";
    public static final String STATUS_RESOLVED = "RESOLVED";
    public static final String STATUS_UNSUPPORTED = "UNSUPPORTED";

    private String filePath;
    private String conflictType;
    private String status;
    private String baseBlobSha;
    private String oursBlobSha;
    private String theirsBlobSha;
    private Boolean text;
    private Long contentSize;
    private Integer lineCount;
    private String charset;
    private String unsupportedReason;

}
