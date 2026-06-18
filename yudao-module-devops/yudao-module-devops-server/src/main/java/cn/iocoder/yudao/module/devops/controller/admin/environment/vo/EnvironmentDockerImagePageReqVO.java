package cn.iocoder.yudao.module.devops.controller.admin.environment.vo;

import cn.iocoder.yudao.framework.common.pojo.PageParam;
import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;
import lombok.EqualsAndHashCode;

@Schema(description = "管理后台 - DevOps Docker 镜像分页 Request VO")
@Data
@EqualsAndHashCode(callSuper = true)
public class EnvironmentDockerImagePageReqVO extends PageParam {

    @Schema(description = "环境编号", requiredMode = Schema.RequiredMode.REQUIRED, example = "1024")
    private Long id;

    @Schema(description = "镜像仓库/标签关键字", example = "nginx")
    private String keyword;

    @Schema(description = "是否只看悬空镜像", example = "false")
    private Boolean dangling;

    @Schema(description = "是否只看未使用镜像", example = "false")
    private Boolean unused;

    @Schema(description = "是否刷新缓存并重新查询 Docker daemon", example = "false")
    private Boolean refreshCache;

}
