package cn.iocoder.yudao.module.devops.controller.admin.artifactregistry.vo;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;

import java.time.LocalDateTime;

@Schema(description = "管理后台 - DevOps 制品仓库 Response VO")
@Data
public class ArtifactRegistryRespVO {

    @Schema(description = "制品仓库编号", requiredMode = Schema.RequiredMode.REQUIRED, example = "1024")
    private Long id;

    @Schema(description = "制品仓库名称", requiredMode = Schema.RequiredMode.REQUIRED, example = "公司 Nexus")
    private String name;

    @Schema(description = "提供方类型，参见 dev_artifact_registry_provider_type", requiredMode = Schema.RequiredMode.REQUIRED,
            example = "NEXUS3")
    private String providerType;

    @Schema(description = "服务地址", requiredMode = Schema.RequiredMode.REQUIRED, example = "https://nexus.example.com")
    private String serverUrl;

    @Schema(description = "认证类型，参见 dev_artifact_registry_auth_type", requiredMode = Schema.RequiredMode.REQUIRED,
            example = "USERNAME_PASSWORD")
    private String authType;

    @Schema(description = "用户名", example = "admin")
    private String username;

    @Schema(description = "密码掩码", example = "****abcd")
    private String passwordMask;

    @Schema(description = "状态，参见 common_status", requiredMode = Schema.RequiredMode.REQUIRED, example = "0")
    private Integer status;

    @Schema(description = "最近检测时间")
    private LocalDateTime lastCheckTime;

    @Schema(description = "最近检测状态，0 成功 1 失败", example = "0")
    private Integer lastCheckStatus;

    @Schema(description = "最近检测结果")
    private String lastCheckMessage;

    @Schema(description = "备注")
    private String remark;

    @Schema(description = "创建时间", requiredMode = Schema.RequiredMode.REQUIRED)
    private LocalDateTime createTime;

}
