package cn.iocoder.yudao.module.devops.controller.admin.pipelinerun.vo;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import lombok.Data;

@Schema(description = "管理后台 - DevOps 代码合并冲突解决 Request VO")
@Data
public class CodeMergeConflictResolutionReqVO {

    @NotBlank(message = "文件路径不能为空")
    private String filePath;

    @NotBlank(message = "解决类型不能为空")
    private String resolutionType;

    @NotNull(message = "解决后的内容不能为空")
    private String resolvedContent;

    private String comment;

}
