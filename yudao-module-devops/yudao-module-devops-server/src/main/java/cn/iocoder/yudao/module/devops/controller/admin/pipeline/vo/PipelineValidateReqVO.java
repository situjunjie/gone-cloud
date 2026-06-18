package cn.iocoder.yudao.module.devops.controller.admin.pipeline.vo;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import cn.iocoder.yudao.module.devops.framework.pipeline.runtime.PipelineCacheConfig;
import lombok.Data;

@Schema(description = "管理后台 - DevOps 流水线校验 Request VO")
@Data
public class PipelineValidateReqVO {

    @Schema(description = "应用环境关系编号", example = "1")
    private Long applicationEnvId;

    @Schema(description = "画布 JSON")
    private String diagramJson;

    @Schema(description = "流水线 YAML", requiredMode = Schema.RequiredMode.REQUIRED)
    @NotBlank(message = "流水线 YAML 不能为空")
    private String specJson;

    @Schema(description = "缓存目录配置")
    private PipelineCacheConfig cacheConfig;

}
