package cn.iocoder.yudao.module.devops.controller.admin.environment.vo;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;

@Schema(description = "管理后台 - DevOps 环境连接检测 Response VO")
@Data
public class EnvironmentConnectionCheckRespVO {

    @Schema(description = "基础设施类型", requiredMode = Schema.RequiredMode.REQUIRED, example = "K8S")
    private String infraType;

    @Schema(description = "检测结果消息", requiredMode = Schema.RequiredMode.REQUIRED)
    private String message;

    @Schema(description = "Namespace 数量，K8S 环境返回")
    private Integer namespaceCount;

}
