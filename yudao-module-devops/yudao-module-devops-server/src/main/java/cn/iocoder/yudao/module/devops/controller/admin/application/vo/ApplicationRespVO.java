package cn.iocoder.yudao.module.devops.controller.admin.application.vo;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;

import java.time.LocalDateTime;
import java.util.List;

@Schema(description = "管理后台 - DevOps 应用 Response VO")
@Data
public class ApplicationRespVO {

    @Schema(description = "应用编号", requiredMode = Schema.RequiredMode.REQUIRED, example = "1024")
    private Long id;

    @Schema(description = "应用标识", requiredMode = Schema.RequiredMode.REQUIRED, example = "gone-cloud")
    private String appKey;

    @Schema(description = "应用名称", requiredMode = Schema.RequiredMode.REQUIRED, example = "Gone Cloud")
    private String name;

    @Schema(description = "应用描述")
    private String description;

    @Schema(description = "应用图标")
    private String icon;

    @Schema(description = "代码库提供方类型，参见 dev_repo_provider_type", requiredMode = Schema.RequiredMode.REQUIRED, example = "GITLAB")
    private String repoProviderType;

    @Schema(description = "代码库唯一标识", requiredMode = Schema.RequiredMode.REQUIRED, example = "group/gone-cloud")
    private String repoIdentifier;

    @Schema(description = "代码库地址", requiredMode = Schema.RequiredMode.REQUIRED, example = "https://gitlab.example.com/group/gone-cloud")
    private String repoUrl;

    @Schema(description = "默认主干分支", requiredMode = Schema.RequiredMode.REQUIRED, example = "master")
    private String defaultBranchName;

    @Schema(description = "负责人用户编号", requiredMode = Schema.RequiredMode.REQUIRED, example = "1")
    private Long ownerUserId;

    @Schema(description = "状态，参见 common_status", requiredMode = Schema.RequiredMode.REQUIRED, example = "0")
    private Integer status;

    @Schema(description = "备注")
    private String remark;

    @Schema(description = "应用环境列表")
    private List<ApplicationEnvRespVO> envs;

    @Schema(description = "创建时间", requiredMode = Schema.RequiredMode.REQUIRED)
    private LocalDateTime createTime;

}
