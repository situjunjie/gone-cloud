package cn.iocoder.yudao.module.devops.controller.admin.application.vo;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;

import java.util.Map;

@Schema(description = "管理后台 - DevOps 应用发布流水线节点 Response VO")
@Data
public class ApplicationReleasePipelineNodeRespVO {

    @Schema(description = "节点编号", requiredMode = Schema.RequiredMode.REQUIRED, example = "checkout")
    private String nodeId;

    @Schema(description = "节点类型", requiredMode = Schema.RequiredMode.REQUIRED, example = "CHECKOUT")
    private String type;

    @Schema(description = "节点名称", requiredMode = Schema.RequiredMode.REQUIRED, example = "拉取代码")
    private String name;

    @Schema(description = "是否启用", requiredMode = Schema.RequiredMode.REQUIRED, example = "true")
    private Boolean enabled;

    @Schema(description = "显示顺序", requiredMode = Schema.RequiredMode.REQUIRED, example = "1")
    private Integer displayOrder;

    @Schema(description = "节点参数")
    private Map<String, Object> params;

    @Schema(description = "超时时间，单位秒", example = "300")
    private Integer timeoutSeconds;

    @Schema(description = "重试次数", example = "1")
    private Integer retryTimes;

    @Schema(description = "失败策略")
    private String failStrategy;

}
