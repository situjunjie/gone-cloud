package cn.iocoder.yudao.module.devops.service.artifactregistry.dto;

import lombok.Data;

import java.time.LocalDateTime;
import java.util.List;

/**
 * Maven 制品搜索结果 DTO。
 */
@Data
public class ArtifactMavenSearchResultDTO {

    private List<Item> items;
    private String continuationToken;

    @Data
    public static class Item {

        private String repository;
        private String groupId;
        private String artifactId;
        private String version;
        private String baseVersion;
        private String classifier;
        private String extension;
        private String path;
        private String downloadUrl;
        private LocalDateTime lastModified;

    }

}
