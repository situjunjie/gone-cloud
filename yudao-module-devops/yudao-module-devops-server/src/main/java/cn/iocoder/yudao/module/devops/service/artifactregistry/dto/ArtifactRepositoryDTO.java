package cn.iocoder.yudao.module.devops.service.artifactregistry.dto;

import lombok.Data;

/**
 * Nexus 仓库元数据 DTO。
 */
@Data
public class ArtifactRepositoryDTO {

    private String repositoryName;
    private String format;
    private String repositoryType;
    private String url;
    private Boolean online;

}
