package cn.iocoder.yudao.module.devops.controller.admin.repositoryprovider.vo;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;

@Schema(description = "管理后台 - DevOps 代码源 GitLab 项目 Response VO")
@Data
public class RepositoryProviderProjectRespVO {

    @Schema(description = "GitLab 项目编号", requiredMode = Schema.RequiredMode.REQUIRED, example = "10001")
    private Long externalProjectId;

    @Schema(description = "项目名称", requiredMode = Schema.RequiredMode.REQUIRED, example = "gone-cloud")
    private String name;

    @Schema(description = "项目完整路径", requiredMode = Schema.RequiredMode.REQUIRED, example = "group/gone-cloud")
    private String pathWithNamespace;

    @Schema(description = "项目页面地址", example = "https://gitlab.example.com/group/gone-cloud")
    private String webUrl;

    @Schema(description = "HTTP 克隆地址", example = "https://gitlab.example.com/group/gone-cloud.git")
    private String httpUrlToRepo;

    @Schema(description = "SSH 克隆地址", example = "git@gitlab.example.com:group/gone-cloud.git")
    private String sshUrlToRepo;

    @Schema(description = "默认分支", example = "master")
    private String defaultBranch;

    @Schema(description = "可见性", example = "private")
    private String visibility;

}
