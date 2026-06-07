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

    @Schema(description = "运行状态：0 排队中 1 运行中 2 成功 3 失败 4 已取消", example = "1")
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

    @Schema(description = "节点运行态列表", requiredMode = Schema.RequiredMode.REQUIRED)
    private List<Node> nodes;

    @Schema(description = "运行节点 Response VO")
    @Data
    public static class Node {

        @Schema(description = "展示节点编号", requiredMode = Schema.RequiredMode.REQUIRED, example = "checkout")
        private String nodeId;

        @Schema(description = "展示节点类型", requiredMode = Schema.RequiredMode.REQUIRED, example = "CHECKOUT")
        private String type;

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

        @Schema(description = "运行摘要")
        private String summary;

        @Schema(description = "错误信息")
        private String errorMessage;

        @Schema(description = "开始时间")
        private LocalDateTime startedAt;

        @Schema(description = "结束时间")
        private LocalDateTime finishedAt;

        @Schema(description = "结果摘要")
        private Map<String, Object> result;

        @Schema(description = "是否有详情按钮", requiredMode = Schema.RequiredMode.REQUIRED, example = "true")
        private Boolean hasDetail;

        @Schema(description = "详情类型：RUN_LOGS / CODE_MERGE_CONFLICT")
        private String detailType;

        @Schema(description = "冲突数量", example = "2")
        private Integer conflictCount;

    }

}
