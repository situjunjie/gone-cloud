package cn.iocoder.yudao.module.devops.controller.admin.repositoryprovider.vo;

import cn.iocoder.yudao.framework.common.enums.CommonStatusEnum;
import cn.iocoder.yudao.framework.common.validation.InEnum;
import cn.iocoder.yudao.module.devops.enums.RepositoryProviderAuthTypeEnum;
import cn.iocoder.yudao.module.devops.enums.RepositoryProviderTypeEnum;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import lombok.Data;

@Schema(description = "管理后台 - DevOps 代码源创建/修改 Request VO")
@Data
public class RepositoryProviderSaveReqVO {

    @Schema(description = "代码源编号，修改时必填", example = "1024")
    private Long id;

    @Schema(description = "代码源名称", requiredMode = Schema.RequiredMode.REQUIRED, example = "公司 GitLab")
    @NotBlank(message = "代码源名称不能为空")
    @Size(max = 64, message = "代码源名称长度不能超过 64 个字符")
    private String name;

    @Schema(description = "提供方类型，参见 dev_repo_provider_type", requiredMode = Schema.RequiredMode.REQUIRED, example = "GITLAB")
    @NotBlank(message = "提供方类型不能为空")
    @InEnum(RepositoryProviderTypeEnum.class)
    private String providerType;

    @Schema(description = "代码托管平台地址", requiredMode = Schema.RequiredMode.REQUIRED, example = "https://gitlab.example.com")
    @NotBlank(message = "代码托管平台地址不能为空")
    @Size(max = 255, message = "代码托管平台地址长度不能超过 255 个字符")
    private String serverUrl;

    @Schema(description = "API 地址", example = "https://gitlab.example.com/api/v4")
    @Size(max = 255, message = "API 地址长度不能超过 255 个字符")
    private String apiUrl;

    @Schema(description = "认证类型，参见 dev_repo_provider_auth_type", requiredMode = Schema.RequiredMode.REQUIRED, example = "ACCESS_TOKEN")
    @NotBlank(message = "认证类型不能为空")
    @InEnum(RepositoryProviderAuthTypeEnum.class)
    private String authType;

    @Schema(description = "访问令牌，创建时必填；修改时为空则保持原值")
    @Size(max = 4000, message = "访问令牌长度不能超过 4000 个字符")
    private String accessToken;

    @Schema(description = "授权范围")
    @Size(max = 512, message = "授权范围长度不能超过 512 个字符")
    private String scopes;

    @Schema(description = "状态，参见 common_status", requiredMode = Schema.RequiredMode.REQUIRED, example = "0")
    @NotNull(message = "状态不能为空")
    @InEnum(CommonStatusEnum.class)
    private Integer status;

    @Schema(description = "备注")
    @Size(max = 512, message = "备注长度不能超过 512 个字符")
    private String remark;

}
