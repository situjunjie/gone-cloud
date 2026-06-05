package cn.iocoder.yudao.module.devops.controller.admin.application.vo;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotNull;
import lombok.Data;

import java.util.List;

@Schema(description = "管理后台 - DevOps 应用环境替换 Request VO")
@Data
public class ApplicationUpdateEnvsReqVO {

    @Schema(description = "应用编号", requiredMode = Schema.RequiredMode.REQUIRED, example = "1024")
    @NotNull(message = "应用编号不能为空")
    private Long appId;

    @Schema(description = "应用环境列表", requiredMode = Schema.RequiredMode.REQUIRED)
    @Valid
    @NotNull(message = "应用环境列表不能为空")
    private List<ApplicationEnvSaveReqVO> envs;

}
