package cn.iocoder.yudao.module.devops.controller.admin.environment.vo;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;

@Schema(description = "管理后台 - DevOps 环境 Kubernetes Namespace Response VO")
@Data
public class EnvironmentKubernetesNamespaceRespVO {

    @Schema(description = "Namespace 名称", requiredMode = Schema.RequiredMode.REQUIRED, example = "default")
    private String name;

    @Schema(description = "状态", example = "Active")
    private String status;

    @Schema(description = "创建时间，Kubernetes 原始时间字符串")
    private String creationTimestamp;

}
