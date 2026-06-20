package cn.iocoder.yudao.module.devops.controller.admin.artifactregistry.vo;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import lombok.Data;

@Schema(description = "管理后台 - DevOps Docker 镜像搜索 Request VO")
@Data
public class ArtifactDockerSearchReqVO {

    @Schema(description = "制品仓库实例编号", requiredMode = Schema.RequiredMode.REQUIRED, example = "100")
    @NotNull(message = "制品仓库编号不能为空")
    private Long registryId;

    @Schema(description = "仓库配置编号", example = "200")
    private Long repositoryId;

    @Schema(description = "Nexus Docker 仓库名称", example = "gigi-docker")
    @Size(max = 128, message = "仓库名称长度不能超过 128 个字符")
    private String repositoryName;

    @Schema(description = "关键词", example = "yudao")
    @Size(max = 128, message = "关键词长度不能超过 128 个字符")
    private String keyword;

    @Schema(description = "镜像名称", example = "gigi-docker/yudao-gateway")
    @Size(max = 255, message = "镜像名称长度不能超过 255 个字符")
    private String imageName;

    @Schema(description = "镜像 Tag", example = "uat-1")
    @Size(max = 128, message = "镜像 Tag 长度不能超过 128 个字符")
    private String tag;

    @Schema(description = "Nexus 翻页 continuationToken")
    @Size(max = 1024, message = "continuationToken 长度不能超过 1024 个字符")
    private String continuationToken;

}
