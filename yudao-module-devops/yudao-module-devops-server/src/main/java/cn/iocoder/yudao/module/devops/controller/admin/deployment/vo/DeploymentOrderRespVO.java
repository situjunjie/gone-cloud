package cn.iocoder.yudao.module.devops.controller.admin.deployment.vo;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;

import java.time.LocalDateTime;
import java.util.Map;

@Schema(description = "管理后台 - DevOps 部署单 Response VO")
@Data
public class DeploymentOrderRespVO {

    @Schema(description = "部署单编号", requiredMode = Schema.RequiredMode.REQUIRED, example = "1024")
    private Long id;

    @Schema(description = "流水线运行编号", requiredMode = Schema.RequiredMode.REQUIRED, example = "800")
    private Long pipelineRunId;

    @Schema(description = "流水线运行日志编号", example = "900")
    private Long pipelineRunLogId;

    @Schema(description = "节点编号", requiredMode = Schema.RequiredMode.REQUIRED, example = "deploy")
    private String nodeId;

    @Schema(description = "节点类型", requiredMode = Schema.RequiredMode.REQUIRED, example = "CONTAINER_DEPLOY")
    private String nodeType;

    @Schema(description = "应用编号", requiredMode = Schema.RequiredMode.REQUIRED, example = "1")
    private Long appId;

    @Schema(description = "应用环境关系编号", requiredMode = Schema.RequiredMode.REQUIRED, example = "100")
    private Long applicationEnvId;

    @Schema(description = "环境编号", requiredMode = Schema.RequiredMode.REQUIRED, example = "10")
    private Long environmentId;

    @Schema(description = "基础设施类型", requiredMode = Schema.RequiredMode.REQUIRED, example = "K8S")
    private String infraType;

    @Schema(description = "部署类型", requiredMode = Schema.RequiredMode.REQUIRED, example = "K8S_DEPLOYMENT")
    private String deployType;

    @Schema(description = "部署状态", requiredMode = Schema.RequiredMode.REQUIRED, example = "RUNNING")
    private String deployStatus;

    @Schema(description = "当前执行次数", example = "1")
    private Integer attempt;

    @Schema(description = "当前执行阶段", example = "WAIT_ROLLOUT")
    private String currentStage;

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

    @Schema(description = "Kubernetes Namespace", example = "test")
    private String namespace;

    @Schema(description = "工作负载类型", example = "Deployment")
    private String workloadKind;

    @Schema(description = "工作负载名称", example = "gone-server")
    private String workloadName;

    @Schema(description = "容器名称", example = "server")
    private String containerName;

    @Schema(description = "目标镜像", example = "registry.example.com/gone:abc123")
    private String image;

    @Schema(description = "目标副本数", example = "2")
    private Integer replicas;

    @Schema(description = "上一个镜像")
    private String previousImage;

    @Schema(description = "上一个副本数")
    private Integer previousReplicas;

    @Schema(description = "上一个 revision")
    private String previousRevision;

    @Schema(description = "目标 revision")
    private String targetRevision;

    @Schema(description = "错误信息")
    private String errorMessage;

    @Schema(description = "配置快照")
    private Map<String, Object> config;

    @Schema(description = "部署结果")
    private Map<String, Object> result;

    @Schema(description = "Kubernetes 当前状态摘要")
    private Map<String, Object> liveStatus;

}
