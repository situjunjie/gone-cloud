package cn.iocoder.yudao.module.devops.controller.admin.environment.vo;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;

import java.util.List;
import java.util.Map;

@Schema(description = "管理后台 - DevOps 环境 Kubernetes Service Response VO")
@Data
public class EnvironmentKubernetesServiceRespVO {

    @Schema(description = "Service 名称", requiredMode = Schema.RequiredMode.REQUIRED, example = "yudao-server")
    private String name;

    @Schema(description = "Namespace", requiredMode = Schema.RequiredMode.REQUIRED, example = "default")
    private String namespace;

    @Schema(description = "Service 类型", example = "ClusterIP")
    private String type;

    @Schema(description = "Cluster IP", example = "10.96.0.1")
    private String clusterIp;

    @Schema(description = "External IP 列表")
    private List<String> externalIps;

    @Schema(description = "端口列表")
    private List<EnvironmentKubernetesServicePortRespVO> ports;

    @Schema(description = "Selector")
    private Map<String, String> selector;

    @Schema(description = "创建时间")
    private String creationTimestamp;

}
