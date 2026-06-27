package cn.iocoder.yudao.module.devops.controller.admin.environment.vo;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;

@Schema(description = "管理后台 - DevOps 环境 Kubernetes 资源 YAML Response VO")
@Data
public class EnvironmentKubernetesResourceYamlRespVO {

    @Schema(description = "资源类型", requiredMode = Schema.RequiredMode.REQUIRED, example = "DEPLOYMENT")
    private String kind;

    @Schema(description = "资源名称", requiredMode = Schema.RequiredMode.REQUIRED, example = "yudao-server")
    private String name;

    @Schema(description = "Namespace", example = "prod")
    private String namespace;

    @Schema(description = "YAML 内容", requiredMode = Schema.RequiredMode.REQUIRED)
    private String yaml;

}
