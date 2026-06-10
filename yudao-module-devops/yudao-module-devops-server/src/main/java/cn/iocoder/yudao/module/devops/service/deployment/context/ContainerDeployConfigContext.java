package cn.iocoder.yudao.module.devops.service.deployment.context;

import lombok.Data;

/**
 * 容器部署配置快照。
 */
@Data
public class ContainerDeployConfigContext {

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
