package cn.iocoder.yudao.module.devops.controller.admin.environment.vo;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;

import java.util.List;
import java.util.Map;

@Schema(description = "管理后台 - DevOps Docker 容器 Response VO")
@Data
public class EnvironmentDockerContainerRespVO {

    @Schema(description = "容器 ID", requiredMode = Schema.RequiredMode.REQUIRED)
    private String id;

    @Schema(description = "短容器 ID", requiredMode = Schema.RequiredMode.REQUIRED)
    private String shortId;

    @Schema(description = "容器名称", example = "gone-server")
    private String name;

    @Schema(description = "容器名称列表")
    private List<String> names;

    @Schema(description = "镜像", example = "nginx:latest")
    private String image;

    @Schema(description = "镜像 ID")
    private String imageId;

    @Schema(description = "启动命令")
    private String command;

    @Schema(description = "状态", example = "running")
    private String state;

    @Schema(description = "状态描述", example = "Up 3 minutes")
    private String status;

    @Schema(description = "创建时间，Unix 秒")
    private Long created;

    @Schema(description = "端口列表")
    private List<EnvironmentDockerContainerPortRespVO> ports;

    @Schema(description = "标签")
    private Map<String, String> labels;

    @Schema(description = "是否可打开终端")
    private Boolean terminalEnabled;

}
