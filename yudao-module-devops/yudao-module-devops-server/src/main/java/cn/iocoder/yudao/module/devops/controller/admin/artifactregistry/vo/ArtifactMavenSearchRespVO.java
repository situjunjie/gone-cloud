package cn.iocoder.yudao.module.devops.controller.admin.artifactregistry.vo;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;

import java.time.LocalDateTime;
import java.util.List;

@Schema(description = "管理后台 - DevOps Maven 制品搜索 Response VO")
@Data
public class ArtifactMavenSearchRespVO {

    @Schema(description = "制品列表")
    private List<Item> items;

    @Schema(description = "Nexus 下一页 continuationToken")
    private String continuationToken;

    @Schema(description = "管理后台 - DevOps Maven 制品搜索项 Response VO")
    @Data
    public static class Item {

        @Schema(description = "仓库名称", example = "maven-releases")
        private String repository;

        @Schema(description = "groupId", example = "cn.iocoder.cloud")
        private String groupId;

        @Schema(description = "artifactId", example = "yudao-module-devops-api")
        private String artifactId;

        @Schema(description = "版本", example = "1.0.0")
        private String version;

        @Schema(description = "基础版本", example = "1.0.0-SNAPSHOT")
        private String baseVersion;

        @Schema(description = "classifier", example = "sources")
        private String classifier;

        @Schema(description = "扩展名", example = "jar")
        private String extension;

        @Schema(description = "路径")
        private String path;

        @Schema(description = "下载地址")
        private String downloadUrl;

        @Schema(description = "最近修改时间")
        private LocalDateTime lastModified;

    }

}
