package cn.iocoder.yudao.module.devops.controller.admin.pipelinerun.vo;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;

@Schema(description = "管理后台 - DevOps 代码合并冲突 Response VO")
@Data
public class CodeMergeConflictRespVO {

    private String filePath;
    private String conflictType;
    private String status;
    private Boolean text;
    private Long contentSize;
    private Integer lineCount;
    private String charset;
    private String unsupportedReason;

}
