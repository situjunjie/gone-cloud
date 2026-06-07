package cn.iocoder.yudao.module.devops.controller.admin.application.vo;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;

import java.util.List;

@Schema(description = "管理后台 - DevOps 应用发布环境详情 Response VO")
@Data
public class ApplicationReleaseEnvDetailRespVO {

    @Schema(description = "环境 Tab 信息", requiredMode = Schema.RequiredMode.REQUIRED)
    private ApplicationReleaseEnvTabRespVO env;

    @Schema(description = "已发布流水线展示信息", requiredMode = Schema.RequiredMode.REQUIRED)
    private ApplicationReleasePipelineRespVO pipeline;

    @Schema(description = "已提交到当前环境的有效分支列表", requiredMode = Schema.RequiredMode.REQUIRED)
    private List<ApplicationReleaseBranchRespVO> mountedBranches;

    @Schema(description = "不在当前环境的有效分支列表", requiredMode = Schema.RequiredMode.REQUIRED)
    private List<ApplicationReleaseBranchRespVO> unmountedBranches;

}
