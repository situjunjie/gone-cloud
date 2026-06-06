package cn.iocoder.yudao.module.devops.controller.admin.repositoryprovider.vo;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;

import java.time.LocalDateTime;

@Schema(description = "管理后台 - DevOps 代码源 Response VO")
@Data
public class RepositoryProviderRespVO {

    @Schema(description = "代码源编号", requiredMode = Schema.RequiredMode.REQUIRED, example = "1024")
    private Long id;

    @Schema(description = "代码源名称", requiredMode = Schema.RequiredMode.REQUIRED, example = "公司 GitLab")
    private String name;

    @Schema(description = "提供方类型，参见 dev_repo_provider_type", requiredMode = Schema.RequiredMode.REQUIRED, example = "GITLAB")
    private String providerType;

    @Schema(description = "代码托管平台地址", requiredMode = Schema.RequiredMode.REQUIRED, example = "https://gitlab.example.com")
    private String serverUrl;

    @Schema(description = "API 地址", example = "https://gitlab.example.com/api/v4")
    private String apiUrl;

    @Schema(description = "认证类型，参见 dev_repo_provider_auth_type", requiredMode = Schema.RequiredMode.REQUIRED, example = "ACCESS_TOKEN")
    private String authType;

    @Schema(description = "令牌掩码", example = "****abcd")
    private String tokenMask;

    @Schema(description = "授权范围")
    private String scopes;

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
