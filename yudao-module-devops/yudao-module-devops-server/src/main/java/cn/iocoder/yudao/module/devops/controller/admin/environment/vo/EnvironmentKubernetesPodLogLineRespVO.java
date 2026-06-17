package cn.iocoder.yudao.module.devops.controller.admin.environment.vo;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;

@Schema(description = "管理后台 - DevOps 环境 Kubernetes Pod 日志行 Response VO")
@Data
public class EnvironmentKubernetesPodLogLineRespVO {

    @Schema(description = "行号", requiredMode = Schema.RequiredMode.REQUIRED, example = "1")
    private Long lineNo;

    @Schema(description = "Pod 名称", requiredMode = Schema.RequiredMode.REQUIRED, example = "gone-api-7d98f")
    private String podName;

    @Schema(description = "容器名称", requiredMode = Schema.RequiredMode.REQUIRED, example = "app")
    private String containerName;

    @Schema(description = "日志内容", requiredMode = Schema.RequiredMode.REQUIRED, example = "Started application")
    private String content;

}
