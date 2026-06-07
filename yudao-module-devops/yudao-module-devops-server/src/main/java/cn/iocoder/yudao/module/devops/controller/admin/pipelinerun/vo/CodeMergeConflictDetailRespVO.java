package cn.iocoder.yudao.module.devops.controller.admin.pipelinerun.vo;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;
import lombok.EqualsAndHashCode;

import java.util.List;

@Schema(description = "管理后台 - DevOps 代码合并冲突详情 Response VO")
@Data
@EqualsAndHashCode(callSuper = true)
public class CodeMergeConflictDetailRespVO extends CodeMergeConflictRespVO {

    private String baseContent;
    private String oursContent;
    private String theirsContent;
    private String workingContent;
    private String resultContent;
    private Boolean contentTooLarge;
    private List<String> supportedActions;

}
