package cn.iocoder.yudao.module.devops.controller.admin.application.vo;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import lombok.Data;

@Schema(description = "管理后台 - DevOps 应用创建/修改 Request VO")
@Data
public class ApplicationSaveReqVO {

    @Schema(description = "应用编号，修改时必填", example = "1024")
    private Long id;

    @Schema(description = "应用标识", requiredMode = Schema.RequiredMode.REQUIRED, example = "gone-cloud")
    @NotBlank(message = "应用标识不能为空")
    @Size(max = 64, message = "应用标识长度不能超过 64 个字符")
    private String appKey;

    @Schema(description = "应用名称", requiredMode = Schema.RequiredMode.REQUIRED, example = "Gone Cloud")
    @NotBlank(message = "应用名称不能为空")
    @Size(max = 128, message = "应用名称长度不能超过 128 个字符")
    private String name;

    @Schema(description = "应用描述")
    @Size(max = 512, message = "应用描述长度不能超过 512 个字符")
    private String description;

    @Schema(description = "应用图标")
    @Size(max = 512, message = "应用图标长度不能超过 512 个字符")
    private String icon;

    @Schema(description = "代码库提供方类型，参见 dev_repo_provider_type", requiredMode = Schema.RequiredMode.REQUIRED, example = "GITLAB")
    @NotBlank(message = "代码库提供方类型不能为空")
    private String repoProviderType;

    @Schema(description = "代码库唯一标识", requiredMode = Schema.RequiredMode.REQUIRED, example = "group/gone-cloud")
    @NotBlank(message = "代码库唯一标识不能为空")
    @Size(max = 255, message = "代码库唯一标识长度不能超过 255 个字符")
    private String repoIdentifier;

    @Schema(description = "代码库地址", requiredMode = Schema.RequiredMode.REQUIRED, example = "https://gitlab.example.com/group/gone-cloud")
    @NotBlank(message = "代码库地址不能为空")
    @Size(max = 512, message = "代码库地址长度不能超过 512 个字符")
    private String repoUrl;

    @Schema(description = "默认主干分支", requiredMode = Schema.RequiredMode.REQUIRED, example = "master")
    @NotBlank(message = "默认主干分支不能为空")
    @Size(max = 64, message = "默认主干分支长度不能超过 64 个字符")
    private String defaultBranchName;

    @Schema(description = "负责人用户编号", requiredMode = Schema.RequiredMode.REQUIRED, example = "1")
    @NotNull(message = "负责人用户编号不能为空")
    private Long ownerUserId;

    @Schema(description = "状态，参见 common_status", requiredMode = Schema.RequiredMode.REQUIRED, example = "0")
    @NotNull(message = "状态不能为空")
    private Integer status;

    @Schema(description = "备注")
    @Size(max = 512, message = "备注长度不能超过 512 个字符")
    private String remark;

}
