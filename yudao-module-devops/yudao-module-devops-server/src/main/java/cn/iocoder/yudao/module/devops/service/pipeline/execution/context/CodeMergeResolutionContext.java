package cn.iocoder.yudao.module.devops.service.pipeline.execution.context;

import lombok.Data;

import java.time.LocalDateTime;

@Data
public class CodeMergeResolutionContext {

    private String filePath;
    private String resolutionType;
    private String resolvedContent;
    private String comment;
    private Long resolvedBy;
    private LocalDateTime resolvedAt;

}
