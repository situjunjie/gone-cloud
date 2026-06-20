package cn.iocoder.yudao.module.devops.controller.admin.application.vo;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;

@Schema(description = "管理后台 - DevOps 应用发布当前流水线运行 Response VO")
@Data
public class ApplicationReleaseCurrentRunRespVO {

    @Schema(description = "应用环境关系编号", requiredMode = Schema.RequiredMode.REQUIRED, example = "1024")
    private Long applicationEnvId;

    @Schema(description = "是否存在流水线运行", requiredMode = Schema.RequiredMode.REQUIRED, example = "true")
    private Boolean hasRun;

    @Schema(description = "流水线运行编号", example = "2048")
    private Long pipelineRunId;

    @Schema(description = "运行状态：0 排队中 1 运行中 2 成功 3 失败 4 已取消 5 等待输入", example = "1")
    private Integer runStatus;

    @Schema(description = "触发来源", example = "APPLICATION_RELEASE_TAB")
    private String triggerType;

    @Schema(description = "触发人用户编号", example = "1")
    private Long triggerUserId;

    @Schema(description = "触发时间")
    private LocalDateTime triggeredAt;

    @Schema(description = "开始时间")
    private LocalDateTime startedAt;

    @Schema(description = "结束时间")
    private LocalDateTime finishedAt;

    @Schema(description = "错误信息")
    private String errorMessage;

    @Schema(description = "前端是否需要继续轮询", requiredMode = Schema.RequiredMode.REQUIRED, example = "true")
    private Boolean polling;

    @Schema(description = "本次发布提交的变更快照列表")
    private List<ApplicationReleaseChangeSnapshotRespVO> changeSnapshots;

    @Schema(description = "已提交到当前环境的有效分支列表", requiredMode = Schema.RequiredMode.REQUIRED)
    private List<ApplicationReleaseBranchRespVO> mountedBranches;

    @Schema(description = "节点运行态列表", requiredMode = Schema.RequiredMode.REQUIRED)
    private List<Node> nodes;

    @Schema(description = "运行节点连线列表", requiredMode = Schema.RequiredMode.REQUIRED)
    private List<ApplicationReleasePipelineEdgeRespVO> edges;

    @Schema(description = "运行节点 Response VO")
    @Data
    public static class Node {

        @Schema(description = "展示节点编号", requiredMode = Schema.RequiredMode.REQUIRED, example = "checkout")
        private String nodeId;

        @Schema(description = "展示节点类型", requiredMode = Schema.RequiredMode.REQUIRED, example = "CHECKOUT")
        private String type;

        @Schema(description = "节点模型类型", example = "JOB")
        private String nodeType;

        @Schema(description = "阶段编号", example = "build_stage")
        private String stageId;

        @Schema(description = "阶段名称", example = "构建阶段")
        private String stageName;

        @Schema(description = "任务编号", example = "package_job")
        private String jobId;

        @Schema(description = "展示节点名称", requiredMode = Schema.RequiredMode.REQUIRED, example = "拉取代码")
        private String name;

        @Schema(description = "显示顺序", requiredMode = Schema.RequiredMode.REQUIRED, example = "1")
        private Integer displayOrder;

        @Schema(description = "是否启用", requiredMode = Schema.RequiredMode.REQUIRED, example = "true")
        private Boolean enabled;

        @Schema(description = "运行日志编号", example = "900")
        private Long runLogId;

        @Schema(description = "运行节点类型", example = "CODE_MERGE")
        private String executionNodeType;

        @Schema(description = "运行状态：PENDING / RUNNING / WAITING_INPUT / SUCCESS / FAILED / CANCELED")
        private String executionStatus;

        @Schema(description = "节点通用状态：NOT_STARTED / RUNNING / BLOCKED / COMPLETED")
        private String status;

        @Schema(description = "节点状态展示文案")
        private String message;

        @Schema(description = "运行摘要")
        private String summary;

