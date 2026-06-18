package cn.iocoder.yudao.module.devops.controller.admin.pipeline.vo;

import io.swagger.v3.oas.annotations.media.Schema;
import cn.iocoder.yudao.module.devops.framework.pipeline.runtime.PipelineCacheConfig;
import lombok.Data;

import java.time.LocalDateTime;

@Schema(description = "管理后台 - DevOps 流水线定义版本 Response VO")
@Data
public class PipelineDefinitionVersionRespVO {

    @Schema(description = "版本编号", requiredMode = Schema.RequiredMode.REQUIRED, example = "1024")
    private Long id;

    @Schema(description = "流水线定义编号", requiredMode = Schema.RequiredMode.REQUIRED, example = "1")
    private Long definitionId;

    @Schema(description = "版本号", requiredMode = Schema.RequiredMode.REQUIRED, example = "1")
    private Integer versionNo;

    @Schema(description = "版本名称", example = "v1")
    private String versionName;

    @Schema(description = "版本状态，参见 dev_pipeline_definition_version_status", requiredMode = Schema.RequiredMode.REQUIRED, example = "0")
    private Integer versionStatus;

    @Schema(description = "画布 JSON")
    private String diagramJson;

    @Schema(description = "流水线 YAML")
    private String specJson;

    @Schema(description = "节点 schema 版本", example = "1.0")
    private String nodeSchemaVersion;

    @Schema(description = "缓存目录配置")
    private PipelineCacheConfig cacheConfig;

    @Schema(description = "回退来源版本编号", example = "6")
    private Long rollbackFromVersionId;

    @Schema(description = "回退来源版本号", example = "6")
    private Integer rollbackFromVersionNo;

    @Schema(description = "回退发生时的当前版本编号", example = "10")
    private Long basedOnCurrentVersionId;

    @Schema(description = "回退发生时的当前版本号", example = "10")
    private Integer basedOnCurrentVersionNo;

    @Schema(description = "回退原因")
    private String rollbackReason;

    @Schema(description = "校验结果 JSON")
    private String validationResultJson;

    @Schema(description = "发布时间")
    private LocalDateTime publishedAt;

    @Schema(description = "发布人用户编号", example = "1")
    private Long publishedBy;

    @Schema(description = "创建时间")
    private LocalDateTime createTime;

}
