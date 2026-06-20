package cn.iocoder.yudao.module.devops.service.artifactregistry.dto;

import lombok.Data;

import java.time.LocalDateTime;
import java.util.List;

/**
 * Docker 镜像搜索结果 DTO。
 */
@Data
public class ArtifactDockerSearchResultDTO {

    private List<Item> items;

    private String continuationToken;

    @Data
    public static class Item {

        private String repository;

        private String imageName;

        private String tag;

        private String path;

        private String downloadUrl;

        private LocalDateTime lastModified;

    }

}
