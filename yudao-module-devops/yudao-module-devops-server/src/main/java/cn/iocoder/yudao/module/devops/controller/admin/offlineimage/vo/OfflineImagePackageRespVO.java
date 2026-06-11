package cn.iocoder.yudao.module.devops.controller.admin.offlineimage.vo;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;

import java.time.LocalDateTime;

@Schema(description = "管理后台 - DevOps 离线镜像包 Response VO")
@Data
public class OfflineImagePackageRespVO {

    @Schema(description = "离线镜像包编号", requiredMode = Schema.RequiredMode.REQUIRED, example = "1024")
    private Long id;

    @Schema(description = "流水线运行编号", requiredMode = Schema.RequiredMode.REQUIRED, example = "800")
    private Long pipelineRunId;

    @Schema(description = "镜像名称", requiredMode = Schema.RequiredMode.REQUIRED, example = "registry.example.com/gone-server")
    private String imageName;

    @Schema(description = "镜像标签", requiredMode = Schema.RequiredMode.REQUIRED, example = "v1.0.0")
    private String imageTag;

    @Schema(description = "镜像摘要（SHA256）", example = "sha256:abc123...")
    private String imageDigest;

    @Schema(description = "目标架构", example = "amd64")
    private String architecture;

    @Schema(description = "离线包 OSS 访问地址", example = "https://oss-cn-hangzhou.aliyuncs.com/my-bucket/offline-images/app_v1.0.tar")
    private String ossUrl;

    @Schema(description = "包大小（字节）", example = "524288000")
    private Long packageSize;

    @Schema(description = "状态（0 打包中 1 就绪 2 失败）", requiredMode = Schema.RequiredMode.REQUIRED, example = "1")
    private Integer status;

    @Schema(description = "错误信息")
    private String errorMessage;

    @Schema(description = "创建时间", requiredMode = Schema.RequiredMode.REQUIRED)
    private LocalDateTime createTime;

    @Schema(description = "更新时间", requiredMode = Schema.RequiredMode.REQUIRED)
    private LocalDateTime updateTime;

}
