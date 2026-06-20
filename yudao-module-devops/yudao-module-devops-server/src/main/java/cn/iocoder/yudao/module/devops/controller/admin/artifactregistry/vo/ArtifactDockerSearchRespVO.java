package cn.iocoder.yudao.module.devops.controller.admin.artifactregistry.vo;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;

import java.time.LocalDateTime;
import java.util.List;

@Schema(description = "管理后台 - DevOps Docker 镜像搜索 Response VO")
@Data
public class ArtifactDockerSearchRespVO {

    @Schema(description = "镜像列表")
    private List<Item> items;

    @Schema(description = "Nexus 下一页 continuationToken")
    private String continuationToken;

    @Schema(description = "管理后台 - DevOps Docker 镜像搜索项 Response VO")
    @Data
    public static class Item {

        @Schema(description = "仓库名称", example = "gigi-docker")
        private String repository;

        @Schema(description = "镜像名称", example = "gigi-docker/yudao-gateway")
        private String imageName;

        @Schema(description = "镜像 Tag", example = "uat-1")
        private String tag;

        @Schema(description = "Manifest 路径")
        private String path;

        @Schema(description = "下载地址")
        private String downloadUrl;

        @Schema(description = "最近修改时间")
        private LocalDateTime lastModified;

    }

}
