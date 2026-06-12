package cn.iocoder.yudao.module.devops.controller.admin.pipeline.vo;

import io.swagger.v3.oas.annotations.media.Schema;
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

    @Schema(description = "流水线 DSL JSON")
    private String specJson;

    @Schema(description = "节点 schema 版本", example = "1.0")
    private String nodeSchemaVersion;

    /**
     * @deprecated Jenkins 已移除，字段保留仅为兼容，值恒为 null
     */
    @Deprecated
    @Schema(description = "Jenkinsfile 文本")
    private String jenkinsfileText;

    /**
     * @deprecated Jenkins 已移除，字段保留仅为兼容，值恒为 null
     */
    @Deprecated
    @Schema(description = "Jenkinsfile SHA-256 校验和")
    private String jenkinsfileChecksum;

    @Schema(description = "校验结果 JSON")
    private String validationResultJson;

    @Schema(description = "发布时间")
    private LocalDateTime publishedAt;

    @Schema(description = "发布人用户编号", example = "1")
    private Long publishedBy;

    @Schema(description = "创建时间")
    private LocalDateTime createTime;

}
