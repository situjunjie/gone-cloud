package cn.iocoder.yudao.module.devops.controller.admin.artifactregistry.vo;

import cn.iocoder.yudao.framework.common.enums.CommonStatusEnum;
import cn.iocoder.yudao.framework.common.validation.InEnum;
import cn.iocoder.yudao.module.devops.enums.ArtifactRegistryAuthTypeEnum;
import cn.iocoder.yudao.module.devops.enums.ArtifactRegistryProviderTypeEnum;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import lombok.Data;

@Schema(description = "管理后台 - DevOps 制品仓库创建/修改 Request VO")
@Data
public class ArtifactRegistrySaveReqVO {

    @Schema(description = "制品仓库编号，修改时必填", example = "1024")
    private Long id;

    @Schema(description = "制品仓库名称", requiredMode = Schema.RequiredMode.REQUIRED, example = "公司 Nexus")
    @NotBlank(message = "制品仓库名称不能为空")
    @Size(max = 64, message = "制品仓库名称长度不能超过 64 个字符")
    private String name;

    @Schema(description = "提供方类型，参见 dev_artifact_registry_provider_type", requiredMode = Schema.RequiredMode.REQUIRED,
            example = "NEXUS3")
    @NotBlank(message = "提供方类型不能为空")
    @InEnum(ArtifactRegistryProviderTypeEnum.class)
    private String providerType;

    @Schema(description = "服务地址", requiredMode = Schema.RequiredMode.REQUIRED, example = "https://nexus.example.com")
    @NotBlank(message = "服务地址不能为空")
    @Size(max = 255, message = "服务地址长度不能超过 255 个字符")
    private String serverUrl;

    @Schema(description = "认证类型，参见 dev_artifact_registry_auth_type", requiredMode = Schema.RequiredMode.REQUIRED,
            example = "USERNAME_PASSWORD")
    @NotBlank(message = "认证类型不能为空")
    @InEnum(ArtifactRegistryAuthTypeEnum.class)
    private String authType;

    @Schema(description = "用户名", requiredMode = Schema.RequiredMode.REQUIRED, example = "admin")
    @Size(max = 128, message = "用户名长度不能超过 128 个字符")
    private String username;

    @Schema(description = "密码，创建时必填；修改时为空则保持原值")
    @Size(max = 4000, message = "密码长度不能超过 4000 个字符")
    private String password;

    @Schema(description = "状态，参见 common_status", requiredMode = Schema.RequiredMode.REQUIRED, example = "0")
    @NotNull(message = "状态不能为空")
    @InEnum(CommonStatusEnum.class)
    private Integer status;

    @Schema(description = "备注")
    @Size(max = 512, message = "备注长度不能超过 512 个字符")
    private String remark;

}
