package cn.iocoder.yudao.module.devops.controller.admin.environment.vo;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;

import java.util.List;
import java.util.Map;

@Schema(description = "管理后台 - DevOps Docker 镜像 Response VO")
@Data
public class EnvironmentDockerImageRespVO {

    @Schema(description = "镜像 ID", example = "sha256:abc")
    private String id;

    @Schema(description = "短镜像 ID", example = "abc123")
    private String shortId;

    @Schema(description = "仓库标签")
    private List<String> repoTags;

    @Schema(description = "仓库摘要")
    private List<String> repoDigests;

    @Schema(description = "创建时间，Unix 秒", example = "1780000000")
    private Long created;

    @Schema(description = "镜像大小，字节", example = "104857600")
    private Long size;

    @Schema(description = "虚拟大小，字节", example = "104857600")
    private Long virtualSize;

    @Schema(description = "Labels")
    private Map<String, String> labels;

    @Schema(description = "使用该镜像的容器数量", example = "2")
    private Integer usedContainerCount;

    @Schema(description = "使用该镜像的容器名称")
    private List<String> usedContainerNames;

    @Schema(description = "关联的 Compose 项目")
    private List<String> composeProjects;

    @Schema(description = "是否未被容器使用")
    private Boolean unused;

    @Schema(description = "是否悬空镜像")
    private Boolean dangling;

}