        @Schema(description = "错误信息")
        private String errorMessage;

        @Schema(description = "开始时间")
        private LocalDateTime startedAt;

        @Schema(description = "结束时间")
        private LocalDateTime finishedAt;

        @Schema(description = "执行耗时，毫秒")
        private Long durationMillis;

        @Schema(description = "结果摘要")
        private Map<String, Object> result;

        @Schema(description = "是否有详情按钮", requiredMode = Schema.RequiredMode.REQUIRED, example = "true")
        private Boolean hasDetail;

        @Schema(description = "详情类型：RUN_LOGS / CODE_MERGE / APPROVAL")
        private String detailType;

        @Schema(description = "详情引用参数")
        private Map<String, Object> detailRef;

        @Schema(description = "当前可执行动作列表")
        private List<Action> actions;

        @Schema(description = "冲突数量", example = "2")
        private Integer conflictCount;

        @Schema(description = "任务下步骤运行态列表")
        private List<Step> steps;

    }

    @Schema(description = "运行步骤 Response VO")
    @Data
    public static class Step {

        @Schema(description = "步骤编号", requiredMode = Schema.RequiredMode.REQUIRED, example = "package_command_step")
        private String stepId;

        @Schema(description = "步骤名称", requiredMode = Schema.RequiredMode.REQUIRED, example = "执行 Maven 构建命令")
        private String name;

        @Schema(description = "步骤类型", requiredMode = Schema.RequiredMode.REQUIRED, example = "Command")
        private String step;

        @Schema(description = "运行日志编号", example = "900")
        private Long runLogId;

        @Schema(description = "运行状态：PENDING / RUNNING / WAITING_INPUT / SUCCESS / FAILED / CANCELED")
        private String executionStatus;

        @Schema(description = "步骤通用状态：NOT_STARTED / RUNNING / BLOCKED / COMPLETED")
        private String status;

        @Schema(description = "步骤状态展示文案")
        private String message;

        @Schema(description = "运行摘要")
        private String summary;

        @Schema(description = "错误信息")
        private String errorMessage;

        @Schema(description = "开始时间")
        private LocalDateTime startedAt;

        @Schema(description = "结束时间")
        private LocalDateTime finishedAt;

        @Schema(description = "执行耗时，毫秒")
        private Long durationMillis;

        @Schema(description = "结果摘要")
        private Map<String, Object> result;

        @Schema(description = "是否有详情按钮", requiredMode = Schema.RequiredMode.REQUIRED, example = "true")
        private Boolean hasDetail;

        @Schema(description = "详情类型：RUN_LOGS / CODE_MERGE / APPROVAL")
        private String detailType;

        @Schema(description = "详情引用参数")
        private Map<String, Object> detailRef;

        @Schema(description = "当前可执行动作列表")
        private List<Action> actions;

        @Schema(description = "冲突数量", example = "2")
        private Integer conflictCount;

    }

    @Schema(description = "运行节点动作 Response VO")
    @Data
    public static class Action {

        @Schema(description = "动作编码", requiredMode = Schema.RequiredMode.REQUIRED, example = "RESOLVE_CODE_CONFLICT")
        private String code;

        @Schema(description = "按钮文案", requiredMode = Schema.RequiredMode.REQUIRED, example = "解决冲突")
        private String label;

        @Schema(description = "按钮样式", example = "primary")
        private String style;

        @Schema(description = "动作目标")
        private ActionTarget target;

    }

    @Schema(description = "运行节点动作目标 Response VO")
    @Data
    public static class ActionTarget {

        @Schema(description = "目标类型：ROUTE / API", requiredMode = Schema.RequiredMode.REQUIRED, example = "ROUTE")
        private String type;

        @Schema(description = "路由路径或接口地址", example = "/devops/pipeline-run/800/code-merge/conflicts")
        private String path;

        @Schema(description = "目标参数")
        private Map<String, Object> params;

    }

}
