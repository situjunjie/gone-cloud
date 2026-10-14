package cn.iocoder.yudao.module.devops.service.deployment.context;

import lombok.Data;

import java.time.LocalDateTime;
import java.util.Map;

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
    /**
     * 本次渲染使用的变量组键值；仅用于展示层判断是否需要掩码，响应前必须置空。
     */
    private Map<String, String> variableGroupVariables;
    private String containerName;
    private String imageExpression;
    private String image;
    private Integer replicas;
    private Integer rolloutTimeoutSeconds;

}
