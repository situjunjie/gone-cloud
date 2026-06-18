package cn.iocoder.yudao.module.devops.controller.admin.environment.vo;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;

import java.util.List;
import java.util.Map;

@Schema(description = "管理后台 - DevOps Docker 网络 Response VO")
@Data
public class EnvironmentDockerNetworkRespVO {

    @Schema(description = "网络 ID", example = "abc123")
    private String id;

    @Schema(description = "网络名称", example = "gone_default")
    private String name;

    @Schema(description = "Driver", example = "bridge")
    private String driver;

    @Schema(description = "Scope", example = "local")
    private String scope;

    @Schema(description = "是否内部网络")
    private Boolean internal;

    @Schema(description = "是否可附加")
    private Boolean attachable;

    @Schema(description = "Labels")
    private Map<String, String> labels;

    @Schema(description = "关联容器 ID")
    private List<String> containerIds;

}
