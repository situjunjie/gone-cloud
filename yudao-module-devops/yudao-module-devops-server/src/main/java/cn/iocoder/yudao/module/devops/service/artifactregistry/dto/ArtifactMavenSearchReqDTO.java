package cn.iocoder.yudao.module.devops.service.artifactregistry.dto;

import lombok.Data;

/**
 * Maven 制品搜索请求 DTO。
 */
@Data
public class ArtifactMavenSearchReqDTO {

    private String repositoryName;
    private String keyword;
    private String groupId;
    private String artifactId;
    private String version;
    private String continuationToken;
    private Integer limit;

}
