package cn.iocoder.yudao.module.devops.controller.admin.environment.vo;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;

@Schema(description = "管理后台 - DevOps Docker 容器日志行 Response VO")
@Data
public class EnvironmentDockerContainerLogLineRespVO {

    @Schema(description = "行号", requiredMode = Schema.RequiredMode.REQUIRED, example = "1")
    private Long lineNo;

    @Schema(description = "容器 ID", requiredMode = Schema.RequiredMode.REQUIRED)
    private String containerId;

    @Schema(description = "容器名称")
    private String containerName;

    @Schema(description = "输出流类型", requiredMode = Schema.RequiredMode.REQUIRED, example = "stdout")
    private String streamType;

    @Schema(description = "日志内容", requiredMode = Schema.RequiredMode.REQUIRED)
    private String content;

}
