package cn.iocoder.yudao.module.devops.controller.admin.artifactregistry.vo;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;

import java.time.LocalDateTime;

@Schema(description = "管理后台 - DevOps 制品仓库配置 Response VO")
@Data
public class ArtifactRepositoryRespVO {

    @Schema(description = "仓库配置编号", requiredMode = Schema.RequiredMode.REQUIRED, example = "1024")
    private Long id;

    @Schema(description = "制品仓库实例编号", requiredMode = Schema.RequiredMode.REQUIRED, example = "100")
    private Long registryId;

    @Schema(description = "Nexus 仓库名称", requiredMode = Schema.RequiredMode.REQUIRED, example = "maven-releases")
    private String repositoryName;

    @Schema(description = "仓库格式，参见 dev_artifact_repository_format", requiredMode = Schema.RequiredMode.REQUIRED,
            example = "MAVEN2")
    private String format;

    @Schema(description = "仓库类型，参见 dev_artifact_repository_type", requiredMode = Schema.RequiredMode.REQUIRED,
            example = "HOSTED")
    private String repositoryType;

    @Schema(description = "仓库 URL", example = "https://nexus.example.com/repository/maven-releases/")
    private String url;

    @Schema(description = "Nexus online 状态", example = "true")
    private Boolean online;

    @Schema(description = "状态，参见 common_status", requiredMode = Schema.RequiredMode.REQUIRED, example = "0")
    private Integer status;

    @Schema(description = "最近同步时间")
    private LocalDateTime lastSyncTime;

}
