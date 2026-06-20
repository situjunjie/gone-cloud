package cn.iocoder.yudao.module.devops.controller.admin.artifactregistry.vo;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import lombok.Data;

@Schema(description = "管理后台 - DevOps Maven 制品搜索 Request VO")
@Data
public class ArtifactMavenSearchReqVO {

    @Schema(description = "制品仓库实例编号", requiredMode = Schema.RequiredMode.REQUIRED, example = "100")
    @NotNull(message = "制品仓库编号不能为空")
    private Long registryId;

    @Schema(description = "仓库配置编号", example = "200")
    private Long repositoryId;

    @Schema(description = "Nexus 仓库名称", example = "maven-releases")
    @Size(max = 128, message = "仓库名称长度不能超过 128 个字符")
    private String repositoryName;

    @Schema(description = "关键词", example = "yudao")
    @Size(max = 128, message = "关键词长度不能超过 128 个字符")
    private String keyword;

    @Schema(description = "Maven groupId", example = "cn.iocoder.cloud")
    @Size(max = 255, message = "groupId 长度不能超过 255 个字符")
    private String groupId;

    @Schema(description = "Maven artifactId", example = "yudao-module-devops-api")
    @Size(max = 255, message = "artifactId 长度不能超过 255 个字符")
    private String artifactId;

    @Schema(description = "Maven version/baseVersion", example = "1.0.0")
    @Size(max = 128, message = "version 长度不能超过 128 个字符")
    private String version;

    @Schema(description = "Nexus 翻页 continuationToken")
    @Size(max = 1024, message = "continuationToken 长度不能超过 1024 个字符")
    private String continuationToken;

    @Schema(description = "返回数量", example = "20")
    private Integer limit;

}
