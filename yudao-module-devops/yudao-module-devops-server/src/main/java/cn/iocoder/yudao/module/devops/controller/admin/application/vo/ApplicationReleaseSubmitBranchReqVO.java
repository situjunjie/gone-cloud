package cn.iocoder.yudao.module.devops.controller.admin.application.vo;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotNull;
import lombok.Data;

import java.util.List;

@Schema(description = "管理后台 - DevOps 应用发布提交分支 Request VO")
@Data
public class ApplicationReleaseSubmitBranchReqVO {

    @Schema(description = "应用环境关系编号", requiredMode = Schema.RequiredMode.REQUIRED, example = "2048")
    @NotNull(message = "应用环境关系编号不能为空")
    private Long applicationEnvId;

    @Schema(description = "当前环境最终应提交部署的变更编号列表；传空数组表示全部退出部署",
            requiredMode = Schema.RequiredMode.REQUIRED, example = "[1024, 1025]")
    @NotNull(message = "变更编号列表不能为空")
    private List<@NotNull(message = "变更编号不能为空") Long> changeIds;

}
