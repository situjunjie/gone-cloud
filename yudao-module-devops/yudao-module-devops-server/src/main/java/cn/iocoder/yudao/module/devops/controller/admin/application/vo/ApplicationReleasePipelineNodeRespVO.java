package cn.iocoder.yudao.module.devops.controller.admin.application.vo;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;

import java.util.List;
import java.util.Map;

@Schema(description = "管理后台 - DevOps 应用发布流水线节点 Response VO")
@Data
public class ApplicationReleasePipelineNodeRespVO {

    @Schema(description = "节点编号", requiredMode = Schema.RequiredMode.REQUIRED, example = "checkout")
    private String nodeId;

    @Schema(description = "节点类型", requiredMode = Schema.RequiredMode.REQUIRED, example = "CHECKOUT")
    private String type;

    @Schema(description = "节点模型类型", example = "JOB")
    private String nodeType;

    @Schema(description = "阶段编号", example = "build_stage")
    private String stageId;

    @Schema(description = "阶段名称", example = "构建阶段")
    private String stageName;

    @Schema(description = "任务编号", example = "package_job")
    private String jobId;

    @Schema(description = "节点名称", requiredMode = Schema.RequiredMode.REQUIRED, example = "拉取代码")
    private String name;

    @Schema(description = "是否启用", requiredMode = Schema.RequiredMode.REQUIRED, example = "true")
    private Boolean enabled;

    @Schema(description = "显示顺序", requiredMode = Schema.RequiredMode.REQUIRED, example = "1")
    private Integer displayOrder;

    @Schema(description = "节点参数")
    private Map<String, Object> params;

    @Schema(description = "超时时间，单位秒", example = "300")
    private Integer timeoutSeconds;

    @Schema(description = "重试次数", example = "1")
    private Integer retryTimes;

    @Schema(description = "失败策略")
    private String failStrategy;

    @Schema(description = "依赖任务编号列表")
    private List<String> needs;

    @Schema(description = "任务下步骤列表")
    private List<Step> steps;

    @Schema(description = "管理后台 - DevOps 应用发布流水线任务步骤 Response VO")
    @Data
    public static class Step {

        @Schema(description = "步骤编号", requiredMode = Schema.RequiredMode.REQUIRED, example = "package_command_step")
        private String stepId;

        @Schema(description = "步骤名称", requiredMode = Schema.RequiredMode.REQUIRED, example = "执行 Maven 构建命令")
        private String name;

        @Schema(description = "步骤类型", requiredMode = Schema.RequiredMode.REQUIRED, example = "Command")
        private String step;

        @Schema(description = "是否启用", requiredMode = Schema.RequiredMode.REQUIRED, example = "true")
        private Boolean enabled;

        @Schema(description = "显示顺序", requiredMode = Schema.RequiredMode.REQUIRED, example = "1")
        private Integer displayOrder;

        @Schema(description = "步骤参数")
        private Map<String, Object> params;

        @Schema(description = "超时时间，单位秒", example = "300")
        private Integer timeoutSeconds;

        @Schema(description = "重试次数", example = "1")
        private Integer retryTimes;

        @Schema(description = "失败策略")
        private String failStrategy;

    }

}
