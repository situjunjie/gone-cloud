package cn.iocoder.yudao.module.devops.controller.admin.pipeline.vo;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;

@Schema(description = "管理后台 - DevOps Jenkins 工具 Response VO")
@Data
public class PipelineJenkinsToolRespVO {

    @Schema(description = "工具类型", requiredMode = Schema.RequiredMode.REQUIRED, example = "JDK")
    private String type;

    @Schema(description = "Jenkins 工具名称", requiredMode = Schema.RequiredMode.REQUIRED, example = "jdk-17.0.12")
    private String name;

    @Schema(description = "工具安装目录", example = "/opt/jdk-17.0.12")
    private String home;

}
