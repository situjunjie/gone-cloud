package cn.iocoder.yudao.module.devops.controller.admin.pipeline.vo;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;

@Schema(description = "管理后台 - DevOps 流水线命令模板 Response VO")
@Data
public class PipelineCommandTemplateRespVO {

    @Schema(description = "模板标识", requiredMode = Schema.RequiredMode.REQUIRED, example = "maven_test")
    private String templateKey;

    @Schema(description = "模板名称", requiredMode = Schema.RequiredMode.REQUIRED, example = "Maven 单元测试")
    private String templateName;

    @Schema(description = "节点类型", requiredMode = Schema.RequiredMode.REQUIRED, example = "UNIT_TEST")
    private String nodeType;

    @Schema(description = "命令", requiredMode = Schema.RequiredMode.REQUIRED, example = "mvn test")
    private String command;

    @Schema(description = "产物路径模式", example = "**/target/*.jar")
    private String artifactPattern;

    @Schema(description = "测试报告路径模式", example = "**/surefire-reports/*.xml")
    private String reportPattern;

    @Schema(description = "描述")
    private String description;

    @Schema(description = "是否启用", requiredMode = Schema.RequiredMode.REQUIRED, example = "true")
    private Boolean enabled;

}
