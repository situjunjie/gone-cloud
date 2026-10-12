package cn.iocoder.yudao.module.devops.service.deployment.context;

import lombok.Data;

import java.time.LocalDateTime;

/**
 * 容器部署配置快照。
 */
@Data
public class ContainerDeployConfigContext {

    private Long orchestrationId;
    private LocalDateTime orchestrationUpdateTime;
    private String infraType;
    private String deployMode;
    private String workloadKind;
    private String namespace;
    private String workloadName;
    private String manifestYaml;
    private String renderedManifestYaml;
    private String containerName;
    private String imageExpression;
    private String image;
    private Integer replicas;
    private Integer rolloutTimeoutSeconds;

}
