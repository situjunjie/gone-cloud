package cn.iocoder.yudao.module.devops.controller.admin.application.vo;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;

import java.time.LocalDateTime;
import java.util.List;

@Schema(description = "管理后台 - DevOps 应用发布流水线 Response VO")
@Data
public class ApplicationReleasePipelineRespVO {

    public static final String EMPTY_REASON_NO_PIPELINE_DEFINITION = "NO_PIPELINE_DEFINITION";
    public static final String EMPTY_REASON_NO_PUBLISHED_VERSION = "NO_PUBLISHED_VERSION";
    public static final String EMPTY_REASON_SPEC_INVALID = "SPEC_INVALID";

    @Schema(description = "流水线定义编号", example = "2048")
    private Long definitionId;

    @Schema(description = "流水线名称", example = "测试环境流水线")
    private String definitionName;

    @Schema(description = "流水线标识", example = "app-env-1001")
    private String definitionKey;

    @Schema(description = "已发布版本编号", example = "4096")
    private Long publishedVersionId;

    @Schema(description = "版本号", example = "1")
    private Integer versionNo;

    @Schema(description = "版本名称", example = "v1")
    private String versionName;

    @Schema(description = "发布时间")
    private LocalDateTime publishedAt;

    @Schema(description = "发布人用户编号", example = "1")
    private Long publishedBy;

    @Schema(description = "线性展示节点列表", requiredMode = Schema.RequiredMode.REQUIRED)
    private List<ApplicationReleasePipelineNodeRespVO> nodes;

    @Schema(description = "节点连线列表", requiredMode = Schema.RequiredMode.REQUIRED)
    private List<ApplicationReleasePipelineEdgeRespVO> edges;

    @Schema(description = "空状态原因：NO_PIPELINE_DEFINITION / NO_PUBLISHED_VERSION / SPEC_INVALID")
    private String emptyReason;

}
