package cn.iocoder.yudao.module.devops.controller.admin.pipeline.vo;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotNull;
import lombok.Data;

import java.util.List;

@Schema(description = "管理后台 - DevOps 流水线清理缓存 Request VO")
@Data
public class PipelineCacheClearReqVO {

    @Schema(description = "流水线定义编号", requiredMode = Schema.RequiredMode.REQUIRED, example = "1")
    @NotNull(message = "流水线定义编号不能为空")
    private Long definitionId;

    @Schema(description = "缓存目录路径；为空时清理全部")
    private List<String> paths;

}
