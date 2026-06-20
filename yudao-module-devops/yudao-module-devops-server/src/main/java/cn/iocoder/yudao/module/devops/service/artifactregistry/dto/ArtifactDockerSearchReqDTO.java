package cn.iocoder.yudao.module.devops.service.artifactregistry.dto;

import lombok.Data;

/**
 * Docker 镜像搜索请求 DTO。
 */
@Data
public class ArtifactDockerSearchReqDTO {

    private String repositoryName;

    private String keyword;

    private String imageName;

    private String tag;

    private String continuationToken;

}
