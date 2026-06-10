package cn.iocoder.yudao.module.devops.controller.admin.environment.vo;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;

import java.util.List;

@Schema(description = "管理后台 - DevOps 环境 Kubernetes Pod Response VO")
@Data
public class EnvironmentKubernetesPodRespVO {

    @Schema(description = "Pod 名称", requiredMode = Schema.RequiredMode.REQUIRED, example = "yudao-server-6d8f9c7f77-xm4gh")
    private String name;

    @Schema(description = "Namespace", requiredMode = Schema.RequiredMode.REQUIRED, example = "default")
    private String namespace;

    @Schema(description = "Pod 阶段", example = "Running")
    private String phase;

    @Schema(description = "Ready 容器数", example = "1")
    private Integer readyContainerCount;

    @Schema(description = "总容器数", example = "2")
    private Integer totalContainerCount;

    @Schema(description = "重启次数", example = "0")
    private Integer restartCount;

    @Schema(description = "所在节点", example = "worker-1")
    private String nodeName;

    @Schema(description = "Pod IP", example = "10.244.1.12")
    private String podIp;

    @Schema(description = "容器名称列表")
    private List<String> containerNames;

    @Schema(description = "是否可打开终端")
    private Boolean terminalEnabled;

    @Schema(description = "创建时间")
    private String creationTimestamp;

}
