package cn.iocoder.yudao.module.devops.controller.admin.pipeline.vo;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;

import java.util.List;
import java.util.Map;

@Schema(description = "管理后台 - DevOps 流水线节点类型 Response VO")
@Data
public class PipelineNodeTypeRespVO {

    @Schema(description = "节点类型", requiredMode = Schema.RequiredMode.REQUIRED, example = "UNIT_TEST")
    private String type;

    @Schema(description = "节点名称", requiredMode = Schema.RequiredMode.REQUIRED, example = "单元测试")
    private String name;

    @Schema(description = "分类", requiredMode = Schema.RequiredMode.REQUIRED, example = "JENKINS")
    private String category;

    @Schema(description = "图标", example = "test-tube")
    private String icon;

    @Schema(description = "是否启用", requiredMode = Schema.RequiredMode.REQUIRED, example = "true")
    private Boolean enabled;

    @Schema(description = "禁用原因")
    private String disabledReason;

    @Schema(description = "默认名称", requiredMode = Schema.RequiredMode.REQUIRED, example = "单元测试")
    private String defaultName;

    @Schema(description = "默认参数")
    private Map<String, Object> defaultParams;

    @Schema(description = "参数 schema")
    private Map<String, Object> paramSchema;

    @Schema(description = "命令模板列表")
    private List<PipelineCommandTemplateRespVO> commandTemplates;

}
