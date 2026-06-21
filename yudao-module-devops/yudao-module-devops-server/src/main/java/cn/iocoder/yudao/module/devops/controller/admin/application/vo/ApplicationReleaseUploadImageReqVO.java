package cn.iocoder.yudao.module.devops.controller.admin.application.vo;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import lombok.Data;

@Schema(description = "管理后台 - DevOps 应用上传镜像包部署 Request VO")
@Data
public class ApplicationReleaseUploadImageReqVO {

    @Schema(description = "应用环境关系编号", requiredMode = Schema.RequiredMode.REQUIRED, example = "2048")
    @NotNull(message = "应用环境关系编号不能为空")
    private Long applicationEnvId;

    @Schema(description = "镜像包文件 URL", requiredMode = Schema.RequiredMode.REQUIRED,
            example = "https://example.com/images/demo.docker.tar.gz")
    @NotBlank(message = "镜像包文件 URL 不能为空")
    private String fileUrl;

}
