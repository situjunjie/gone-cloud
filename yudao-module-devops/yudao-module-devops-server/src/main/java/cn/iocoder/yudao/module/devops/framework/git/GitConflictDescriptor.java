package cn.iocoder.yudao.module.devops.framework.git;

import lombok.Data;

@Data
public class GitConflictDescriptor {

    private String filePath;
    private String conflictType;
    private String baseBlobSha;
    private String oursBlobSha;
    private String theirsBlobSha;
    private Boolean text;
    private Long contentSize;
    private Integer lineCount;
    private String charset;
    private String unsupportedReason;

}
