package cn.iocoder.yudao.module.devops.controller.admin.environment.vo;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;

import java.util.List;

@Schema(description = "管理后台 - DevOps 环境 Kubernetes Deployment Response VO")
@Data
public class EnvironmentKubernetesDeploymentRespVO {

    @Schema(description = "Deployment 名称", requiredMode = Schema.RequiredMode.REQUIRED, example = "yudao-server")
    private String name;

    @Schema(description = "Namespace", requiredMode = Schema.RequiredMode.REQUIRED, example = "default")
    private String namespace;

    @Schema(description = "期望副本数", example = "3")
    private Integer replicas;

    @Schema(description = "Ready 副本数", example = "2")
    private Integer readyReplicas;

    @Schema(description = "Available 副本数", example = "2")
    private Integer availableReplicas;

    @Schema(description = "Updated 副本数", example = "3")
    private Integer updatedReplicas;

    @Schema(description = "容器镜像")
    private List<String> images;

    @Schema(description = "创建时间")
    private String creationTimestamp;

}
