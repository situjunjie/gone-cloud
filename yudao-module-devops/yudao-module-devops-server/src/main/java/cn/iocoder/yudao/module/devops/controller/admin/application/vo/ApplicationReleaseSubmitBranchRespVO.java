package cn.iocoder.yudao.module.devops.controller.admin.application.vo;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;

import java.util.List;

@Schema(description = "管理后台 - DevOps 应用发布提交分支 Response VO")
@Data
public class ApplicationReleaseSubmitBranchRespVO {

    @Schema(description = "应用环境关系编号", requiredMode = Schema.RequiredMode.REQUIRED, example = "2048")
    private Long applicationEnvId;

    @Schema(description = "同步后当前环境已提交部署的变更编号列表", requiredMode = Schema.RequiredMode.REQUIRED,
            example = "[1024, 1025]")
    private List<Long> mountedChangeIds;

    @Schema(description = "本次从当前环境退出部署的变更编号列表", requiredMode = Schema.RequiredMode.REQUIRED,
            example = "[1026]")
    private List<Long> unmountedChangeIds;

    @Schema(description = "流水线运行编号；changeIds 为空时不触发流水线，返回空", example = "8192")
    private Long pipelineRunId;

    @Schema(description = "流水线运行状态，参见 dev_pipeline_run_status；changeIds 为空时返回空", example = "1")
    private Integer runStatus;

}
